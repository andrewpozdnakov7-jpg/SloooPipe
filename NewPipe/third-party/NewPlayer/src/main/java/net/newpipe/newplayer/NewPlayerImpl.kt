/* NewPlayer
 *
 * @author Christian Schabesberger
 *
 * Copyright (C) NewPipe e.V. 2026 <code(at)newpipe-ev.de>
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

package net.newpipe.newplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.newpipe.newplayer.data.Chapter
import net.newpipe.newplayer.data.NewPlayerException
import net.newpipe.newplayer.data.PlayMode
import net.newpipe.newplayer.data.RepeatMode
import net.newpipe.newplayer.logic.ActionResponse
import net.newpipe.newplayer.logic.NoResponse
import net.newpipe.newplayer.logic.ReplaceItemResponse
import net.newpipe.newplayer.logic.ReplaceMediaItemResponse
import net.newpipe.newplayer.logic.StreamExceptionResponse
import net.newpipe.newplayer.repository.MediaRepository
import net.newpipe.newplayer.service.NewPlayerService
import net.newpipe.newplayer.uiModel.InternalNewPlayerViewModel

private const val TAG = "NewPlayerImpl"

/**
 * @param onNewPlayer builds the ExoPlayer instance. Supply your own [MediaSource.Factory] via
 * [ExoPlayer.Builder.setMediaSourceFactory] here, for example to merge the streams of an item,
 * or to use a custom `HttpDataSource.Factory`.
 */
