// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import java.net.URI
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** Selection only: no Android, network requests, playback or account access. */
internal object NetworkStreamSelector {
    class Selection(val streams: List<Stream>, val manifestUrl: String?, val manifestType: DeliveryMethod?, val live: Boolean)

    fun select(
        info: StreamInfo,
        videos: List<VideoStream> = info.videoStreams.orEmpty() + info.videoOnlyStreams.orEmpty(),
        audios: List<AudioStream> = info.audioStreams.orEmpty(),
        videoIndex: (List<VideoStream>) -> Int = { 0 },
        audioIndex: (List<AudioStream>) -> Int = { 0 }
    ): Selection {
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (live) manifest(info, true)?.let { return it }
        val audio = audios.filter(::isProgressive)
        if ((info.streamType == StreamType.AUDIO_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) && audio.isNotEmpty()) {
            return Selection(listOf(audio[audioIndex(audio).takeIf { it in audio.indices } ?: 0]), null, null, live)
        }
        // Never silently produce a mute video-only source when the checkpoint requires audio.
        val video = videos.filter { isProgressive(it) && (!it.isVideoOnly || audio.isNotEmpty()) }
        if (video.isNotEmpty()) {
            val selected = video[videoIndex(video).takeIf { it in video.indices } ?: 0]
            val streams = if (selected.isVideoOnly) {
                listOf(selected, audio[audioIndex(audio).takeIf { it in audio.indices } ?: 0])
            } else {
                listOf(selected)
            }
            return Selection(streams, null, null, live)
        }
        manifest(info, live)?.let { return it }
        throw UnsupportedOperationException("NEWPLAYER_NO_SUPPORTED_VIDEO_SOURCE")
    }

    private fun manifest(info: StreamInfo, live: Boolean): Selection? = when {
        isHttpUrl(info.hlsUrl) -> Selection(emptyList(), info.hlsUrl, DeliveryMethod.HLS, live)
        isHttpUrl(info.dashMpdUrl) -> Selection(emptyList(), info.dashMpdUrl, DeliveryMethod.DASH, live)
        else -> null
    }

    fun isProgressive(stream: Stream): Boolean = stream.isUrl &&
        stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && isHttpUrl(stream.content)

    private fun isHttpUrl(value: String?): Boolean = try {
        val uri = URI(value.orEmpty())
        (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) {
        false
    }
}
