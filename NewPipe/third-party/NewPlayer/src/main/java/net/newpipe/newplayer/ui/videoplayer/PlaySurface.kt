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

package net.newpipe.newplayer.ui.videoplayer

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale as ComposeContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import net.newpipe.newplayer.ui.ContentScale
import net.newpipe.newplayer.uiModel.NewPlayerUIState

/**
 * The size handed to [resizeWithContentScale]. Only its aspect ratio matters, the absolute value
 * is picked large enough to keep the scale factor free of rounding artifacts.
 */
private const val SOURCE_SIZE_BASE = 1000f

/** @hide */
@OptIn(UnstableApi::class)
@Composable
internal fun PlaySurface(
    modifier: Modifier,
    player: Player?,
    uiState: NewPlayerUIState
) {
    val contentRatio = uiState.contentRatio

    val surfaceModifier = if (!uiState.uiMode.fullscreen) {
        modifier
            .fillMaxHeight()
            .aspectRatio(contentRatio)
    } else {
        when (uiState.contentFitMode) {
            ContentScale.STRETCHED -> modifier.fillMaxSize()

            ContentScale.FIT_INSIDE ->
                modifier
                    .aspectRatio(contentRatio)
                    .fillMaxSize()

            ContentScale.CROP -> modifier.resizeWithContentScale(
                contentScale = ComposeContentScale.Crop,
                sourceSizeDp = Size(contentRatio * SOURCE_SIZE_BASE, SOURCE_SIZE_BASE)
            )
        }
    }

    // Recreate the native surface for renderer handoffs and fullscreen/embedded bounds changes.
    // Queue selection uses fullscreen bounds: reusing its SurfaceView on return can leave the
    // holder below the smaller embedded ComposeView, clipping the video while audio continues.
    key(uiState.videoSurfaceGeneration, uiState.uiMode.fullscreen) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = surfaceModifier
        )
    }
}
