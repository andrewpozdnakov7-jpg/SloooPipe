/* NewPlayer
 *
 * @author Christian Schabesberger
 *
 * Copyright (C) NewPipe e.V. 2024 <code(at)newpipe-ev.de>
 *
 * NewPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPlayer.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.newpipe.newplayer.repository

import android.graphics.Bitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import net.newpipe.newplayer.data.Chapter
import java.net.URL

/**
 * This is a meta media repository that functions as a cache.
 * By default, NewPlayer itself does not cache anything, which is why it asks the repository
 * for anything over and over again. If these requests are not cached your app might unnecessarily
 * perform network requests or other IO. In order to prevent this use this media repository.
 *
 * However, it's discouraged to use this cache if your app already has a cache. Use your own
 * cache instead in order not to cache the same data twice. When using your own cache you will also
 * be able to share cached data between your app and NewPlayer. (IE. NewPipe should not use this).
 *
 * @param actualRepository the actual repository
 * @param cacheRepoScope The CoroutineScope this repo should use to perform requests with.
 * If you want to use your own Scope make sure you use a Dispatcher that does not spawn more
 * then one Thread, since the caching itself is not thradsafe.
 *
 * TODO: Maybe a (individual) timeout would be nice
 */
class CachingRepository(
    private val actualRepository: MediaRepository,
    private var cacheRepoScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) : MediaRepository {
    private class Entry<T>(val data: T)

    private open class Cache<K, T> {
        var cache: HashMap<K, Entry<T>> = HashMap()
        open suspend fun get(key: K, onCacheMiss: suspend () -> T): T = cache.getOrPut(key) {
            Entry(onCacheMiss())
        }.data

        suspend fun flush() {
            cache = HashMap()
        }
    }

    private data class TimestampedItem(val item: String, val timestamp: Long)
    private class ItemCache<T> : Cache<String, T>()
    private open class TimeStampedCache<T> : Cache<TimestampedItem, T>()
    private inner class PreviewThumbnailCache : TimeStampedCache<Bitmap?>() {
        override suspend fun get(
            key: TimestampedItem, onCacheMiss: suspend () -> Bitmap?
        ): Bitmap? {
            val info = getPreviewThumbnailsInfo(key.item)
            if (info.distanceInMS <= 0) {
                return null
            }
            val correctedTimestamp = (key.timestamp / info.distanceInMS) * info.distanceInMS
            return super.get(TimestampedItem(key.item, correctedTimestamp), onCacheMiss)
        }
    }

    private var metaInfoCache = ItemCache<MediaMetadata>()
    private var mediaItemCache = ItemCache<MediaItem>()
    private var previewThumbnailsInfoCache = ItemCache<MediaRepository.PreviewThumbnailsInfo>()
    private var thumbnailCache = PreviewThumbnailCache()
    private var chapterCache = ItemCache<List<Chapter>>()
    private var timestampLinkCache = TimeStampedCache<URL?>()
    private var linkCache = ItemCache<URL?>()

    override suspend fun getMetaInfo(item: String) = metaInfoCache.get(item) {
        actualRepository.getMetaInfo(item)
    }

    override suspend fun getMediaItem(item: String) = mediaItemCache.get(item) {
        actualRepository.getMediaItem(item)
    }

    override suspend fun getPreviewThumbnail(item: String, timestampInMs: Long) =
        thumbnailCache.get(TimestampedItem(item, timestampInMs)) {
            actualRepository.getPreviewThumbnail(item, timestampInMs)
        }

    override suspend fun getPreviewThumbnailsInfo(item: String) =
        previewThumbnailsInfoCache.get(item) {
            actualRepository.getPreviewThumbnailsInfo(item)
        }

    override suspend fun getChapters(item: String) = chapterCache.get(item) {
        actualRepository.getChapters(item)
    }

    override suspend fun getTimestampLink(item: String, timestampInSeconds: Long) =
        timestampLinkCache.get(TimestampedItem(item, timestampInSeconds)) {
            actualRepository.getTimestampLink(item, timestampInSeconds)
        }

    override suspend fun getLink(item: String) = linkCache.get(item) {
        actualRepository.getLink(item)
    }

    /**
     * Will flush the caches.
     */
    suspend fun flush() {
        cacheRepoScope.cancel()
        metaInfoCache.flush()
        mediaItemCache.flush()
        previewThumbnailsInfoCache.flush()
        thumbnailCache.flush()
        chapterCache.flush()
        timestampLinkCache.flush()
        linkCache.flush()
    }
}