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


package net.newpipe.newplayer.ui.videoplayer.gesture_ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.util.lerp
import androidx.media3.common.util.UnstableApi
import net.newpipe.newplayer.ui.common.activity
import net.newpipe.newplayer.uiModel.EmbeddedUiConfig
import net.newpipe.newplayer.uiModel.UIModeState
import net.newpipe.newplayer.uiModel.NewPlayerUIState
import net.newpipe.newplayer.uiModel.InternalNewPlayerViewModel
import net.newpipe.newplayer.uiModel.NewPlayerViewModelDummy
import net.newpipe.newplayer.ui.theme.VideoPlayerDarkTheme
import net.newpipe.newplayer.ui.common.getDefaultBrightness
import net.newpipe.newplayer.ui.common.getEmbeddedUiConfig

/** How small the indicator is when it sits "in the background". */
private const val INDICATOR_BACKGROUND_SCALE = 0.9f

private enum class IndicatorMode {
    NONE, VOLUME_INDICATOR_VISIBLE, BRIGHTNESS_INDICATOR_VISIBLE
}

@OptIn(UnstableApi::class)
@Composable

/** @hide */
internal fun FullscreenGestureUI(
    modifier: Modifier = Modifier, viewModel: InternalNewPlayerViewModel, uiState: NewPlayerUIState,
    onVolumeIndicatorVisibilityChanged: (Boolean) -> Unit
) {

    var heightPx by remember {
        mutableFloatStateOf(0f)
    }

    var indicatorMode by remember {
        mutableStateOf(IndicatorMode.NONE)
    }

    val defaultOnRegularTap = {
        if (uiState.uiMode.videoControllerUiVisible) {
            viewModel.changeUiMode(uiState.uiMode.getUiHiddenState(), null)
        } else {
            viewModel.changeUiMode(uiState.uiMode.getControllerUiVisibleState(), null)
        }
    }

    var sumOfCenterMovement by remember {
        mutableFloatStateOf(0f)
    }

    val defaultBrightness = activity(0.5f, { getDefaultBrightness() })

    val embeddedUiConfig = getEmbeddedUiConfig()


    Box(modifier = modifier.onGloballyPositioned { coordinates ->
        heightPx = coordinates.size.height.toFloat()
    }) {
        Row {
            GestureSurface(modifier = Modifier.weight(1f),
                onRegularTap = defaultOnRegularTap,
                onMultiTap = {
                    viewModel.fastSeek(-it)
                },
                onMultiTapFinished = viewModel::finishFastSeek,
                onUp = {
                    indicatorMode = IndicatorMode.NONE
                    onVolumeIndicatorVisibilityChanged(false)
                },
                onMovement = { change ->
                    if (indicatorMode == IndicatorMode.NONE)
                        onVolumeIndicatorVisibilityChanged(true)

                    if (indicatorMode == IndicatorMode.NONE
                        || indicatorMode == IndicatorMode.BRIGHTNESS_INDICATOR_VISIBLE
                    ) {
                        indicatorMode = IndicatorMode.BRIGHTNESS_INDICATOR_VISIBLE

                        if (heightPx != 0f) {
                            viewModel.brightnessChange(-change.y / heightPx, defaultBrightness)
                        }
                    }
                }) {
                FadedAnimationForSeekFeedback(
                    uiState.fastSeekSeconds, backwards = true
                ) { fastSeekSecondsToDisplay ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        FastSeekVisualFeedback(
                            seconds = -fastSeekSecondsToDisplay,
                            backwards = true,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
            GestureSurface(modifier = Modifier.weight(1f),
                onRegularTap = defaultOnRegularTap,
                onUp = {sumOfCenterMovement = 0f},
                onMovement = { movement ->
                    sumOfCenterMovement += movement.y
                    if (100 < sumOfCenterMovement) {
                        viewModel.changeUiMode(UIModeState.EMBEDDED_VIDEO, embeddedUiConfig)
                    }
                },
                onMultiTap = { count ->
                    if (count == 1) {
                        if (uiState.playing) {
                            viewModel.pause()
                            viewModel.changeUiMode(
                                uiState.uiMode.getControllerUiVisibleState(),
                                null
                            )
                        } else {
                            viewModel.play()
                        }
                    }
                })
            GestureSurface(modifier = Modifier.weight(1f),
                onRegularTap = defaultOnRegularTap,
                onMultiTap = viewModel::fastSeek,
                onMultiTapFinished = viewModel::finishFastSeek,
                onUp = {
                    indicatorMode = IndicatorMode.NONE
                    onVolumeIndicatorVisibilityChanged(false)
                },
                onMovement = { change ->
                    if (indicatorMode == IndicatorMode.NONE)
                        onVolumeIndicatorVisibilityChanged(true)

                    if (indicatorMode == IndicatorMode.NONE
                        || indicatorMode == IndicatorMode.VOLUME_INDICATOR_VISIBLE
                    ) {
                        indicatorMode = IndicatorMode.VOLUME_INDICATOR_VISIBLE

                        if (heightPx != 0f) {
                            viewModel.volumeChange(-change.y / heightPx)
                        }
                    }
                }) {
                FadedAnimationForSeekFeedback(uiState.fastSeekSeconds) { fastSeekSecondsToDisplay ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        FastSeekVisualFeedback(
                            modifier = Modifier.align(Alignment.CenterStart),
                            seconds = fastSeekSecondsToDisplay,
                            backwards = false
                        )
                    }
                }
            }
        }

        IndicatorAnimation(
            modifier = Modifier.align(Alignment.Center),
            visible = indicatorMode == IndicatorMode.VOLUME_INDICATOR_VISIBLE,
        ) {
            VolumeCircle(volumeFraction = uiState.soundVolume)
        }

        IndicatorAnimation(
            modifier = Modifier.align(Alignment.Center),
            visible = indicatorMode == IndicatorMode.BRIGHTNESS_INDICATOR_VISIBLE,
        ) {
            VolumeCircle(
                volumeFraction = uiState.brightness ?: defaultBrightness,
                modifier = Modifier.align(Alignment.Center),
                isBrightness = true
            )
        }
    }
}

@Composable

/** @hide */
internal fun IndicatorAnimation(
    modifier: Modifier,
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    if (visible || 0.001f < progress.value) {
        Box(modifier = modifier.graphicsLayer {
            alpha = progress.value
            val scale = lerp(INDICATOR_BACKGROUND_SCALE, 1f, progress.value)
            scaleX = scale
            scaleY = scale
        }) {
            content()
        }
    }
}


@OptIn(UnstableApi::class)
@Preview(device = "spec:width=1080px,height=600px,dpi=440,orientation=landscape")
@Composable
private fun FullscreenGestureUIPreview() {
    VideoPlayerDarkTheme {
        Surface(modifier = Modifier.wrapContentSize(), color = Color.DarkGray) {
            FullscreenGestureUI(
                modifier = Modifier, object : NewPlayerViewModelDummy() {
                    override fun fastSeek(steps: Int) {
                        println("fast seek by $steps steps")
                    }
                },
                uiState = NewPlayerUIState.DEFAULT,
                onVolumeIndicatorVisibilityChanged = {}
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Preview(device = "spec:parent=pixel_8,orientation=landscape")
@Composable
private fun FullscreenGestureUIPreviewInteractive() {

    var seekSeconds by remember {
        mutableIntStateOf(0)
    }

    var brightnessValue by remember {
        mutableFloatStateOf(0f)
    }

    var soundVolume by remember {
        mutableFloatStateOf(0f)
    }

    var uiVisible by remember {
        mutableStateOf(false)
    }

    VideoPlayerDarkTheme {
        Surface(modifier = Modifier.wrapContentSize(), color = Color.Gray) {
            FullscreenGestureUI(
                modifier = Modifier,
                @OptIn(UnstableApi::class)
                object : NewPlayerViewModelDummy() {
                    override fun changeUiMode(
                        newUiModeState: UIModeState,
                        embeddedUiConfig: EmbeddedUiConfig?
                    ) {
                        super.changeUiMode(newUiModeState, embeddedUiConfig)
                        uiVisible = newUiModeState.videoControllerUiVisible
                    }

                    override fun fastSeek(steps: Int) {
                        seekSeconds = steps * 10
                    }

                    override fun finishFastSeek() {
                        seekSeconds = 0
                    }

                    override fun brightnessChange(changeRate: Float, currentValue: Float) {
                        brightnessValue = (brightnessValue + changeRate).coerceIn(0f, 1f)
                    }

                    override fun volumeChange(changeRate: Float) {
                        soundVolume = (soundVolume + changeRate).coerceIn(0f, 1f)
                    }
                },
                uiState = NewPlayerUIState.DEFAULT.copy(
                    uiMode = if (uiVisible) UIModeState.FULLSCREEN_VIDEO_CONTROLLER_UI
                    else UIModeState.FULLSCREEN_VIDEO,
                    fastSeekSeconds = seekSeconds,
                    soundVolume = soundVolume,
                    brightness = brightnessValue
                ),
                onVolumeIndicatorVisibilityChanged = {}
            )
        }

        AnimatedVisibility(uiVisible) {
            Text("UI is Visible")
        }
    }
}
