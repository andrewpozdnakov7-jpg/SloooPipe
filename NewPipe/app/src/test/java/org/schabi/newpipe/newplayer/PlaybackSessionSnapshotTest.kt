// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

class PlaybackSessionSnapshotTest {
    @Test fun savedSnapshotRetainsSourceAndDuplicateOrderForRestore() {
        val source = SinglePlayQueue(
            listOf(
                StreamInfoItem(0, "https://example.org/a", "A", StreamType.VIDEO_STREAM),
                StreamInfoItem(0, "https://example.org/b", "B", StreamType.VIDEO_STREAM)
            ),
            0
        )
        val state = SavedPlayback(
            listOf(SavedFeed("feed", source)),
            listOf(SavedSlot("feed", 1), SavedSlot("feed", 0), SavedSlot("feed", 1)),
            2,
            45000L,
            2,
            true
        )
        val bytes = ByteArrayOutputStream().use { out ->
            ObjectOutputStream(out).use { it.writeObject(state) }
            out.toByteArray()
        }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as SavedPlayback }
        assertEquals(state.order, restored.order)
        assertEquals(2, restored.index)
        assertEquals(45000L, restored.position)
        assertEquals(listOf("https://example.org/a", "https://example.org/b"), restored.feeds.single().source.streams.map { it.url })
        assertEquals(true, restored.shuffle)
        assertEquals(2, restored.repeat)
    }
}
