// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.PermissionHelper

/** Kotlin entry point for Java screens. Authorize first; all playback mutations follow it. */
object PlaybackRequests {
    @JvmStatic
    @JvmOverloads
    fun play(context: Context, source: PlayQueue, playing: Boolean, mode: PlaybackPresentation = PlaybackPresentation.VIDEO, info: StreamInfo? = null, fullscreen: Boolean = false) {
        if (source.isEmpty) return
        if (mode == PlaybackPresentation.POPUP && !PermissionHelper.isPopupEnabledElseAsk(context)) return
        // Freeze the requested order while a confirmation dialog is on screen.
        val requested = try {
            QueuePlayback.copy(source)
        } catch (error: Exception) {
            net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.queue_copy", error)
            android.widget.Toast.makeText(context, R.string.slooopipe_newplayer_error, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        PlaybackConsent.request(
            context,
            requested,
            Runnable {
                val runtime = NewPlayerRuntime.get(context.applicationContext as Application)
                val fragment = (context as? AppCompatActivity)?.supportFragmentManager?.findFragmentById(R.id.fragment_player_holder)
                if (fragment is VideoDetailFragment) fragment.prepareForNewPlayer() else PlayerHolder.getInstance().stopService()
                runtime.startNetwork(requested, info, playing, mode)
                when (mode) {
                    PlaybackPresentation.VIDEO, PlaybackPresentation.PIP -> NewPlayerActivity.showActive(context, playing, fullscreen, mode == PlaybackPresentation.PIP)
                    PlaybackPresentation.AUDIO -> Unit
                    PlaybackPresentation.POPUP -> context.startService(Intent(context, NewPlayerPopupService::class.java))
                }
            }
        )
    }

    @JvmStatic
    fun timestamp(context: Context, serviceId: Int, url: String, seconds: Int) {
        val runtime = NewPlayerRuntime.get(context.applicationContext as Application)
        val millis = seconds.coerceAtLeast(0).toLong() * 1000
        val index = runtime.player.playlist.value.indexOfFirst {
            QueueMediaRepository.request(it)?.entry?.reference?.let { ref -> ref.serviceId == serviceId && ref.url == url } == true
        }
        val exo = runtime.player.exoPlayer.value
        if (index >= 0 && exo != null) {
            QueueMediaRepository.request(exo.getMediaItemAt(index))?.entry?.resumePending = false
            exo.seekTo(index, millis)
            exo.play()
            return
        }
        val reference = org.schabi.newpipe.extractor.stream.StreamInfoItem(serviceId, url, context.getString(R.string.slooopipe_newplayer_title), org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM)
        val queue = org.schabi.newpipe.player.playqueue.SinglePlayQueue(reference)
        queue.setRecovery(0, millis)
        play(context, queue, true, PlaybackPresentation.POPUP)
    }

    @JvmStatic
    fun enqueue(context: Context, source: PlayQueue, next: Boolean, mode: PlaybackPresentation) {
        if (NewPlayerRuntime.enqueueIfActive(source, next)) return
        play(context, source, false, mode)
    }
}
