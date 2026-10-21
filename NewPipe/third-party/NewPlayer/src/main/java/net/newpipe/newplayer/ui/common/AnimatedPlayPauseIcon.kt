/* NewPlayer
 *
 * @author Christian Schabesberger
 *
 * Copyright (C) NewPipe e.V. 2024 <code(at)newpipe-ev.de>
 *
 * NewPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPlayer.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.newpipe.newplayer.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp

// Morph-compatible play/pause shapes taken from
// https://gist.github.com/ozgurg/0ff8e1e315b1cb5c385bda1c25da80e6
private const val PLAY_PATH =
    "M 8.016 6.797 Q 8.016 6.797 8.016 6.797 Q 8.016 6.234 8.532 5.953 Q 9.048 5.672 9.563 6 Q 13.617 8.578 17.672 11.156 Q 18.141 11.437 18.141 12 Q 18.141 12.563 17.672 12.844 Q 13.617 15.422 9.563 18 Q 9.047 18.328 8.532 18.047 Q 8.017 17.766 8.016 17.203 Q 8.016 15.469 8.016 13.734 Q 8.016 12 8.016 10.266 L 8.016 6.797 M 16.009 11.999 Q 16.009 11.999 16.009 11.999 Q 16.009 11.999 16.009 11.999 Q 16.009 11.999 16.009 11.999 Q 16.009 11.999 16.009 11.999 L 16.009 11.999 Q 16.009 11.999 16.009 11.999 Q 16.009 11.999 16.009 11.999 Q 16.009 11.999 16.009 11.999 Q 16.009 11.999 16.009 11.999 L 16.009 11.999 L 16.009 11.999"
private const val PAUSE_PATH =
    "M 6 6.984 Q 6 6.187 6.586 5.601 Q 7.172 5.015 8.016 5.015 Q 8.813 5.015 9.399 5.601 Q 9.985 6.187 9.985 6.984 Q 9.985 9.492 9.985 11.999 Q 9.985 14.507 9.985 17.015 Q 9.985 17.812 9.399 18.398 Q 8.813 18.984 8.016 18.984 Q 8.016 18.984 8.016 18.984 Q 7.172 18.984 6.586 18.398 Q 6 17.812 6 17.015 L 6 6.984 M 14.016 6.984 Q 14.016 6.187 14.602 5.601 Q 15.188 5.015 15.985 5.015 Q 16.829 5.015 17.415 5.601 Q 18.001 6.187 18.001 6.984 L 18.001 17.015 Q 18.001 17.812 17.415 18.398 Q 16.829 18.984 15.985 18.984 Q 15.188 18.984 14.602 18.398 Q 14.016 17.812 14.016 17.015 L 14.016 6.984 L 14.016 6.984"

private const val VIEWPORT_SIZE = 24f

private class PathCommand(val type: Char, val coords: FloatArray)

private fun parsePath(pathData: String): List<PathCommand> {
    val commands = mutableListOf<PathCommand>()
    var type = ' '
    val coords = mutableListOf<Float>()
    for (token in pathData.split(' ').filter { it.isNotEmpty() }) {
        if (token[0].isLetter()) {
            if (type != ' ') commands.add(PathCommand(type, coords.toFloatArray()))
            type = token[0]
            coords.clear()
        } else {
            coords.add(token.toFloat())
        }
    }
    commands.add(PathCommand(type, coords.toFloatArray()))
    return commands
}

private val playCommands = parsePath(PLAY_PATH)
private val pauseCommands = parsePath(PAUSE_PATH)

private fun Path.morph(fraction: Float) {
    reset()
    for (i in playCommands.indices) {
        val from = playCommands[i].coords
        val to = pauseCommands[i].coords
        fun c(n: Int) = lerp(from[n], to[n], fraction)
        when (playCommands[i].type) {
            'M' -> moveTo(c(0), c(1))
            'L' -> lineTo(c(0), c(1))
            'Q' -> quadraticTo(c(0), c(1), c(2), c(3))
        }
    }
}

/** @hide */
@Composable
internal fun AnimatedPlayPauseIcon(
    playing: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val fraction by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "playPauseMorph"
    )
    val path = remember { Path() }

    Canvas(modifier = modifier.semantics {
        if (contentDescription != null) {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    }) {
        path.morph(fraction)
        scale(size.width / VIEWPORT_SIZE, size.height / VIEWPORT_SIZE, pivot = Offset.Zero) {
            drawPath(path, tint)
        }
    }
}
