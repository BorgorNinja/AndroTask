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
        var instance: MacroAccessibilityService? = null
            private set
        var isRecording = false
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

    fun playSteps(steps: List<MacroStep>, loopCount: Int, loopDelay: Long) {
        serviceScope.launch {
            repeat(loopCount) {
                for (step in steps.sortedBy { it.stepIndex }) {
                    delay(step.delayBefore)
                    dispatchStep(step)
                }
                if (loopCount > 1) delay(loopDelay)
            }
        }
    }

    private suspend fun dispatchStep(step: MacroStep) {
        if (step.type == StepType.WAIT) {
            delay(step.duration)
            return
        }

        suspendCancellableCoroutine { cont ->
            val gestureBuilder = GestureDescription.Builder()

            when (step.type) {
                StepType.TAP, StepType.LONG_PRESS -> {
                    val path = Path().apply { moveTo(step.x, step.y) }
                    val dur = if (step.type == StepType.LONG_PRESS) 600L else step.duration
                    gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, dur))
                }
                StepType.SWIPE -> {
                    val path = Path().apply {
                        moveTo(step.x, step.y)
                        lineTo(step.x2, step.y2)
                    }
                    gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, step.duration))
                }
                StepType.PINCH -> {
                    val p1 = Path().apply { moveTo(step.x, step.y); lineTo(step.x2, step.y2) }
                    val p2 = Path().apply { moveTo(step.x2, step.y2); lineTo(step.x, step.y) }
                    gestureBuilder.addStroke(GestureDescription.StrokeDescription(p1, 0, step.duration))
                    gestureBuilder.addStroke(GestureDescription.StrokeDescription(p2, 0, step.duration))
                }
                StepType.WAIT -> { /* unreachable */ }
            }

            dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = cont.resume(Unit)
                override fun onCancelled(gestureDescription: GestureDescription?) = cont.resume(Unit)
            }, null)
        }
    }
}
