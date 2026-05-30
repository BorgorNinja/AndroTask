package com.borgorninja.androtask

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.random.Random

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: MacroAccessibilityService? = null
            private set

        // Recording state
        @Volatile var isRecording: Boolean = false
        @Volatile var recordingMode: RecordingMode = RecordingMode.PASSIVE
        val recordedSteps = mutableListOf<MacroStep>()

        // Playback state (observable by UI)
        @Volatile var isPlaying: Boolean = false

        enum class RecordingMode { PASSIVE, OVERLAY }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private val scrollPositions = HashMap<String, Pair<Int, Int>>()

    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ── Passive recording via accessibility events ────────────────────────────
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRecording || recordingMode != RecordingMode.PASSIVE) return
        val ev  = event ?: return
        val src = ev.source ?: return
        val bounds = Rect()
        src.getBoundsInScreen(bounds)
        if (bounds.isEmpty) { src.recycle(); return }

        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        when (ev.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                addPassiveStep(MacroStep(macroId=0, stepIndex=recordedSteps.size,
                    type=StepType.TAP, x=cx, y=cy, duration=120L))

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ->
                addPassiveStep(MacroStep(macroId=0, stepIndex=recordedSteps.size,
                    type=StepType.LONG_PRESS, x=cx, y=cy, duration=650L))

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val key  = "${ev.windowId}:${ev.className}"
                val prev = scrollPositions[key]
                val curX = ev.scrollX; val curY = ev.scrollY
                val (prevX, prevY) = prev ?: Pair(curX, curY)
                scrollPositions[key] = Pair(curX, curY)

                val dy = if (Build.VERSION.SDK_INT >= 28) ev.scrollDeltaY else curY - prevY
                val dx = if (Build.VERSION.SDK_INT >= 28) ev.scrollDeltaX else curX - prevX
                if (abs(dy) < 1 && abs(dx) < 1) { src.recycle(); return }

                addPassiveStep(MacroStep(macroId=0, stepIndex=recordedSteps.size,
                    type=if (dy >= 0) StepType.SCROLL_DOWN else StepType.SCROLL_UP,
                    x=cx, y=cy, duration=300L))
            }
        }
        src.recycle()
    }

    private fun addPassiveStep(step: MacroStep) {
        val last = recordedSteps.lastOrNull()
        if (last != null && last.type == step.type &&
            abs(last.x - step.x) < 5f && abs(last.y - step.y) < 5f) return
        recordedSteps.add(step)
    }

    // ── Playback ──────────────────────────────────────────────────────────────
    fun playSteps(
        steps: List<MacroStep>,
        loopCount: Int,
        loopDelay: Long,
        speedMultiplier: Float = 1.0f,
        recordedWidth: Int     = 0,
        recordedHeight: Int    = 0,
        startFromIndex: Int    = 0
    ) {
        scope.launch {
            acquireWakeLock()
            isPlaying = true
            try {
                // Fix #1: deprecated defaultDisplay
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val (screenW, screenH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val b = wm.currentWindowMetrics.bounds
                    Pair(b.width().toFloat(), b.height().toFloat())
                } else {
                    @Suppress("DEPRECATION")
                    val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
                    Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
                }

                val scaleX = if (recordedWidth  > 0) screenW / recordedWidth  else 1f
                val scaleY = if (recordedHeight > 0) screenH / recordedHeight else 1f
                val sp     = speedMultiplier.coerceIn(0.1f, 10f)
                val ordered = steps.sortedBy { it.stepIndex }.drop(startFromIndex)
                val loops   = if (loopCount < 0) Int.MAX_VALUE else loopCount

                repeat(loops) { iter ->
                    for (step in ordered) {
                        if (!isPlaying) return@launch
                        val jitter  = if (step.jitter > 0) Random.nextLong(-step.jitter, step.jitter + 1) else 0L
                        val delayMs = ((step.delayBefore + jitter).coerceAtLeast(0L) / sp).toLong()
                        if (delayMs > 0) delay(delayMs)
                        dispatchStep(step, scaleX, scaleY, sp)
                    }
                    if (iter < loops - 1 && loopDelay > 0) delay((loopDelay / sp).toLong())
                }
            } finally {
                isPlaying = false
                releaseWakeLock()
            }
        }
    }

    fun stopPlayback() { isPlaying = false }

    fun replayStep(step: MacroStep) { scope.launch { dispatchStep(step, 1f, 1f, 1f) } }

    // ── Gesture dispatch ──────────────────────────────────────────────────────
    private suspend fun dispatchStep(step: MacroStep, sx: Float, sy: Float, sp: Float) {
        val dur = (step.duration / sp).toLong().coerceAtLeast(50L)

        when (step.type) {

            StepType.WAIT -> delay(dur)

            StepType.TAP, StepType.LONG_PRESS -> {
                val path    = Path().apply { moveTo(step.x * sx, step.y * sy) }
                val realDur = if (step.type == StepType.LONG_PRESS) maxOf(dur, 600L) else dur
                dispatchGestureAwait(GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, realDur)).build())
            }

            StepType.SWIPE -> {
                val path = Path().apply {
                    moveTo(step.x * sx, step.y * sy); lineTo(step.x2 * sx, step.y2 * sy)
                }
                dispatchGestureAwait(GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build())
            }

            StepType.PINCH -> {
                val p1 = Path().apply { moveTo(step.x*sx, step.y*sy); lineTo(step.x2*sx, step.y2*sy) }
                val p2 = Path().apply { moveTo(step.x2*sx, step.y2*sy); lineTo(step.x*sx, step.y*sy) }
                dispatchGestureAwait(GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(p1, 0, dur))
                    .addStroke(GestureDescription.StrokeDescription(p2, 0, dur)).build())
            }

            StepType.SCROLL_UP, StepType.SCROLL_DOWN -> {
                val node   = findScrollableAt(step.x * sx, step.y * sy)
                val action = if (step.type == StepType.SCROLL_DOWN)
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                if (node != null && node.performAction(action)) {
                    node.recycle(); delay(200)
                } else {
                    node?.recycle()
                    val y1 = if (step.type == StepType.SCROLL_DOWN) step.y*sy+400f else step.y*sy-400f
                    val y2 = if (step.type == StepType.SCROLL_DOWN) step.y*sy-400f else step.y*sy+400f
                    dispatchGestureAwait(GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(
                            Path().apply { moveTo(step.x*sx, y1); lineTo(step.x*sx, y2) }, 0, 300L))
                        .build())
                }
            }

            StepType.BACK          -> { performGlobalAction(GLOBAL_ACTION_BACK);          delay(200) }
            StepType.HOME          -> { performGlobalAction(GLOBAL_ACTION_HOME);          delay(300) }
            StepType.RECENTS       -> { performGlobalAction(GLOBAL_ACTION_RECENTS);       delay(300) }
            StepType.NOTIFICATIONS -> { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); delay(300) }

            StepType.VOLUME_UP, StepType.VOLUME_DOWN -> {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.adjustVolume(
                    if (step.type == StepType.VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                delay(100)
            }

            StepType.TYPE_TEXT -> {
                val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (node != null) {
                    val args = Bundle().apply {
                        putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    node.recycle()
                }
                delay(150)
            }
        }
    }

    private fun findScrollableAt(x: Float, y: Float): AccessibilityNodeInfo? {
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            val b = Rect(); node.getBoundsInScreen(b)
            if (b.contains(x.toInt(), y.toInt()) && node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val r = search(node.getChild(i)); if (r != null) return r
            }
            return null
        }
        return search(rootInActiveWindow)
    }

    private suspend fun dispatchGestureAwait(gesture: GestureDescription) =
        suspendCancellableCoroutine { cont ->
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) = cont.resume(Unit)
                override fun onCancelled(g: GestureDescription?) = cont.resume(Unit)
            }, null)
        }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AndroTask::Playback"
        ).apply { acquire(30 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }
}
