package com.borgorninja.androtask.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {

    // ── Macros ────────────────────────────────────────────────────────────────
    @Query("SELECT * FROM macros ORDER BY createdAt DESC")
    fun getAllMacros(): Flow<List<Macro>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getMacroById(id: Long): Macro?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacro(macro: Macro): Long

    @Update
    suspend fun updateMacro(macro: Macro)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteMacroById(id: Long)

    // Fix #4: toggle isEnabled without a full update
    @Query("UPDATE macros SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    // ── Steps ─────────────────────────────────────────────────────────────────
    @Query("SELECT * FROM macro_steps WHERE macroId = :macroId ORDER BY stepIndex ASC")
    fun getStepsForMacro(macroId: Long): Flow<List<MacroStep>>

    @Query("SELECT * FROM macro_steps WHERE macroId = :macroId ORDER BY stepIndex ASC")
    suspend fun getStepsListForMacro(macroId: Long): List<MacroStep>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: MacroStep): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<MacroStep>)

    @Query("DELETE FROM macro_steps WHERE macroId = :macroId")
    suspend fun deleteStepsForMacro(macroId: Long)

    // ── MacroWithSteps (JOIN) ─────────────────────────────────────────────────
    @Transaction
    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getMacroWithSteps(id: Long): MacroWithSteps?

    @Transaction
    @Query("SELECT * FROM macros ORDER BY createdAt DESC")
    fun getAllMacrosWithSteps(): Flow<List<MacroWithSteps>>

    // ── Scheduled triggers ────────────────────────────────────────────────────
    @Query("SELECT * FROM scheduled_triggers ORDER BY hourOfDay ASC, minute ASC")
    fun getAllTriggers(): Flow<List<ScheduledTrigger>>

    @Query("SELECT * FROM scheduled_triggers WHERE macroId = :macroId")
    fun getTriggersForMacro(macroId: Long): Flow<List<ScheduledTrigger>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrigger(trigger: ScheduledTrigger): Long

    @Update
    suspend fun updateTrigger(trigger: ScheduledTrigger)

    @Query("DELETE FROM scheduled_triggers WHERE id = :id")
    suspend fun deleteTrigger(id: Long)

    @Query("SELECT * FROM scheduled_triggers WHERE isEnabled = 1")
    suspend fun getEnabledTriggers(): List<ScheduledTrigger>
}
