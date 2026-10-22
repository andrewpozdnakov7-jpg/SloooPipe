// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.newpipe.newplayer.repository.MediaRepository
import org.schabi.newpipe.auth.AuthRuntime
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/** Lightweight queue entries; extract signed URLs only when Media3 prepares an item. */
internal class QueueMediaRepository(private val network: NetworkMediaRepository, private val history: PlaybackHistory) : MediaRepository {
    class Entry(val reference: PlayQueueItem, val generation: Long, val feed: String, @Volatile var resumePending: Boolean, val explicitPosition: Long?) {
        val key: String = UUID.randomUUID().toString()
        val loading = Mutex()

        @Volatile var info: StreamInfo? = null

        @Volatile var startPosition = 0L

        @Volatile var forceLoad = false

        @Volatile var videoChoice: String? = null

        @Volatile var audioChoice: String? = null
    }

    class Request(val entry: Entry)
    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun offer(reference: PlayQueueItem, feed: String, initial: Boolean = false, position: Long? = null, info: StreamInfo? = null): String {
        val entry = Entry(reference, AuthRuntime.generation(), feed, initial, position)
        entry.info = info
        entries[entry.key] = entry
        return "queue:${entry.key}"
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    private fun entry(key: String): Entry {
        val entry = entries[key] ?: error("NEWPLAYER_QUEUE_EXPIRED")
        check(entry.generation == AuthRuntime.generation()) { "AUTH_SESSION_CHANGED" }
        return entry
    }

    override suspend fun getMediaItem(item: String): MediaItem = entry(item).let { entry ->
        MediaItem.Builder().setUri(entry.reference.url).setTag(Request(entry)).build()
    }

    suspend fun resolve(request: Request): MediaItem = request.entry.loading.withLock {
        withContext(Dispatchers.IO) {
            val entry = entry(request.entry.key)
            val reference = entry.reference
            val knownInfo = entry.info
            val key = if (knownInfo != null && !entry.forceLoad) {
                network.offer(knownInfo)
            } else {
                network.resolve(null, reference.serviceId, reference.url, entry.forceLoad)
            }
            val info = network.info(key)
            val resolved = network.mediaItem(key, entry.videoChoice, entry.audioChoice)
            val start = if (entry.resumePending) history.resumePosition(info, entry.explicitPosition ?: reference.recoveryPosition) else 0L
            this@QueueMediaRepository.entry(entry.key) // Reject cancelled account/queue results.
            entry.info = info
            entry.startPosition = start
            entry.forceLoad = false
            synchronized(this@QueueMediaRepository) {
                // Keep queue references, but bound retained heavy extraction data.
                entries.values.filter { it !== entry && it.info != null }.dropLast(7).forEach { it.info = null }
            }
            resolved
        }
    }

    override suspend fun getMetaInfo(item: String): MediaMetadata {
        val reference = entry(item).reference
        return MediaMetadata.Builder().setTitle(reference.title).setArtist(reference.uploader)
            .setDurationMs(NetworkMetadata.durationMs(reference.streamType, reference.duration))
            .setArtworkUri(reference.thumbnails.firstOrNull()?.url?.let(Uri::parse)).setIsPlayable(true).build()
    }

    private suspend fun info(item: String): StreamInfo {
        val entry = entry(item)
        if (entry.info == null) resolve(Request(entry))
        return checkNotNull(entry(item).info)
    }

    override suspend fun getPreviewThumbnail(item: String, timestampInMs: Long) = network.details.preview(info(item), timestampInMs)
    override suspend fun getPreviewThumbnailsInfo(item: String) = network.details.previewInfo(info(item))
    override suspend fun getChapters(item: String) = network.details.chapters(info(item))
    override suspend fun getTimestampLink(item: String, timestampInSeconds: Long): URL? = network.details.timestampLink(info(item), timestampInSeconds)
    override suspend fun getLink(item: String): URL = URL(entry(item).reference.url)

    @Synchronized
    fun retain(keys: Set<String>) {
        entries.keys.retainAll(keys)
    }

    companion object {
        fun request(item: MediaItem?): Request? = item?.localConfiguration?.tag as? Request
    }
}
