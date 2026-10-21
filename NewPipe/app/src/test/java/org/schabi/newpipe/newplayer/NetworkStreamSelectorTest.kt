// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class NetworkStreamSelectorTest {
    @Test
    fun audioOnlyStreamUsesPreferredAudio() {
        val preferred = audio("preferred")
        val selected = NetworkStreamSelector.select(info(StreamType.AUDIO_STREAM), emptyList(), listOf(audio("other"), preferred), audioIndex = { 1 })
        assertSame(preferred, selected.streams.single())
    }

    @Test(expected = UnsupportedOperationException::class)
    fun videoWithoutVideoSourceDoesNotSilentlyBecomeAudioOnly() {
        NetworkStreamSelector.select(info(), emptyList(), listOf(audio("sound")))
    }

    @Test
    fun muxedVideoDoesNotMergeAnotherAudioTrack() {
        val video = video(false)
        val selected = NetworkStreamSelector.select(info(), listOf(video), listOf(audio("a")))
        assertEquals(1, selected.streams.size)
        assertSame(video, selected.streams.single())
    }

    @Test
    fun separateVideoAlwaysHasSelectedAudio() {
        val video = video(true)
        val preferredAudio = audio("second")
        val selected = NetworkStreamSelector.select(info(), listOf(video), listOf(audio("first"), preferredAudio), audioIndex = { 1 })
        assertEquals(2, selected.streams.size)
        assertSame(video, selected.streams[0])
        assertSame(preferredAudio, selected.streams[1])
    }

    @Test
    fun videoOnlyWithoutAudioUsesAvailableMuxedVideo() {
        val muxed = video(false)
        val selected = NetworkStreamSelector.select(info(), listOf(video(true), muxed), emptyList())
        assertSame(muxed, selected.streams.single())
    }

    @Test(expected = UnsupportedOperationException::class)
    fun refusesSilentVideoOnlyWithoutAudioOrManifest() {
        NetworkStreamSelector.select(info(), listOf(video(true)), emptyList())
    }

    @Test
    fun liveStreamUsesManifestBeforeDirectFormats() {
        val info = info(StreamType.LIVE_STREAM).apply { hlsUrl = "https://example.org/live.m3u8" }
        val selected = NetworkStreamSelector.select(info, listOf(video(false)), emptyList())
        assertTrue(selected.live)
        assertEquals(DeliveryMethod.HLS, selected.manifestType)
        assertEquals(info.hlsUrl, selected.manifestUrl)
        assertTrue(selected.streams.isEmpty())
    }

    @Test
    fun vodFallsBackToDashManifestWhenNoDirectSources() {
        val info = info().apply { dashMpdUrl = "https://example.org/video.mpd" }
        assertEquals(DeliveryMethod.DASH, NetworkStreamSelector.select(info).manifestType)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun rejectsTorrentEvenWithAnHttpsUrl() {
        NetworkStreamSelector.select(info(), listOf(video(false, method = DeliveryMethod.TORRENT)), emptyList())
    }

    @Test(expected = UnsupportedOperationException::class)
    fun rejectsNonNetworkSourceInNetworkHandoff() {
        NetworkStreamSelector.select(info(), listOf(video(false, url = "file:///sdcard/private.mp4")), emptyList())
    }

    @Test
    fun invalidPreferredIndexDoesNotCrashSelection() {
        val video = video(false)
        assertSame(video, NetworkStreamSelector.select(info(), listOf(video), emptyList(), videoIndex = { -1 }).streams.single())
    }

    private fun info(type: StreamType = StreamType.VIDEO_STREAM) = StreamInfo(0, "https://example.org/watch", "https://example.org/watch", type, "test", "Test video", 0)

    private fun video(only: Boolean, url: String = "https://example.org/video.mp4", method: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP) = VideoStream.Builder()
        .setId("video").setContent(url, true).setIsVideoOnly(only).setResolution("360p")
        .setMediaFormat(MediaFormat.MPEG_4).setDeliveryMethod(method).build()

    private fun audio(id: String) = AudioStream.Builder().setId(id).setContent("https://example.org/$id.m4a", true)
        .setAverageBitrate(128).setMediaFormat(MediaFormat.M4A).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP).build()
}
