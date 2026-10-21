// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ExoPlayer's MaskingMediaSource delays createPeriod until this source publishes a timeline. */
internal class DeferredNetworkMediaSource(
    private val item: MediaItem,
    private val repository: QueueMediaRepository,
    private val factory: NetworkMediaSourceFactory
) : CompositeMediaSource<Unit>() {
    private var child: MediaSource? = null
    private var loading: Job? = null
    private var failure: IOException? = null

    override fun getMediaItem(): MediaItem = item

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        failure = null
        val playbackHandler = Handler(checkNotNull(Looper.myLooper()))
        loading = CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolved = repository.resolve(checkNotNull(QueueMediaRepository.request(item)))
                    .buildUpon().setMediaId(item.mediaId).setMediaMetadata(item.mediaMetadata).build()
                // All CompositeMediaSource operations must run on the playback looper, not Main.
                withContext(playbackHandler.asCoroutineDispatcher()) {
                    val source = factory.createMediaSource(resolved)
                    child = source
                    prepareChildSource(Unit, source)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(playbackHandler.asCoroutineDispatcher()) {
                    failure = IOException("NEWPLAYER_QUEUE_SOURCE_FAILED", error)
                }
            }
        }
    }

    override fun onChildSourceInfoRefreshed(id: Unit, mediaSource: MediaSource, timeline: Timeline) {
        refreshSourceInfo(object : ForwardingTimeline(timeline) {
            override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window {
                super.getWindow(windowIndex, window, defaultPositionProjectionUs)
                // Preserve queue identity and metadata when the child publishes its resolved plan.
                window.mediaItem = item
                return window
            }
        })
    }

    override fun maybeThrowSourceInfoRefreshError() {
        failure?.let { throw it }
        super.maybeThrowSourceInfoRefreshError()
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod = checkNotNull(child).createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) = checkNotNull(child).releasePeriod(mediaPeriod)

    override fun releaseSourceInternal() {
        loading?.cancel()
        loading = null
        super.releaseSourceInternal()
        child = null
        failure = null
    }
}
