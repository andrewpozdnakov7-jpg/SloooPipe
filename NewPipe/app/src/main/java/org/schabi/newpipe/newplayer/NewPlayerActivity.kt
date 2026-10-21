// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.newpipe.newplayer.data.PlayMode
import net.newpipe.newplayer.ui.NewPlayerView
import net.newpipe.newplayer.uiModel.NewPlayerUIEvent
import net.newpipe.newplayer.uiModel.NewPlayerViewModelImpl
import net.newpipe.newplayer.uiModel.UIModeState
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.SerializedCache

/** A single host and engine for local documents and explicitly selected NewPipe network videos. */
class NewPlayerActivity : AppCompatActivity() {
    private lateinit var runtime: NewPlayerRuntime
    private lateinit var model: NewPlayerViewModelImpl
    private lateinit var header: LinearLayout
    private lateinit var status: TextView
    private lateinit var videoTitle: TextView
    private lateinit var video: NewPlayerView
    private lateinit var root: LinearLayout
    private lateinit var chooseOrRetry: Button
    private var selection: Job? = null
    private var currentUri: Uri? = null
    private var network = false
    private var serviceId = -1
    private var pageUrl: String? = null
    private var handoff: String? = null
    private var requestGeneration = 0L
    private var pendingPosition: Long? = null
    private var restorePlaying = true
    private var pendingRestore = false
    private var fullscreen = false
    private var queueRequest: String? = null
    private var initialFullscreen = false

