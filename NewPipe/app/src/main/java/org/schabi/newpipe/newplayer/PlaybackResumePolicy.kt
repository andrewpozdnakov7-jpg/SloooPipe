// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import org.schabi.newpipe.database.stream.model.StreamStateEntity

/** Explicit positions (including zero) take precedence over timestamps and unfinished history. */
internal object PlaybackResumePolicy {
    fun position(durationSeconds: Long, explicitMillis: Long, timestampSeconds: Long, history: StreamStateEntity?): Long {
        val requested = when {
            explicitMillis >= 0 -> explicitMillis
            timestampSeconds > 0 -> milliseconds(timestampSeconds)
            history != null && !history.isFinished(durationSeconds) -> history.progressMillis
            else -> 0
        }
        return if (durationSeconds > 0) requested.coerceIn(0, milliseconds(durationSeconds) - 1) else requested.coerceAtLeast(0)
    }

    private fun milliseconds(seconds: Long): Long = if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
}
