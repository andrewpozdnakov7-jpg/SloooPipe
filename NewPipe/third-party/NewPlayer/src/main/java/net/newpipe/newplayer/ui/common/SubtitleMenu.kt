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

import androidx.annotation.OptIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import net.newpipe.newplayer.R
import net.newpipe.newplayer.uiModel.InternalNewPlayerViewModel
import net.newpipe.newplayer.uiModel.NewPlayerUIState
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable

/** @hide */
internal fun SubtitleMenu(
    uiState: NewPlayerUIState,
    viewModel: InternalNewPlayerViewModel,
    isVisible: Boolean,
    makeInvisible: () -> Unit
) {
    val availableLanguages = remember(uiState.currentlyAvailableTracks) {
        getAvailableSubtitleLanguages(uiState.currentlyAvailableTracks)
    }
    val playingLanguage = getPlayingSubtitleLanguage(uiState.currentlyPlayingTracks)

    DropdownMenu(expanded = isVisible, onDismissRequest = {
        makeInvisible()
        viewModel.dialogVisible(false)
    }) {
        SubtitleMenuItem(stringResource(R.string.menu_item_subtitles_off), playingLanguage == null) {
            viewModel.selectSubtitleLanguage(null)
            makeInvisible()
        }
        for (language in availableLanguages) {
            val name = Locale.forLanguageTag(language).displayName
                .ifBlank { stringResource(R.string.menu_item_subtitles_on) }
            SubtitleMenuItem(name, language == playingLanguage) {
                viewModel.selectSubtitleLanguage(language)
                makeInvisible()
            }
        }
    }
}

@Composable
private fun SubtitleMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = {
            if (selected) {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
            }
        },
        onClick = onClick
    )
}

@OptIn(UnstableApi::class)
@Composable

/** @hide */
internal fun SubtitleMenuItem(uiState: NewPlayerUIState, onClick: () -> Unit) {
    val availableLanguages = remember(uiState.currentlyAvailableTracks) {
        getAvailableSubtitleLanguages(uiState.currentlyAvailableTracks)
    }

    if (availableLanguages.isNotEmpty()) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_item_sub_titles)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = stringResource(R.string.menu_item_sub_titles)
                )
            }, onClick = onClick
        )
    }
}

@OptIn(UnstableApi::class)
private fun getAvailableSubtitleLanguages(tracks: List<Format>) =
    tracks.filter { MimeTypes.isText(it.sampleMimeType) }.mapNotNull { it.language }.distinct()

@OptIn(UnstableApi::class)
private fun getPlayingSubtitleLanguage(tracks: List<Format>) =
    tracks.firstOrNull { MimeTypes.isText(it.sampleMimeType) }?.language
