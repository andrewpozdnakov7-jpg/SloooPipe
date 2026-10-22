// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.newpipe.newplayer.data.Chapter
import net.newpipe.newplayer.repository.MediaRepository
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import org.schabi.newpipe.util.image.CoilHelper

/** Existing extractor data, bounded storyboard storage and no fabricated chapters/previews. */
internal class NetworkMediaDetails(private val context: Context) {
    private val epoch = AtomicLong()
    private val atlasLock = Mutex()
    private val atlases = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun clear() {
        epoch.incrementAndGet()
        atlases.evictAll()
    }

    fun chapters(info: StreamInfo): List<Chapter> = info.streamSegments.orEmpty().map {
        Chapter(it.startTimeSeconds.coerceAtLeast(0) * 1000L, it.title, it.previewUrl?.takeIf(String::isNotBlank)?.let(Uri::parse))
    }

    private fun frames(info: StreamInfo): Frameset? {
        val type = SeekbarPreviewThumbnailHelper.getSeekbarPreviewThumbnailType(context)
        if (type == SeekbarPreviewThumbnailHelper.SeekbarPreviewThumbnailType.NONE) return null
        val valid = info.previewFrames.orEmpty().filter { it.totalCount > 0 && it.durationPerFrame > 0 && it.frameWidth > 0 && it.frameHeight > 0 }
        return if (type == SeekbarPreviewThumbnailHelper.SeekbarPreviewThumbnailType.LOW_QUALITY) {
            valid.minByOrNull { it.frameWidth.toLong() * it.frameHeight }
        } else {
            valid.maxByOrNull { it.frameWidth.toLong() * it.frameHeight }
        }
    }

    fun previewInfo(info: StreamInfo): MediaRepository.PreviewThumbnailsInfo = frames(info)?.let {
        MediaRepository.PreviewThumbnailsInfo(it.totalCount.toLong(), it.durationPerFrame.toLong())
    } ?: MediaRepository.PreviewThumbnailsInfo(0, 1)

    suspend fun preview(info: StreamInfo, timestamp: Long): Bitmap? = withContext(Dispatchers.IO) {
        val owner = epoch.get()
        val frame = frames(info) ?: return@withContext null
        val bounded = timestamp.coerceIn(0, (frame.totalCount - 1L) * frame.durationPerFrame)
        val bounds = frame.getFrameBoundsAt(bounded)
        val url = frame.urls.getOrNull(bounds[0]) ?: return@withContext null
        atlasLock.withLock {
            if (owner != epoch.get()) return@withLock null
            val atlas = atlases.get(url) ?: CoilHelper.loadBitmapBlocking(context, url)?.also {
                currentCoroutineContext().ensureActive()
                if (owner == epoch.get()) atlases.put(url, it)
            } ?: return@withLock null
            if (owner != epoch.get()) return@withLock null
            val left = bounds[1].coerceIn(0, atlas.width - 1)
            val top = bounds[2].coerceIn(0, atlas.height - 1)
            val result = Bitmap.createBitmap(atlas, left, top, frame.frameWidth.coerceAtMost(atlas.width - left), frame.frameHeight.coerceAtMost(atlas.height - top))
            if (result === atlas) atlas.copy(Bitmap.Config.ARGB_8888, false) else result
        }
    }

    fun timestampLink(info: StreamInfo, seconds: Long): URL? {
        if (info.serviceId != ServiceList.YouTube.serviceId) return null
        val uri = Uri.parse(info.url)
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filterNot { it == "t" || it == "start" }.forEach { key ->
            uri.getQueryParameters(key).forEach { builder.appendQueryParameter(key, it) }
        }
        return URL(builder.appendQueryParameter("t", seconds.coerceAtLeast(0).toString()).build().toString())
    }
}
