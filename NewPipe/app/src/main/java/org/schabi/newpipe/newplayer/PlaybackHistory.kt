// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.schabi.newpipe.R
import org.schabi.newpipe.auth.AuthRuntime
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.DependentPreferenceHelper

/** One writer preserves pause/seek/transition ordering; local files never enter NewPipe history. */
internal class PlaybackHistory(private val context: Context) {
    private sealed interface Work
    private data class Write(val info: StreamInfo, val position: Long?, val viewed: Boolean, val generation: Long = AuthRuntime.generation()) : Work
    private class Barrier(val done: CompletableDeferred<Unit>) : Work
    private val manager = HistoryRecordManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writes = Channel<Work>(Channel.UNLIMITED)
    private var player: Player? = null
    private var viewedId: String? = null
    private val viewedItems = mutableSetOf<String>()
    private val observedInfo = LinkedHashMap<String, StreamInfo>()
    private val lastSaved = mutableMapOf<String, Long>()
    private var progressJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            for (work in writes) {
                if (work is Barrier) {
                    work.done.complete(Unit)
                    continue
                }
                val write = work as Write
                if (!enabled(context) || write.generation != AuthRuntime.generation()) continue
                try {
                    if (write.viewed) manager.onViewed(write.info).awaitValue()
                    write.position?.let { manager.saveStreamState(write.info, it).awaitCompletion() }
                    Log.i(NewPlayerRuntime.TAG, "history.saved viewed=${write.viewed} position=${write.position}")
                } catch (error: Exception) {
                    net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.history_write", error)
                }
            }
        }
    }

    fun attach(next: Player?) {
        if (player === next) return
        player?.let { it.removeListener(listener) }
        progressJob?.cancel()
        player = next
        viewedId = null
        viewedItems.clear()
        observedInfo.clear()
        lastSaved.clear()
        next?.addListener(listener)
        if (next != null) {
            progressJob = scope.launch {
                while (player === next) {
                    delay(5000)
                    if (next.isPlaying) save(next)
                }
            }
        }
    }

    private fun info(item: MediaItem?): StreamInfo? {
        if (item == null) return null
        val entry = QueueMediaRepository.request(item)?.entry ?: return null
        if (entry.generation != AuthRuntime.generation()) return null
        val info = entry.info ?: observedInfo[item.mediaId]
        if (info != null) {
            observedInfo[item.mediaId] = info
            while (observedInfo.size > 4) observedInfo.remove(observedInfo.keys.first())
        }
        return info
    }

    fun currentInfo(): StreamInfo? = info(player?.currentMediaItem)

    fun save(player: Player) = save(player.currentMediaItem, player.contentPosition)

    private fun save(item: MediaItem?, position: Long) {
        if (item == null || item.mediaId !in viewedItems || position < 0 || !enabled(context)) return
        val info = info(item) ?: return
        if (info.streamType == StreamType.VIDEO_STREAM || info.streamType == StreamType.AUDIO_STREAM) {
            if (lastSaved[item.mediaId] == position) return
            lastSaved[item.mediaId] = position
            writes.trySend(Write(info, position, false))
        }
    }

    private val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            save(oldPosition.mediaItem, oldPosition.contentPositionMs)
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                QueueMediaRepository.request(newPosition.mediaItem)?.entry?.resumePending = false
                save(newPosition.mediaItem, newPosition.contentPositionMs)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            viewedId = null
        }

        override fun onEvents(player: Player, events: Player.Events) {
            val item = player.currentMediaItem
            val entry = QueueMediaRepository.request(item)?.entry
            if (player.playbackState == Player.STATE_READY && entry?.resumePending == true) {
                entry.resumePending = false
                if (entry.startPosition > 0 && !player.isCurrentMediaItemLive) {
                    player.seekTo(entry.startPosition)
                    Log.i(NewPlayerRuntime.TAG, "history.resume position=${entry.startPosition}")
                }
            }
            if (!enabled(context)) {
                viewedId = null
                viewedItems.clear()
                lastSaved.clear()
            }
            if (enabled(context) && player.isPlaying && item != null && viewedId != item.mediaId) {
                info(item)?.let {
                    viewedId = item.mediaId
                    viewedItems.add(item.mediaId)
                    while (viewedItems.size > 32) viewedItems.remove(viewedItems.first())
                    lastSaved.keys.retainAll(viewedItems)
                    if (enabled(context)) writes.trySend(Write(it, null, true))
                }
            }
            if (!player.isPlaying) save(player)
            if (player.playbackState == Player.STATE_ENDED) {
                val info = info(item)
                val duration = info?.let { NetworkMetadata.durationMs(it.streamType, it.duration) }
                if (duration != null && duration < Long.MAX_VALUE - 1000) save(item, duration + 1000)
            }
        }
    }

    suspend fun resumePosition(info: StreamInfo, explicit: Long): Long {
        val barrier = CompletableDeferred<Unit>()
        writes.send(Barrier(barrier))
        barrier.await()
        return readResumePosition(context, info, explicit)
    }

    companion object {
        private fun enabled(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.enable_watch_history_key), true)

        private suspend fun readResumePosition(context: Context, info: StreamInfo, explicit: Long): Long {
            if (info.streamType != StreamType.VIDEO_STREAM && info.streamType != StreamType.AUDIO_STREAM) return 0
            val initial = PlaybackResumePolicy.position(info.duration, explicit, info.startPosition, null)
            if (explicit != PlayQueueItem.RECOVERY_UNSET && explicit >= 0 || info.startPosition > 0) return initial
            if (!DependentPreferenceHelper.getResumePlaybackEnabled(context)) return 0
            return try {
                PlaybackResumePolicy.position(info.duration, explicit, info.startPosition, HistoryRecordManager(context).loadStreamState(info).awaitValue())
            } catch (error: Exception) {
                net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.history_read", error)
                0
            }
        }
    }
}

internal suspend fun <T : Any> Maybe<T>.awaitValue(): T? = suspendCancellableCoroutine { continuation ->
    val subscription = subscribe(
        { continuation.resumeWith(Result.success(it)) },
        { continuation.resumeWith(Result.failure(it)) },
        { continuation.resumeWith(Result.success(null)) }
    )
    continuation.invokeOnCancellation { subscription.dispose() }
}

private suspend fun Completable.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    val subscription = subscribe({ continuation.resumeWith(Result.success(Unit)) }, { continuation.resumeWith(Result.failure(it)) })
    continuation.invokeOnCancellation { subscription.dispose() }
}
