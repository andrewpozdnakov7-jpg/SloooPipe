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
    private class Feed(val source: PlayQueue, val id: String = UUID.randomUUID().toString()) {
        val keys = mutableListOf<String>()
        var requestedAtSize = -1
        var subscription: Disposable? = null
        var pending = true
    }

    private val feeds = mutableListOf<Feed>()
    private var revision = 0L
    private var savedLayoutKey: List<String>? = null
    private var savedFeeds = emptyList<SavedFeed>()
    private var savedOrder = emptyList<SavedSlot>()
    var active = false
        private set

    fun pendingReferences() = feeds.flatMap { it.source.streams }

    fun owns(items: List<androidx.media3.common.MediaItem>): Boolean = items.all { item -> QueueMediaRepository.request(item)?.entry?.feed in feeds.map { it.id } }

    fun saved(position: Long, repeat: Int, shuffle: Boolean): SavedPlayback? {
        if (!active || feeds.any { it.pending }) return null
        val items = player().playlist.value
        val layoutKey = items.map { it.mediaId } + feeds.map { "${it.id}:${it.source.size()}:${it.source.isComplete}" }
        if (layoutKey != savedLayoutKey) {
            val slots = buildMap {
                feeds.forEach { feed -> feed.keys.forEachIndexed { index, key -> put(key.removePrefix("queue:"), SavedSlot(feed.id, index)) } }
            }
            savedOrder = items.map { item ->
                val key = QueueMediaRepository.request(item)?.entry?.key ?: return null
                slots[key] ?: return null
            }
            savedFeeds = feeds.map { SavedFeed(it.id, copy(it.source)) }
            savedLayoutKey = layoutKey
        }
        val order = savedOrder
        if (order.isEmpty()) return null
        return SavedPlayback(
            savedFeeds,
            order,
            player().currentlyPlayingPlaylistItem,
            position,
            repeat,
            shuffle
        )
    }

    fun restore(state: SavedPlayback) {
        close()
        active = true
        val owner = revision
        val selected = state.order[state.index]
        state.feeds.forEach { saved ->
            val feed = Feed(saved.source, saved.id)
            feed.keys.addAll(
                feed.source.streams.mapIndexed { index, reference ->
                    val current = selected.feed == feed.id && selected.sourceIndex == index
                    repository.offer(reference, feed.id, current, state.position.takeIf { current })
                }
            )
            feeds.add(feed)
        }
        val keys = state.order.map { slot -> checkNotNull(feeds.firstOrNull { it.id == slot.feed }).keys[slot.sourceIndex] }
        player().playQueue(keys, state.index, false).invokeOnCompletion { failure ->
            if (failure != null || owner != revision) return@invokeOnCompletion
            feeds.forEach { it.pending = false }
            player().exoPlayer.value?.let {
                it.repeatMode = state.repeat
                it.shuffleModeEnabled = state.shuffle
            }
            reconcile()
        }
        feeds.toList().forEach(::subscribe)
    }

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
        player().playQueue(feed.keys.toList(), index, playing).invokeOnCompletion {
            feed.pending = false
            reconcile()
        }
        subscribe(feed)
        Log.i(NewPlayerRuntime.TAG, "queue.start count=${feed.keys.size} index=$index playing=$playing")
    }

    fun append(source: PlayQueue, next: Boolean) {
        if (source.isEmpty) return
        val feed = Feed(copy(source))
        feeds.add(feed)
        feed.keys.addAll(feed.source.streams.map { repository.offer(it, feed.id) })
        val afterId = player().currentlyPlaying.value?.mediaId
        player().insertQueueItems(
            feed.keys.toList(),
            insertion = if (next) {
                { player().playlist.value.indexOfFirst { it.mediaId == afterId }.takeIf { it >= 0 }?.plus(1) }
            } else {
                null
            }
        ).invokeOnCompletion {
            feed.pending = false
            reconcile()
        }
        subscribe(feed)
        Log.i(NewPlayerRuntime.TAG, "queue.append count=${feed.keys.size} next=$next")
    }

    private fun subscribe(feed: Feed) {
        feed.source.init()
        feed.subscription = feed.source.broadcastReceiver?.subscribe(
            onEvent@{ event ->
                if (!active || feed !in feeds) return@onEvent
                if (event is PlayQueueEvent.AppendEvent && event.amount == 0) feed.requestedAtSize = -1
                val newItems = feed.source.streams.drop(feed.keys.size)
                if (newItems.isNotEmpty()) {
                    fun insertion() = QueueFeedPolicy.insertionIndex(player().playlist.value.map { QueueMediaRepository.request(it)?.entry?.feed }, feed.id)
                    if (insertion() == null) return@onEvent
                    val keys = newItems.map { repository.offer(it, feed.id) }
                    feed.keys.addAll(keys)
                    feed.pending = true
                    player().insertQueueItems(keys, insertion = ::insertion).invokeOnCompletion {
                        feed.pending = false
                        reconcile()
                    }
                    Log.i(NewPlayerRuntime.TAG, "queue.page added=${keys.size} complete=${feed.source.isComplete}")
                }
                maybeFetch()
            },
            { error -> net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.queue_page", error) }
        )
    }

    fun maybeFetch() {
        if (!active) return
        val current = QueueMediaRepository.request(player().currentlyPlaying.value)?.entry ?: return
        feeds.firstOrNull { it.id == current.feed }?.let { feed ->
            val index = feed.keys.indexOf("queue:${current.key}")
            if (QueueFeedPolicy.shouldFetch(index, feed.keys.size, feed.source.isComplete, feed.requestedAtSize)) {
                feed.requestedAtSize = feed.keys.size
                feed.source.fetch()
                Log.i(NewPlayerRuntime.TAG, "queue.fetch loaded=${feed.keys.size}")
            }
        }
    }

    fun reconcile() {
        if (!active) return
        val entries = player().playlist.value.mapNotNull { QueueMediaRepository.request(it)?.entry }
        val retired = feeds.filter { !it.pending && entries.none { entry -> entry.feed == it.id } }
        retired.forEach {
            it.subscription?.dispose()
            it.source.dispose()
        }
        feeds.removeAll(retired.toSet())
        repository.retain(entries.map { it.key }.toSet() + feeds.filter { it.pending }.flatMap { it.keys }.map { it.removePrefix("queue:") })
    }

    fun close() {
        revision++
        active = false
        feeds.forEach {
            it.subscription?.dispose()
            it.source.dispose()
        }
        feeds.clear()
        savedLayoutKey = null
        savedFeeds = emptyList()
        savedOrder = emptyList()
    }

    companion object {
        fun copy(queue: PlayQueue): PlayQueue {
            val cache = SerializedCache.getInstance()
            val key = checkNotNull(cache.put(queue, PlayQueue::class.java)) { "NEWPLAYER_QUEUE_COPY_FAILED" }
            return checkNotNull(cache.take(key, PlayQueue::class.java))
        }
    }
}
