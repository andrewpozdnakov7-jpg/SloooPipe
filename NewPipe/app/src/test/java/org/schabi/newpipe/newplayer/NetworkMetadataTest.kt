// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamType

class NetworkMetadataTest {
    @Test
    fun finiteVideoAndAudioExposeMillisecondsForTheQueue() {
        assertEquals(635000L, NetworkMetadata.durationMs(StreamType.VIDEO_STREAM, 635))
        assertEquals(180000L, NetworkMetadata.durationMs(StreamType.AUDIO_STREAM, 180))
    }

    @Test
    fun liveDurationRemainsUnknownEvenWhenTheExtractorProvidesAnUptime() {
        assertNull(NetworkMetadata.durationMs(StreamType.LIVE_STREAM, 86400))
        assertNull(NetworkMetadata.durationMs(StreamType.AUDIO_LIVE_STREAM, 86400))
    }

    @Test
    fun unknownDurationIsNotTurnedIntoZeroOrNegativeMilliseconds() {
        assertNull(NetworkMetadata.durationMs(StreamType.VIDEO_STREAM, -1))
        assertNull(NetworkMetadata.durationMs(StreamType.AUDIO_STREAM, Long.MIN_VALUE))
    }

    @Test
    fun zeroRemainsAValidFiniteDuration() {
        assertEquals(0L, NetworkMetadata.durationMs(StreamType.VIDEO_STREAM, 0))
    }

    @Test
    fun conversionCannotOverflow() {
        val limit = Long.MAX_VALUE / 1000
        assertEquals(limit * 1000, NetworkMetadata.durationMs(StreamType.VIDEO_STREAM, limit))
        assertNull(NetworkMetadata.durationMs(StreamType.VIDEO_STREAM, limit + 1))
    }
}
