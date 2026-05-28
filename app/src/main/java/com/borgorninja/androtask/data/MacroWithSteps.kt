package com.borgorninja.androtask.data

import androidx.room.Embedded
import androidx.room.Relation

data class MacroWithSteps(
    @Embedded val macro: Macro,
    @Relation(
        parentColumn = "id",
        entityColumn  = "macroId"
    )
    val steps: List<MacroStep>
)
