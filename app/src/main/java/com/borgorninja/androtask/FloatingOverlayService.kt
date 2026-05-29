package com.borgorninja.androtask

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.provider.Settings
import android.view.*
import androidx.core.app.NotificationCompat
import com.borgorninja.androtask.MacroAccessibilityService.Companion.RecordingMode
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.hypot

class FloatingOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var bubbleView:      View? = null
    private var overlayView:     View? = null
    private var coordPickerView: View? = null
    private var isRecording = false

    // Touch tracking for overlay mode
    private var downX = 0f; private var downY = 0f; private var downTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotif())
        if (Settings.canDrawOverlays(this)) showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(CMD)) {
            CMD_START      -> startRecording()
            CMD_STOP       -> stopRecording()
            CMD_TOGGLE     -> if (isRecording) stopRecording() else startRecording()
            CMD_PICK_START -> {
                val forField = intent.getStringExtra(EXTRA_PICK_FIELD) ?: FIELD_XY
                showCoordPicker(forField)
            }
            CMD_PICK_CANCEL -> hideCoordPicker()
        }
        return START_STICKY
    }

    override fun onDestroy() { removeBubble(); removeOverlay(); hideCoordPicker(); super.onDestroy() }

    // ── Notification ──────────────────────────────────────────────────────────
    private fun buildNotif(): Notification {
        val pi = PendingIntent.getService(this, 0,
            Intent(this, FloatingOverlayService::class.java).putExtra(CMD, CMD_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (isRecording) "Recording…" else "AndroTask")
            .setContentText(if (isRecording) "Tap to stop" else "Floating bubble active")
            .setContentIntent(pi).setOngoing(true).build()
    }
    private fun refreshNotif() =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotif())

    // ── Bubble ────────────────────────────────────────────────────────────────
    private fun showBubble() {
        if (bubbleView != null) return
        val view = BubbleView(this)
        val lp = baseLp(BUBBLE_SIZE, BUBBLE_SIZE).apply { gravity = Gravity.TOP or Gravity.START; x = 16; y = 320 }
        setupDrag(view, lp) { if (isRecording) stopRecording() else startRecording() }
        wm.addView(view, lp)
        bubbleView = view
    }

    /** Remove the bubble and re-add it so it sits on top of overlayView. */
    private fun bringBubbleToFront() {
        bubbleView?.let { v ->
            val lp = v.layoutParams as WindowManager.LayoutParams
            runCatching { wm.removeView(v) }
            bubbleView = null
            val fresh = BubbleView(this)
            setupDrag(fresh, lp) { if (isRecording) stopRecording() else startRecording() }
            wm.addView(fresh, lp)
            bubbleView = fresh
        }
    }

    private fun removeBubble() { bubbleView?.let { runCatching { wm.removeView(it) }; bubbleView = null } }

    // ── Recording ─────────────────────────────────────────────────────────────
    private fun startRecording() {
        if (isRecording) return
        isRecording = true
        MacroAccessibilityService.isRecording = true
        MacroAccessibilityService.recordedSteps.clear()
        bubbleView?.invalidate()
        refreshNotif()

        if (MacroAccessibilityService.recordingMode == RecordingMode.OVERLAY) {
            showOverlay()
            bringBubbleToFront()          // bubble must be above the capture overlay
        }
        // PASSIVE mode: no overlay needed – accessibility events do the work
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        MacroAccessibilityService.isRecording = false
        bubbleView?.invalidate()
        refreshNotif()
        removeOverlay()
    }

    // ── Capture overlay (OVERLAY mode) ────────────────────────────────────────
    private fun showOverlay() {
        if (overlayView != null) return
        val v = object : View(this) {
            private val tint   = Paint().apply { color = Color.argb(20, 220, 0, 0) }
            private val border = Paint().apply { color = Color.argb(160, 220, 0, 0); style = Paint.Style.STROKE; strokeWidth = 8f }
            override fun onDraw(c: Canvas) {
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tint)
                c.drawRect(4f, 4f, width - 4f, height - 4f, border)
            }
            override fun onTouchEvent(ev: MotionEvent): Boolean { handleRecordTouch(ev); return true }
        }
        wm.addView(v, baseLp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            .apply { gravity = Gravity.TOP or Gravity.START })
        overlayView = v
    }
    private fun removeOverlay() { overlayView?.let { runCatching { wm.removeView(it) }; overlayView = null } }

    private fun handleRecordTouch(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; downTime = System.currentTimeMillis() }
            MotionEvent.ACTION_UP   -> {
                val dur  = System.currentTimeMillis() - downTime
                val dist = hypot(ev.x - downX, ev.y - downY)
                val step = when {
                    dist > 40  -> MacroStep(macroId=0, stepIndex=MacroAccessibilityService.recordedSteps.size,
                                    type=StepType.SWIPE, x=downX, y=downY, x2=ev.x, y2=ev.y,
                                    duration=dur.coerceIn(50,3000))
                    dur >= 500 -> MacroStep(macroId=0, stepIndex=MacroAccessibilityService.recordedSteps.size,
                                    type=StepType.LONG_PRESS, x=downX, y=downY, duration=dur.coerceIn(600,5000))
                    else       -> MacroStep(macroId=0, stepIndex=MacroAccessibilityService.recordedSteps.size,
                                    type=StepType.TAP, x=downX, y=downY, duration=100L)
                }
                MacroAccessibilityService.recordedSteps.add(step)
                MacroAccessibilityService.instance?.replayStep(step) // pass-through to underlying app
            }
        }
    }

    // ── Coord picker ──────────────────────────────────────────────────────────
    private fun showCoordPicker(field: String) {
        if (coordPickerView != null) return
        val dm = resources.displayMetrics
        var pickX = dm.widthPixels / 2f
        var pickY = dm.heightPixels / 2f
        var lastRawX = 0f; var lastRawY = 0f

        val view = object : View(this) {
            val gridPaint = Paint().apply { color = Color.argb(40, 255,255,255); strokeWidth = 1f }
            val linePaint = Paint().apply { color = Color.argb(220, 0,200,255); strokeWidth = 2f }
            val circleFill= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60,0,200,255) }
            val circleEdge= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255,0,200,255); style=Paint.Style.STROKE; strokeWidth=3f }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=48f; textAlign=Paint.Align.CENTER
                setShadowLayer(4f,2f,2f,Color.BLACK) }
            val btnPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220,30,30,30) }
            val btnTxtPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=44f; textAlign=Paint.Align.CENTER
                setShadowLayer(3f,1f,1f,Color.BLACK) }
            val bgPaint   = Paint().apply { color = Color.argb(90,0,0,0) }

            override fun onDraw(c: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                c.drawRect(0f,0f,w,h,bgPaint)
                // grid
                var gx = 0f; while (gx < w) { c.drawLine(gx,0f,gx,h,gridPaint); gx+=100f }
                var gy = 0f; while (gy < h) { c.drawLine(0f,gy,w,gy,gridPaint); gy+=100f }
                // crosshair
                c.drawLine(pickX, 0f, pickX, h, linePaint)
                c.drawLine(0f, pickY, w, pickY, linePaint)
                c.drawCircle(pickX, pickY, 40f, circleFill)
                c.drawCircle(pickX, pickY, 40f, circleEdge)
                // coords label
                val label = "(${pickX.toInt()}, ${pickY.toInt()})"
                val labelY = if (pickY > h * 0.8f) pickY - 60f else pickY + 80f
                c.drawText(label, pickX, labelY, textPaint)
                // buttons at bottom
                val bw = 300f; val bh = 100f; val margin = 40f; val by = h - bh - margin
                c.drawRoundRect(margin, by, margin+bw, by+bh, 20f,20f, btnPaint)
                c.drawText("Cancel", margin+bw/2, by+bh*0.65f, btnTxtPaint)
                val setX = w - margin - bw
                val setBg = Paint(btnPaint).apply { color = Color.argb(220,0,140,200) }
                c.drawRoundRect(setX, by, setX+bw, by+bh, 20f,20f, setBg)
                c.drawText("Set", setX+bw/2, by+bh*0.65f, btnTxtPaint)
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                val w = width.toFloat(); val h = height.toFloat()
                val bw = 300f; val bh = 100f; val margin = 40f; val by = h - bh - margin
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> { lastRawX = ev.rawX; lastRawY = ev.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        pickX = (pickX + ev.rawX - lastRawX).coerceIn(0f, w)
                        pickY = (pickY + ev.rawY - lastRawY).coerceIn(0f, h)
                        lastRawX = ev.rawX; lastRawY = ev.rawY
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        val tx = ev.x; val ty = ev.y
                        // Cancel button
                        if (tx in margin..(margin+bw) && ty in by..(by+bh)) {
                            coordPickResult.value = null
                            hideCoordPicker()
                        }
                        // Set button
                        val setX = w - margin - bw
                        if (tx in setX..(setX+bw) && ty in by..(by+bh)) {
                            coordPickResult.value = Pair(field, Pair(pickX, pickY))
                            hideCoordPicker()
                        }
                    }
                }
                return true
            }
        }
        val lp = baseLp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            .apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(view, lp)
        coordPickerView = view
        bringBubbleToFront()
    }

    private fun hideCoordPicker() {
        coordPickerView?.let { runCatching { wm.removeView(it) }; coordPickerView = null }
    }

    // ── Drag helper ───────────────────────────────────────────────────────────
    private fun setupDrag(view: View, lp: WindowManager.LayoutParams, onClick: () -> Unit) {
        var sx = 0f; var sy = 0f; var dragging = false
        view.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { sx = ev.rawX; sy = ev.rawY; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(ev.rawX-sx, ev.rawY-sy) > 12f) dragging = true
                    if (dragging) { lp.x += (ev.rawX-sx).toInt(); lp.y += (ev.rawY-sy).toInt()
                                    wm.updateViewLayout(view, lp); sx=ev.rawX; sy=ev.rawY }
                    true
                }
                MotionEvent.ACTION_UP  -> { if (!dragging) onClick(); true }
                else -> false
            }
        }
    }

    // ── Layout params helper ──────────────────────────────────────────────────
    private fun baseLp(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    // ── Bubble view ───────────────────────────────────────────────────────────
    private inner class BubbleView(ctx: Context) : View(ctx) {
        private val fill   = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; style=Paint.Style.STROKE; strokeWidth=5f }
        private val txt    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textAlign=Paint.Align.CENTER; textSize=34f; typeface=Typeface.DEFAULT_BOLD }
        override fun onDraw(c: Canvas) {
            val cx=width/2f; val cy=height/2f; val r=minOf(cx,cy)-6f
            fill.color = if (isRecording) Color.argb(220,210,45,45) else Color.argb(220,33,150,243)
            c.drawCircle(cx,cy,r,fill); c.drawCircle(cx,cy,r,stroke)
            val count = MacroAccessibilityService.recordedSteps.size
            val label = if (isRecording && count > 0) "$count" else if (isRecording) "●" else "⏺"
            c.drawText(label, cx, cy+12f, txt)
        }
    }

    companion object {
        private const val NOTIF_ID   = 1337
        private const val BUBBLE_SIZE = 150

        const val CMD            = "command"
        const val CMD_START      = "start_record"
        const val CMD_STOP       = "stop_record"
        const val CMD_TOGGLE     = "toggle"
        const val CMD_PICK_START = "pick_start"
        const val CMD_PICK_CANCEL= "pick_cancel"
        const val EXTRA_PICK_FIELD = "pick_field"
        const val FIELD_XY       = "xy"
        const val FIELD_XY2      = "xy2"

        /** Result emitted when the user confirms a coord pick.
         *  Pair<field, Pair<x,y>>  where field is FIELD_XY or FIELD_XY2 */
        val coordPickResult = MutableStateFlow<Pair<String, Pair<Float, Float>>?>(null)
    }
}
