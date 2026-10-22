// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.newplayer.datasource.YoutubeHttpDataSource

/** The tag survives NewPlayer's metadata/id updates and carries only the selected streams. */
internal class NetworkMediaSourceFactory(private val context: Context, private val queue: QueueMediaRepository) : MediaSource.Factory {
    private val local = DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
    private var drmProvider: DrmSessionManagerProvider? = null
    private var errorPolicy: LoadErrorHandlingPolicy? = null

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        drmProvider = provider
        local.setDrmSessionManagerProvider(provider)
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        errorPolicy = policy
        local.setLoadErrorHandlingPolicy(policy)
    }

    override fun getSupportedTypes(): IntArray = local.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        if (QueueMediaRepository.request(mediaItem) != null) return DeferredNetworkMediaSource(mediaItem, queue, this)
        val plan = mediaItem.localConfiguration?.tag as? NetworkMediaRepository.Plan ?: return local.createMediaSource(mediaItem)
        val sources = plan.sources.mapIndexed { index, part ->
            val http = DataSource.Factory { RoutedHttpSource(part) }
            val data = DefaultDataSource.Factory(context, http)
            val factory = DefaultMediaSourceFactory(data)
            drmProvider?.let(factory::setDrmSessionManagerProvider)
            errorPolicy?.let(factory::setLoadErrorHandlingPolicy)
            // Let DefaultMediaSourceFactory attach modern parsed subtitle sources even for
            // extractor-generated DASH. Its BaseURLs are absolute; only the manifest is inline.
            val uri = part.inlineDash?.let { Uri.parse("data:application/dash+xml;base64," + Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)) } ?: part.uri
            val subtitles = if (index == 0) mediaItem.localConfiguration?.subtitleConfigurations.orEmpty() else emptyList()
            val item = mediaItem.buildUpon().setUri(uri).setMimeType(part.mime).setTag(null).setSubtitleConfigurations(subtitles).build()
            val source = factory.createMediaSource(item)
            source.addEventListener(android.os.Handler(android.os.Looper.getMainLooper()), LoadDiagnostics())
            source
        }
        Log.i(NewPlayerRuntime.TAG, "network.source_create parts=${sources.size} merged=${sources.size > 1} live=${plan.live}")
        return if (sources.size == 1) sources[0] else MergingMediaSource(true, *sources.toTypedArray())
    }

    /** YouTube's special POST/range policy applies to media segments, never to manifest hosts. */
    private class RoutedHttpSource(private val part: NetworkMediaRepository.Source) : DataSource {
        private var active: DataSource? = null
        private val listeners = mutableListOf<TransferListener>()

        override fun addTransferListener(transferListener: TransferListener) {
            listeners.add(transferListener)
            active?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val host = dataSpec.uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
            val youtubeMedia = part.youtube && (host == "googlevideo.com" || host.endsWith(".googlevideo.com")) &&
                dataSpec.uri.path.orEmpty().startsWith("/videoplayback")
            val selected = if (youtubeMedia) {
                YoutubeHttpDataSource.Factory().setRangeParameterEnabled(part.inlineDash != null).setRnParameterEnabled(true).createDataSource()
            } else {
                DefaultHttpDataSource.Factory().setUserAgent(DownloaderImpl.USER_AGENT).createDataSource()
            }
            active = selected
            listeners.forEach(selected::addTransferListener)
            Log.i(NewPlayerRuntime.TAG, "network.open host=$host youtubeMedia=$youtubeMedia position=${dataSpec.position} length=${dataSpec.length}")
            return selected.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = checkNotNull(active).read(buffer, offset, length)
        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()
        override fun close() {
            try {
                active?.close()
            } finally {
                active = null
            }
        }
    }

    private class LoadDiagnostics : MediaSourceEventListener {
        override fun onLoadCompleted(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
            Log.i(NewPlayerRuntime.TAG, "network.load_complete type=${mediaLoadData.dataType} bytes=${loadEventInfo.bytesLoaded} ms=${loadEventInfo.loadDurationMs}")
        }

        override fun onLoadError(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
            val status = (error as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            Log.e(NewPlayerRuntime.TAG, "network.load_error type=${mediaLoadData.dataType} error=${error.javaClass.simpleName} http=$status canceled=$wasCanceled bytes=${loadEventInfo.bytesLoaded}")
        }
    }
}
