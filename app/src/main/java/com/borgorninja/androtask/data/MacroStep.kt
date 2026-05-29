package com.borgorninja.androtask.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class StepType {
    // Touch gestures
    TAP, LONG_PRESS, SWIPE, PINCH, WAIT,
    // Scroll (dispatched via AccessibilityNodeInfo actions)
    SCROLL_UP, SCROLL_DOWN,
    // System / global actions
    BACK, HOME, RECENTS, NOTIFICATIONS,
    VOLUME_UP, VOLUME_DOWN,
    // Text injection
    TYPE_TEXT
}

@Serializable
@Entity(
    tableName = "macro_steps",
    foreignKeys = [ForeignKey(
        entity        = Macro::class,
        parentColumns = ["id"],
        childColumns  = ["macroId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("macroId")]
)
data class MacroStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macroId: Long,
    val stepIndex: Int,
    val type: StepType,
    val x: Float = 0f,
    val y: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val duration: Long  = 100L,
    val delayBefore: Long = 0L,
    val text: String  = "",    // used by TYPE_TEXT
    val label: String = "",    // optional user comment
    val jitter: Long  = 0L    // random ±jitter ms added to delayBefore
)
