// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.newpipe.newplayer.repository.MediaRepository
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ListHelper

/** In-memory handoff of the already extracted StreamInfo, with URL-only process restoration. */
internal class NetworkMediaRepository(private val context: Context) : MediaRepository {
    class Source(val uri: Uri, val mime: String?, val youtube: Boolean, val inlineDash: String? = null) {
        override fun toString() = "Source(mime=$mime, youtube=$youtube, inlineDash=${inlineDash != null})"
    }

    class Plan(val sources: List<Source>, val live: Boolean)
    private class Entry(val info: StreamInfo, val generation: Long, @Volatile var mediaItem: MediaItem? = null)
    private val entries = LinkedHashMap<String, Entry>()
    internal val details = NetworkMediaDetails(context)

    @Synchronized
    fun clear() {
        entries.clear()
        details.clear()
    }

    @Synchronized
    fun offer(info: StreamInfo): String {
        val key = UUID.randomUUID().toString()
        entries[key] = Entry(info, org.schabi.newpipe.auth.AuthRuntime.generation())
        while (entries.size > 8) entries.remove(entries.keys.first())
        return key
    }

    suspend fun resolve(key: String?, serviceId: Int, url: String, forceLoad: Boolean = false): String = withContext(Dispatchers.IO) {
        val generation = org.schabi.newpipe.auth.AuthRuntime.generation()
        if (!forceLoad && key != null && synchronized(this@NetworkMediaRepository) { entries.containsKey(key) }) {
            entry(key) // Reject a handoff from a previous account generation.
            return@withContext key
        }
        val info = suspendCancellableCoroutine<StreamInfo> { continuation ->
            val request = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad).subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io()).subscribe(
                { value -> continuation.resumeWith(Result.success(value)) },
                { error -> continuation.resumeWith(Result.failure(error)) }
            )
            continuation.invokeOnCancellation { request.dispose() }
        }
        check(generation == org.schabi.newpipe.auth.AuthRuntime.generation()) { "AUTH_SESSION_CHANGED" }
        offer(info)
    }

    @Synchronized
    private fun entry(key: String): Entry {
        val entry = entries[key] ?: error("NEWPLAYER_STREAM_HANDOFF_EXPIRED")
        check(entry.generation == org.schabi.newpipe.auth.AuthRuntime.generation()) { "AUTH_SESSION_CHANGED" }
        return entry
    }

    fun title(key: String): String = entry(key).info.name

    fun info(key: String): StreamInfo = entry(key).info

    override suspend fun getMediaItem(item: String): MediaItem = mediaItem(item, null, null)

    suspend fun mediaItem(item: String, videoChoice: String?, audioChoice: String?): MediaItem = withContext(Dispatchers.IO) {
        val entry = entry(item)
        if (videoChoice == null && audioChoice == null) entry.mediaItem?.let { return@withContext it }
        val info = entry.info
        // Filter before preference grouping/deduplication, so an unsupported format cannot
        // displace a usable alternative (including a muxed video when separate audio is absent).
        val allAudios = ListHelper.getPlayableStreams(info.audioStreams, info.serviceId).filter(NetworkStreamSelector::isProgressive)
        val audios = if (audioChoice == null) ListHelper.getFilteredAudioStreams(context, allAudios) else allAudios
        val allVideos = ListHelper.getPlayableStreams(info.videoStreams.orEmpty() + info.videoOnlyStreams.orEmpty(), info.serviceId)
            .filter { NetworkStreamSelector.isProgressive(it) && (!it.isVideoOnly || audios.isNotEmpty()) }
        val videos = ListHelper.getSortedStreamVideosList(
            context,
            ListHelper.getPlayableStreams(info.videoStreams, info.serviceId).filter(NetworkStreamSelector::isProgressive),
            if (audios.isEmpty()) emptyList() else ListHelper.getPlayableStreams(info.videoOnlyStreams, info.serviceId).filter(NetworkStreamSelector::isProgressive),
            false,
            true
        )
        val selection = NetworkStreamSelector.select(
            info,
            (if (videoChoice == null) videos else allVideos).let { available ->
                if (audioChoice != null && available.any { it.isVideoOnly }) available.filter { it.isVideoOnly } else available
            },
            audios,
            { streams -> streams.indexOfFirst { NetworkStreamSelector.identity(it) == videoChoice }.takeIf { it >= 0 } ?: ListHelper.getDefaultResolutionIndex(context, streams) },
            { streams -> streams.indexOfFirst { NetworkStreamSelector.identity(it) == audioChoice }.takeIf { it >= 0 } ?: ListHelper.getAudioFormatIndex(context, streams, null) }
        )
        val youtube = info.serviceId == ServiceList.YouTube.serviceId
        val sources = if (selection.manifestUrl != null) {
            listOf(Source(Uri.parse(selection.manifestUrl), manifestMime(selection.manifestType), youtube))
        } else {
            selection.streams.map { stream ->
                // Preserve NewPipe's seekable segment-base manifests for YouTube adaptive MP4/WebM.
                val adaptive = stream is AudioStream || stream is VideoStream && stream.isVideoOnly
                val manifest = if (youtube && adaptive && info.streamType == StreamType.VIDEO_STREAM && stream.itagItem != null) {
                    try {
                        YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, stream.itagItem!!, info.duration)
                    } catch (error: Exception) {
                        Log.w(NewPlayerRuntime.TAG, "network.generated_dash_unavailable error=${error.javaClass.simpleName}")
                        null
                    }
                } else {
                    null
                }
                Source(Uri.parse(stream.content), if (manifest == null) stream.format?.mimeType else manifestMime(DeliveryMethod.DASH), youtube, manifest)
            }
        }
        Log.i(NewPlayerRuntime.TAG, "network.plan service=${info.serviceId} live=${selection.live} parts=${sources.size} kind=${selection.manifestType ?: "PROGRESSIVE"} inlineDash=${sources.count { it.inlineDash != null }}")
        val subtitles = info.subtitles.orEmpty().filter { it.isUrl && it.format?.mimeType != null }.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.content)).setMimeType(it.format!!.mimeType)
                .setLanguage(it.languageTag).setLabel(it.displayLanguageName).build()
        }
        val mediaItem = MediaItem.Builder().setUri(sources.first().uri).setTag(Plan(sources, selection.live))
            .setSubtitleConfigurations(subtitles).build()
        // getMetaInfo/getMediaItem cannot publish across an account change.
        entry(item)
        if (videoChoice == null && audioChoice == null) entry.mediaItem = mediaItem
        mediaItem
    }

    override suspend fun getMetaInfo(item: String): MediaMetadata {
        val info = entry(item).info
        return MediaMetadata.Builder().setTitle(info.name).setArtist(info.uploaderName)
            .setDurationMs(NetworkMetadata.durationMs(info.streamType, info.duration)).setIsPlayable(true).build()
    }

    override suspend fun getPreviewThumbnail(item: String, timestampInMs: Long) = details.preview(info(item), timestampInMs)
    override suspend fun getPreviewThumbnailsInfo(item: String) = details.previewInfo(info(item))
    override suspend fun getChapters(item: String) = details.chapters(info(item))
    override suspend fun getTimestampLink(item: String, timestampInSeconds: Long): URL? = details.timestampLink(info(item), timestampInSeconds)
    override suspend fun getLink(item: String): URL = URL(entry(item).info.url)

    private fun manifestMime(method: DeliveryMethod?) = when (method) {
        DeliveryMethod.HLS -> "application/x-mpegURL"
        DeliveryMethod.DASH -> "application/dash+xml"
        else -> error("NEWPLAYER_UNSUPPORTED_MANIFEST")
    }
}
