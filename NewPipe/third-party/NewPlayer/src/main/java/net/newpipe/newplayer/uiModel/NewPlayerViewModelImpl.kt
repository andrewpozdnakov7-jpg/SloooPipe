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

import android.app.Application
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.BundleCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.newpipe.newplayer.NewPlayer
import net.newpipe.newplayer.NewPlayerImpl
import net.newpipe.newplayer.data.PlayMode
import net.newpipe.newplayer.data.RepeatMode
import net.newpipe.newplayer.ui.ContentScale
import net.newpipe.newplayer.ui.common.getPlaylistDurationInMS
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

val VIDEOPLAYER_UI_STATE = "video_player_ui_state"

private const val TAG = "VideoPlayerViewModel"

private const val GESTURE_SCROLL_RATE_MULTIPLIER = 1.3f

// How long to wait for the UI to swap the video surface before retrying playback regardless.
private const val FRESH_SURFACE_TIMEOUT_MS = 500L

@UnstableApi
class NewPlayerViewModelImpl constructor(
    application: Application,
) : AndroidViewModel(application), InternalNewPlayerViewModel {

    // private
    private val mutableUiState = MutableStateFlow(NewPlayerUIState.DEFAULT)
    private var currentContentRatio = 1F

    private var playlistItemToBeMoved: Int? = null
    private var playlistItemNewPosition: Int = 0

    private var hideUiDelayedJob: Job? = null
    private var progressUpdaterJob: Job? = null
    private var playlistProgressUpdaterJob: Job? = null
    private var updatePreviewThumbnailJob: Job? = null
    private var probeLinksJob: Job? = null

    // this is necessary to restore the embedded view UI configuration when returning from fullscreen
    private var embeddedUiConfig: EmbeddedUiConfig? = null

    private var playbackPositionWhenFastSeekStarted = 0L

    private var dialogIsVisible = false

    // The width of the video surface currently attached to the player, or 0 if there is none.
    private val videoSurfaceWidth = MutableStateFlow(0)

    // The playlist position a fresh surface was already handed to, so a stream that simply can not
    // be decoded does not end up in a retry loop.
    private var decoderInitRetriedFor: Int? = null

    private val audioManager =
        getSystemService(application.applicationContext, AudioManager::class.java)!!

    init {
        mutableUiState.update {
            val soundVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                .toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()

            it.copy(soundVolume = soundVolume)
        }
    }

    //interface
    override var newPlayer: NewPlayer? = null
        set(value) {
            field.attachViewModel(null)
            field = value
            value.attachViewModel(this)
            installNewPlayer()
        }

    override val uiState = mutableUiState.asStateFlow()

    private var mutableUiEvent = MutableSharedFlow<NewPlayerUIEvent>()
    override val uiEvent = mutableUiEvent.asSharedFlow()


    override var minContentRatio: Float = 4F / 3F
        set(value) {
            if (value <= 0 || maxContentRatio < value) Log.e(
                TAG,
                "Ignoring maxContentRatio: It must not be 0 or less and it may not be bigger then mmaxContentRatio. It was Set to: $value"
            )
            else {
                field = value
                mutableUiState.update { it.copy(embeddedUiRatio = getEmbeddedUiRatio()) }
            }
        }


    override var maxContentRatio: Float = 3F / 1F
        set(value) {
            if (value <= 0 || value < minContentRatio) Log.e(
                TAG,
                "Ignoring maxContentRatio: It must not be 0 or less and it may not be smaller then minContentRatio. It was Set to: $value"
            )
            else {
                field = value
                mutableUiState.update { it.copy(embeddedUiRatio = getEmbeddedUiRatio()) }
            }
        }

    override var contentFitMode: ContentScale
        get() = mutableUiState.value.contentFitMode
        set(value) {
            mutableUiState.update {
                it.copy(contentFitMode = value)
            }
        }

    override var showPlaylistInAudioPlayer: Boolean
        get() = mutableUiState.value.showPlaylistInAudioPlayer
        set(value) {
            mutableUiState.update {
                it.copy(showPlaylistInAudioPlayer = value)
            }
        }

    override var deviceInPowerSaveMode: Boolean = false
        set(value) {
            field = value
            if (progressUpdaterJob?.isActive == true) {
                startProgressUpdatePeriodicallyJob()
            }
        }

    private fun installNewPlayer() {
        newPlayer?.let { newPlayer ->
            viewModelScope.launch {
                newPlayer.exoPlayer.collect { player ->

                    Log.d(TAG, "Install player: ${player?.videoSize?.width}")

                    player?.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            super.onIsPlayingChanged(isPlaying)
                            Log.d(TAG, "Playing state changed. Is Playing: $isPlaying")
                            mutableUiState.update {
                                it.copy(playing = isPlaying, isLoading = player.isBuffering)
                            }
                            if (isPlaying && uiState.value.uiMode.videoControllerUiVisible) {
                                resetHideDelayTimer()
                            } else {
                                hideUiDelayedJob?.cancel()
                            }
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            super.onVideoSizeChanged(videoSize)
                            updateContentRatio(videoSize)
                        }

                        override fun onSurfaceSizeChanged(width: Int, height: Int) {
                            super.onSurfaceSizeChanged(width, height)
                            videoSurfaceWidth.value = width
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            super.onPlaybackStateChanged(playbackState)
                            if (playbackState == Player.STATE_READY) {
                                decoderInitRetriedFor = null
                            }
                            updatePlaybackActive(player)
                            updateIsLoading(player)
                        }

                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            super.onPlayWhenReadyChanged(playWhenReady, reason)
                            updatePlaybackActive(player)
                        }

                        override fun onRepeatModeChanged(repeatMode: Int) {
                            super.onRepeatModeChanged(repeatMode)
                            mutableUiState.update {
                                it.copy(repeatMode = newPlayer.repeatMode)
                            }
                        }

                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            super.onShuffleModeEnabledChanged(shuffleModeEnabled)
                            mutableUiState.update {
                                it.copy(shuffleEnabled = newPlayer.shuffle)
                            }
                        }

                        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                            super.onPlaybackParametersChanged(playbackParameters)
                            mutableUiState.update {
                                it.copy(playbackParameters = player.playbackParameters)
                            }
                        }

                        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
                            super.onSkipSilenceEnabledChanged(skipSilenceEnabled)
                            mutableUiState.update {
                                it.copy(skipSilence = skipSilenceEnabled)
                            }
                        }
                    })

                    // Nothing replays the size of an already attached surface.
                    videoSurfaceWidth.value = player?.surfaceSize?.width ?: 0
                }
            }

            viewModelScope.launch {
                newPlayer.playBackMode.collect { newMode ->
                    val currentMode = mutableUiState.value.uiMode.toPlayMode()

                    if (currentMode != newMode) {
                        changeUiMode(UIModeState.fromPlayMode(newMode), embeddedUiConfig)
                    }
                }
            }

            viewModelScope.launch {
                newPlayer.playlist.collect { playlist ->
                    mutableUiState.update {
                        it.copy(
                            playList = playlist,
                            currentPlaylistItemIndex = newPlayer.currentlyPlayingPlaylistItem
                        )
                    }
                }
            }
            viewModelScope.launch {
                newPlayer.currentlyPlaying.collect { playlistItem ->
                    mutableUiState.update {
                        it.copy(
                            currentlyPlaying = playlistItem,
                            currentPlaylistItemIndex = newPlayer.currentlyPlayingPlaylistItem
                        )
                    }
                    probeLinks(playlistItem)
                }
            }

            viewModelScope.launch {
                newPlayer.currentChapters.collect { chapters ->
                    mutableUiState.update {
                        it.copy(chapters = chapters)
                    }
                }
            }

            viewModelScope.launch {
                newPlayer.currentlyAvailableTracks.collect { availableTracks ->
                    mutableUiState.update {
                        it.copy(
                            currentlyAvailableTracks = availableTracks
                        )
                    }
                }
            }

            viewModelScope.launch {
                newPlayer.currentlyPlayingTracks.collect { playingTracks ->
                    mutableUiState.update {
                        it.copy(
                            currentlyPlayingTracks = playingTracks
                        )
                    }
                }
            }

            viewModelScope.launch {
                newPlayer.pinnedVideoTrack.collect { pinned ->
                    mutableUiState.update {
                        it.copy(pinnedVideoTrack = pinned)
                    }
                }
            }

            mutableUiState.update {
                it.copy(
                    playing = newPlayer.exoPlayer.value?.isPlaying ?: false,
                    playbackActive = newPlayer.exoPlayer.value.isPlaybackActive,
                    isLoading = newPlayer.exoPlayer.value.isBuffering
                )
            }
        }
    }

    private fun probeLinks(mediaItem: MediaItem?) {
        probeLinksJob?.cancel()
        mutableUiState.update {
            it.copy(linkAvailable = false, timestampLinkAvailable = false)
        }

        val item = mediaItem?.let { newPlayer?.getItemFromMediaItem(it) } ?: return
        val repository = newPlayer?.repository ?: return

        probeLinksJob = viewModelScope.launch {
            val linkAvailable = repository.getLink(item) != null
            val timestampLinkAvailable = repository.getTimestampLink(item, 0) != null
            mutableUiState.update {
                it.copy(
                    linkAvailable = linkAvailable,
                    timestampLinkAvailable = timestampLinkAvailable
                )
            }
        }
    }

    private fun updateIsLoading(player: Player?) {
        val loading = player.isBuffering
        mutableUiState.update {
            if (it.isLoading == loading) it else it.copy(isLoading = loading)
        }
    }

    private fun updatePlaybackActive(player: Player?) {
        val active = player.isPlaybackActive
        mutableUiState.update {
            if (it.playbackActive == active) it else it.copy(playbackActive = active)
        }
    }


    // Not Player.isLoading: that stays true while ExoPlayer keeps caching ahead during a pause.
    private val Player?.isBuffering get() = this?.playbackState == Player.STATE_BUFFERING

    private val Player?.isPlaybackActive
        get() = this != null && playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED

    private fun updateContentRatio(videoSize: VideoSize) {
        val newRatio = videoSize.ratio
        val ratio = if (newRatio.isNaN()) currentContentRatio else newRatio
        currentContentRatio = ratio
        Log.d(TAG, "Update Content ratio: $ratio")
        mutableUiState.update {
            it.copy(
                contentRatio = currentContentRatio, embeddedUiRatio = getEmbeddedUiRatio()
            )
        }
    }

    // Errors are handed over to the view model by NewPlayerImpl directly, since the hop is internal
    // to the library and has no business being part of the NewPlayer interface.
    private fun NewPlayer?.attachViewModel(viewModel: InternalNewPlayerViewModel?) {
        (this as? NewPlayerImpl)?.attachedViewModel = viewModel
    }

    override fun onPlaybackError(exception: Exception): Boolean {
        if (exception !is PlaybackException || exception.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
            return false
        }

        val player = newPlayer?.exoPlayer?.value ?: return false

        // Without a surface there is nobody who could recreate one, so this is not our error.
        if (player.surfaceSize.width <= 0) {
            return false
        }

        if (player.currentMediaItemIndex == decoderInitRetriedFor) {
            return false
        }
        decoderInitRetriedFor = player.currentMediaItemIndex

        Log.d(TAG, "Decoder init failed. Retrying with a fresh video surface.")

        // A software renderer holds on to the surface it renders into as a CPU producer, which is
        // what makes MediaCodec fail to connect once a stream needs a hardware decoder. Only
        // destroying the surface hands that connection back.
        mutableUiState.update {
            it.copy(videoSurfaceGeneration = it.videoSurfaceGeneration + 1)
        }

        viewModelScope.launch {
            // Preparing again while the old surface is still alive would run into the same decoder
            // init failure, so wait until the UI took it down and put up a new one.
            val freshSurfaceWidth = withTimeoutOrNull(FRESH_SURFACE_TIMEOUT_MS.milliseconds) {
                videoSurfaceWidth.first { it <= 0 }
                videoSurfaceWidth.first { it > 0 }
            }
            if (freshSurfaceWidth == null) {
                Log.d(TAG, "Timed out waiting for a fresh video surface. Retrying anyway.")
            }
            newPlayer?.prepare()
            newPlayer?.play()
        }

        return true
    }

    override fun onCleared() {
        super.onCleared()

        newPlayer.attachViewModel(null)

        Log.d(TAG, "viewmodel cleared")
    }

    @OptIn(UnstableApi::class)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun initUIState(instanceState: Bundle) {
        BundleCompat.getParcelable(
            instanceState, VIDEOPLAYER_UI_STATE, NewPlayerUIState::class.java,
        )?.let { recoveredUiState ->
            mutableUiState.value = recoveredUiState
        }
    }

    override fun play() {
        changeUiMode(uiState.value.uiMode.getUiHiddenState(), null)
        newPlayer?.play()
    }

    override fun pause() {
        hideUiDelayedJob?.cancel()
        newPlayer?.pause()

    }

    override fun prevStream() {
        resetHideDelayTimer()
        newPlayer?.let { newPlayer ->
            if (0 <= newPlayer.currentlyPlayingPlaylistItem - 1) {
                newPlayer.currentlyPlayingPlaylistItem -= 1
            }
        }
    }

    override fun nextStream() {
        resetHideDelayTimer()
        newPlayer?.let { newPlayer ->
            if (newPlayer.currentlyPlayingPlaylistItem + 1 < (newPlayer.exoPlayer.value?.mediaItemCount
                    ?: 0)
            ) {
                newPlayer.currentlyPlayingPlaylistItem += 1
            }
        }
    }

    private fun requiresPlaylistProgressUpdate(uiModeState: UIModeState) =
        uiModeState.isVideoStreamSelect || (uiModeState.inAudioMode && uiState.value.showPlaylistInAudioPlayer)

    override fun captureInitialEmbeddedUiConfig(embeddedUiConfig: EmbeddedUiConfig) {
        if (this.embeddedUiConfig != null) {
            return
        }
        this.embeddedUiConfig = embeddedUiConfig
        // Publish it right away, so the UI is able to restore the Activity configuration even if
        // NewPlayer was launched directly into a fullscreen mode and therefore never performed a
        // embedded -> fullscreen transition.
        mutableUiState.update {
            it.copy(embeddedUiConfig = embeddedUiConfig)
        }
    }

    override fun changeUiMode(newUiModeState: UIModeState, embeddedUiConfig: EmbeddedUiConfig?) {
        if (newUiModeState == uiState.value.uiMode) {
            return
        }

        if (!uiState.value.uiMode.fullscreen && newUiModeState.fullscreen && embeddedUiConfig != null) {
            this.embeddedUiConfig = embeddedUiConfig
        }

        if ((newUiModeState == UIModeState.EMBEDDED_VIDEO_CONTROLLER_UI || newUiModeState == UIModeState.FULLSCREEN_VIDEO_CONTROLLER_UI) && (newPlayer?.exoPlayer?.value?.isPlaying == true)) {
            resetHideDelayTimer()
        } else {
            hideUiDelayedJob?.cancel()
        }

        if (requiresPlaylistProgressUpdate(newUiModeState)) {
            startPlaylistProgressUpdaterJob()
        } else {
            playlistProgressUpdaterJob?.cancel()
        }

        if (newUiModeState.requiresProgressUpdate) {
            startProgressUpdatePeriodicallyJob()
        } else {
            progressUpdaterJob?.cancel()
        }

        // notify the UI itself about the change of the UIMode
        mutableUiState.update {
            if (newUiModeState == UIModeState.PIP) {
                if (it.uiMode.inAudioMode) {
                    it.copy(uiMode = it.uiMode.getVideoEquivalent(), enteringPip = true)
                } else if (it.uiMode.videoControllerUiVisible) {
                    it.copy(uiMode = it.uiMode.getUiHiddenState(), enteringPip = true)
                } else {
                    it.copy(enteringPip = true)
                }
            } else {
                if (it.uiMode.fullscreen && !newUiModeState.fullscreen) {
                    it.copy(uiMode = newUiModeState, embeddedUiConfig = this.embeddedUiConfig)
                } else {
                    it.copy(uiMode = newUiModeState)
                }
            }
        }


        // update play mode in NewPlayer if that value was not updated through the newPlayer object
        val newPlayMode = newUiModeState.toPlayMode()
        // take the next value from the player because changeUiMode is called when the playBackMode
        // of the player changes. If this value was taken from the viewModel instead
        // this would lead to an endless loop. of changeMode state calling itself over and over again
        // through the callback of the newPlayer?.playBackMode change
        val currentPlayMode = newPlayer?.playBackMode?.value ?: PlayMode.IDLE
        if (newPlayMode != currentPlayMode) {
            newPlayer?.playBackMode?.update {
                newPlayMode
            }
        }
    }

    override fun resetHideDelayTimer() {
        hideUiDelayedJob?.cancel()
        if (!dialogIsVisible) {
            hideUiDelayedJob = viewModelScope.launch {
                delay(2000)
                if (!dialogIsVisible) changeUiMode(uiState.value.uiMode.getUiHiddenState(), null)
            }
        }
    }

    override fun onShowPlaylistInAudioPlayerToggle() {
        showPlaylistInAudioPlayer = !showPlaylistInAudioPlayer
        if (requiresPlaylistProgressUpdate(uiState.value.uiMode)) {
            startPlaylistProgressUpdaterJob()
        } else {
            playlistProgressUpdaterJob?.cancel()
        }
    }

    private fun startProgressUpdatePeriodicallyJob() {
        // TODO this function should check whether the UI state every time deviceInPowerSaveMode or
        //  mutableUiState.value.uiMode.requiresProgressUpdate changes, and either start or stop the
        //  job. The condition checks should not be left to the callers.

        progressUpdaterJob?.cancel()
        progressUpdaterJob = viewModelScope.launch {
            while (true) {
                updateProgressOnce()
                delay(if (deviceInPowerSaveMode) 1000 else 1000 / 30/*fps*/)
            }
        }
    }

    private fun updateProgressOnce() {
        mutableUiState.update {
            val progress = newPlayer?.currentPosition ?: 0
            val duration = newPlayer?.duration ?: C.TIME_UNSET
            val bufferedPercentage = (newPlayer?.bufferedPercentage?.toFloat() ?: 0f) / 100f
            val progressPercentage =
                if (0L < duration) progress.toFloat() / duration.toFloat() else 0f

            it.copy(
                seekerPosition = progressPercentage,
                durationInMs = duration,
                playbackPositionInMs = progress,
                bufferedPercentage = bufferedPercentage,
            )
        }
    }

    private fun startPlaylistProgressUpdaterJob() {
        playlistProgressUpdaterJob?.cancel()
        playlistProgressUpdaterJob = viewModelScope.launch {
            while (true) {
                updateProgressInPlaylistOnce()
                delay(1000)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateProgressInPlaylistOnce() {
        mutableUiState.update {
            val preceding = it.playList.takeWhile { item -> item.mediaId != it.currentlyPlaying?.mediaId }
            val elapsed = getPlaylistDurationInMS(preceding)
            val position = (newPlayer?.currentPosition ?: 0L).coerceAtLeast(0)
            val progress = if (elapsed != null && position <= Long.MAX_VALUE - elapsed) {
                elapsed + position
            } else {
                C.TIME_UNSET
            }

            it.copy(
                playbackPositionInPlaylistMs = progress
            )
        }
    }

    private fun getSeekerPositionInMs(uiState: NewPlayerUIState) =
        ((newPlayer?.duration?.toFloat() ?: 0F) * uiState.seekerPosition).toLong()

    override fun seekPositionChanged(newValue: Float) {
        if (uiState.value.uiMode.videoControllerUiVisible) {
            changeUiMode(uiState.value.uiMode.getControllerUiVisibleState(), null)
        }
        hideUiDelayedJob?.cancel()
        progressUpdaterJob?.cancel()

        val seekPositionInMs = getSeekerPositionInMs(uiState.value)
        newPlayer?.currentPosition = seekPositionInMs
        Log.i(TAG, "Seek to Ms: $seekPositionInMs")

        updateSeekPreview(seekPositionInMs)
        mutableUiState.update {
            it.copy(
                seekerPosition = newValue,
                playbackPositionInMs = getSeekerPositionInMs(it),
                seekPreviewVisible = true
            )
        }
    }

    private fun updateSeekPreview(seekPositionInMs: Long) {
        updatePreviewThumbnailJob?.cancel()

        updatePreviewThumbnailJob = viewModelScope.launch {

            val item = newPlayer?.currentlyPlaying?.value?.let {
                newPlayer?.getItemFromMediaItem(it)
            }

            val chapters = mutableUiState.value.chapters
            val chapter = chapters.lastOrNull {
                it.chapterStartInMs < seekPositionInMs
            }

            item?.let {
                val bitmap = newPlayer?.repository?.getPreviewThumbnail(item, seekPositionInMs)

                mutableUiState.update {
                    it.copy(
                        currentSeekPreviewText = chapter?.chapterTitle,
                        currentSeekPreviewThumbnail = bitmap?.asImageBitmap(),
                        seekPreviewVisible = true
                    )
                }
            }
        }
    }

    override fun seekingFinished() {
        newPlayer?.currentPosition = getSeekerPositionInMs(mutableUiState.value)

        mutableUiState.update {
            it.copy(seekPreviewVisible = false)
        }

        resetHideDelayTimer()
        startProgressUpdatePeriodicallyJob()
    }

    override fun embeddedDraggedDown(offset: Float) {
        safeTryEmit(mutableUiEvent, NewPlayerUIEvent.DragDownBy(offset))
    }

    override fun fastSeek(count: Int) {
        if (abs(count) == 1) {
            playbackPositionWhenFastSeekStarted = newPlayer?.currentPosition ?: 0
        }

        val fastSeekAmountInS = count * (newPlayer?.fastSeekAmountSec ?: 10)
        mutableUiState.update {
            it.copy(
                fastSeekSeconds = fastSeekAmountInS
            )
        }

        if (fastSeekAmountInS != 0) {
            Log.d(TAG, "fast seeking seeking by $fastSeekAmountInS seconds")

            newPlayer?.currentPosition =
                playbackPositionWhenFastSeekStarted + (fastSeekAmountInS * 1000)

        }

        if (mutableUiState.value.uiMode.videoControllerUiVisible) {
            resetHideDelayTimer()
        }
    }

    override fun finishFastSeek() {
        if (mutableUiState.value.uiMode.videoControllerUiVisible) {
            resetHideDelayTimer()
        }
        mutableUiState.update {
            it.copy(fastSeekSeconds = 0)
        }
    }

    override fun brightnessChange(changeRate: Float, systemBrightness: Float) {
        mutableUiState.update {
            if (it.uiMode.fullscreen) {
                val currentBrightness = when (val brightness = it.brightness) {
                    -1f -> systemBrightness
                    else -> brightness
                }
                Log.d(
                    TAG,
                    "currentBrightnes: $currentBrightness, sytemBrightness: $systemBrightness, changeRate: $changeRate"
                )

                val newBrightness =
                    (currentBrightness + changeRate * GESTURE_SCROLL_RATE_MULTIPLIER).coerceIn(
                        0f, 1f
                    )
                it.copy(brightness = newBrightness)
            } else {
                it
            }
        }
    }

    override fun volumeChange(changeRate: Float) {
        val currentVolume = mutableUiState.value.soundVolume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        // we multiply changeRate by 1.5 so your finger only has to swipe a portion of the whole
        // screen in order to fully enable or disable the volume
        val newVolume = (currentVolume + changeRate * 1.3f).coerceIn(0f, 1f)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC, (newVolume * maxVolume).toInt(), 0
        )
        mutableUiState.update {
            it.copy(soundVolume = newVolume)
        }
    }

    override fun openInBrowser() {
        val item = newPlayer?.currentlyPlaying?.value?.let {
            newPlayer?.getItemFromMediaItem(it)
        }

        if (item == null) return

        viewModelScope.launch {
            val link = newPlayer?.repository?.getLink(item)
            if (link == null) {
                Log.w(TAG, "No link to open in browser for item: $item")
                return@launch
            }
            safeTryEmit(mutableUiEvent, NewPlayerUIEvent.OpenInBrowser(Uri.parse(link.toString())))
        }
    }

    override fun shareTimestamp() {
        val item = newPlayer?.currentlyPlaying?.value?.let {
            newPlayer?.getItemFromMediaItem(it)
        }

        if (item == null) return

        viewModelScope.launch {
            val timestampInSeconds = (newPlayer?.currentPosition ?: 0) / 1000
            val link = newPlayer?.repository?.getTimestampLink(item, timestampInSeconds)
            if (link == null) {
                Log.w(TAG, "No timestamp link to share for item: $item")
                return@launch
            }
            safeTryEmit(mutableUiEvent, NewPlayerUIEvent.ShareThisLink(Uri.parse(link.toString())))
        }
    }

    override fun onBackPressed() {
        val nextMode = uiState.value.uiMode.getNextModeWhenBackPressed()
        if (nextMode != null) {
            changeUiMode(nextMode, null)
        } else {
            safeTryEmit(mutableUiEvent, NewPlayerUIEvent.OnBackPressed())
        }
    }

    override fun chapterSelected(chapterId: Int) {
        newPlayer?.selectChapter(chapterId)
    }

    override fun streamSelected(streamId: Int) {
        newPlayer?.let {
            if (it.currentlyPlayingPlaylistItem != streamId) {
                it.currentlyPlayingPlaylistItem = streamId
            }
        }
    }

    override fun cycleRepeatMode() {
        newPlayer?.let {
            it.repeatMode = when (it.repeatMode) {
                RepeatMode.DO_NOT_REPEAT -> RepeatMode.REPEAT_ALL
                RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
                RepeatMode.REPEAT_ONE -> RepeatMode.DO_NOT_REPEAT
            }
        }
    }

    override fun cycleContentFitMode() {
        mutableUiState.update {
            it.copy(
                contentFitMode = when (it.contentFitMode) {
                    ContentScale.STRETCHED -> ContentScale.FIT_INSIDE
                    ContentScale.FIT_INSIDE -> ContentScale.CROP
                    ContentScale.CROP -> ContentScale.STRETCHED
                }
            )
        }
    }

    override fun toggleShuffle() {
        newPlayer?.let {
            it.shuffle = !it.shuffle
        }
    }

    override fun onStorePlaylist() {

    }

    override fun movePlaylistItem(from: Int, to: Int) {
        if (playlistItemToBeMoved == null) {
            playlistItemToBeMoved = from
        }
        playlistItemNewPosition = to

        // TODO: add a comment explaining that if there are performance problems, this can be
        //  reduced to O(|to-from|) using a mutable list.
        mutableUiState.update {
            val tempList = ArrayList(it.playList)
            val item = it.playList[from]
            tempList.removeAt(from)
            tempList.add(to, item)

            it.copy(
                playList = tempList
            )
        }
        startPlaylistProgressUpdaterJob()
    }

    override fun onStreamItemDragFinished() {
        playlistItemToBeMoved?.let {
            newPlayer?.movePlaylistItem(it, playlistItemNewPosition)
        }
        playlistItemToBeMoved = null
    }

    override fun selectVideoTrack(format: Format?) {
        newPlayer?.selectVideoTrack(format)
        dialogVisible(false)
    }

    override fun selectAudioLanguage(language: String) {
        newPlayer?.currentStreamLanguageConstraint = language
        dialogVisible(false)
    }

    override fun selectSubtitleLanguage(language: String?) {
        newPlayer?.selectSubtitleLanguage(language)
        dialogVisible(false)
    }

    override fun dialogVisible(visible: Boolean) {
        dialogIsVisible = visible
        if (visible) {
            hideUiDelayedJob?.cancel()
            if (!uiState.value.uiMode.videoControllerUiVisible) {
                changeUiMode(uiState.value.uiMode.getControllerUiVisibleState(), null)
                hideUiDelayedJob?.cancel()
            }
        } else {
            resetHideDelayTimer()
        }
    }

    override fun doneEnteringPip() {
        mutableUiState.update {
            it.copy(enteringPip = false)
        }
    }

    override fun onPictureInPictureModeChanged(isPictureInPictureMode: Boolean) {
        if (isPictureInPictureMode) {
            mutableUiState.update {
                it.copy(uiMode = UIModeState.PIP)
            }
        } else {
            changeUiMode(UIModeState.FULLSCREEN_VIDEO, null)
        }
    }

    override fun onSpeedPitchChanged(speed: Float, pitch: Float) {
        Log.d(TAG, "speed: $speed, pitch: $pitch")
        val effectivePitch = if (mutableUiState.value.pitchDetached) pitch else speed
        applyPlaybackParameters(PlaybackParameters(speed, effectivePitch))
    }

    override fun onPitchDetachedToggle(detached: Boolean) {
        Log.d(TAG, "pitch detached: $detached")
        mutableUiState.update {
            it.copy(pitchDetached = detached)
        }
        if (!detached) {
            val parameters = mutableUiState.value.playbackParameters
            val attached = min(parameters.speed, parameters.pitch)
            applyPlaybackParameters(PlaybackParameters(attached, attached))
        }
    }

    override fun onSkipSilenceToggle(enabled: Boolean) {
        Log.d(TAG, "skip silence: $enabled")
        newPlayer?.skipSilence = enabled
        mutableUiState.update {
            it.copy(skipSilence = enabled)
        }
    }

    private fun applyPlaybackParameters(parameters: PlaybackParameters) {
        newPlayer?.exoPlayer?.value?.playbackParameters = parameters
        mutableUiState.update {
            it.copy(playbackParameters = parameters)
        }
    }

    override fun removePlaylistItem(uniqueId: Long) {
        newPlayer?.removePlaylistItem(uniqueId)
    }

    private fun getEmbeddedUiRatio() = newPlayer?.exoPlayer?.value?.let { player ->
        val videoRatio = player.videoSize.ratio
        return (if (videoRatio.isNaN()) currentContentRatio
        else videoRatio).coerceIn(minContentRatio, maxContentRatio)
    } ?: minContentRatio

    private fun <T> safeTryEmit(sharedFlow: MutableSharedFlow<T>, value: T) {
        if (!sharedFlow.tryEmit(value)) {
            viewModelScope.launch {
                sharedFlow.emit(value)
            }
        }
    }
}

private val VideoSize.ratio get() = (width * pixelWidthHeightRatio) / height
