// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import net.newpipe.newplayer.logic.PlaybackRequestGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRequestGateTest {
    @Test fun lateAIsRejectedAfterB() {
        val gate = PlaybackRequestGate()
        val a = gate.next()
        val b = gate.next()
        assertFalse(gate.isCurrent(a))
        assertTrue(gate.isCurrent(b))
    }

    @Test fun insertionBelongsToCapturedQueue() {
        val gate = PlaybackRequestGate()
        gate.next()
        val insertion = gate.snapshot()
        assertTrue(gate.isCurrent(insertion))
        gate.next()
        assertFalse(gate.isCurrent(insertion))
    }

    @Test fun releaseRejectsPendingRequest() {
        val gate = PlaybackRequestGate()
        val request = gate.next()
        gate.invalidate()
        assertFalse(gate.isCurrent(request))
        assertTrue(gate.isCurrent(gate.next()))
    }
}
