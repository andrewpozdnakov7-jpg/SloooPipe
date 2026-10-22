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
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

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
    private var fullscreen = false
    private var selectingLocal = false
    private var initialFullscreen = false
    private var pipWasActive = false
    private var hadAttachedMedia = false

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
        buildScreen()
        addOnPictureInPictureModeChangedListener { mode ->
            if (mode.isInPictureInPictureMode) {
                pipWasActive = true
            } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                pipWasActive = false
            }
            runtime.setPresentation(if (mode.isInPictureInPictureMode) PlaybackPresentation.PIP else PlaybackPresentation.VIDEO)
            model.onPictureInPictureModeChanged(mode.isInPictureInPictureMode)
            Log.i(NewPlayerRuntime.TAG, "screen.pip=${mode.isInPictureInPictureMode}")
        }
        observePlayer()
        Log.i(NewPlayerRuntime.TAG, "screen.create network=$network restored=${savedInstanceState != null}")
        if (!selectingLocal && runtime.player.exoPlayer.value != null) {
            video.visibility = View.VISIBLE
            videoTitle.text = runtime.player.currentlyPlaying.value?.mediaMetadata?.title
        } else if (network) {
            NewPlayerRuntime.restoreIfNeeded(application)
        } else {
            currentUri?.let { loadLocal(it, savedInstanceState?.getLong("position") ?: 0, savedInstanceState?.getBoolean("playing") ?: true) }
        }
        if (!selectingLocal && !intent.getBooleanExtra(EXTRA_ENTER_PIP, false) && !intent.getBooleanExtra(EXTRA_SHOW_QUEUE, false)) runtime.setPresentation(PlaybackPresentation.VIDEO)
        if (intent.getBooleanExtra(EXTRA_RESUME_ACTIVE, false) && runtime.queue.active) runtime.player.play()
        if (intent.getBooleanExtra(EXTRA_SHOW_QUEUE, false) && runtime.queue.active) model.changeUiMode(queueUiMode(), null)
        if (intent.getBooleanExtra(EXTRA_ENTER_PIP, false) && Build.VERSION.SDK_INT >= 26) {
            runtime.setPresentation(PlaybackPresentation.PIP)
            video.post { model.changeUiMode(UIModeState.PIP, null) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < 26 || !isInPictureInPictureMode) pipWasActive = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_MODE) == "local") {
            selectingLocal = true
            network = false
            currentUri = null
            video.visibility = View.GONE
            configureAction()
            return
        }
        selectingLocal = false
        initialFullscreen = intent.getBooleanExtra(EXTRA_FULLSCREEN, false)
        network = runtime.queue.active
        if (!intent.getBooleanExtra(EXTRA_SHOW_QUEUE, false)) runtime.setPresentation(PlaybackPresentation.VIDEO)
        video.visibility = View.VISIBLE
        if (intent.getBooleanExtra(EXTRA_RESUME_ACTIVE, false)) runtime.player.play()
        applyInitialFullscreen()
        if (intent.getBooleanExtra(EXTRA_SHOW_QUEUE, false)) model.changeUiMode(queueUiMode(), null)
        if (intent.getBooleanExtra(EXTRA_ENTER_PIP, false) && Build.VERSION.SDK_INT >= 26) {
            runtime.setPresentation(PlaybackPresentation.PIP)
            model.changeUiMode(UIModeState.PIP, null)
        }
    }

    private fun readRequest(request: Intent, state: Bundle?) {
        selectingLocal = state?.getBoolean("selectingLocal") ?: (request.getStringExtra(EXTRA_MODE) == "local")
        network = !selectingLocal && (runtime.queue.active || state?.getBoolean("network") == true)
        currentUri = if (selectingLocal) null else state?.getString("localUri")?.let(Uri::parse) ?: runtime.localUri
        initialFullscreen = request.getBooleanExtra(EXTRA_FULLSCREEN, false) && state == null
    }

    private fun queueUiMode() = if (runtime.presentation.value == PlaybackPresentation.AUDIO || runtime.isAudioOnly()) UIModeState.AUDIO_STREAM_SELECT else UIModeState.EMBEDDED_VIDEO_STREAM_SELECT

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
                        if (!selectingLocal && item != null) {
                            hadAttachedMedia = true
                            network = QueueMediaRepository.request(item) != null
                            videoTitle.text = item.mediaMetadata.title
                            video.visibility = View.VISIBLE
                            configureAction()
                            applyInitialFullscreen()
                        }
                    }
                }
                launch {
                    // The last-item transition can emit null before release closes the session.
                    // Observe the engine's release too; StateFlow will not emit the same null twice.
                    runtime.player.exoPlayer.collect { engine ->
                        if (engine == null && hadAttachedMedia && !selectingLocal && selection?.isActive != true && !runtime.queue.active && runtime.localUri == null) {
                            Log.i(NewPlayerRuntime.TAG, "screen.session_closed")
                            finish()
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
                                status.setText(if (player.isPlaying) R.string.slooopipe_local_playing else R.string.slooopipe_local_paused)
                            }

                            Player.STATE_ENDED -> status.setText(R.string.slooopipe_local_ended)
                        }
                    }
                }
            }
        }
    }

    private fun applyInitialFullscreen() {
        if (initialFullscreen && runtime.player.exoPlayer.value?.playbackState == Player.STATE_READY) {
            initialFullscreen = false
            model.changeUiMode(UIModeState.FULLSCREEN_VIDEO, null)
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
                        status.setText(R.string.slooopipe_newplayer_error)
                    }
                } else {
                    Log.i(NewPlayerRuntime.TAG, "picker.open")
                    try {
                        openVideo.launch(arrayOf("video/*", "audio/*"))
                    } catch (error: Exception) {
                        showError(error)
                    }
                }
            }
        }
        actions.addView(chooseOrRetry, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actions)
        lateinit var modes: LinearLayout
        var modeCount = 0
        fun modeButton(label: Int, action: () -> Unit) {
            // Four equal columns split long translated labels into individual syllables.
            // Two columns keep the PiP action readable at ordinary phone widths.
            if (modeCount++ % 2 == 0) {
                modes = LinearLayout(this).apply { isBaselineAligned = false }
                header.addView(modes)
            }
            modes.addView(
                Button(this).apply {
                    setText(label)
                    contentDescription = getString(label)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setOnClickListener { action() }
                },
                LinearLayout.LayoutParams(0, -2, 1f)
            )
        }
        modeButton(R.string.slooopipe_tracks) { PlaybackTrackOptions.show(this, runtime) }
        modeButton(R.string.slooopipe_mode_audio) {
            runtime.setPresentation(PlaybackPresentation.AUDIO)
            finish()
        }
        modeButton(R.string.slooopipe_mode_popup) {
            if (org.schabi.newpipe.util.PermissionHelper.isPopupEnabledElseAsk(this)) {
                runtime.setPresentation(PlaybackPresentation.POPUP)
                startService(Intent(this, NewPlayerPopupService::class.java))
                finish()
            }
        }
        if (Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            modeButton(R.string.slooopipe_mode_pip) {
                runtime.setPresentation(PlaybackPresentation.PIP)
                model.changeUiMode(UIModeState.PIP, null)
            }
        }
        videoTitle = TextView(this).apply {
            setTextColor(foreground)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
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

    private fun loadLocal(uri: Uri, position: Long = 0, playing: Boolean = true) {
        selection?.cancel()
        selection = lifecycleScope.launch {
            try {
                val document = runtime.localRepository.register(uri)
                PlaybackConsent.requestLocal(
                    this@NewPlayerActivity,
                    Runnable {
                        PlayerHolder.getInstance().stopService()
                        selectingLocal = false
                        network = false
                        currentUri = uri
                        videoTitle.text = document.title
                        video.visibility = View.VISIBLE
                        runtime.startLocal(uri, position, playing)
                    }
                )
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
        outState.putBoolean("network", network)
        outState.putBoolean("selectingLocal", selectingLocal)
        outState.putString("localUri", currentUri?.toString())
        outState.putString("videoTitle", videoTitle.text.toString())
        outState.putLong("position", runtime.player.currentPosition)
        outState.putBoolean("playing", runtime.player.playWhenReady)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        if (!selectingLocal && !isChangingConfigurations && runtime.presentation.value == PlaybackPresentation.VIDEO && !(Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode)) {
            selection?.cancel()
            when (PlayerHelper.getMinimizeOnExitAction(this)) {
                PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND -> runtime.setPresentation(PlaybackPresentation.AUDIO)

                PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP -> {
                    if (android.provider.Settings.canDrawOverlays(this)) {
                        runtime.setPresentation(PlaybackPresentation.POPUP)
                        startService(Intent(this, NewPlayerPopupService::class.java))
                    } else {
                        runtime.player.pause()
                    }
                }

                else -> runtime.player.pause()
            }
            if (runtime.player.exoPlayer.value == null) runtime.releasePlayback()
            Log.i(NewPlayerRuntime.TAG, "screen.stop mode=${runtime.presentation.value}")
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            selection?.cancel()
            // Returning to browsing retains paused local/network sessions; dismissing PiP closes.
            if (!selectingLocal && (pipWasActive || runtime.player.exoPlayer.value == null)) runtime.releasePlayback()
        }
        Log.i(NewPlayerRuntime.TAG, "screen.destroy finishing=$isFinishing")
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MODE = "newplayer.mode"
        private const val EXTRA_FULLSCREEN = "newplayer.fullscreen"
        private const val EXTRA_RESUME_ACTIVE = "newplayer.resumeActive"
        private const val EXTRA_SHOW_QUEUE = "newplayer.showQueue"
        private const val EXTRA_ENTER_PIP = "newplayer.enterPip"

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
            context.startActivity(Intent(context, NewPlayerActivity::class.java).putExtra(EXTRA_MODE, "local").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }

        @JvmStatic
        @JvmOverloads
        fun openNetwork(context: Context, info: StreamInfo, source: PlayQueue? = null, playing: Boolean = true, fullscreen: Boolean = false) {
            PlaybackRequests.play(context, source ?: SinglePlayQueue(info), playing, PlaybackPresentation.VIDEO, info, fullscreen)
        }

        @JvmStatic
        @JvmOverloads
        fun openQueue(context: Context, source: PlayQueue, playing: Boolean = true) {
            PlaybackRequests.play(context, source, playing, PlaybackPresentation.VIDEO, null, PlayerHelper.isStartMainPlayerFullscreenEnabled(context))
        }

        internal fun showActive(context: Context, playing: Boolean, fullscreen: Boolean = false, pip: Boolean = false) {
            val request = Intent(context, NewPlayerActivity::class.java)
                .putExtra(EXTRA_RESUME_ACTIVE, playing).putExtra(EXTRA_FULLSCREEN, fullscreen)
                .putExtra(EXTRA_ENTER_PIP, pip).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (context !is android.app.Activity) request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(request)
        }
    }
}
