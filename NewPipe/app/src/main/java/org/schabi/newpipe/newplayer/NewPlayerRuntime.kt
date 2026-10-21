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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    internal val localRepository = LocalMediaRepository(app.contentResolver)
    internal val networkRepository = NetworkMediaRepository(app)
    private val history = PlaybackHistory(app)
    internal val queueRepository = QueueMediaRepository(networkRepository, history)
    internal var currentItem: String? = null
    private val repository = MultiRepository(listOf(MultiRepoEntry("local", localRepository), MultiRepoEntry("network", networkRepository), MultiRepoEntry("queue", queueRepository)))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val player = NewPlayerImpl(
        app = app,
        repository = repository,
        beforeRelease = { onPlayerReleased() },
        playerActivityClass = NewPlayerActivity::class.java,
        onNewPlayer = { application ->
            Log.i(TAG, "engine.create media3=1.9.2 engine=NewPlayer sources=local,network")
            ExoPlayer.Builder(application)
                .setUseLazyPreparation(true)
                .setMediaSourceFactory(NetworkMediaSourceFactory(application, queueRepository))
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
                .also {
                    it.addListener(Diagnostics())
                    it.addAnalyticsListener(AudioDiagnostics())
                    history.attach(it)
                }
        }
    )
    internal val queue = QueuePlayback(queueRepository) { player }

    init {
        scope.launch {
            player.onPlayerEvent.collect { (exo, events) ->
                QueueMediaRepository.request(exo.currentMediaItem)?.entry?.let { currentItem = "queue:${it.key}" }
                queue.maybeFetch()
                if (queue.active && events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) notifyQueueChanged()
            }
        }
        scope.launch {
            player.errorFlow.collect { error ->
                Log.e(TAG, "engine.error type=${error.javaClass.simpleName}")
                net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.engine_failed", error)
            }
        }
    }

    private class Diagnostics : Player.Listener {
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

    private fun onPlayerReleased() {
        val wasQueued = queue.active
        player.exoPlayer.value?.let(history::save)
        queue.close()
        history.attach(null)
        queueRepository.clear()
        currentItem = null
        if (wasQueued) notifyQueueChanged()
    }

    private fun notifyQueueChanged() {
        app.sendBroadcast(Intent(QUEUE_CHANGED).setPackage(app.packageName))
    }

    companion object {
        @JvmStatic
        fun invalidateNetworkSession() {
            instance?.let { runtime ->
                runtime.networkRepository.clear()
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
        fun stopForLegacyPlayback() {
            instance?.releasePlayback()
        }

        @Volatile private var instance: NewPlayerRuntime? = null

        @JvmStatic
        fun get(app: Application): NewPlayerRuntime = instance ?: synchronized(this) {
            instance ?: NewPlayerRuntime(app).also { instance = it }
        }
    }
}
