package com.borgorninja.androtask

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: MacroAccessibilityService? = null
            private set

        /** True while the floating overlay is capturing touches */
        @Volatile var isRecording: Boolean = false

        /** Raw steps accumulated during a live recording session */
        val recordedSteps = mutableListOf<MacroStep>()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    fun playSteps(steps: List<MacroStep>, loopCount: Int, loopDelay: Long) {
        serviceScope.launch {
            repeat(loopCount) { iter ->
                for (step in steps.sortedBy { it.stepIndex }) {
                    delay(step.delayBefore)
                    dispatchStep(step)
                }
                if (iter < loopCount - 1) delay(loopDelay)
            }
        }
    }

    /** Replay a single recorded step immediately (used during live recording). */
    fun replayStep(step: MacroStep) {
        serviceScope.launch { dispatchStep(step) }
    }

    // ── Internal gesture dispatch ─────────────────────────────────────────────

    private suspend fun dispatchStep(step: MacroStep) {
        if (step.type == StepType.WAIT) { delay(step.duration); return }

        suspendCancellableCoroutine { cont ->
            val builder = GestureDescription.Builder()

            when (step.type) {
                StepType.TAP, StepType.LONG_PRESS -> {
                    val path = Path().apply { moveTo(step.x, step.y) }
                    val dur  = if (step.type == StepType.LONG_PRESS) maxOf(step.duration, 600L) else step.duration
                    builder.addStroke(GestureDescription.StrokeDescription(path, 0, dur))
                }
                StepType.SWIPE -> {
                    val path = Path().apply { moveTo(step.x, step.y); lineTo(step.x2, step.y2) }
                    builder.addStroke(GestureDescription.StrokeDescription(path, 0, step.duration))
                }
                StepType.PINCH -> {
                    val p1 = Path().apply { moveTo(step.x, step.y); lineTo(step.x2, step.y2) }
                    val p2 = Path().apply { moveTo(step.x2, step.y2); lineTo(step.x, step.y) }
                    builder.addStroke(GestureDescription.StrokeDescription(p1, 0, step.duration))
                    builder.addStroke(GestureDescription.StrokeDescription(p2, 0, step.duration))
                }
                StepType.WAIT -> { /* unreachable */ }
            }

            dispatchGesture(
                builder.build(),
                object : GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) = cont.resume(Unit)
                    override fun onCancelled(g: GestureDescription?) = cont.resume(Unit)
                },
                null
            )
        }
    }
}
