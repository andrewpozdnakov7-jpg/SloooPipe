// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

/** Local documents have no NewPipe service/detail page; this view observes the same player. */
object LocalPlaybackMini {
    @JvmStatic
    fun attach(activity: AppCompatActivity) {
        val content = activity.findViewById<View>(R.id.fragment_holder) ?: return
        val parent = content.parent as? CoordinatorLayout ?: return
        if (parent.findViewWithTag<View>(TAG) != null) return
        val runtime = NewPlayerRuntime.get(activity.application)
        val height = (64 * activity.resources.displayMetrics.density).toInt()
        val banner = LinearLayout(activity).apply {
            tag = TAG
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            // NewPipe uses AppCompat themes; colorSurface is not part of their contract.
            setBackgroundColor(ThemeHelper.resolveColorFromAttr(activity, R.attr.contrast_background_color))
        }
        val title = TextView(activity).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(height / 8, 0, height / 8, 0)
            contentDescription = activity.getString(R.string.slooopipe_mode_full)
            setOnClickListener { NewPlayerActivity.showActive(activity, false) }
        }
        val toggle = Button(activity).apply {
            setOnClickListener {
                if (runtime.player.playWhenReady) runtime.player.pause() else runtime.player.play()
            }
        }
        val close = Button(activity).apply {
            setText(R.string.close)
            setOnClickListener { runtime.releasePlayback() }
        }
        banner.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
        banner.addView(toggle, LinearLayout.LayoutParams(-2, -1))
        banner.addView(close, LinearLayout.LayoutParams(-2, -1))
        parent.addView(banner, CoordinatorLayout.LayoutParams(-1, height).apply { gravity = Gravity.BOTTOM })
        val originalMargin = (content.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        var showing = false
        fun render() {
            val item = runtime.player.currentlyPlaying.value
            val local = runtime.localUri != null && item != null && QueueMediaRepository.request(item) == null
            title.text = item?.mediaMetadata?.title
            toggle.setText(if (runtime.player.playWhenReady) R.string.pause else R.string.play)
            if (showing == local) return
            showing = local
            banner.visibility = if (local) View.VISIBLE else View.GONE
            (content.layoutParams as ViewGroup.MarginLayoutParams).let {
                it.bottomMargin = originalMargin + if (local) height else 0
                content.layoutParams = it
            }
            if (local) {
                // An old network detail sheet must not remain as the local document's mini-player.
                activity.findViewById<View>(R.id.fragment_player_holder)?.let {
                    BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_HIDDEN
                }
            }
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { runtime.player.currentlyPlaying.collect { render() } }
                launch { runtime.player.onPlayerEvent.collect { render() } }
            }
        }
    }

    private const val TAG = "slooopipe.localMini"
}
