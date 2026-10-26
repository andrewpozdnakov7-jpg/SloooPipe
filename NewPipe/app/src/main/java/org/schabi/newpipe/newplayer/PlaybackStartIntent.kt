// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import java.net.URI
import java.net.URLDecoder
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

/** Preserve an explicit zero before a stream's canonical URL discards timestamp parameters. */
object PlaybackStartIntent {
    private val timestampNames = setOf("t", "start", "time_continue")
    private val zeroTime = Regex("0+|0+(?::0+){1,2}|(?:0+h)?(?:0+m)?(?:0+s)?")

    /** Extraction may replace originalUrl; capture the caller's zero before that happens. */
    @JvmStatic
    fun createQueue(info: StreamInfo, requestedUrl: String?): SinglePlayQueue {
        if (requestedUrl == null || info.startPosition > 0) return SinglePlayQueue(info)
        val position = if (hasExplicitZeroTimestamp(requestedUrl)) 0L else PlayQueueItem.RECOVERY_UNSET
        return SinglePlayQueue(info, position)
    }

    @JvmStatic
    fun hasExplicitZeroTimestamp(url: String?): Boolean {
        if (url == null) return false
        return try {
            val uri = URI(url)
            sequenceOf(uri.rawQuery, uri.rawFragment).filterNotNull()
                .flatMap { it.split('&').asSequence() }
                .any { parameter ->
                    val name = URLDecoder.decode(parameter.substringBefore('='), "UTF-8")
                    val value = URLDecoder.decode(parameter.substringAfter('=', ""), "UTF-8")
                    name in timestampNames && value.isNotEmpty() && zeroTime.matches(value)
                }
        } catch (_: Exception) {
            false
        }
    }
}
