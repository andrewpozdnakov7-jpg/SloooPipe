// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.player.playqueue.PlayQueue

internal data class SavedFeed(val id: String, val source: PlayQueue) : Serializable
internal data class SavedSlot(val feed: String, val sourceIndex: Int) : Serializable
internal data class SavedPlayback(
    val feeds: List<SavedFeed>,
    val order: List<SavedSlot>,
    val index: Int,
    val position: Long,
    val repeat: Int,
    val shuffle: Boolean
) : Serializable

/** Private unbacked queue/feed references, including opaque extractor page cursors; no StreamInfo or auth-module credentials. */
internal class PlaybackSessionStore(context: Context) {
    private val file = AtomicFile(java.io.File(context.noBackupFilesDir, "newplayer-queue-v1.bin"))
    private val writes = Channel<SavedPlayback?>(Channel.CONFLATED)
    private val lock = kotlinx.coroutines.sync.Mutex()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (state in writes) {
                lock.lock()
                try {
                    if (state == null) {
                        file.delete()
                        continue
                    }
                    val bytes = ByteArrayOutputStream().use { buffer ->
                        ObjectOutputStream(buffer).use { it.writeObject(state) }
                        buffer.toByteArray()
                    }
                    if (bytes.size > MAX_BYTES) {
                        file.delete()
                        continue
                    }
                    val stream = file.startWrite()
                    try {
                        stream.write(bytes)
                        file.finishWrite(stream)
                    } catch (error: Exception) {
                        file.failWrite(stream)
                        throw error
                    }
                } catch (error: Exception) {
                    net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.queue_save", error)
                } finally {
                    lock.unlock()
                }
            }
        }
    }

    fun save(state: SavedPlayback?) {
        writes.trySend(state)
    }

    suspend fun read(): SavedPlayback? = withContext(Dispatchers.IO) {
        lock.lock()
        try {
            if (!file.baseFile.isFile || file.baseFile.length() > MAX_BYTES) return@withContext null
            ObjectInputStream(ByteArrayInputStream(file.readFully())).use { it.readObject() as? SavedPlayback }
                ?.takeIf { it.order.isNotEmpty() && it.index in it.order.indices && it.feeds.size <= 1000 && it.order.size <= 50_000 }
        } catch (error: Exception) {
            file.delete()
            net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.queue_restore", error)
            null
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private const val MAX_BYTES = 16L * 1024 * 1024
    }
}
