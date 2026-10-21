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

package net.newpipe.newplayer.ui.common.speed_and_pitch


import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import net.newpipe.newplayer.R
import net.newpipe.newplayer.ui.theme.VideoPlayerDarkTheme
import net.newpipe.newplayer.uiModel.InternalNewPlayerViewModel
import net.newpipe.newplayer.uiModel.NewPlayerUIState
import net.newpipe.newplayer.uiModel.NewPlayerViewModelDummy

val MIN_SPEEDUP_VALUE = 0.1f
val MAX_SPEEDUP_VALUE = 5.0f

val MIN_PITCH_VALUE = 0.1f
val MAX_PITCH_VALUE = 5.0f

val DEFAULT_STEP_SIZE = 0.25f


val fastTween = tween<Float>(durationMillis = 150)
val fastSizeTween = tween<IntSize>(durationMillis = 150)

/** @hide **/
@OptIn(UnstableApi::class)
@Composable
internal fun PlaybackSpeedDialog(
    uiState: NewPlayerUIState, viewModel: InternalNewPlayerViewModel, onDismiss: () -> Unit
) {
    DisposableEffect(Unit) {
        viewModel.dialogVisible(true)
        onDispose { viewModel.dialogVisible(false) }
    }

    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )
    ) {
        DialogContent(uiState, viewModel, onDismiss)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun DialogContent(
    uiState: NewPlayerUIState, viewModel: InternalNewPlayerViewModel, onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier
                .widthIn(0.dp, 500.dp)
                .fillMaxWidth(0.95f)
                .pointerInput(Unit) { detectTapGestures { } },
            elevation = CardDefaults.elevatedCardElevation(5.dp),
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SelectorsColumn(uiState, viewModel)
                CheckboxesAndButtonsColumn(uiState, viewModel, onDismiss)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SelectorsColumn(
    uiState: NewPlayerUIState,
    viewModel: InternalNewPlayerViewModel,
) {
    Box(modifier = Modifier.height(10.dp))

    SpeedSelector(
        titleText = R.string.playback_speed,
        minValue = MIN_SPEEDUP_VALUE,
        maxValue = MAX_SPEEDUP_VALUE,
        currentValue = uiState.playbackParameters.speed,
        onValueChange = {
            viewModel.onSpeedPitchChanged(
                speed = it, pitch = uiState.playbackParameters.pitch
            )
        })

    AnimatedVisibility(
        uiState.pitchDetached,
        enter = fadeIn(fastTween) + expandVertically(fastSizeTween),
        exit = fadeOut(fastTween) + shrinkVertically(fastSizeTween)
    ) {
        SpeedSelector(
            titleText = R.string.playback_pitch,
            minValue = MIN_PITCH_VALUE,
            maxValue = MAX_PITCH_VALUE,
            currentValue = uiState.playbackParameters.pitch,
            onValueChange = {
                viewModel.onSpeedPitchChanged(
                    speed = uiState.playbackParameters.speed, pitch = it
                )
            })
    }

}

@OptIn(UnstableApi::class)
@Composable
private fun CheckboxesAndButtonsColumn(
    uiState: NewPlayerUIState, viewModel: InternalNewPlayerViewModel, onDismiss: () -> Unit
) {

    Row(
        modifier = Modifier.padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.detach_pitch_with_description)
        )
        Switch(
            uiState.pitchDetached, onCheckedChange = viewModel::onPitchDetachedToggle
        )
    }

    Row(
        modifier = Modifier.padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f), text = stringResource(R.string.fast_forward_on_silence)
        )
        Switch(
            uiState.skipSilence, onCheckedChange = viewModel::onSkipSilenceToggle
        )
    }

    Row {
        Box(modifier = Modifier.width(17.dp))
        TextButton(onClick = {
            viewModel.onSpeedPitchChanged(speed = 1f, pitch = 1f)
            viewModel.onSkipSilenceToggle(false)
        }) {
            Text(stringResource(R.string.reset))
        }

        Box(modifier = Modifier.weight(1f))

        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.ok))
        }
        Box(modifier = Modifier.width(17.dp))
    }
}


@OptIn(UnstableApi::class)
@Preview(device = "spec:width=1080px,height=1580px,dpi=440,orientation=portrait")
@Preview(device = "spec:width=2580px,height=1080px,dpi=440,orientation=landscape")
@Composable
private fun PlaybackSpeedDialogPreview() {

    VideoPlayerDarkTheme {
        DialogContent(
            viewModel = NewPlayerViewModelDummy(), uiState = NewPlayerUIState.DUMMY, onDismiss = {})
    }
}