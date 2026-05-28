package com.borgorninja.androtask

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.provider.Settings
import android.view.*
import androidx.core.app.NotificationCompat
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType
import kotlin.math.hypot

class FloatingOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var bubbleView: View?   = null
    private var overlayView: View?  = null
    private var isRecording = false

    // gesture tracking
    private var downX = 0f; private var downY = 0f
    private var downTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification(false))
        if (Settings.canDrawOverlays(this)) showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(CMD)) {
            CMD_START  -> if (!isRecording) startRecording()
            CMD_STOP   -> if (isRecording)  stopRecording()
            CMD_TOGGLE -> if (isRecording)  stopRecording() else startRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeBubble(); removeOverlay()
        super.onDestroy()
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private fun buildNotification(recording: Boolean): Notification {
        val tapIntent = Intent(this, FloatingOverlayService::class.java)
            .putExtra(CMD, CMD_TOGGLE)
        val pi = PendingIntent.getService(this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (recording) "Recording…" else "AndroTask ready")
            .setContentText(if (recording) "Tap notification to stop" else "Floating bubble active")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(isRecording))
    }

    // ── Floating bubble ───────────────────────────────────────────────────────

    private fun showBubble() {
        val view = BubbleView(this)
        val lp = baseLayoutParams(BUBBLE_SIZE, BUBBLE_SIZE).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16; y = 300
        }
        wm.addView(view, lp)
        bubbleView = view

        var startX = 0f; var startY = 0f
        var lastX  = 0f; var lastY  = 0f
        var dragging = false

        view.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.rawX; startY = ev.rawY
                    lastX  = ev.rawX; lastY  = ev.rawY
                    dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - lastX; val dy = ev.rawY - lastY
                    if (!dragging && hypot(ev.rawX - startX, ev.rawY - startY) > 12f) dragging = true
                    if (dragging) {
                        lp.x += dx.toInt(); lp.y += dy.toInt()
                        wm.updateViewLayout(view, lp)
                        lastX = ev.rawX; lastY = ev.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) view.performClick()
                    true
                }
                else -> false
            }
        }
        view.setOnClickListener { if (isRecording) stopRecording() else startRecording() }
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { wm.removeView(it) }; bubbleView = null }
    }

    // ── Recording overlay ─────────────────────────────────────────────────────

    private fun startRecording() {
        isRecording = true
        MacroAccessibilityService.isRecording = true
        MacroAccessibilityService.recordedSteps.clear()
        bubbleView?.invalidate()
        refreshNotification()
        showOverlay()
    }

    private fun stopRecording() {
        isRecording = false
        MacroAccessibilityService.isRecording = false
        bubbleView?.invalidate()
        refreshNotification()
        removeOverlay()
    }

    private fun showOverlay() {
        if (overlayView != null || !Settings.canDrawOverlays(this)) return

        val view = object : View(this) {
            private val tintPaint = Paint().apply { color = Color.argb(25, 220, 0, 0) }
            private val borderPaint = Paint().apply {
                color = Color.argb(180, 220, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
            override fun onDraw(c: Canvas) {
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
                c.drawRect(4f, 4f, width - 4f, height - 4f, borderPaint)
            }
            override fun onTouchEvent(ev: MotionEvent): Boolean {
                handleRecordTouch(ev); return true
            }
        }

        val lp = baseLayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        wm.addView(view, lp)
        overlayView = view
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { wm.removeView(it) }; overlayView = null }
    }

    // ── Touch → step conversion ───────────────────────────────────────────────

    private fun handleRecordTouch(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val endX     = ev.x; val endY = ev.y
                val duration = System.currentTimeMillis() - downTime
                val dist     = hypot(endX - downX, endY - downY)

                val step = when {
                    dist > 40f -> MacroStep(
                        macroId = 0, stepIndex = MacroAccessibilityService.recordedSteps.size,
                        type = StepType.SWIPE,
                        x = downX, y = downY, x2 = endX, y2 = endY,
                        duration = duration.coerceIn(50, 3000)
                    )
                    duration >= 500 -> MacroStep(
                        macroId = 0, stepIndex = MacroAccessibilityService.recordedSteps.size,
                        type = StepType.LONG_PRESS,
                        x = downX, y = downY,
                        duration = duration.coerceIn(600, 5000)
                    )
                    else -> MacroStep(
                        macroId = 0, stepIndex = MacroAccessibilityService.recordedSteps.size,
                        type = StepType.TAP,
                        x = downX, y = downY,
                        duration = 100L
                    )
                }

                MacroAccessibilityService.recordedSteps.add(step)
                // Forward the gesture to the underlying app via accessibility service
                MacroAccessibilityService.instance?.replayStep(step)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun baseLayoutParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    /** Custom drawn circle bubble */
    private inner class BubbleView(context: android.content.Context) : View(context) {
        private val fillPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            textSize = 36f; typeface = Typeface.DEFAULT_BOLD
        }

        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) - 6f
            fillPaint.color = if (isRecording) Color.argb(220, 220, 50, 50)
                              else             Color.argb(220, 33, 150, 243)
            c.drawCircle(cx, cy, r, fillPaint)
            c.drawCircle(cx, cy, r, strokePaint)
            c.drawText(if (isRecording) "■" else "⏺", cx, cy + 12f, textPaint)
        }
    }

    companion object {
        private const val NOTIF_ID   = 1337
        private const val BUBBLE_SIZE = 150
        const val CMD        = "command"
        const val CMD_START  = "start_record"
        const val CMD_STOP   = "stop_record"
        const val CMD_TOGGLE = "toggle"
    }
}
