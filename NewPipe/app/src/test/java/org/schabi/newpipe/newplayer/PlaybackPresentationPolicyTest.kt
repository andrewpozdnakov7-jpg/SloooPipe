// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import net.newpipe.newplayer.data.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPresentationPolicyTest {
    @Test
    fun returningFromAudioQueueToVideoEnablesVideoPresentation() {
        for (mode in listOf(PlayMode.EMBEDDED_VIDEO, PlayMode.FULLSCREEN_VIDEO)) {
            assertEquals(PlaybackPresentation.VIDEO, PlaybackPresentationPolicy.fromPlayerMode(PlaybackPresentation.AUDIO, mode, false))
        }
    }

    @Test
    fun switchingFromVideoToAudioDisablesVideoPresentation() {
        for (mode in listOf(PlayMode.EMBEDDED_AUDIO, PlayMode.FULLSCREEN_AUDIO)) {
            assertEquals(PlaybackPresentation.AUDIO, PlaybackPresentationPolicy.fromPlayerMode(PlaybackPresentation.VIDEO, mode, false))
        }
    }

    @Test
    fun queueAndControllerModesDoNotChangeAlreadyMatchingPresentation() {
        assertEquals(PlaybackPresentation.AUDIO, PlaybackPresentationPolicy.fromPlayerMode(PlaybackPresentation.AUDIO, PlayMode.FULLSCREEN_AUDIO, false))
        assertEquals(PlaybackPresentation.VIDEO, PlaybackPresentationPolicy.fromPlayerMode(PlaybackPresentation.VIDEO, PlayMode.EMBEDDED_VIDEO, false))
    }

    @Test
    fun popupAndPipRemainOwnedByTheirHostLifecycle() {
        for (current in listOf(PlaybackPresentation.POPUP, PlaybackPresentation.PIP)) {
            for (mode in PlayMode.entries) assertEquals(current, PlaybackPresentationPolicy.fromPlayerMode(current, mode, false))
        }
    }

    @Test
    fun placeholderAndObsoleteModesDoNotOverrideTheHost() {
        for (mode in listOf(PlayMode.IDLE, PlayMode.PIP, PlayMode.BACKGROUND_AUDIO, PlayMode.BACKGROUND_VIDEO)) {
            for (current in PlaybackPresentation.entries) assertEquals(current, PlaybackPresentationPolicy.fromPlayerMode(current, mode, false))
        }
    }

    @Test
    fun audioOnlyContentDoesNotTurnIntoVideoPresentation() {
        for (current in PlaybackPresentation.entries) {
            for (mode in PlayMode.entries) assertEquals(current, PlaybackPresentationPolicy.fromPlayerMode(current, mode, true))
        }
    }
}
