// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.newpipe.newplayer.NewPlayerImpl
import net.newpipe.newplayer.repository.MultiRepoEntry
import net.newpipe.newplayer.repository.MultiRepository
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.localplayer.LocalMediaRepository
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Application-scoped instance shared with NewPlayer's media session service. */
class NewPlayerRuntime private constructor(private val app: Application) {
    private var sessionIdentity = UUID.randomUUID().toString()
    private val mutablePresentation = MutableStateFlow(PlaybackPresentation.VIDEO)
    val presentation = mutablePresentation.asStateFlow()
    internal val localRepository = LocalMediaRepository(app.contentResolver)
    internal val networkRepository = NetworkMediaRepository(app)
    private val history = PlaybackHistory(app)
    private val sessionStore = PlaybackSessionStore(app)
    private var restoreAttempted = false
    private var retainSavedSession = false
    internal val queueRepository = QueueMediaRepository(networkRepository, history)
    internal var currentItem: String? = null
    internal var localUri: android.net.Uri? = null
    private val repository = MultiRepository(listOf(MultiRepoEntry("local", localRepository), MultiRepoEntry("network", networkRepository), MultiRepoEntry("queue", queueRepository)))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val player = NewPlayerImpl(
        app = app,
        repository = repository,
        beforeRelease = { onPlayerReleased() },
        beforeServiceRelease = {
            saveSession()
            retainSavedSession = true
        },
        playerActivityClass = NewPlayerActivity::class.java,
        onNewPlayer = { application ->
            Log.i(TAG, "engine.create media3=1.9.2 engine=NewPlayer sources=local,network")
            ExoPlayer.Builder(application)
                .setUseLazyPreparation(true)
                .setMediaSourceFactory(NetworkMediaSourceFactory(application, queueRepository))
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
                .also {
                    it.addListener(Diagnostics(it, app))
                    it.addAnalyticsListener(AudioDiagnostics())
                    history.attach(it)
                }
        }
    )
    internal val queue = QueuePlayback(queueRepository) { player }

