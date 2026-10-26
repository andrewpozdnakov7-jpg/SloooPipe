// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

class PlaybackStartIntentTest {
    @Test
    fun rawZeroRequestSurvivesRewrittenExtractorOriginalUrl() {
        for (request in listOf("$URL&t=0", "$URL#t=0", "$URL&start=00")) {
            val queue = PlaybackStartIntent.createQueue(info(URL), request)
            val item = queue.item!!
            assertEquals(URL, item.url)
            assertEquals(0L, item.recoveryPosition)
            assertEquals(0L, PlaybackResumePolicy.position(600, item.recoveryPosition, 0, StreamStateEntity(1, 120000)))
            assertEquals(0L, checkNotNull(PlaybackResumePolicy.requestedPosition(600, item.recoveryPosition, 0)))
        }
    }

    @Test
    fun ordinaryRequestIgnoresAStaleZeroHintInMetadata() {
        val item = PlaybackStartIntent.createQueue(info("$URL&t=0"), URL).item!!
        assertEquals(PlayQueueItem.RECOVERY_UNSET, item.recoveryPosition)
        assertEquals(120000L, PlaybackResumePolicy.position(600, item.recoveryPosition, 0, StreamStateEntity(1, 120000)))
        assertNull(PlaybackResumePolicy.requestedPosition(600, item.recoveryPosition, 0))
    }

    @Test
    fun parsedPositiveTimestampKeepsItsExistingPrecedence() {
        val item = PlaybackStartIntent.createQueue(info("$URL&t=45", 45), "$URL&t=0").item!!
        assertEquals(45000L, item.recoveryPosition)
    }

    @Test
    fun absentRawRequestKeepsTheMetadataFallback() {
        assertEquals(0L, PlaybackStartIntent.createQueue(info("$URL&t=0"), null).item!!.recoveryPosition)
        assertEquals(PlayQueueItem.RECOVERY_UNSET, PlaybackStartIntent.createQueue(info(URL), null).item!!.recoveryPosition)
    }

    @Test
    fun queryZeroSurvivesCanonicalQueueAndHistoryFallback() {
        val item = PlayQueueItem(info("$URL&t=0"))
        assertEquals(URL, item.url)
        assertEquals(0L, item.recoveryPosition)
        assertEquals(0L, PlaybackResumePolicy.position(600, item.recoveryPosition, 0, StreamStateEntity(1, 120000)))
        assertEquals(0L, checkNotNull(PlaybackResumePolicy.requestedPosition(600, item.recoveryPosition, 0)))
    }

    @Test
    fun fragmentZeroAlsoSurvivesCanonicalization() {
        assertEquals(0L, PlayQueueItem(info("$URL#t=0")).recoveryPosition)
    }

    @Test
    fun absentTimestampKeepsHistoryAndDoesNotRewindAnActiveSession() {
        val item = PlayQueueItem(info(URL))
        assertEquals(PlayQueueItem.RECOVERY_UNSET, item.recoveryPosition)
        assertEquals(120000L, PlaybackResumePolicy.position(600, item.recoveryPosition, 0, StreamStateEntity(1, 120000)))
        assertNull(PlaybackResumePolicy.requestedPosition(600, item.recoveryPosition, 0))
    }

    @Test
    fun positiveTimestampIsPreserved() {
        val item = PlayQueueItem(info("$URL&t=45", 45))
        assertEquals(45000L, item.recoveryPosition)
        assertEquals(45000L, checkNotNull(PlaybackResumePolicy.requestedPosition(600, item.recoveryPosition, 45)))
    }

    @Test
    fun recognizedZeroFormsAreExplicitRequests() {
        for (suffix in listOf("&t=00", "&start=0", "&time_continue=0", "&%74=%30", "&t=0m0s", "&t=00%3A00")) {
            assertTrue(suffix, PlaybackStartIntent.hasExplicitZeroTimestamp(URL + suffix))
        }
    }

    @Test
    fun unrelatedMalformedAndNonzeroValuesDoNotForceZero() {
        for (value in listOf(null, "not a URI", "$URL&t=%", "$URL&t=10", "$URL&t=0.5", "$URL&t=0oops", "$URL&not_t=0", "$URL&other=t%3D0", "https://example.org/t=0")) {
            assertFalse(value, PlaybackStartIntent.hasExplicitZeroTimestamp(value))
        }
    }

    @Test
    fun parsedPositiveTimestampWinsOverAConflictingZeroHint() {
        assertEquals(45000L, PlayQueueItem(info("$URL&t=0", 45)).recoveryPosition)
    }

    @Test
    fun explicitRecoveryWinsOverLinkForFreshAndReusedQueues() {
        for (explicit in listOf(0L, 25000L)) {
            val queue = SinglePlayQueue(info("$URL&t=90", 90), explicit)
            val recovery = queue.item!!.recoveryPosition
            assertEquals(explicit, PlaybackResumePolicy.position(600, recovery, 90, StreamStateEntity(1, 120000)))
            assertEquals(explicit, checkNotNull(PlaybackResumePolicy.requestedPosition(600, recovery, 90)))
        }
    }

    private fun info(originalUrl: String, startSeconds: Long = 0) = StreamInfo(0, URL, originalUrl, StreamType.VIDEO_STREAM, "fixture", "Fixture", 0).apply {
        startPosition = startSeconds
        duration = 600
        uploaderName = "Fixture"
        thumbnails = emptyList()
    }

    private companion object {
        const val URL = "https://example.org/watch?v=fixture"
    }
}
