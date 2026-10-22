// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.newplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue

/** No extraction, detachment, queue mutation or engine handoff occurs before authorization. */
object PlaybackConsent {
    @JvmStatic
    fun request(context: Context, requested: PlayQueue, action: Runnable) {
        authorize(context, requested.streams.map { StreamIdentity(it.serviceId, it.url) }, action)
    }

    internal fun requestLocal(context: Context, action: Runnable) = authorize(context, emptyList(), action)

    private fun authorize(context: Context, desired: List<StreamIdentity>, action: Runnable) {
        val active = currentSnapshot()
        if (!QueueReplacementPolicy.requiresConfirmation(PlayerHelper.isClearingQueueConfirmationRequired(context), active, desired)) {
            action.run()
            return
        }
        var owner = context
        while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
        val activity = owner as? Activity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.w(NewPlayerRuntime.TAG, "queue.consent_requires_activity")
            return
        }
        val consent = QueueReplacementPolicy.Consent(checkNotNull(active))
        AlertDialog.Builder(activity)
            .setTitle(R.string.clear_queue_confirmation_description)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (consent.accept(currentSnapshot())) {
                    action.run()
                } else {
                    Log.i(NewPlayerRuntime.TAG, "queue.consent_stale")
                }
            }
            .show()
    }

    private fun currentSnapshot(): PlaybackSnapshot? {
        NewPlayerRuntime.snapshot()?.let { return it }
        val legacy = PlayerHolder.getInstance().queueSnapshot ?: return null
        val ids = legacy.streams.map { System.identityHashCode(it).toString() }
        return PlaybackSnapshot(
            "legacy:" + System.identityHashCode(legacy),
            ids,
            ids.getOrNull(legacy.index),
            legacy.streams.map { StreamIdentity(it.serviceId, it.url) }
        )
    }
}
