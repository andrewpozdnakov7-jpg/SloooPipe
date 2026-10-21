/* NewPlayer
 *
 * @author Christian Schabesberger
 *
 * Copyright (C) NewPipe e.V. 2026 <code(at)newpipe-ev.de>
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

package net.newpipe.newplayer.ui.common.speed_and_pitch

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Quadratic mapping between a slider fraction and a speed/pitch value, ported from
 * NewPipe's `SliderStrategy.Quadratic`.
 *
 * The value grows with the square of the distance from the center, so the slope is zero
 * at [center]: resolution is finest around normal playback and coarsest at the extremes.
 * The two halves are scaled independently, which puts [center] on the exact midpoint of
 * the travel for any range.
 */

/** Maps a slider fraction in `0f..1f` to a value in [minValue]..[maxValue]. */
internal fun sliderFractionToValue(
    fraction: Float,
    minValue: Float,
    maxValue: Float,
    center: Float = 1f
): Float {
    require(center in minValue..maxValue) { "center must lie between minValue and maxValue" }

    val offset = 2f * fraction.coerceIn(0f, 1f) - 1f
    val gap = if (offset >= 0f) maxValue - center else center - minValue

    return center + sign(offset) * offset * offset * gap
}

/** Inverse of [sliderFractionToValue]: maps a value to its slider fraction in `0f..1f`. */
internal fun valueToSliderFraction(
    value: Float,
    minValue: Float,
    maxValue: Float,
    center: Float = 1f
): Float {
    require(center in minValue..maxValue) { "center must lie between minValue and maxValue" }

    val difference = value.coerceIn(minValue, maxValue) - center
    val gap = if (difference >= 0f) maxValue - center else center - minValue
    val offset = if (gap == 0f) 0f else sign(difference) * sqrt(abs(difference) / gap)

    return (offset + 1f) / 2f
}
