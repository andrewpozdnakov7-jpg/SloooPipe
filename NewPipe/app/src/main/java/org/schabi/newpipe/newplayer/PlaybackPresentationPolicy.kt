// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import net.newpipe.newplayer.data.PlayMode

/** NewPlayer's own audio/video controls must also update the host's track selection. */
internal object PlaybackPresentationPolicy {
    fun fromPlayerMode(current: PlaybackPresentation, mode: PlayMode, audioOnly: Boolean): PlaybackPresentation {
        // Overlay/PiP lifecycle and audio-only content have their own presentation rules.
        if (current == PlaybackPresentation.POPUP || current == PlaybackPresentation.PIP || audioOnly) return current
        return when (mode) {
            PlayMode.EMBEDDED_VIDEO, PlayMode.FULLSCREEN_VIDEO -> PlaybackPresentation.VIDEO
            PlayMode.EMBEDDED_AUDIO, PlayMode.FULLSCREEN_AUDIO -> PlaybackPresentation.AUDIO
            else -> current
        }
    }
}
