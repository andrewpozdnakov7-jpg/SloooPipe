// SPDX-License-Identifier: GPL-3.0-or-later
package net.newpipe.newplayer.logic

/** Main-thread ownership token for asynchronous replacement of a playback session. */
class PlaybackRequestGate {
    private var generation = 0L

    fun snapshot(): Long = generation

    fun next(): Long = ++generation

    fun isCurrent(request: Long): Boolean = request == generation

    fun invalidate() {
        generation++
    }
}