    init {
        scope.launch {
            while (true) {
                delay(5000)
                if (queue.active) saveSession()
            }
        }
        scope.launch {
            player.exoPlayer.collect { exo ->
                exo?.let { applyPresentation(it) }
            }
        }
        scope.launch {
            player.playBackMode.collect { mode ->
                if (mode != player.playBackMode.value) return@collect
                val next = PlaybackPresentationPolicy.fromPlayerMode(presentation.value, mode, isAudioOnly())
                if (next != presentation.value) {
                    Log.i(TAG, "presentation.player_mode=$mode target=$next")
                    setPresentation(next)
                }
            }
        }
        scope.launch {
            player.onPlayerEvent.collect { (exo, events) ->
                if (exo !== player.exoPlayer.value) return@collect
                QueueMediaRepository.request(exo.currentMediaItem)?.entry?.let { currentItem = "queue:${it.key}" }
                if (presentation.value == PlaybackPresentation.VIDEO) updateContentMode()
                queue.reconcile()
                queue.maybeFetch()
                if (queue.active && events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) notifyQueueChanged()
                if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_TIMELINE_CHANGED)) saveSession()
            }
        }
        scope.launch {
            player.errorFlow.collect { error ->
                Log.e(TAG, "engine.error type=${error.javaClass.simpleName}")
                net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.engine_failed", error)
            }
        }
    }

    private class Diagnostics(private val exo: Player, private val context: android.content.Context) : Player.Listener {
        override fun onPlaybackSuppressionReasonChanged(reason: Int) {
            if (reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS &&
                !org.schabi.newpipe.player.helper.PlayerHelper.isResumeAfterAudioFocusGain(context)
            ) {
                exo.pause()
            }
        }
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                // Media3 intentionally hides live positions from legacy system MediaSession.
                // Read the real timeline here instead of interpreting dumpsys position=-1 as a stall.
                val duration = player.duration.takeIf { it != C.TIME_UNSET }
                val liveOffset = player.currentLiveOffset.takeIf { it != C.TIME_UNSET }
                Log.i(TAG, "engine.timeline live=${player.isCurrentMediaItemLive} seekable=${player.isCurrentMediaItemSeekable} position=${player.currentPosition} buffered=${player.bufferedPosition} duration=$duration liveOffset=$liveOffset playing=${player.isPlaying} speed=${player.playbackParameters.speed}")
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(TAG, "engine.state=$playbackState")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.i(TAG, "engine.playing=$isPlaying")
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "engine.failure code=${error.errorCodeName} type=${error.javaClass.simpleName}")
            net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.playback_failed", error)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            Log.i(TAG, "engine.video=${videoSize.width}x${videoSize.height}")
        }

        override fun onRenderedFirstFrame() {
            Log.i(TAG, "engine.first_frame")
        }

        override fun onTracksChanged(tracks: Tracks) {
            for (group in tracks.groups) {
                for (index in 0 until group.length) {
                    val format = group.getTrackFormat(index)
                    Log.i(TAG, "engine.track type=${group.type} selected=${group.isTrackSelected(index)} supported=${group.isTrackSupported(index)} mime=${format.sampleMimeType} codecs=${format.codecs} size=${format.width}x${format.height}")
                }
            }
        }
    }

    private class AudioDiagnostics : AnalyticsListener {
        override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
            Log.i(TAG, "engine.audio_advancing position=${eventTime.currentPlaybackPositionMs}")
        }
    }

    fun releasePlayback() {
        player.release()
    }

    private fun saveSession() {
        val enabled = androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
            .getBoolean(app.getString(org.schabi.newpipe.R.string.enable_watch_history_key), true)
        if (!enabled) {
            sessionStore.save(null)
            return
        }
        val exo = player.exoPlayer.value ?: return
        queue.saved(exo.currentPosition, exo.repeatMode, exo.shuffleModeEnabled)?.let(sessionStore::save)
    }

    private fun restoreSession() {
        if (restoreAttempted) return
        restoreAttempted = true
        val identity = sessionIdentity
        scope.launch {
            val state = sessionStore.read() ?: return@launch
            val enabled = androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
                .getBoolean(app.getString(org.schabi.newpipe.R.string.enable_watch_history_key), true)
            if (!enabled || identity != sessionIdentity || queue.active || player.exoPlayer.value != null) return@launch
            try {
                queue.restore(state)
                notifyQueueChanged()
            } catch (error: Exception) {
                releasePlayback()
                net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.queue_restore_rejected", error)
            }
        }
    }

    fun setPresentation(mode: PlaybackPresentation) {
        mutablePresentation.value = mode
        val oldMode = player.playBackMode.value
        if (mode == PlaybackPresentation.AUDIO) {
            player.playBackMode.value = net.newpipe.newplayer.data.PlayMode.EMBEDDED_AUDIO
        } else if (mode == PlaybackPresentation.VIDEO) {
            if (oldMode == net.newpipe.newplayer.data.PlayMode.PIP) player.playBackMode.value = net.newpipe.newplayer.data.PlayMode.EMBEDDED_VIDEO
            updateContentMode()
        }
        player.exoPlayer.value?.let(::applyPresentation)
        if (mode != PlaybackPresentation.POPUP) app.stopService(Intent(app, NewPlayerPopupService::class.java))
        notifyQueueChanged()
    }

    internal fun isAudioOnly(): Boolean {
        localUri?.let { return localRepository.isAudio(it) }
        if (!queue.active) return false
        val entry = QueueMediaRepository.request(player.currentlyPlaying.value)?.entry ?: return false
        return NetworkStreamSelector.isAudio(entry.info?.streamType ?: entry.reference.streamType)
    }

    private fun updateContentMode() {
        val old = player.playBackMode.value
        val video = old == net.newpipe.newplayer.data.PlayMode.EMBEDDED_VIDEO || old == net.newpipe.newplayer.data.PlayMode.FULLSCREEN_VIDEO
        val audio = old == net.newpipe.newplayer.data.PlayMode.EMBEDDED_AUDIO || old == net.newpipe.newplayer.data.PlayMode.FULLSCREEN_AUDIO
        if (isAudioOnly() && video) {
            player.playBackMode.value = if (old == net.newpipe.newplayer.data.PlayMode.FULLSCREEN_VIDEO) net.newpipe.newplayer.data.PlayMode.FULLSCREEN_AUDIO else net.newpipe.newplayer.data.PlayMode.EMBEDDED_AUDIO
        } else if (!isAudioOnly() && audio) {
            player.playBackMode.value = if (old == net.newpipe.newplayer.data.PlayMode.FULLSCREEN_AUDIO) net.newpipe.newplayer.data.PlayMode.FULLSCREEN_VIDEO else net.newpipe.newplayer.data.PlayMode.EMBEDDED_VIDEO
        }
    }

    private fun applyPresentation(exo: ExoPlayer) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, presentation.value == PlaybackPresentation.AUDIO).build()
    }

    internal fun startNetwork(source: PlayQueue, info: StreamInfo?, playing: Boolean, mode: PlaybackPresentation) {
        val desired = source.streams.map { StreamIdentity(it.serviceId, it.url) }
        val existing = snapshot()
        val exo = player.exoPlayer.value
        if (existing?.streams == desired && exo != null && exo.mediaItemCount > 0) {
            val index = source.index.coerceIn(0, exo.mediaItemCount - 1)
            if (index != exo.currentMediaItemIndex) exo.seekToDefaultPosition(index)
            val explicit = info?.startPosition?.takeIf { it > 0 }?.coerceAtMost(Long.MAX_VALUE / 1000)?.times(1000)
                ?: source.item?.recoveryPosition?.takeIf { it >= 0 }
            if (explicit != null) exo.seekTo(explicit)
            setPresentation(mode)
            player.playWhenReady = playing
            return
        }
        // Keep the ExoPlayer/MediaSession instance; only the accepted queue is replaced.
        exo?.let(history::save)
        queue.close()
        queueRepository.clear()
        networkRepository.clear()
        sessionIdentity = UUID.randomUUID().toString()
        sessionStore.save(null)
        restoreAttempted = true
        currentItem = "queue:loading"
        localUri = null
        setPresentation(mode)
        // A detail screen can still hold old account data after the extractor cache was cleared.
        val handoff = info.takeIf { org.schabi.newpipe.auth.AuthRuntime.generation() == 0L }
        queue.start(source, handoff, null, playing)
    }

    internal fun startLocal(uri: android.net.Uri, position: Long, playing: Boolean) {
        player.exoPlayer.value?.let(history::save)
        queue.close()
        queueRepository.clear()
        networkRepository.clear()
        sessionIdentity = UUID.randomUUID().toString()
        sessionStore.save(null)
        currentItem = "local:$uri"
        localUri = uri
        setPresentation(PlaybackPresentation.VIDEO)
        player.playSingle("local:$uri", if (isAudioOnly()) net.newpipe.newplayer.data.PlayMode.EMBEDDED_AUDIO else net.newpipe.newplayer.data.PlayMode.EMBEDDED_VIDEO, position, playing)
    }

    private fun onPlayerReleased() {
        sessionIdentity = UUID.randomUUID().toString()
        val wasQueued = queue.active
        player.exoPlayer.value?.let(history::save)
        queue.close()
        history.attach(null)
        queueRepository.clear()
        networkRepository.clear()
        currentItem = null
        localUri = null
        if (retainSavedSession) restoreAttempted = false else sessionStore.save(null)
        retainSavedSession = false
        mutablePresentation.value = PlaybackPresentation.VIDEO
        app.stopService(Intent(app, NewPlayerPopupService::class.java))
        if (wasQueued) notifyQueueChanged()
    }

    private fun notifyQueueChanged() {
        app.sendBroadcast(Intent(QUEUE_CHANGED).setPackage(app.packageName))
    }

    companion object {
        @JvmStatic
        fun restoreIfNeeded(app: Application) {
            get(app).restoreSession()
        }
        internal fun snapshot(): PlaybackSnapshot? {
            val runtime = instance ?: return null
            val items = runtime.player.playlist.value
            if (!runtime.queue.active) {
                val uri = runtime.localUri ?: return null
                return PlaybackSnapshot(runtime.sessionIdentity, items.map { it.mediaId }, runtime.player.currentlyPlaying.value?.mediaId, listOf(StreamIdentity(-1, uri.toString())))
            }
            val references = if (items.isEmpty() || !runtime.queue.owns(items)) {
                runtime.queue.pendingReferences()
            } else {
                items.mapNotNull { QueueMediaRepository.request(it)?.entry?.reference }
            }
            return PlaybackSnapshot(
                runtime.sessionIdentity + ":" + org.schabi.newpipe.auth.AuthRuntime.generation(),
                items.map { it.mediaId },
                runtime.player.currentlyPlaying.value?.mediaId,
                references.map { StreamIdentity(it.serviceId, it.url) }
            )
        }

        @JvmStatic
        fun invalidateNetworkSession() {
            instance?.let { runtime ->
                runtime.networkRepository.clear()
                runtime.sessionStore.save(null)
                runtime.restoreAttempted = true
                runtime.sessionIdentity = UUID.randomUUID().toString()
                if (runtime.queue.active || runtime.currentItem?.startsWith("network:") == true) {
                    runtime.releasePlayback()
                    runtime.player.mutableErrorFlow.tryEmit(IllegalStateException("AUTH_SESSION_CHANGED"))
                }
            }
        }

        const val TAG = "SloooPipeLocal"
        const val QUEUE_CHANGED = "org.schabi.newpipe.NEWPLAYER_QUEUE_CHANGED"

        @JvmStatic
        fun enqueueIfActive(source: PlayQueue, next: Boolean): Boolean {
            val runtime = instance ?: return false
            if (!runtime.queue.active || runtime.player.exoPlayer.value == null) return false
            return try {
                runtime.queue.append(source, next)
                true
            } catch (error: Exception) {
                net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.queue_append", error)
                false
            }
        }

        @JvmStatic
        fun isNetworkQueueActive(): Boolean = instance?.let { it.queue.active && it.player.playlist.value.isNotEmpty() } == true

        // A committed request owns a session even while its first playlist is still loading.
        @JvmStatic
        fun hasNetworkSession(): Boolean = instance?.queue?.active == true

        @JvmStatic
        fun isNetworkPlaying(): Boolean = instance?.let { it.queue.active && it.player.playWhenReady && it.player.exoPlayer.value?.isPlaying == true } == true

        @JvmStatic
        fun pauseNetworkQueue() {
            instance?.takeIf { it.queue.active }?.player?.pause()
        }

        @JvmStatic
        fun currentQueueItem(): PlayQueueItem? = instance?.player?.exoPlayer?.value?.currentMediaItem?.let { QueueMediaRepository.request(it)?.entry?.reference }

        @JvmStatic
        fun currentStreamInfo(): StreamInfo? = instance?.history?.currentInfo()

        @JvmStatic
        fun stopNetworkPlayback() {
            // Hiding the old network detail sheet also happens when a local mini-player appears.
            // That sheet owns no local session and must not close the replacement document.
            instance?.takeIf { it.localUri == null && it.queue.active }?.releasePlayback()
        }

        @Volatile private var instance: NewPlayerRuntime? = null

        @JvmStatic
        fun get(app: Application): NewPlayerRuntime = instance ?: synchronized(this) {
            instance ?: NewPlayerRuntime(app).also { instance = it }
        }
    }
}