@OptIn(UnstableApi::class)
class NewPlayerImpl(
    val app: Application,
    val onNewPlayer: (Application) -> ExoPlayer = ::defaultBuildNewExoplayer,
    override val playerActivityClass: Class<out Activity>,
    override val repository: MediaRepository,
    val beforeRelease: () -> Unit = {},
    override val notificationIcon: IconCompat = IconCompat.createWithResource(
        app,
        R.drawable.new_player_tiny_icon
    ),
    override val rescueStreamFault: suspend (
        item: String?,
        mediaItem: MediaItem?,
        exception: Exception,
        repository: MediaRepository
    ) -> StreamExceptionResponse = { _, _, _, _ -> NoResponse() }
) : NewPlayer {
    private val mutableExoPlayer = MutableStateFlow<ExoPlayer?>(null)
    override val exoPlayer = mutableExoPlayer.asStateFlow()

    /**
     * Must be in IETF-BCP-47 format
     */
    override var preferredStreamLanguages: List<String> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            applyPreferredAudioLanguages()
        }

    private var playerScope = CoroutineScope(Dispatchers.Main + Job())
    private var playbackGeneration = 0L

    private var uniqueIdToItemLookup = HashMap<Long, String>()

    // this is used to take care of the NewPlayerService
    private var mediaController: MediaController? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    // The view model that currently drives NewPlayer's UI, if there is one. Playback errors travel
    // through it before NewPlayer handles them, because some of them can only be fixed by the UI.
    internal var attachedViewModel: InternalNewPlayerViewModel? = null

    // Buffered, so an emitter is never blocked by a slow collector (the UI collector only exists
    // while the player screen is composed) and errors are not lost while one catches up.
    var mutableErrorFlow = MutableSharedFlow<Exception>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val errorFlow = mutableErrorFlow.asSharedFlow()

    override val bufferedPercentage: Int
        get() = exoPlayer.value?.bufferedPercentage ?: 0

    override var currentPosition: Long
        get() = exoPlayer.value?.currentPosition ?: 0
        set(value) {
            exoPlayer.value?.seekTo(value)
        }

    override var fastSeekAmountSec: Int = 10

    override var playBackMode = MutableStateFlow(PlayMode.IDLE)

    override var shuffle: Boolean
        get() = exoPlayer.value?.shuffleModeEnabled ?: false
        set(value) {
            exoPlayer.value?.shuffleModeEnabled = value
        }

    override var skipSilence: Boolean
        @OptIn(UnstableApi::class)
        get() = exoPlayer.value?.skipSilenceEnabled ?: false

        @OptIn(UnstableApi::class)
        set(value) {
            exoPlayer.value?.skipSilenceEnabled = value
        }

    override var repeatMode: RepeatMode
        get() = when (exoPlayer.value?.repeatMode) {
            Player.REPEAT_MODE_OFF -> RepeatMode.DO_NOT_REPEAT
            Player.REPEAT_MODE_ALL -> RepeatMode.REPEAT_ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.REPEAT_ONE
            else -> throw NewPlayerException("Unknown Repeatmode option returned by ExoPlayer: ${exoPlayer.value?.repeatMode}")
        }
        set(value) {
            when (value) {
                RepeatMode.DO_NOT_REPEAT -> exoPlayer.value?.repeatMode = Player.REPEAT_MODE_OFF
                RepeatMode.REPEAT_ALL -> exoPlayer.value?.repeatMode = Player.REPEAT_MODE_ALL
                RepeatMode.REPEAT_ONE -> exoPlayer.value?.repeatMode = Player.REPEAT_MODE_ONE
            }
        }

    private var mutablePayerOnEvent = MutableSharedFlow<Pair<Player, Player.Events>>()
    override val onPlayerEvent: SharedFlow<Pair<Player, Player.Events>> =
        mutablePayerOnEvent.asSharedFlow()

    override var playWhenReady: Boolean
        set(value) {
            exoPlayer.value?.playWhenReady = value
        }
        get() = exoPlayer.value?.playWhenReady ?: false

    override val duration: Long
        get() = exoPlayer.value?.duration ?: C.TIME_UNSET

    private val mutablePlaylist = MutableStateFlow<List<MediaItem>>(emptyList())
    override val playlist: StateFlow<List<MediaItem>> = mutablePlaylist.asStateFlow()

    private val mutableCurrentlyPlaying = MutableStateFlow<MediaItem?>(null)
    override val currentlyPlaying: StateFlow<MediaItem?> = mutableCurrentlyPlaying.asStateFlow()

    private val mutableCurrentChapter = MutableStateFlow<List<Chapter>>(emptyList())
    override val currentChapters: StateFlow<List<Chapter>> = mutableCurrentChapter.asStateFlow()

    override var currentlyPlayingPlaylistItem: Int
        get() = exoPlayer.value?.currentMediaItemIndex ?: -1
        set(value) {
            if (value !in playlist.value.indices) {
                throw NewPlayerException("Playlist item selection out of bound: selected item index: $value, available chapters: ${playlist.value.size}")
            }
            exoPlayer.value?.seekTo(value, 0)
        }

    private var mutableCurrentlyAvailableTracks = MutableStateFlow<List<Format>>(emptyList())
    override val currentlyAvailableTracks: StateFlow<List<Format>> =
        mutableCurrentlyAvailableTracks.asStateFlow()

    private var mutableCurrentlyPlayingTracks = MutableStateFlow<List<Format>>(emptyList())
    override val currentlyPlayingTracks: StateFlow<List<Format>> =
        mutableCurrentlyPlayingTracks.asStateFlow()

    override var currentStreamLanguageConstraint: String? = null
        set(value) {
            if (field == value) return
            field = value
            applyPreferredAudioLanguages()
        }

    private val mutablePinnedVideoTrack = MutableStateFlow<Format?>(null)
    override val pinnedVideoTrack: StateFlow<Format?> = mutablePinnedVideoTrack.asStateFlow()

    // Changing the parameters makes ExoPlayer re-select tracks of the loaded media in place.
    private fun updateTrackSelectionParameters(builder: TrackSelectionParameters.Builder.() -> Unit) {
        val player = exoPlayer.value ?: return
        player.trackSelectionParameters =
            player.trackSelectionParameters.buildUpon().apply(builder).build()
    }

    private fun applyPreferredAudioLanguages() {
        val languages = currentStreamLanguageConstraint?.let { listOf(it) } ?: preferredStreamLanguages
        updateTrackSelectionParameters { setPreferredAudioLanguages(*languages.toTypedArray()) }
    }

    @OptIn(UnstableApi::class)
    private fun setupNewExoplayer() {
        val newExoPlayer = onNewPlayer(app)

        newExoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onPlayBackError(error)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
                launchJobAndCollectError {
                    mutablePayerOnEvent.emit(Pair(player, events))
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // TODO shouldn't you check that reason == TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED
                if (reason == TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    mutablePlaylist.update {
                        (0..<newExoPlayer.mediaItemCount).map {
                            newExoPlayer.getMediaItemAt(it)
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentStreamLanguageConstraint = null
                if (pinnedVideoTrack.value != null) selectVideoTrack(null)
                super.onMediaItemTransition(mediaItem, reason)
                mutableCurrentlyPlaying.update { mediaItem }
                if (mediaItem == null) {
                    mutableCurrentlyAvailableTracks.update { emptyList() }
                }
            }

            @OptIn(UnstableApi::class)
            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                val availableTracks = formatsFromMedia3Tracks(tracks, onlySelectedTracks = false)
                Log.d(TAG, "New available tracks: \n" + availableTracks.joinToString())
                mutableCurrentlyAvailableTracks.update { availableTracks }
                mutableCurrentlyPlayingTracks.update {
                    val playingTracks =
                        formatsFromMedia3Tracks(tracks, onlySelectedTracks = true)
                            .ifEmpty { availableTracks.filterNot { MimeTypes.isText(it.sampleMimeType) } }
                    Log.d(
                        TAG,
                        "currently playing tracks: \n ${playingTracks.joinToString("\n")}"
                    )

                    playingTracks
                }
            }
        })
        mutableExoPlayer.update {
            newExoPlayer
        }
        if (preferredStreamLanguages.isNotEmpty()) applyPreferredAudioLanguages()
    }

    fun onPlayBackError(exception: Exception) {
        if (attachedViewModel?.onPlaybackError(exception) == true) {
            return
        }

        exoPlayer.value?.pause()

        launchJobAndCollectError {
            val item = exoPlayer.value?.currentMediaItem?.mediaId?.let {
                uniqueIdToItemLookup[it.toLong()]
            }
            val response = rescueStreamFault(
                item,
                exoPlayer.value?.currentMediaItem!!,
                exception,
                repository
            )
            when (response) {
                is ActionResponse -> {
                    response.action()
                }

                is ReplaceMediaItemResponse -> {
                    replaceCurrentMediaItem(response.mediaItem)
                }

                is ReplaceItemResponse -> {
                    replaceCurrentItem(response.newItem)
                }

                is NoResponse -> {
                    try {
                        throw NewPlayerException(
                            "Playback Exception happened, but no response was send by rescueStreamFault(). You may consider to implement this function.",
                            exception
                        )
                    } catch (e: Exception) {
                        mutableErrorFlow.emit(e)
                    }
                }
            }
        }
    }

    init {
        playerScope.launch {
            currentlyPlaying.collect { playing ->
                playing?.let {
                    try {
                        mutableCurrentChapter.update {
                            val chapters = repository.getChapters(
                                uniqueIdToItemLookup[playing.mediaId.toLong()]!!
                            )

                            chapters
                        }
                    } catch (e: Exception) {
                        mutableErrorFlow.emit(e)
                    }
                }
            }
        }
    }

    override fun prepare() {
        if (exoPlayer.value == null) {
            setupNewExoplayer()
        }
        exoPlayer.value?.prepare()
        if (mediaController == null && mediaControllerFuture == null) {
            val playerAtConnect = exoPlayer.value
            val sessionToken = SessionToken(app, ComponentName(app, NewPlayerService::class.java))
            val pendingController = MediaController.Builder(app, sessionToken).buildAsync()
            mediaControllerFuture = pendingController
            pendingController.addListener({
                try {
                    val connected = pendingController.get()
                    if (mediaControllerFuture === pendingController && exoPlayer.value === playerAtConnect) {
                        mediaController = connected
                    } else {
                        connected.release()
                    }
                } catch (error: Exception) {
                    if (!pendingController.isCancelled) {
                        launchJobAndCollectError { throw NewPlayerException("Media session connection failed", error) }
                    }
                } finally {
                    if (mediaControllerFuture === pendingController) mediaControllerFuture = null
                }
            }, MoreExecutors.directExecutor())
        }
    }

    override fun play() {
        val player = exoPlayer.value ?: return
        if (player.currentMediaItem == null) {
            Log.i(TAG, "Tried to start playing but no media Item was cued")
            return
        }

        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(player.currentMediaItemIndex, 0L)
        }
        player.play()
    }

    override fun pause() {
        exoPlayer.value?.pause()
    }

    @OptIn(UnstableApi::class)
    override fun addToPlaylist(item: String) {
        if (exoPlayer.value == null) {
            prepare()
        }
        launchJobAndCollectError {
            exoPlayer.value?.addMediaItem(toMediaItem(item))
        }
    }

    override fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        exoPlayer.value?.moveMediaItem(fromIndex, toIndex)
    }

    override fun removePlaylistItem(uniqueId: Long) {
        for (i in 0..<(exoPlayer.value?.mediaItemCount ?: 0)) {
            val id = exoPlayer.value?.getMediaItemAt(i)?.mediaId?.toLong() ?: 0
            if (id == uniqueId) {
                exoPlayer.value?.removeMediaItem(i)
                break
            }
        }

        if (exoPlayer.value?.mediaItemCount == 0) {
            release()
        }
    }

    @OptIn(UnstableApi::class)
    override fun playStream(item: String, playMode: PlayMode) {
        val generation = playbackGeneration
        launchJobAndCollectError {
            val mediaItem = toMediaItem(item)
            if (generation == playbackGeneration) internalPlayStream(mediaItem, playMode)
        }
    }

    /** Atomically install an ordered queue. Repositories may supply deferred media sources. */
    fun playQueue(items: List<String>, startIndex: Int, playing: Boolean) {
        require(items.isNotEmpty() && startIndex in items.indices)
        val generation = playbackGeneration
        launchJobAndCollectError {
            val mediaItems = items.map { toMediaItem(it) }
            if (generation != playbackGeneration) return@launchJobAndCollectError
            if (exoPlayer.value == null) setupNewExoplayer()
            playBackMode.value = PlayMode.EMBEDDED_VIDEO
            exoPlayer.value?.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            exoPlayer.value?.playWhenReady = playing
            prepare()
        }
    }

    /** Resolve metadata as one ordered batch, with the same release guard as playStream. */
    fun insertQueueItems(items: List<String>, index: Int? = null) {
        val generation = playbackGeneration
        launchJobAndCollectError {
            val mediaItems = items.map { toMediaItem(it) }
            if (generation != playbackGeneration) return@launchJobAndCollectError
            val player = exoPlayer.value ?: return@launchJobAndCollectError
            player.addMediaItems((index ?: player.mediaItemCount).coerceIn(0, player.mediaItemCount), mediaItems)
        }
    }

    @OptIn(UnstableApi::class)
    @Throws(IndexOutOfBoundsException::class)
    override fun selectChapter(index: Int) {
        val chapters = currentChapters.value
        if (index !in chapters.indices) {
            throw IndexOutOfBoundsException("Chapter selection out of bound: selected chapter index: $index, available chapters: ${chapters.size}")
        }
        val chapter = chapters[index]
        currentPosition = chapter.chapterStartInMs
    }

    override fun selectVideoTrack(format: Format?) {
        if (format == null) {
            updateTrackSelectionParameters { clearOverridesOfType(C.TRACK_TYPE_VIDEO) }
            mutablePinnedVideoTrack.update { null }
            return
        }
        val override = exoPlayer.value?.currentTracks?.groups
            ?.filter { it.type == C.TRACK_TYPE_VIDEO }
            ?.firstNotNullOfOrNull { group ->
                (0 until group.length).firstOrNull { group.getTrackFormat(it) == format }
                    ?.let { TrackSelectionOverride(group.mediaTrackGroup, it) }
            }
        if (override == null) {
            Log.w(TAG, "Tried to select a video track the current item does not provide: $format")
            return
        }
        updateTrackSelectionParameters { setOverrideForType(override) }
        mutablePinnedVideoTrack.update { format }
    }

    override fun selectSubtitleLanguage(language: String?) {
        updateTrackSelectionParameters {
            setTrackTypeDisabled(C.TRACK_TYPE_TEXT, language == null)
            setPreferredTextLanguage(language)
        }
    }

    override fun release() {
        beforeRelease()
        playbackGeneration++
        val pendingController = mediaControllerFuture
        mediaControllerFuture = null
        pendingController?.cancel(true)
        mediaController?.release()
        exoPlayer.value?.release()
        playBackMode.update {
            PlayMode.IDLE
        }
        mutableExoPlayer.update {
            null
        }
        mediaController = null
        mutableCurrentlyAvailableTracks.update { emptyList() }
        mutableCurrentlyPlayingTracks.update { emptyList() }
        mutablePinnedVideoTrack.update { null }
        mutablePlaylist.update { emptyList() }
        mutableCurrentlyPlaying.update { null }
        uniqueIdToItemLookup = HashMap()
    }

    override fun getItemFromMediaItem(mediaItem: MediaItem) = uniqueIdToItemLookup[mediaItem.mediaId.toLong()]
        ?: throw NewPlayerException("Could not find an item corresponding to a media item with uniqueid: ${mediaItem.mediaId}")

    @OptIn(UnstableApi::class)
    private fun internalPlayStream(
        mediaItem: MediaItem,
        playMode: PlayMode
    ) {
        currentStreamLanguageConstraint = null
        if (exoPlayer.value?.playbackState == Player.STATE_IDLE || exoPlayer.value == null) {
            prepare()
        }
        this.playBackMode.update { playMode }

        this.exoPlayer.value?.setMediaItem(mediaItem)
        this.exoPlayer.value?.play()
    }

    /**
     * Replaces the current playlist entry with [mediaItem] and continues playing at the position
     * playback stopped. This can be used to replace faulty streams.
     */
    private suspend fun replaceCurrentMediaItem(mediaItem: MediaItem) {
        val item = uniqueIdToItemLookup[this.currentlyPlaying.value?.mediaId?.toLong()]!!
        replaceCurrentPlaylistEntry(toMediaItem(item, mediaItem))
    }

    // Removing and re-adding (instead of replaceMediaItem()) makes ExoPlayer build a fresh source.
    private fun replaceCurrentPlaylistEntry(mediaItem: MediaItem) {
        val currentPosition = this.currentPosition
        val currentlyPlayingPlaylistItem = this.currentlyPlayingPlaylistItem

        this.exoPlayer.value?.removeMediaItem(currentlyPlayingPlaylistItem)
        this.exoPlayer.value?.addMediaItem(currentlyPlayingPlaylistItem, mediaItem)
        if (this.exoPlayer.value?.playbackState == Player.STATE_IDLE) {
            prepare()
        }
        this.currentlyPlayingPlaylistItem = currentlyPlayingPlaylistItem
        this.exoPlayer.value?.seekTo(currentPosition)
        this.exoPlayer.value?.play()
    }

    private suspend fun replaceCurrentItem(item: String) {
        replaceCurrentPlaylistEntry(toMediaItem(item))
    }

    private suspend fun toMediaItem(item: String): MediaItem = toMediaItem(item, repository.getMediaItem(item))

    private suspend fun toMediaItem(item: String, mediaItem: MediaItem): MediaItem {
        val uniqueId = Random.nextLong()
        uniqueIdToItemLookup[uniqueId] = item

        val builder = mediaItem.buildUpon().setMediaId(uniqueId.toString())
        try {
            builder.setMediaMetadata(repository.getMetaInfo(item))
        } catch (e: Exception) {
            mutableErrorFlow.emit(e)
        }
        return builder.build()
    }

    private fun launchJobAndCollectError(task: suspend () -> Unit): Job {
        val generation = playbackGeneration
        return playerScope.launch {
            if (generation != playbackGeneration) return@launch
            try {
                task()
            } catch (e: Exception) {
                if (generation == playbackGeneration) mutableErrorFlow.emit(e)
            }
        }
    }
}

