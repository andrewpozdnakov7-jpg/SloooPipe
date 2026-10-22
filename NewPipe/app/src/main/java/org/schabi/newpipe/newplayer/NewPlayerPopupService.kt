// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.newplayer

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import org.schabi.newpipe.R

/** A surface/controller for the existing NewPlayer session, with no second engine or session. */
class NewPlayerPopupService : Service() {
    private lateinit var runtime: NewPlayerRuntime
    private lateinit var windows: WindowManager
    private var root: View? = null
    private var video: PlayerView? = null
    private var observed: Player? = null
    private val startup = android.os.Handler(android.os.Looper.getMainLooper())
    private var attempts = 0
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.mediaItemCount == 0) stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runtime = NewPlayerRuntime.get(application)
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || runtime.presentation.value != PlaybackPresentation.POPUP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val exo = runtime.player.exoPlayer.value
        if (exo == null) {
            // Queue metadata is committed asynchronously on Main; do not race a new owner.
            if (++attempts > 50) {
                runtime.setPresentation(PlaybackPresentation.AUDIO)
                stopSelf()
                return START_NOT_STICKY
            }
            startup.postDelayed({
                if (runtime.presentation.value == PlaybackPresentation.POPUP) onStartCommand(intent, flags, startId)
            }, 100)
            return START_NOT_STICKY
        }
        if (root != null) {
            video?.player = exo
            return START_NOT_STICKY
        }
        val density = resources.displayMetrics.density
        val (availableWidth, availableHeight) = viewportSize()
        val width = (availableWidth * .75f).toInt().coerceAtMost((480 * density).toInt()).coerceAtLeast(1)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val drag = TextView(this).apply {
            setText(R.string.slooopipe_popup_drag)
            setTextColor(android.graphics.Color.WHITE)
            setPadding((12 * density).toInt(), (8 * density).toInt(), 0, (8 * density).toInt())
        }
        layout.addView(drag)
        val actions = LinearLayout(this)
        actions.addView(
            Button(this).apply {
                setText(R.string.slooopipe_mode_full)
                setOnClickListener {
                    val playing = runtime.player.playWhenReady
                    runtime.setPresentation(PlaybackPresentation.VIDEO)
                    NewPlayerActivity.showActive(this@NewPlayerPopupService, playing)
                }
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        actions.addView(
            Button(this).apply {
                setText(R.string.close)
                setOnClickListener {
                    runtime.releasePlayback()
                    stopSelf()
                }
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        layout.addView(actions)
        layout.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val bounds = PopupBounds.fit(width, availableHeight, layout.measuredHeight, (48 * density).toInt())
        video = PlayerView(this).apply {
            player = exo
            useController = true
        }
        layout.addView(video, LinearLayout.LayoutParams(-1, bounds.videoHeight))
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(width, -2, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (availableWidth - width) / 2
            y = bounds.top
        }
        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        drag.setOnTouchListener { handle, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = (originX + event.rawX - downX).toInt().coerceIn(0, (availableWidth - layout.width).coerceAtLeast(0))
                    params.y = (originY + event.rawY - downY).toInt().coerceIn(0, (availableHeight - layout.height).coerceAtLeast(0))
                    windows.updateViewLayout(layout, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    handle.performClick()
                    true
                }

                else -> false
            }
        }
        try {
            windows.addView(layout, params)
            root = layout
            observed = exo
            exo.addListener(listener)
        } catch (error: RuntimeException) {
            net.slooopipe.auth.api.AuthDiagnostics.failure("newplayer.popup_window", error)
            video?.player = null
            runtime.setPresentation(PlaybackPresentation.AUDIO)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun viewportSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windows.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            return (metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(1) to
                (metrics.bounds.height() - insets.top - insets.bottom).coerceAtLeast(1)
        }
        val size = android.graphics.Point()
        @Suppress("DEPRECATION")
        windows.defaultDisplay.getSize(size)
        return size.x.coerceAtLeast(1) to size.y.coerceAtLeast(1)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rebuild only the window after rotation; keep the same player, item and position.
        if (root != null) {
            detachWindow()
            attempts = 0
            onStartCommand(null, 0, 0)
        }
    }

    private fun detachWindow() {
        startup.removeCallbacksAndMessages(null)
        observed?.removeListener(listener)
        observed = null
        video?.player = null
        video = null
        root?.let { windows.removeView(it) }
        root = null
    }

    override fun onDestroy() {
        detachWindow()
        super.onDestroy()
    }
}