    private val openVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            Log.i(NewPlayerRuntime.TAG, "picker.cancelled")
        } else {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                Log.w(NewPlayerRuntime.TAG, "picker.transient_grant")
            }
            loadLocal(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = NewPlayerRuntime.get(application)
        model = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[NewPlayerViewModelImpl::class.java]
        if (model.newPlayer !== runtime.player) model.newPlayer = runtime.player
        readRequest(intent, savedInstanceState)
        if (!network && runtime.queue.active) runtime.releasePlayback()
        buildScreen()
        addOnPictureInPictureModeChangedListener { mode ->
            model.onPictureInPictureModeChanged(mode.isInPictureInPictureMode)
            Log.i(NewPlayerRuntime.TAG, "screen.pip=${mode.isInPictureInPictureMode}")
        }
        observePlayer()
        Log.i(NewPlayerRuntime.TAG, "screen.create network=$network restored=${savedInstanceState != null}")
        if ((savedInstanceState != null || !intent.hasExtra(EXTRA_MODE)) && runtime.player.exoPlayer.value != null) {
            video.visibility = View.VISIBLE
            videoTitle.text = savedInstanceState?.getString("videoTitle") ?: runtime.player.currentlyPlaying.value?.mediaMetadata?.title
        } else if (network) {
            loadNetwork(savedInstanceState?.getLong("position") ?: intent.getLongExtra(EXTRA_POSITION, -1), savedInstanceState?.getBoolean("playing") ?: intent.getBooleanExtra(EXTRA_PLAYING, true))
        } else {
            currentUri?.let { loadLocal(it, savedInstanceState?.getLong("position") ?: 0, savedInstanceState?.getBoolean("playing") ?: true) }
        }
        if (intent.getBooleanExtra(EXTRA_RESUME_ACTIVE, false) && runtime.queue.active) runtime.player.play()
        if (intent.getBooleanExtra(EXTRA_SHOW_QUEUE, false) && runtime.queue.active) model.changeUiMode(UIModeState.EMBEDDED_VIDEO_STREAM_SELECT, null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Notification taps have no request extras: retain the current session.
        if (!intent.hasExtra(EXTRA_MODE)) {
            if (intent.getBooleanExtra(EXTRA_RESUME_ACTIVE, false)) runtime.player.play()
            if (intent.getBooleanExtra(EXTRA_SHOW_QUEUE, false)) model.changeUiMode(UIModeState.EMBEDDED_VIDEO_STREAM_SELECT, null)
            return
        }
        setIntent(intent)
        selection?.cancel()
        runtime.releasePlayback()
        readRequest(intent, null)
        videoTitle.text = ""
        video.visibility = View.GONE
        configureAction()
        if (network) loadNetwork(intent.getLongExtra(EXTRA_POSITION, -1), intent.getBooleanExtra(EXTRA_PLAYING, true)) else status.setText(R.string.slooopipe_local_hint)
    }

    private fun readRequest(request: Intent, state: Bundle?) {
        network = (state?.getString(EXTRA_MODE) ?: request.getStringExtra(EXTRA_MODE)) == "network"
        serviceId = state?.getInt(EXTRA_SERVICE, -1) ?: request.getIntExtra(EXTRA_SERVICE, -1)
        pageUrl = state?.getString(EXTRA_PAGE) ?: request.getStringExtra(EXTRA_PAGE)
        handoff = state?.getString(EXTRA_HANDOFF) ?: request.getStringExtra(EXTRA_HANDOFF)
        requestGeneration = state?.getLong(EXTRA_GENERATION) ?: request.getLongExtra(EXTRA_GENERATION, org.schabi.newpipe.auth.AuthRuntime.generation())
        currentUri = state?.getString("localUri")?.let(Uri::parse)
        queueRequest = request.getStringExtra(EXTRA_QUEUE)
        initialFullscreen = request.getBooleanExtra(EXTRA_FULLSCREEN, false) && state == null
        if (!request.hasExtra(EXTRA_MODE) && runtime.queue.active) network = true
    }

    private fun observePlayer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.uiState.collect { state ->
                        if (fullscreen != state.uiMode.fullscreen) {
                            fullscreen = state.uiMode.fullscreen
                            header.visibility = if (fullscreen) View.GONE else View.VISIBLE
                            ViewCompat.requestApplyInsets(root)
                        }
                    }
                }
                launch { model.uiEvent.collect { if (it is NewPlayerUIEvent.OnBackPressed) finish() } }
                launch { runtime.player.errorFlow.collect { showError(it) } }
                launch {
                    runtime.player.currentlyPlaying.collect { item ->
                        if (network && item != null) {
                            videoTitle.text = item.mediaMetadata.title
                            QueueMediaRepository.request(item)?.entry?.reference?.let { reference ->
                                serviceId = reference.serviceId
                                pageUrl = reference.url
                                handoff = null
                            }
                        }
                    }
                }
                launch {
                    runtime.player.onPlayerEvent.collect { (player, _) ->
                        when (player.playbackState) {
                            Player.STATE_BUFFERING -> status.setText(R.string.slooopipe_local_loading)

                            Player.STATE_READY -> {
                                if (initialFullscreen) {
                                    initialFullscreen = false
                                    model.changeUiMode(UIModeState.FULLSCREEN_VIDEO, null)
                                }
                                if (pendingRestore) {
                                    pendingRestore = false
                                    pendingPosition?.let(player::seekTo)
                                    pendingPosition = null
                                    player.playWhenReady = restorePlaying
                                }
                                status.setText(if (player.isPlaying) R.string.slooopipe_local_playing else R.string.slooopipe_local_paused)
                            }

                            Player.STATE_ENDED -> status.setText(R.string.slooopipe_local_ended)
                        }
                    }
                }
            }
        }
    }

    private fun buildScreen() {
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val foreground = if (dark) Color.WHITE else Color.BLACK
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (dark) Color.BLACK else Color.WHITE)
        }
        header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val actions = LinearLayout(this)
        actions.addView(
            Button(this).apply {
                setText(R.string.slooopipe_local_back)
                setOnClickListener { finish() }
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        chooseOrRetry = Button(this).apply {
            setOnClickListener {
                if (network) {
                    val exo = runtime.player.exoPlayer.value
                    val entry = QueueMediaRepository.request(exo?.currentMediaItem)?.entry
                    if (exo != null && entry != null) {
                        entry.forceLoad = true
                        entry.info = null
                        exo.stop()
                        exo.prepare()
                        exo.play()
                    } else {
                        handoff = null
                        requestGeneration = org.schabi.newpipe.auth.AuthRuntime.generation()
                        loadNetwork(forceLoad = true)
                    }
                } else {
                    Log.i(NewPlayerRuntime.TAG, "picker.open")
                    try {
                        openVideo.launch(arrayOf("video/*"))
                    } catch (error: Exception) {
                        showError(error)
                    }
                }
            }
        }
        actions.addView(chooseOrRetry, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actions)
        videoTitle = TextView(this).apply { setTextColor(foreground) }
        status = TextView(this).apply {
            setTextColor(foreground)
            setText(if (network) R.string.slooopipe_local_loading else R.string.slooopipe_local_hint)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        header.setPadding(padding, 0, padding, padding)
        header.addView(videoTitle)
        header.addView(status)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        video = NewPlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            viewModel = model
            visibility = View.GONE
        }
        root.addView(video, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        configureAction()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, if (fullscreen) 0 else bars.top, bars.right, if (fullscreen) 0 else bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun configureAction() {
        setTitle(if (network) R.string.slooopipe_newplayer_title else R.string.slooopipe_local_title)
        chooseOrRetry.setText(if (network) R.string.slooopipe_newplayer_retry else R.string.slooopipe_local_choose)
    }

    private fun beginLoad(position: Long, playing: Boolean) {
        selection?.cancel()
        runtime.releasePlayback()
        PlayerHolder.getInstance().stopService()
        pendingPosition = position.takeIf { it > 0 }
        pendingRestore = true
        restorePlaying = playing
        video.visibility = View.GONE
        status.setText(R.string.slooopipe_local_loading)
    }

    private fun play(item: String) {
        runtime.currentItem = item
        video.visibility = View.VISIBLE
        runtime.player.playStream(item, PlayMode.EMBEDDED_VIDEO)
    }

    private fun loadLocal(uri: Uri, position: Long = 0, playing: Boolean = true) {
        beginLoad(position, playing)
        selection = lifecycleScope.launch {
            try {
                val document = runtime.localRepository.register(uri)
                currentUri = uri
                videoTitle.text = document.title
                Log.i(NewPlayerRuntime.TAG, "document.ready scheme=${uri.scheme} mime=${document.mime} bytes=${document.size}")
                play("local:$uri")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error)
            }
        }
    }

    private fun loadNetwork(position: Long = -1, playing: Boolean = true, forceLoad: Boolean = false) {
        beginLoad(0, playing)
        pendingRestore = false // The queue/history bridge restores the selected item once READY.
        selection = lifecycleScope.launch {
            try {
                val page = requireNotNull(pageUrl) { "NEWPLAYER_MISSING_PAGE_URL" }
                check(requestGeneration == org.schabi.newpipe.auth.AuthRuntime.generation()) { "AUTH_SESSION_CHANGED" }
                val source = queueRequest?.let { SerializedCache.getInstance().take(it, PlayQueue::class.java) }
                queueRequest = null
                val info = if (source == null || handoff != null) {
                    runtime.networkRepository.info(runtime.networkRepository.resolve(handoff, serviceId, page, forceLoad))
                } else {
                    null
                }
                check(requestGeneration == org.schabi.newpipe.auth.AuthRuntime.generation()) { "AUTH_SESSION_CHANGED" }
                val queue = source ?: SinglePlayQueue(checkNotNull(info))
                videoTitle.text = queue.item?.title
                video.visibility = View.VISIBLE
                runtime.currentItem = "queue:loading"
                runtime.queue.start(queue, info, position.takeIf { it >= 0 }, playing)
                Log.i(NewPlayerRuntime.TAG, "network.handoff_ready service=$serviceId engine=NewPlayer queued=true")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(error)
            }
        }
    }

    private fun showError(error: Exception) {
        Log.e(NewPlayerRuntime.TAG, "screen.failure network=$network error=${error.javaClass.simpleName}")
        net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.failed", error)
        if (runtime.player.exoPlayer.value != null) runtime.player.playBackMode.value = PlayMode.EMBEDDED_VIDEO
        status.setText(if (network) R.string.slooopipe_newplayer_error else R.string.slooopipe_newplayer_local_error)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXTRA_MODE, if (network) "network" else "local")
        outState.putInt(EXTRA_SERVICE, serviceId)
        outState.putString(EXTRA_PAGE, pageUrl)
        outState.putString(EXTRA_HANDOFF, handoff)
        outState.putLong(EXTRA_GENERATION, requestGeneration)
        outState.putString("localUri", currentUri?.toString())
        outState.putString("videoTitle", videoTitle.text.toString())
        outState.putLong("position", runtime.player.currentPosition)
        outState.putBoolean("playing", runtime.player.playWhenReady)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !(Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode)) {
            selection?.cancel()
            runtime.player.pause()
            // No late asynchronous getMediaItem may start playback after leaving the screen.
            if (runtime.player.exoPlayer.value == null) runtime.releasePlayback()
            Log.i(NewPlayerRuntime.TAG, "screen.stop paused=true")
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            selection?.cancel()
            // Keep an explicitly paused network queue available while browsing and enqueueing.
            // Local documents and an incomplete start still release as before.
            if (!network || runtime.player.exoPlayer.value == null) runtime.releasePlayback()
        }
        Log.i(NewPlayerRuntime.TAG, "screen.destroy finishing=$isFinishing")
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MODE = "newplayer.mode"
        private const val EXTRA_SERVICE = "newplayer.service"
        private const val EXTRA_PAGE = "newplayer.page"
        private const val EXTRA_HANDOFF = "newplayer.handoff"
        private const val EXTRA_POSITION = "newplayer.position"
        private const val EXTRA_GENERATION = "newplayer.generation"
        private const val EXTRA_QUEUE = "newplayer.queue"
        private const val EXTRA_PLAYING = "newplayer.playing"
        private const val EXTRA_FULLSCREEN = "newplayer.fullscreen"
        private const val EXTRA_RESUME_ACTIVE = "newplayer.resumeActive"
        private const val EXTRA_SHOW_QUEUE = "newplayer.showQueue"

        @JvmStatic
        fun showQueue(context: Context) {
            val request = Intent(context, NewPlayerActivity::class.java).putExtra(EXTRA_SHOW_QUEUE, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (context !is android.app.Activity) request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(request)
        }

        @JvmStatic
        fun resumeQueue(context: Context) {
            val request = Intent(context, NewPlayerActivity::class.java).putExtra(EXTRA_RESUME_ACTIVE, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (context !is android.app.Activity) request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(request)
        }

        @JvmStatic
        fun openLocal(context: Context) {
            detachLegacyPlayer(context)
            context.startActivity(Intent(context, NewPlayerActivity::class.java).putExtra(EXTRA_MODE, "local").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }

        @JvmStatic
        @JvmOverloads
        fun openNetwork(context: Context, info: StreamInfo, source: PlayQueue? = null, playing: Boolean = true, fullscreen: Boolean = false) {
            val runtime = NewPlayerRuntime.get(context.applicationContext as android.app.Application)
            val key = runtime.networkRepository.offer(info)
            val queueKey = cacheQueue(context, source ?: SinglePlayQueue(info)) ?: return
            detachLegacyPlayer(context)
            context.startActivity(
                Intent(context, NewPlayerActivity::class.java)
                    .putExtra(EXTRA_MODE, "network")
                    .putExtra(EXTRA_SERVICE, info.serviceId)
                    .putExtra(EXTRA_PAGE, info.url)
                    .putExtra(EXTRA_HANDOFF, key)
                    .putExtra(EXTRA_QUEUE, queueKey)
                    .putExtra(EXTRA_PLAYING, playing)
                    .putExtra(EXTRA_FULLSCREEN, fullscreen)
                    .putExtra(EXTRA_GENERATION, org.schabi.newpipe.auth.AuthRuntime.generation())
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }

        @JvmStatic
        @JvmOverloads
        fun openQueue(context: Context, source: PlayQueue, playing: Boolean = true) {
            val reference = source.item ?: return
            val key = cacheQueue(context, source) ?: return
            detachLegacyPlayer(context)
            val request = Intent(context, NewPlayerActivity::class.java)
                .putExtra(EXTRA_MODE, "network").putExtra(EXTRA_SERVICE, reference.serviceId)
                .putExtra(EXTRA_PAGE, reference.url).putExtra(EXTRA_QUEUE, key)
                .putExtra(EXTRA_FULLSCREEN, PlayerHelper.isStartMainPlayerFullscreenEnabled(context))
                .putExtra(EXTRA_POSITION, if (playing) -1L else reference.recoveryPosition.coerceAtLeast(0))
                .putExtra(EXTRA_PLAYING, playing).putExtra(EXTRA_GENERATION, org.schabi.newpipe.auth.AuthRuntime.generation())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (context !is android.app.Activity) request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(request)
        }

        private fun detachLegacyPlayer(context: Context) {
            val fragment = (context as? AppCompatActivity)?.supportFragmentManager?.findFragmentById(R.id.fragment_player_holder)
            if (fragment is VideoDetailFragment) {
                fragment.prepareForNewPlayer()
            } else {
                PlayerHolder.getInstance().stopService()
            }
        }

        private fun cacheQueue(context: Context, source: PlayQueue): String? {
            val key = SerializedCache.getInstance().put(source, PlayQueue::class.java)
            if (key == null) {
                Log.e(NewPlayerRuntime.TAG, "queue.copy_failed")
                Toast.makeText(context, R.string.slooopipe_newplayer_error, Toast.LENGTH_LONG).show()
            }
            return key
        }
    }
}
