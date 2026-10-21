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

package net.newpipe.newplayer.ui.videoplayer.controller

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import net.newpipe.newplayer.R
import net.newpipe.newplayer.ui.ContentScale
import net.newpipe.newplayer.ui.common.LanguageMenu
import net.newpipe.newplayer.ui.common.LanguageMenuItem
import net.newpipe.newplayer.ui.common.SubtitleMenu
import net.newpipe.newplayer.ui.common.SubtitleMenuItem
import net.newpipe.newplayer.uiModel.NewPlayerUIState
import net.newpipe.newplayer.uiModel.InternalNewPlayerViewModel
import net.newpipe.newplayer.uiModel.NewPlayerViewModelDummy
import net.newpipe.newplayer.uiModel.UIModeState
import net.newpipe.newplayer.ui.theme.VideoPlayerDarkTheme
import net.newpipe.newplayer.ui.videoplayer.pip.supportsPip
import net.newpipe.newplayer.ui.common.getEmbeddedUiConfig

/** @hide */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayerMenu(
    modifier: Modifier = Modifier,
    viewModel: InternalNewPlayerViewModel,
    uiState: NewPlayerUIState,
    defaultShowMenuState: MenuShowState = MenuShowState.Nothing
) {
    var menuShowState: MenuShowState by remember {
        mutableStateOf(defaultShowMenuState)
    }

    val pixel_density = LocalDensity.current

    var offsetY by remember {
        mutableStateOf(0.dp)
    }

    val embeddedUiConfig = getEmbeddedUiConfig()


    Box {
        IconButton(onClick = {
            menuShowState = MenuShowState.MainMenu
            viewModel.dialogVisible(true)
        }, modifier = modifier.onPlaced {
            offsetY = with(pixel_density) {
                it.size.height.toDp()
            }

        }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.menu_item_more_settings)
            )
        }
        DropdownMenu(modifier = Modifier.align(Alignment.TopStart),
            offset = DpOffset(x = 0.dp, y = -offsetY),
            expanded = menuShowState == MenuShowState.MainMenu,
            onDismissRequest = {
                if (menuShowState == MenuShowState.MainMenu) {
                    menuShowState = MenuShowState.Nothing
                    viewModel.dialogVisible(false)
                }
            }) {

            if (uiState.linkAvailable) {
                CustomMenuItem(Icons.Rounded.Language, R.string.menu_item_open_in_browser) {
                    viewModel.openInBrowser()
                    menuShowState = MenuShowState.Nothing
                }
            }
            if (uiState.timestampLinkAvailable) {
                CustomMenuItem(Icons.Rounded.Share, R.string.menu_item_share_timestamp) {
                    viewModel.shareTimestamp()
                    menuShowState = MenuShowState.Nothing
                }
            }
            CustomMenuItem(Icons.Rounded.Headset, R.string.audio_mode) {
                viewModel.changeUiMode(UIModeState.FULLSCREEN_AUDIO, embeddedUiConfig)
                menuShowState = MenuShowState.Nothing
            }
            if (supportsPip(LocalContext.current)) {
                CustomMenuItem(Icons.Rounded.PictureInPicture, R.string.pip_button_description) {
                    viewModel.changeUiMode(UIModeState.PIP, embeddedUiConfig)
                    menuShowState = MenuShowState.Nothing
                }
            }
            if (uiState.uiMode.fullscreen) {
                when (uiState.contentFitMode) {
                    ContentScale.STRETCHED -> CustomMenuItem(
                        Icons.Rounded.ZoomOutMap,
                        R.string.menu_item_stretch_video
                    ) {
                        viewModel.cycleContentFitMode()
                    }

                    ContentScale.FIT_INSIDE -> CustomMenuItem(
                        Icons.Rounded.FitScreen,
                        R.string.menu_item_fit_screen
                    ) {
                        viewModel.cycleContentFitMode()
                    }

                    ContentScale.CROP -> CustomMenuItem(
                        Icons.Rounded.Crop,
                        R.string.menu_item_crop_video
                    ) {
                        viewModel.cycleContentFitMode()
                    }
                }

            }
            SubtitleMenuItem(uiState = uiState, onClick = {
                menuShowState = MenuShowState.SubtitleMenu
            })

            LanguageMenuItem(uiState = uiState, onClick = {
                menuShowState = MenuShowState.LanguageMenu
            })
        }

        LanguageMenu(
            uiState = uiState,
            viewModel = viewModel,
            isVisible = menuShowState == MenuShowState.LanguageMenu,
            makeInvisible = {
                menuShowState = MenuShowState.Nothing
            })

        SubtitleMenu(
            uiState = uiState,
            viewModel = viewModel,
            isVisible = menuShowState == MenuShowState.SubtitleMenu,
            makeInvisible = {
                menuShowState = MenuShowState.Nothing
            })
    }
}

internal enum class MenuShowState {
    Nothing,
    MainMenu,
    LanguageMenu,
    SubtitleMenu,
}


@Composable
private fun CustomMenuItem(icon: ImageVector, text: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(text)
            )
        },
        onClick = onClick
    )
}

///////////////////////////////////////////////////////////////////
// Preview
///////////////////////////////////////////////////////////////////

@OptIn(UnstableApi::class)
@Preview(device = "spec:width=1080px,height=1080px,dpi=440,orientation=landscape")
@Composable
private fun VideoPlayerControllerDropDownPreview() {
    VideoPlayerDarkTheme {
        Box(Modifier.fillMaxSize()) {
            VideoPlayerMenu(
                viewModel = NewPlayerViewModelDummy(),
                uiState = NewPlayerUIState.DUMMY.copy(
                    uiMode = UIModeState.FULLSCREEN_VIDEO_CONTROLLER_UI
                ),
                defaultShowMenuState = MenuShowState.MainMenu
            )
        }
    }
}
