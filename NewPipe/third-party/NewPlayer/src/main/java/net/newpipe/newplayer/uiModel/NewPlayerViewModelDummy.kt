package net.newpipe.newplayer.uiModel

import android.app.Activity
import android.os.Bundle
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.newpipe.newplayer.NewPlayer
import net.newpipe.newplayer.ui.ContentScale

/**
 * A dummy/placeholder implementation of the [NewPlayerViewModel]
 */
@UnstableApi
open class NewPlayerViewModelDummy(val onPause: () -> Unit = {}) : InternalNewPlayerViewModel {
    override var newPlayer: NewPlayer? = null
    override val uiState = MutableStateFlow(NewPlayerUIState.DEFAULT)
    override var minContentRatio = 4F / 3F
    override var maxContentRatio = 16F / 9F
    override var contentFitMode = ContentScale.FIT_INSIDE
    override val uiEvent = MutableSharedFlow<NewPlayerUIEvent>().asSharedFlow()
    override var deviceInPowerSaveMode: Boolean = false
    override var showPlaylistInAudioPlayer: Boolean = false

    override fun initUIState(instanceState: Bundle) {
        println("dummy impl")
    }

    override fun onPlaybackError(exception: Exception): Boolean {
        println("dummy impl")
        return false
    }

    override fun play() {
        println("dummy impl")
    }

    override fun onBackPressed() {
        println("dummy impl")
    }

    override fun seekPositionChanged(newValue: Float) {
        println("dymmy seekPositionChanged: newValue: ${newValue}")
    }

    override fun seekingFinished() {
        println("dummy impl")
    }

    override fun embeddedDraggedDown(offset: Float) {
        println("dymmy embeddedDraggedDown: offset: ${offset}")
    }

    override fun fastSeek(steps: Int) {
        println("dummy impl: steps: $steps")
    }

    override fun finishFastSeek() {
        println("dummy impl")
    }

    override fun brightnessChange(changeRate: Float, systemBrightness: Float) {
        println("dummy impl")
    }

    override fun volumeChange(changeRate: Float) {
        println("dummy impl")
    }

    override fun chapterSelected(chapterId: Int) {
        println("dummp impl chapter selected: $chapterId")
    }

    override fun streamSelected(streamId: Int) {
        println("dummy impl stream selected: $streamId")
    }

    override fun cycleRepeatMode() {
        println("dummy impl")
    }

    override fun cycleContentFitMode() {
        println("dummy impl")
    }

    override fun toggleShuffle() {
        println("dummy impl")
    }

    override fun onStorePlaylist() {
        println("dummy impl")
    }

    override fun movePlaylistItem(from: Int, to: Int) {
        println("dummy impl")
    }

    override fun removePlaylistItem(uniqueId: Long) {
        println("dummy impl delete uniqueId: $uniqueId")
    }

    override fun onStreamItemDragFinished() {
        println("dummy impl")
    }

    override fun dialogVisible(visible: Boolean) {
        println("dummy impl dialog visible: $visible")
    }

    override fun doneEnteringPip() {
        println("dummy impl")
    }

    override fun resetHideDelayTimer() {
        println("dummy reset hide delay timer")
    }

    override fun onShowPlaylistInAudioPlayerToggle() {
        println("dummy impl")
    }

    override fun onSpeedPitchChanged(speed: Float, pitch: Float) {
        println("dummy impl: speed: $speed, pitch: $pitch")
    }

    override fun onPitchDetachedToggle(detached: Boolean) {
        println("dummy impl: pitch detached: $detached")
    }

    override fun onSkipSilenceToggle(enabled: Boolean) {
        println("dummy impl: skip silence: $enabled")
    }

    override fun onPictureInPictureModeChanged(isPictureInPictureMode: Boolean) {
        println("dummy impl isInPictureInPictureMode: $isPictureInPictureMode")
    }

    override fun pause() {
        println("dummy pause")
        onPause()
    }

    override fun prevStream() {
        println("dummy impl")
    }

    override fun nextStream() {
        println("dummy impl")
    }

    override fun changeUiMode(newUiModeState: UIModeState, embeddedUiConfig: EmbeddedUiConfig?) {
        println("dummy uiMode change: New UI Mode State: $newUiModeState")
    }

    override fun captureInitialEmbeddedUiConfig(embeddedUiConfig: EmbeddedUiConfig) {
        println("dummy impl")
    }

    override fun openInBrowser() {
        println("dummy open in browser")
    }

    override fun shareTimestamp() {
        println("dummy timestamp")
    }

    override fun selectVideoTrack(format: Format?) {
        println("dummy select video track: $format")
    }

    override fun selectAudioLanguage(language: String) {
        println("dummy select audio language: $language")
    }

    override fun selectSubtitleLanguage(language: String?) {
        println("dummy select subtitle language: $language")
    }
}
