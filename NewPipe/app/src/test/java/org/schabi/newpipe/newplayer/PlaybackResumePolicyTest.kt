// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.database.stream.model.StreamStateEntity

class PlaybackResumePolicyTest {
    @Test
    fun explicitZeroRestartsInsteadOfResumingHistoryOrTimestamp() {
        assertEquals(0L, PlaybackResumePolicy.position(600, 0, 45, StreamStateEntity(1, 120000)))
    }

    @Test
    fun queueRecoveryWinsOverLinkAndHistory() {
        assertEquals(25000L, PlaybackResumePolicy.position(600, 25000, 45, StreamStateEntity(1, 120000)))
    }

    @Test
    fun timestampWinsOverHistoryWhenNoExplicitPositionWasRequested() {
        assertEquals(45000L, PlaybackResumePolicy.position(600, Long.MIN_VALUE, 45, StreamStateEntity(1, 120000)))
    }

    @Test
    fun unfinishedHistoryResumes() {
        assertEquals(120000L, PlaybackResumePolicy.position(600, Long.MIN_VALUE, 0, StreamStateEntity(1, 120000)))
    }

    @Test
    fun finishedHistoryStartsAgain() {
        assertEquals(0L, PlaybackResumePolicy.position(600, Long.MIN_VALUE, 0, StreamStateEntity(1, 590000)))
    }

    @Test
    fun invalidTimestampCannotSeekPastTheVideo() {
        assertEquals(599999L, PlaybackResumePolicy.position(600, Long.MIN_VALUE, 900, null))
    }
}
