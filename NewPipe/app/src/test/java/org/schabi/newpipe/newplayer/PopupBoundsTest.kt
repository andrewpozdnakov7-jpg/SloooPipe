// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupBoundsTest {
    @Test
    fun landscapeControlsAndVideoFitInsideTheUsableHeight() {
        val bounds = PopupBounds.fit(960, 672, 166, 96)
        assertEquals(506, bounds.videoHeight)
        assertEquals(0, bounds.top)
        assertTrue(bounds.top + 166 + bounds.videoHeight <= 672)
    }

    @Test
    fun portraitRetainsAspectRatioAndPreferredOffset() {
        assertEquals(PopupBounds(303, 96), PopupBounds.fit(540, 1232, 204, 96))
    }

    @Test
    fun tallerTranslatedControlsReduceVideoRatherThanOverflow() {
        val bounds = PopupBounds.fit(960, 672, 280, 96)
        assertEquals(392, bounds.videoHeight)
        assertTrue(bounds.top + 280 + bounds.videoHeight <= 672)
    }

    @Test
    fun noVideoRoomOrNegativePreferredTopCannotProduceNegativeDimensions() {
        assertEquals(PopupBounds(0, 0), PopupBounds.fit(540, 180, 204, 96))
        assertEquals(0, PopupBounds.fit(540, 1232, 204, -30).top)
    }
}
