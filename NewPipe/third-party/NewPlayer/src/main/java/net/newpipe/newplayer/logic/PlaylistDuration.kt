// SPDX-License-Identifier: GPL-3.0-or-later
package net.newpipe.newplayer.logic

/** Null/negative duration and arithmetic overflow are unknown, never zero or an exception. */
object PlaylistDuration {
    fun sum(durations: Iterable<Long?>): Long? {
        var total = 0L
        for (value in durations) {
            if (value == null || value < 0 || value > Long.MAX_VALUE - total) return null
            total += value
        }
        return total
    }
}
