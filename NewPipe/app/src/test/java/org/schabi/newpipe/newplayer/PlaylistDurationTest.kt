// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import net.newpipe.newplayer.logic.PlaylistDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistDurationTest {
    @Test fun duplicateOccurrencesContributeSeparately() {
        assertEquals(14000L, PlaylistDuration.sum(listOf(4000L, 6000L, 4000L)))
    }

    @Test fun liveUnknownOrNegativeIsUnknownRatherThanZero() {
        assertNull(PlaylistDuration.sum(listOf(1000L, null)))
        assertNull(PlaylistDuration.sum(listOf(-1L, 1000L)))
    }

    @Test fun sumOverflowIsUnknown() {
        assertNull(PlaylistDuration.sum(listOf(Long.MAX_VALUE, 1L)))
        assertEquals(Long.MAX_VALUE, PlaylistDuration.sum(listOf(Long.MAX_VALUE)))
    }

    @Test fun emptyAndZeroDurationRemainValid() {
        assertEquals(0L, PlaylistDuration.sum(emptyList()))
        assertEquals(0L, PlaylistDuration.sum(listOf(0L)))
    }
}
