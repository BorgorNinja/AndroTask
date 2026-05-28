package com.borgorninja.androtask.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "scheduled_triggers")
data class ScheduledTrigger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macroId: Long,
    val hourOfDay: Int,
    val minute: Int,
    /** Comma-separated day numbers 1–7 (1 = Mon … 7 = Sun). Empty = run once. */
    val repeatDays: String = "1,2,3,4,5,6,7",
    val isEnabled: Boolean = true,
    val label: String = ""
)
