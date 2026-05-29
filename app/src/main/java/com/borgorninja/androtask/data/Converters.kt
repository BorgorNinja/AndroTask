package com.borgorninja.androtask.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStepType(value: StepType): String = value.name

    @TypeConverter
    fun toStepType(value: String): StepType =
        try { StepType.valueOf(value) } catch (_: IllegalArgumentException) { StepType.TAP }
}
