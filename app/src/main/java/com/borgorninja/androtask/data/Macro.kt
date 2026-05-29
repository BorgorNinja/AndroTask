package com.borgorninja.androtask.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "macros")
data class Macro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val loopCount: Int = 1,             // -1 = infinite
    val loopDelay: Long = 0L,
    val isEnabled: Boolean = true,
    val speedMultiplier: Float = 1.0f,  // 0.25 – 4.0
    val recordedWidth: Int = 0,         // 0 = no scaling applied
    val recordedHeight: Int = 0
)
