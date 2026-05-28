package com.borgorninja.androtask.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class StepType {
    TAP, LONG_PRESS, SWIPE, PINCH, WAIT
}

@Serializable
@Entity(
    tableName = "macro_steps",
    foreignKeys = [ForeignKey(
        entity = Macro::class,
        parentColumns = ["id"],
        childColumns = ["macroId"],
        onDelete = ForeignKey.CASCADE
    )]
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
    val duration: Long = 100L,
    val delayBefore: Long = 0L
)
