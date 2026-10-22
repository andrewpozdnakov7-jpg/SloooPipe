// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueFeedPolicyTest {
    @Test fun pageFollowsLastSurvivingFeedEntryAfterReorderAndEnqueue() {
        assertEquals(4, QueueFeedPolicy.insertionIndex(listOf("other", "feed", "new", "feed", "other"), "feed"))
    }

    @Test fun removedFeedCannotAppendALatePageToAnotherQueue() {
        assertNull(QueueFeedPolicy.insertionIndex(listOf("new", "new"), "old"))
        assertNull(QueueFeedPolicy.insertionIndex(emptyList(), "old"))
    }

    @Test fun demandUsesLoadedSourcePositionRatherThanReorderedPlaybackIndex() {
        assertTrue(QueueFeedPolicy.shouldFetch(7, 10, false, -1))
        assertFalse(QueueFeedPolicy.shouldFetch(0, 10, false, -1))
    }

    @Test fun pendingOrCompleteFeedCannotRequestTheSamePageAgain() {
        assertFalse(QueueFeedPolicy.shouldFetch(9, 10, false, 10))
        assertFalse(QueueFeedPolicy.shouldFetch(9, 10, true, -1))
        assertFalse(QueueFeedPolicy.shouldFetch(-1, 10, false, -1))
        assertTrue(QueueFeedPolicy.shouldFetch(9, 10, false, -1))
    }
}
