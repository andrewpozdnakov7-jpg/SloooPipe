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

package net.newpipe.newplayer.uiModel

import android.os.Bundle
import androidx.media3.common.Format
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)

/** @hide */
internal interface InternalNewPlayerViewModel : NewPlayerViewModel {
    var minContentRatio: Float
    var maxContentRatio: Float
    var deviceInPowerSaveMode: Boolean

    fun initUIState(instanceState: Bundle)

    /**
     * Handed every playback error by NewPlayer before NewPlayer deals with it itself. Some faults
     * can only be fixed from the UI side, which is why the view model gets the first shot at them.
     *
     * @return true if the view model took over the error. NewPlayer then neither stops playback
     * nor reports the error to the app.
     */
    fun onPlaybackError(exception: Exception): Boolean

    fun play()
    fun pause()
    fun prevStream()
    fun nextStream()
    fun changeUiMode(newUiModeState: UIModeState, embeddedUiConfig: EmbeddedUiConfig?)
    fun captureInitialEmbeddedUiConfig(embeddedUiConfig: EmbeddedUiConfig)
    fun onBackPressed()
    fun seekPositionChanged(newValue: Float)
    fun seekingFinished()
    fun brightnessChange(changeRate: Float, systemBrightness: Float)
    fun chapterSelected(chapterId: Int)
    fun cycleContentFitMode()
    fun cycleRepeatMode()
    fun dialogVisible(visible: Boolean)
    fun doneEnteringPip()
    fun embeddedDraggedDown(offset: Float)
    fun fastSeek(count: Int)
    fun finishFastSeek()
    fun movePlaylistItem(from: Int, to: Int)
    fun onShowPlaylistInAudioPlayerToggle()
    fun onSpeedPitchChanged(speed: Float, pitch:Float)
    fun onPitchDetachedToggle(detached: Boolean)
    fun onSkipSilenceToggle(enabled: Boolean)
    fun onStorePlaylist()
    fun onStreamItemDragFinished()
    fun removePlaylistItem(uniqueId: Long)
    fun resetHideDelayTimer()
    fun streamSelected(streamId: Int)
    fun toggleShuffle()
    fun volumeChange(changeRate: Float)
    fun openInBrowser()
    fun shareTimestamp()
    fun selectVideoTrack(format: Format?)
    fun selectAudioLanguage(language: String)
    fun selectSubtitleLanguage(language: String?)
}