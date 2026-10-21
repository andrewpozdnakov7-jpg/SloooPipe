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

package net.newpipe.newplayer.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.Typography
import androidx.lifecycle.compose.collectAsStateWithLifecycle


import androidx.media3.common.util.UnstableApi
import net.newpipe.newplayer.data.NewPlayerException
import net.newpipe.newplayer.uiModel.UIModeState
import net.newpipe.newplayer.uiModel.InternalNewPlayerViewModel
import net.newpipe.newplayer.uiModel.NewPlayerUIEvent
import net.newpipe.newplayer.uiModel.NewPlayerViewModel
import net.newpipe.newplayer.uiModel.NewPlayerViewModelDummy
import net.newpipe.newplayer.ui.audioplayer.AudioPlayerUI
import net.newpipe.newplayer.ui.theme.VideoPlayerDarkTheme
import net.newpipe.newplayer.ui.videoplayer.VideoPlayerUi
import net.newpipe.newplayer.ui.common.LockScreenOrientation
import net.newpipe.newplayer.ui.common.activity
import net.newpipe.newplayer.ui.common.getDefaultBrightness
import net.newpipe.newplayer.ui.common.getEmbeddedUiConfig
import net.newpipe.newplayer.ui.common.isInPowerSaveMode
import net.newpipe.newplayer.ui.common.setScreenBrightness
import net.newpipe.newplayer.ui.theme.NewPlayerTypography
import net.newpipe.newplayer.ui.theme.NewPlayerDarkColorScheme
import net.newpipe.newplayer.ui.theme.NewPlayerLightColorScheme

private const val TAG = "NewPlayerUI"


/**
 * The NewPlayerUI composable. Use this in your compose setup to display the NewPlayerUI.
 *
 * Keep in mind that NewPlayer will deeply integrate into your UI and your Activity.
 * You must take care about complying to requests of NewPlayer like when NewPlayer wants to
 * display the NewPlayerUI in fullscreen mode. It's your duty to ensure that all other composable
 * or views are hidden and only NewPlayerUI is visible. You can read more about this in
 * the [NewPlayerViewModel], since the [viewModel] is responsible to tell your UI how to behave
 * in such cases.
 *
 * @param viewModel the NewPlayerViewModel that should control this NewPlayerUi.
 * @param lightColorScheme is the color scheme used by non video ui related composables, when the system is in light mode.
 * @param darkColorScheme is the color scheme used by the video ui related composables, and the rest of the ui if the system is in dark mode.
 * @param the typography used by NewPlayer
 */
