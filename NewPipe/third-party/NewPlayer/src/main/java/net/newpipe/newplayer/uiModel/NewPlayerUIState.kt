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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import net.newpipe.newplayer.NewPlayer
import net.newpipe.newplayer.data.*
import net.newpipe.newplayer.ui.ContentScale
import net.newpipe.newplayer.ui.NewPlayerUI

/**
 * Shows the state the UI is rendering. In the manner of MVVM, [NewPlayerViewModel] sends this
 * state over to [NewPlayerUI], to tell it what should be rendered.
 */
@UnstableApi
data class NewPlayerUIState(
    /**
     * Depicts which screen of NewPlayer's UI should be visible.
     */
    val uiMode: UIModeState,

    /**
     * Is the stream currently playing or not.
     */
    val playing: Boolean,

    /**
     * Is the player working through the current item, meaning playback is requested and neither
     * finished nor stopped. Unlike [playing] this stays true while the player stalls to buffer,
     * which is why it, and not [playing], decides whether the screen has to stay awake.
     */
    val playbackActive: Boolean,

    /**
     * The content ratio of the currently playing video.
     */
    val contentRatio: Float,

    /**
     * The ratio of the embedded UI. This might differ from the content ratio.
     */
    val embeddedUiRatio: Float,

    /**
     * Depicts weather the video should fit inside, be cropped or stretched.
     */
    val contentFitMode: ContentScale,

    /**
     * The position of the seeker thumb. Whereas seekerPosition ∈ [0; 1].
     */
    val seekerPosition: Float,

    /**
     * The amount of buffered video/audio material, Whereas bufferedPercentage ∈ [0; 1].
     */
    val bufferedPercentage: Float,

    /**
     * The same as ExoPlayers isLoading. See [Player.isLoading]
     */
    val isLoading: Boolean,

    /**
     * The duration of the currently playing stream in milliseconds,
     * or [androidx.media3.common.C.TIME_UNSET] while it is not known yet.
     */
    val durationInMs: Long,

    /**
     * The playback position of the currently playing stream in milliseconds.
     * This only updates if the progress updater job runs within [NewPlayerViewModel].
     */
    val playbackPositionInMs: Long,

    /**
     * The playback position within the whole currently playing playlist.
     */
    val playbackPositionInPlaylistMs: Long,

    /**
     * Amount of seconds on step of fast seek can take.
     */
    val fastSeekSeconds: Int,

    /**
     * The current sound volume.
     */
    val soundVolume: Float,

    /**
     * The brightness volume. Might be negative if the system is in control of the brightness.
     */
    val brightness: Float,

    /**
     * This is used to restore several values when switching back from a fullscreen view to an
     * embedded screen view. Such values include the color scheme of the system info bar, the
     * screen brightness and the default screen orientation.
     *
     * At least within the test app restoring these values does not always correctly work.
     * One reason this might be is because the main activity layout of the test app actually hosts
     * two NewPlayerViews. Because the viewModel now switches between these two views weird things
     * happen. For the sake of better animations and this EmbeddedUiConfig stuff to work it was
     * better to only have one NewPlayerView in the whole layout. Or ... well at best directly
     * Compose.
     */
    val embeddedUiConfig: EmbeddedUiConfig?,

    /**
     * The currently playing playlist as provided by ExoPlayer. However, it might sometimes
     * drift from the playlist provided by exoplayer. This can happen when the user reorders
     * items. The playlist will then immediately reflect the new state of the list, even though
     * that state was not yet confirmed by exoplayer. If this list could not drift you would expect
     * the UI to be jittery upon item reordering.
     */
    val playList: List<MediaItem>,

    /**
     * The chapters provided by the currently playing item. Might be empty if the item does not have
     * chapters or when the chapter didn't load up yet.
     */
    val chapters: List<Chapter>,

    /**
     * Same as [NewPlayer.shuffle].
     */
    val shuffleEnabled: Boolean,

    /**
     * Same as [NewPlayer.repeatMode]
     */
    val repeatMode: RepeatMode,

    /**
     * Duration of the whole currently playing playlist
     */
    val playListDurationInS: Int,

    /**
     * The media item that is currently playing.
     */
    val currentlyPlaying: MediaItem?,

    /**
     * The index into the [playList], that depicts the currently playing item.
     */
    val currentPlaylistItemIndex: Int,

    /**
     * The [Format]s of all tracks available for the currently playing item.
     */
    val currentlyAvailableTracks: List<Format>,

    /**
     * The [Format]s of the tracks that are currently playing.
     * in other words these tracks are actively playing back and are getting muxed together by
     * ExoPlayer.
     */
    val currentlyPlayingTracks: List<Format>,

    /**
     * The video track the user picked, or null if ExoPlayer picks the quality on its own.
     */
    val pinnedVideoTrack: Format?,

    /**
     * Is the Ui currently transitioning to PiP mode.
     */
    val enteringPip: Boolean,

    /**
     * The seeker preview thumbnail that should be visible. This updates if the user uses
     * the seeker thumb to seek through a stream. If null no thumbnail is available.
     */
    val currentSeekPreviewThumbnail: ImageBitmap?,

    /**
     * The seeker preview text that should be visible when the playback position is changed through
     * the seek bar. If null no text will be rendered.
     */
    val currentSeekPreviewText: String?,

    /**
     * Depicts weather the seeker preview thumbnail should be visible or not.
     */
    val seekPreviewVisible: Boolean,

    /** Depicts weather to show the playlist or the current stream thumbnail in the audio player.**/
    var showPlaylistInAudioPlayer: Boolean,

    /**
     * Counts how often the video surface had to be thrown away. The video player UI keys its
     * surface on this value, so incrementing it makes the UI destroy the surface it currently
     * shows and hand the player a pristine one.
     */
    val videoSurfaceGeneration: Int,

    /**
     * Depicts the current speed and pitch of the played media.
     */
    val playbackParameters: PlaybackParameters,

    /**
     * Depicts weather the pitch may be set independently of the playback speed.
     * If it is not detached the pitch follows the speed, the way an analog tape would.
     */
    val pitchDetached: Boolean,

    /**
     * Depicts weather silent sections of the audio track are skipped over during playback.
     */
    val skipSilence: Boolean,

    /**
     * Depicts weather the repository can provide a link to the currently playing item.
     */
    val linkAvailable: Boolean,

    /**
     * Depicts weather the repository can provide a timestamped link to the currently playing item.
     */
    val timestampLinkAvailable: Boolean
) {
    companion object {

        /**
         * Default object to initialize the UIState.
         */
        val DEFAULT = NewPlayerUIState(
            uiMode = UIModeState.PLACEHOLDER,
            playing = false,
            playbackActive = false,
            contentRatio = 16 / 9f,
            embeddedUiRatio = 16f / 9f,
            contentFitMode = ContentScale.FIT_INSIDE,
            seekerPosition = 0f,
            bufferedPercentage = 0f,
            isLoading = true,
            durationInMs = C.TIME_UNSET,
            playbackPositionInPlaylistMs = 0,
            playbackPositionInMs = 0,
            fastSeekSeconds = 0,
            soundVolume = 0f,
            brightness = -1f,
            embeddedUiConfig = null,
            playList = emptyList(),
            chapters = emptyList(),
            shuffleEnabled = false,
            repeatMode = RepeatMode.DO_NOT_REPEAT,
            playListDurationInS = 0,
            currentlyPlaying = null,
            currentPlaylistItemIndex = 0,
            currentlyAvailableTracks = emptyList(),
            currentlyPlayingTracks = emptyList(),
            pinnedVideoTrack = null,
            enteringPip = false,
            currentSeekPreviewThumbnail = null,
            currentSeekPreviewText = null,
            seekPreviewVisible = false,
            showPlaylistInAudioPlayer = false,
            videoSurfaceGeneration = 0,
            playbackParameters = PlaybackParameters(1f),
            pitchDetached = true,
            skipSilence = false,
            linkAvailable = false,
            timestampLinkAvailable = false
        )

        /**
         * Dummy object that is mainly used for Compose previews.
         */
        val DUMMY = DEFAULT.copy(
            linkAvailable = true,
            timestampLinkAvailable = true,
            currentlyAvailableTracks = listOf(
                dummyVideoFormat(1920, 1080),
                dummyVideoFormat(1280, 720),
                dummyVideoFormat(853, 480),
                dummyAudioFormat("en"),
                dummyAudioFormat("es"),
            ),
            currentlyPlayingTracks = listOf(
                dummyVideoFormat(1920, 1080),
                dummyAudioFormat("es"),
            ),
            uiMode = UIModeState.EMBEDDED_VIDEO,
            playing = true,
            playbackActive = true,
            seekerPosition = 0.3f,
            bufferedPercentage = 0.5f,
            isLoading = false,
            durationInMs = 12000,
            playbackPositionInPlaylistMs = 5039,
            playbackPositionInMs = 400,
            fastSeekSeconds = 10,
            soundVolume = 0.5f,
            brightness = 0.2f,
            shuffleEnabled = true,
            playListDurationInS = 5493,
            currentlyPlaying = MediaItem.Builder()
                .setUri("https://ftp.fau.de/cdn.media.ccc.de/congress/2010/mp4-h264-HQ/27c3-4159-en-reverse_engineering_mos_6502.mp4")
                .setMediaId("0")
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle("Superawesome Video")
                    .setArtist("Yours truly")
                    .setArtworkUri(null)
                    .setDurationMs(4201000L)
                    .build())
                .build(),
            currentPlaylistItemIndex = 1,
            chapters = arrayListOf(
                Chapter(
                    chapterStartInMs = 0,
                    chapterTitle = "Intro",
                    thumbnail = null
                ),
                Chapter(
                    chapterStartInMs = 5000,
                    chapterTitle = "First Chapter",
                    thumbnail = null
                ),
                Chapter(
                    chapterStartInMs = 10000,
                    chapterTitle = "Second Chapter",
                    thumbnail = null
                ),
            ),
            playList = arrayListOf(
                MediaItem.Builder()
                    .setUri("https://ftp.fau.de/cdn.media.ccc.de/congress/2010/mp4-h264-HQ/27c3-4159-en-reverse_engineering_mos_6502.mp4")
                    .setMediaId("0")
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle("Stream 1")
                        .setArtist("Yours truly")
                        .setArtworkUri(null)
                        .setDurationMs(4201000L)
                        .build())
                    .build(),
                MediaItem.Builder()
                    .setUri("https://ftp.fau.de/cdn.media.ccc.de/congress/2010/mp4-h264-HQ/27c3-4159-en-reverse_engineering_mos_6502.mp4")
                    .setMediaId("1")
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle("Stream 2")
                        .setArtist("Yours truly")
                        .setArtworkUri(null)
                        .setDurationMs(3201000L)
                        .build())
                    .build(),
                MediaItem.Builder()
                    .setUri("https://ftp.fau.de/cdn.media.ccc.de/congress/2010/mp4-h264-HQ/27c3-4159-en-reverse_engineering_mos_6502.mp4")
                    .setMediaId("2")
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle("Stream 3")
                        .setArtist("Yours truly")
                        .setArtworkUri(null)
                        .setDurationMs(2201000L)
                        .build())
                    .build(),
            ),
            playbackParameters = PlaybackParameters(1.2f, 1.5f)
        )
    }
}

@UnstableApi
private fun dummyVideoFormat(width: Int, height: Int) = Format.Builder()
    .setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(width).setHeight(height).setFrameRate(30F)
    .build()

@UnstableApi
private fun dummyAudioFormat(language: String) = Format.Builder()
    .setSampleMimeType(MimeTypes.AUDIO_AAC).setAverageBitrate(49000).setLanguage(language)
    .build()
