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
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import net.newpipe.newplayer.R
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import net.newpipe.newplayer.uiModel.InternalNewPlayerViewModel
import net.newpipe.newplayer.uiModel.NewPlayerUIState
import net.newpipe.newplayer.uiModel.UIModeState
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable

/** @hide */
internal fun LanguageMenu(uiState: NewPlayerUIState, viewModel: InternalNewPlayerViewModel, isVisible: Boolean, makeInvisible: () -> Unit) {
    val availableLanguages = remember(uiState.currentlyAvailableTracks) {
        getAvailableLanguages(uiState.currentlyAvailableTracks)
    }
    val playingLanguage = getPlayingLanguage(uiState.currentlyPlayingTracks)

    DropdownMenu(expanded = isVisible, onDismissRequest = {
        makeInvisible()
        viewModel.dialogVisible(false)
    }) {
        for (language in availableLanguages) {
            DropdownMenuItem(
                text = {
                    Text(Locale(language).displayLanguage)
                },
                trailingIcon = {
                    if (language == playingLanguage) {
                        Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                    }
                },
                onClick = {
                    viewModel.selectAudioLanguage(language)
                    makeInvisible()
                })
        }
    }
}


@OptIn(UnstableApi::class)
@Composable

/** @hide */
internal fun LanguageMenuItem(uiState: NewPlayerUIState, onClick: () -> Unit) {
    val availableLanguages = remember(uiState.currentlyAvailableTracks) {
        getAvailableLanguages(uiState.currentlyAvailableTracks)
    }

    if (2 <= availableLanguages.size) {
        val language = getPlayingLanguage(uiState.currentlyPlayingTracks) ?: availableLanguages[0]
        val locale = Locale(language)
        DropdownMenuItem(text = { Text(locale.displayLanguage) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Translate,
                    contentDescription = String.format(
                        stringResource(R.string.menu_selected_language_item),
                        locale.displayLanguage
                    )
                )
            }, onClick = onClick)
    }
}

@OptIn(UnstableApi::class)
private fun getAvailableLanguages(tracks: List<Format>) =
    tracks.filter { MimeTypes.isAudio(it.sampleMimeType) }.mapNotNull { it.language }.distinct()

@OptIn(UnstableApi::class)
private fun getPlayingLanguage(tracks: List<Format>) =
    tracks.firstOrNull { MimeTypes.isAudio(it.sampleMimeType) }?.language
