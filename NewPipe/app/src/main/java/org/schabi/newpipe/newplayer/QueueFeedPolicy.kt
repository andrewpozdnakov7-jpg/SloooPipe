// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

/** Feed order is independent of playback order; no surviving anchor means no page insertion. */
internal object QueueFeedPolicy {
    fun insertionIndex(orderedFeeds: List<String?>, feed: String): Int? = orderedFeeds.indexOfLast { it == feed }.takeIf { it >= 0 }?.plus(1)

    fun shouldFetch(sourceIndex: Int, loaded: Int, complete: Boolean, lastRequestedSize: Int): Boolean = !complete && loaded > 0 && sourceIndex >= 0 && sourceIndex >= loaded - 3 && loaded != lastRequestedSize
}
