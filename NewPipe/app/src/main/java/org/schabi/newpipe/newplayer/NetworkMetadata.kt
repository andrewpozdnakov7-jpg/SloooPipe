// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import org.schabi.newpipe.extractor.stream.StreamType

/** Preserve unknown/live duration; Media3 metadata uses milliseconds, extractor values use seconds. */
internal object NetworkMetadata {
    fun durationMs(type: StreamType, seconds: Long): Long? {
        if (type != StreamType.VIDEO_STREAM && type != StreamType.AUDIO_STREAM) return null
        return seconds.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1000 }?.times(1000)
    }
}
