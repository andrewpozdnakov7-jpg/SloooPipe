// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

/** Presentation never owns or recreates a playback engine or its queue. */
enum class PlaybackPresentation { VIDEO, AUDIO, POPUP, PIP }
