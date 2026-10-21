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

package net.newpipe.newplayer.repository

import android.graphics.Bitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.MediaSource
import net.newpipe.newplayer.data.Chapter
import java.net.URL

/**
 * You, dear Developer who uses NewPlayer, will want to implement MediaRepository.
 * This interface is the thing that will enable NewPlayer to access your content.
 * Just like the ViewModel and the View, the Repository is an element of the MVVM architecture.
 * This repository is basically the "view" to your data.
 *
 * ## How does MediaRepository work
 * Every piece of media that can be defined as a unit is referred to as an *item*.
 * An item can be a YouTube video or a media.ccc talk, or a video stored on your phones storage
 *
 * Each *item* is played through one Media3 [MediaItem], which you supply via [getMediaItem].
 * Usually that is the URI of a media container (like an mpeg4 or a webm file) or of a DASH/HLS
 * manifest. Track selection is left to ExoPlayer.
 *
 * If an item consists of multiple streams (for example separate audio and video files), describe
 * them in [MediaItem.LocalConfiguration.tag] and supply a [MediaSource.Factory] to ExoPlayer
 * that turns such a [MediaItem] into, for example, a `MergingMediaSource`. The same goes for
 * networking: a custom `HttpDataSource.Factory` belongs into that [MediaSource.Factory].
 *
 * The way it works is that you tell [NewPlayer] to play a certain **item**. [NewPlayer] will then
 * *ask* the repository for information about this *item*.
 * The [MediaItem] and everything else, like [Chapter]s, this **item**'s [MediaMetadata],
 * preview thumbnails etc., basically things [NewPlayer] might want to know about an **item**, is
 * supplied through the repository.
 *
 * ## What is an item
 * An item is a string or tag that uniquely identifies one specific piece or unit of media.
 * Something like a youtube video.
 * For example an **item** can be the link to a youtube video or a media.ccc video, or the filepath
 * and name of a video stored on your phone.
 *
 * You can also use uuid's or randomly generated values to create items. You basically define
 * what an item should be, as long as the string meets these two criteria:
 * - The string must be unique for one piece of media (the item must be relational)
 * - The repository must be able to understand that item string and be able to return
 *   the information related to that **item**.
 *
 * So the way you identify and use **item** with [NewPlayer] depends on how [MediaRepository]
 * is implemented.
 */
interface MediaRepository {
    /**
     * Information about thumbnail previews. This is required to get prefetching to work correctly.
     */
    data class PreviewThumbnailsInfo (
        /**
         * How many preview thumbnails are there for this video
         */
        val count: Long,

        /**
         * The distance between each thumbnail in milliseconds.
         */
        val distanceInMS: Long,
    )

    /**
     * Get MediaMetadata information for a certain item. Please refer to the media3 documentation
     * for [MediaMetadata].
     */
    suspend fun getMetaInfo(item: String): MediaMetadata

    /**
     * This should return the [MediaItem] that plays the **item**.
     *
     * The [MediaItem] is handed to ExoPlayer as is, so it is turned into a [MediaSource] by the
     * [MediaSource.Factory] of the ExoPlayer you build in `NewPlayerImpl.onNewPlayer`.
     * Put subtitles on it via [MediaItem.Builder.setSubtitleConfigurations].
     * NewPlayer overrides the media id and sets the [MediaMetadata] from [getMetaInfo].
     */
    suspend fun getMediaItem(item: String): MediaItem

    /**
     * This should return a thumbnail associated with the provided timestamp of the provided
     * **item**. The reason this function does not simply return a Uri to a bitmap is that
     * some platforms like Youtube provide the preview thumbnail as a collage of multiple
     * thumbnails within one jpeg file. It is then the task of the client to crop this jpeg to only
     * show the piece of that image that depicts the thumbnail for the given timestamp.
     *
     * So because of this you will have to download, or precache, the thumbnails by yourself.
     */
    suspend fun getPreviewThumbnail(item: String, timestampInMs: Long): Bitmap?

    /**
     * The amount of review thumbnails an item provides. This is used to tell async
     * requesting tools how much requests to spawn to get all thumbnails.
     * It's also used to calculate the correct keys for the caching repository.
     */
    suspend fun getPreviewThumbnailsInfo(item: String) : PreviewThumbnailsInfo

    /**
     * This should return the chapters associated with an **item**. If an **item** does not have
     * chapters you may return an empty list.
     */
    suspend fun getChapters(item: String): List<Chapter>

    /**
     * Should return the link to a certain timestamp of the video/audio stream associated by **item**.
     * An example for a timestamp link would be this (don't watch it though its dangerous):
     * [https://www.youtube.com/watch?v=H8ZH_mkfPUY&t=19s](https://www.youtube.com/watch?v=H8ZH_mkfPUY&t=19s)
     * If the repository, or the video does not support timestamped Links simple null should be returned.
     */
    suspend fun getTimestampLink(item: String, timestampInSeconds: Long): URL?

    /**
     * Should return the link to the video, ideally not the link to the stream itself,
     * but the webpage that hosts the video stream. If not link to the video stream's page
     * can be returned (for example because it is a local file) then null should be returned.
     */
    suspend fun getLink(item: String): URL?
}