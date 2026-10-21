// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.localplayer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.newplayer.data.Chapter
import net.newpipe.newplayer.repository.MediaRepository

/** SAF grants are used directly: no filesystem path guessing or storage-wide permission. */
internal class LocalMediaRepository(private val resolver: ContentResolver) : MediaRepository {
    data class Document(val uri: Uri, val title: String, val mime: String?, val size: Long?)

    private val documents = ConcurrentHashMap<String, Document>()

    suspend fun register(uri: Uri): Document = withContext(Dispatchers.IO) {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) { "Expected a document URI" }
        resolver.openAssetFileDescriptor(uri, "r")?.use { } ?: error("Document is not readable")
        var title = "Video"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) title = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val document = Document(uri, title, resolver.getType(uri), size)
        documents[uri.toString()] = document
        document
    }

    private fun document(item: String) = documents[item] ?: error("Document has not been selected")

    override suspend fun getMetaInfo(item: String): MediaMetadata = MediaMetadata.Builder()
        .setTitle(document(item).title)
        .setIsPlayable(true)
        .build()

    override suspend fun getMediaItem(item: String): MediaItem {
        val document = document(item)
        return MediaItem.Builder()
            .setUri(document.uri)
            .apply { document.mime?.takeIf { it.startsWith("video/") && !it.contains('*') }?.let { setMimeType(it) } }
            .build()
    }

    override suspend fun getPreviewThumbnail(item: String, timestampInMs: Long): Bitmap? = null

    override suspend fun getPreviewThumbnailsInfo(item: String) = MediaRepository.PreviewThumbnailsInfo(0, 1)

    override suspend fun getChapters(item: String): List<Chapter> = emptyList()

    override suspend fun getTimestampLink(item: String, timestampInSeconds: Long): URL? = null

    override suspend fun getLink(item: String): URL? = null
}
