// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueReplacementPolicyTest {
    private val a = StreamIdentity(0, "https://example.org/a")
    private val b = StreamIdentity(0, "https://example.org/b")
    private val snapshot = PlaybackSnapshot("session", listOf("a1", "b1", "a2"), "b1", listOf(a, b, a))

    @Test fun pausedQueueRequiresConfirmation() {
        assertTrue(QueueReplacementPolicy.requiresConfirmation(true, snapshot, listOf(b)))
    }

    @Test fun orderAndDuplicateMultiplicityMatter() {
        assertFalse(QueueReplacementPolicy.requiresConfirmation(true, snapshot, listOf(a, b, a)))
        assertTrue(QueueReplacementPolicy.requiresConfirmation(true, snapshot, listOf(a, a, b)))
        assertTrue(QueueReplacementPolicy.requiresConfirmation(true, snapshot, listOf(a, b)))
    }

    @Test fun disabledPreferenceOrAbsentSessionAllowsLaunch() {
        assertFalse(QueueReplacementPolicy.requiresConfirmation(false, snapshot, listOf(b)))
        assertFalse(QueueReplacementPolicy.requiresConfirmation(true, null, listOf(b)))
    }

    @Test fun consentAppliesExactlyOnce() {
        val consent = QueueReplacementPolicy.Consent(snapshot)
        assertTrue(consent.accept(snapshot))
        assertFalse(consent.accept(snapshot))
    }

    @Test fun staleConsentNeverAppliesToReorderedOrReplacedSession() {
        assertFalse(QueueReplacementPolicy.Consent(snapshot).accept(snapshot.copy(ids = listOf("b1", "a1", "a2"))))
        assertFalse(QueueReplacementPolicy.Consent(snapshot).accept(snapshot.copy(session = "new-account")))
        assertFalse(QueueReplacementPolicy.Consent(snapshot).accept(snapshot.copy(selected = "a1")))
        assertFalse(QueueReplacementPolicy.Consent(snapshot).accept(null))
    }
}
