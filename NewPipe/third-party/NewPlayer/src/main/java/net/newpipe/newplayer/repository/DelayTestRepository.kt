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
import kotlinx.coroutines.delay
import net.newpipe.newplayer.data.Chapter
import java.net.URL
import kotlin.time.Duration

/**
 * This is a meta repository implementation meant for testing. You can give it an actual repository
 * and a delay, each request to your actual repository will then be delayed. This can be used to
 * test a slow network.
 *
 * @param actualRepo your actual MediaRepository
 * @param requestDelay depicts how long each request should be delayed before being executed.
 */
class DelayTestRepository(val actualRepo: MediaRepository, var requestDelay: Duration) : MediaRepository {
    override suspend fun getMetaInfo(item: String): MediaMetadata {
        delay(requestDelay)
        return actualRepo.getMetaInfo(item)
    }

    override suspend fun getMediaItem(item: String): MediaItem {
        delay(requestDelay)
        return actualRepo.getMediaItem(item)
    }

    override suspend fun getPreviewThumbnail(item: String, timestampInMs: Long): Bitmap? {
        delay(requestDelay)
        return actualRepo.getPreviewThumbnail(item, timestampInMs)
    }

    override suspend fun getPreviewThumbnailsInfo(item: String): MediaRepository.PreviewThumbnailsInfo {
        delay(requestDelay)
        return actualRepo.getPreviewThumbnailsInfo(item)
    }

    override suspend fun getChapters(item: String): List<Chapter> {
        delay(requestDelay)
        return actualRepo.getChapters(item)
    }

    override suspend fun getTimestampLink(item: String, timestampInSeconds: Long): URL? {
        delay(requestDelay)
        return actualRepo.getTimestampLink(item, timestampInSeconds)
    }

    override suspend fun getLink(item: String): URL? {
        delay(requestDelay)
        return actualRepo.getLink(item)
    }
}