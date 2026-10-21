// SPDX-License-Identifier: GPL-3.0-or-later
package net.newpipe.newplayer

/** Host application supplies the same instance to its screen and media session service. */
interface NewPlayerProvider {
    val newPlayer: NewPlayer
}
