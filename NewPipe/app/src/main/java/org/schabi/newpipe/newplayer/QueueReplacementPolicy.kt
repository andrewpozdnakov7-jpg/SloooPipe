// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

/** Ordered identities retain duplicate occurrences; a feed URL is not an active queue. */
internal data class StreamIdentity(val service: Int, val url: String)

internal data class PlaybackSnapshot(
    val session: String,
    val ids: List<String>,
    val selected: String?,
    val streams: List<StreamIdentity>
)

internal object QueueReplacementPolicy {
    fun requiresConfirmation(enabled: Boolean, active: PlaybackSnapshot?, requested: List<StreamIdentity>): Boolean = enabled && active != null && active.streams.isNotEmpty() && active.streams != requested

    /** A dialog may authorize its captured session once, never a changed queue or account. */
    class Consent(private val expected: PlaybackSnapshot) {
        private var consumed = false

        fun accept(current: PlaybackSnapshot?): Boolean {
            if (consumed) return false
            consumed = true
            return current == expected
        }
    }
}
