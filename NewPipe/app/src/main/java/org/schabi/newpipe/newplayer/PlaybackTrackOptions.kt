// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.util.ListHelper

/** Source-level alternatives that cannot be exposed as in-manifest Media3 tracks. */
internal object PlaybackTrackOptions {
    fun show(activity: AppCompatActivity, runtime: NewPlayerRuntime) {
        val exo = runtime.player.exoPlayer.value ?: return
        val item = exo.currentMediaItem ?: return
        val entry = QueueMediaRepository.request(item)?.entry ?: return
        val info = entry.info ?: return
        val allAudios = ListHelper.getPlayableStreams(info.audioStreams, info.serviceId).filter(NetworkStreamSelector::isProgressive)
        val videos = ListHelper.getPlayableStreams(info.videoStreams.orEmpty() + info.videoOnlyStreams.orEmpty(), info.serviceId)
            .filter { NetworkStreamSelector.isProgressive(it) && (!it.isVideoOnly || allAudios.isNotEmpty()) }
        val audioOnly = NetworkStreamSelector.isAudio(info.streamType)
        val audios = if (audioOnly || videos.any { it.isVideoOnly }) allAudios else emptyList()
        val choices = videos.map { it.resolution + " · " + (it.format?.name ?: "video") } +
            audios.map { (it.audioTrackName ?: it.audioLocale?.displayName ?: "Audio") + " · " + (it.averageBitrate.toString() + " kbps") }
        if (choices.isEmpty()) return
        val session = NewPlayerRuntime.snapshot()
        AlertDialog.Builder(activity).setTitle(org.schabi.newpipe.R.string.slooopipe_tracks)
            .setItems(choices.toTypedArray()) { _, index ->
                if (NewPlayerRuntime.snapshot() != session || exo.currentMediaItem?.mediaId != item.mediaId) return@setItems
                if (index < videos.size) {
                    entry.videoChoice = NetworkStreamSelector.identity(videos[index])
                    if (!videos[index].isVideoOnly) entry.audioChoice = null
                } else {
                    entry.audioChoice = NetworkStreamSelector.identity(audios[index - videos.size])
                }
                // Keep the same queue entry, index, position and user play/pause intent.
                entry.startPosition = exo.currentPosition
                entry.resumePending = false
                val position = exo.currentPosition
                val playing = exo.playWhenReady
                exo.stop()
                exo.seekTo(exo.currentMediaItemIndex, position)
                exo.prepare()
                exo.playWhenReady = playing
            }.show()
    }
}