@OptIn(UnstableApi::class)
@Composable
fun NewPlayerUI(
    viewModel: NewPlayerViewModel?,
    lightColorScheme: ColorScheme = NewPlayerLightColorScheme,
    darkColorScheme: ColorScheme = NewPlayerDarkColorScheme,
    typography: Typography = NewPlayerTypography
) {
    MaterialTheme(colorScheme = darkColorScheme, typography = typography) {
        if (viewModel !is InternalNewPlayerViewModel?) {
            throw NewPlayerException(
                "The view model given to NewPlayerUI must be of type InternalNewPlayerViewModel. "
                        + "This can not be implemented externally, so do not extend NewPlayerViewModel"
            )
        }

        if (viewModel == null) {
            LoadingPlaceholder()
        } else if (viewModel.newPlayer == null) {
            LoadingPlaceholder(viewModel.uiState.collectAsStateWithLifecycle().value.embeddedUiRatio)
        } else {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // find out whether application is light or dark mode from LocalContext.current
            val view = LocalView.current
            val activity = activity()
            val window = activity.window

            // Remember how the Activity was configured before NewPlayer touched it, so it can be
            // restored when leaving fullscreen. This has to be read during composition (and not
            // from within an effect), because effects run after LockScreenOrientation had the
            // chance to alter the Activity's requested orientation.
            // The view model only keeps the first config it is handed, so it does not matter that
            // this runs again whenever NewPlayerUI re-enters composition.
            val currentEmbeddedUiConfig = activity.getEmbeddedUiConfig()
            LaunchedEffect(Unit) {
                viewModel.captureInitialEmbeddedUiConfig(currentEmbeddedUiConfig)
            }

            LaunchedEffect(viewModel) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is NewPlayerUIEvent.OpenInBrowser -> activity.tryStartActivity(
                            Intent(Intent.ACTION_VIEW, event.link)
                        )

                        is NewPlayerUIEvent.ShareThisLink -> activity.tryStartActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, event.link.toString())
                            }, null)
                        )

                        // the remaining events are meant for the hosting app
                        else -> Unit
                    }
                }
            }

            // Setup fullscreen

            val isInDarkTheme = isSystemInDarkTheme()
            LaunchedEffect(uiState.uiMode.forceSysUiDarkThemeMode, key2 = isInDarkTheme) {
                if (uiState.uiMode.forceSysUiDarkThemeMode) {
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                        false
                } else {
                    uiState.embeddedUiConfig?.let {
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isInDarkTheme
                    }
                }
            }

            if (uiState.uiMode.fullscreen) {
                BackHandler {
                    viewModel.onBackPressed()
                }
            }

            // setup immersive mode
            LaunchedEffect(
                key1 = uiState.uiMode.systemInsetsVisible,
            ) {
                val windowInsetsController =
                    WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                if (uiState.uiMode.systemInsetsVisible) {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                } else {
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val isInPowerSaveMode = isInPowerSaveMode()
                LaunchedEffect(key1 = isInPowerSaveMode) {
                    viewModel.deviceInPowerSaveMode = isInPowerSaveMode
                }
            }

            if (uiState.uiMode.fitScreenRotation) {
                if (uiState.contentRatio < 1) {
                    LockScreenOrientation(orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
                } else {
                    LockScreenOrientation(orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                }
            } else {
                // Restore the orientation the Activity had before NewPlayer locked it to fit the
                // video. Fall back to the config read above in case the view model did not publish
                // its captured config (yet).
                LockScreenOrientation(
                    orientation = (uiState.embeddedUiConfig ?: currentEmbeddedUiConfig)
                        .screenOrientation
                )
            }

            val defaultBrightness = remember(activity) { activity.getDefaultBrightness() }
            LaunchedEffect(key1 = uiState.brightness) {
                setScreenBrightness(
                    if (uiState.brightness < 0) defaultBrightness else uiState.brightness, activity
                )
            }

            when (uiState.uiMode) {

                UIModeState.FULLSCREEN_VIDEO,
                UIModeState.FULLSCREEN_VIDEO_CONTROLLER_UI,
                UIModeState.FULLSCREEN_VIDEO_CHAPTER_SELECT,
                UIModeState.FULLSCREEN_VIDEO_STREAM_SELECT,
                UIModeState.EMBEDDED_VIDEO,
                UIModeState.EMBEDDED_VIDEO_CONTROLLER_UI,
                UIModeState.EMBEDDED_VIDEO_STREAM_SELECT,
                UIModeState.EMBEDDED_VIDEO_CHAPTER_SELECT,
                UIModeState.PIP -> {
                    VideoPlayerUi(viewModel = viewModel, uiState = uiState)
                }

                UIModeState.FULLSCREEN_AUDIO,
                UIModeState.EMBEDDED_AUDIO,
                UIModeState.AUDIO_STREAM_SELECT,
                UIModeState.AUDIO_CHAPTER_SELECT -> {
                    val windowSize = currentWindowSize()
                    MaterialTheme(
                        colorScheme =
                            if (isInDarkTheme) darkColorScheme else lightColorScheme,
                        typography = typography
                    ) {
                        AudioPlayerUI(
                            viewModel = viewModel, uiState = uiState,
                            isLandScape = windowSize.height < windowSize.width
                        )
                    }
                }

                else -> {
                    LoadingPlaceholder(uiState.embeddedUiRatio)
                }
            }
        }
    }
}


private fun Activity.tryStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No activity found to handle intent: $intent", e)
    }
}

@OptIn(UnstableApi::class)
@Preview(device = "spec:width=1080px,height=700px,dpi=440,orientation=landscape")
@Composable
private fun PlayerUIPreviewEmbedded() {
    VideoPlayerDarkTheme {
        NewPlayerUI(viewModel = NewPlayerViewModelDummy())
    }
}
