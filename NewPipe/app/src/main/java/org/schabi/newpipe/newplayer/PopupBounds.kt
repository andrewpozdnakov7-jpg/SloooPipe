// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

/** Size the video after measuring the translated buttons and drag handle. Units are pixels. */
internal data class PopupBounds(val videoHeight: Int, val top: Int) {
    companion object {
        fun fit(width: Int, availableHeight: Int, chromeHeight: Int, preferredTop: Int): PopupBounds {
            val room = (availableHeight.toLong() - chromeHeight.coerceAtLeast(0)).coerceAtLeast(0)
            val video = (width.coerceAtLeast(0).toLong() * 9 / 16).coerceAtMost(room).toInt()
            val maxTop = (room - video).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return PopupBounds(video, preferredTop.coerceIn(0, maxTop))
        }
    }
}
