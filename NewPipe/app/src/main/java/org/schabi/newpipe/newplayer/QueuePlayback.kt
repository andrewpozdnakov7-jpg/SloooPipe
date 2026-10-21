// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.util.Log
import io.reactivex.rxjava3.disposables.Disposable
import java.util.UUID
import net.newpipe.newplayer.NewPlayerImpl
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueEvent
import org.schabi.newpipe.util.SerializedCache

/** Owns cloned NewPipe queues for pagination; Media3 owns selection, ordering and removal. */
internal class QueuePlayback(private val repository: QueueMediaRepository, private val player: () -> NewPlayerImpl) {
    private class Feed(val source: PlayQueue) {
        val id = UUID.randomUUID().toString()
        val keys = mutableListOf<String>()
        var requestedAtSize = -1
        var subscription: Disposable? = null
    }

    private val feeds = mutableListOf<Feed>()
    var active = false
        private set

    fun start(source: PlayQueue, info: StreamInfo?, position: Long?, playing: Boolean) {
        check(!source.isEmpty)
        active = true
        val feed = Feed(source)
        feeds.add(feed)
        val index = source.index.coerceIn(0, source.size() - 1)
        feed.keys.addAll(
            source.streams.mapIndexed { i, reference ->
                repository.offer(reference, feed.id, i == index, position.takeIf { i == index }, info?.takeIf { it.serviceId == reference.serviceId && it.url == reference.url })
            }
        )
        player().playQueue(feed.keys.toList(), index, playing)
        subscribe(feed)
        Log.i(NewPlayerRuntime.TAG, "queue.start count=${feed.keys.size} index=$index playing=$playing")
    }

    fun append(source: PlayQueue, next: Boolean) {
        if (source.isEmpty) return
        val feed = Feed(copy(source))
        feeds.add(feed)
        feed.keys.addAll(feed.source.streams.map { repository.offer(it, feed.id) })
        val insertion = if (next) player().exoPlayer.value?.currentMediaItemIndex?.plus(1) else null
        player().insertQueueItems(feed.keys.toList(), insertion)
        subscribe(feed)
        Log.i(NewPlayerRuntime.TAG, "queue.append count=${feed.keys.size} next=$next")
    }

    private fun subscribe(feed: Feed) {
        feed.source.init()
        feed.subscription = feed.source.broadcastReceiver?.subscribe({ event ->
            if (event is PlayQueueEvent.AppendEvent && event.amount == 0) feed.requestedAtSize = -1
            val newItems = feed.source.streams.drop(feed.keys.size)
            if (newItems.isNotEmpty()) {
                val insertion = player().playlist.value.indexOfLast { QueueMediaRepository.request(it)?.entry?.feed == feed.id }
                    .takeIf { it >= 0 }?.plus(1)
                val keys = newItems.map { repository.offer(it, feed.id) }
                feed.keys.addAll(keys)
                player().insertQueueItems(keys, insertion)
                Log.i(NewPlayerRuntime.TAG, "queue.page added=${keys.size} complete=${feed.source.isComplete}")
            }
            maybeFetch()
        }, { error -> net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.queue_page", error) })
    }

    fun maybeFetch() {
        if (!active) return
        val current = QueueMediaRepository.request(player().currentlyPlaying.value)?.entry ?: return
        feeds.firstOrNull { it.id == current.feed }?.let { feed ->
            val index = feed.keys.indexOf("queue:${current.key}")
            if (!feed.source.isComplete && index >= feed.keys.size - 3 && feed.requestedAtSize != feed.keys.size) {
                feed.requestedAtSize = feed.keys.size
                feed.source.fetch()
                Log.i(NewPlayerRuntime.TAG, "queue.fetch loaded=${feed.keys.size}")
            }
        }
    }

    fun close() {
        active = false
        feeds.forEach {
            it.subscription?.dispose()
            it.source.dispose()
        }
        feeds.clear()
    }

    companion object {
        fun copy(queue: PlayQueue): PlayQueue {
            val cache = SerializedCache.getInstance()
            val key = checkNotNull(cache.put(queue, PlayQueue::class.java)) { "NEWPLAYER_QUEUE_COPY_FAILED" }
            return checkNotNull(cache.take(key, PlayQueue::class.java))
        }
    }
}