/** Video, then audio, then text. Video and audio are sorted from best to worst. */
@SuppressLint("UnsafeOptInUsageError")
private val formatOrder = compareBy<Format> {
    when {
        MimeTypes.isVideo(it.sampleMimeType) -> 0
        MimeTypes.isAudio(it.sampleMimeType) -> 1
        else -> 2
    }
}
    .thenByDescending { it.width * it.height }
    .thenByDescending { it.frameRate }
    .thenByDescending { it.bitrate }

private fun formatsFromMedia3Tracks(media3Tracks: Tracks, onlySelectedTracks: Boolean): List<Format> = media3Tracks.groups
    .filter { it.type in listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT) }
    .flatMap { group ->
        (0 until group.length)
            .filter { !onlySelectedTracks || group.isTrackSelected(it) }
            .map { group.getTrackFormat(it) }
    }
    .sortedWith(formatOrder)

@OptIn(UnstableApi::class)
private fun defaultBuildNewExoplayer(app: Application): ExoPlayer {
    val factory =
        DefaultRenderersFactory(app).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

    // Enables seeking in constant bitrate MP3 files without a seek table.
    val extractorsFactory = DefaultExtractorsFactory()
        .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)

    val newExoPlayer =
        ExoPlayer.Builder(app)
            .setRenderersFactory(factory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(app, extractorsFactory))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

    return newExoPlayer
}
