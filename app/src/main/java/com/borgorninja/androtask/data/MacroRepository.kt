package com.borgorninja.androtask.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MacroExportBundle(
    val macro: Macro,
    val steps: List<MacroStep>
)

private val jsonCodec = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class MacroRepository(private val dao: MacroDao) {

    val allMacros: Flow<List<Macro>>                   = dao.getAllMacros()
    val allMacrosWithSteps: Flow<List<MacroWithSteps>> = dao.getAllMacrosWithSteps()
    val allTriggers: Flow<List<ScheduledTrigger>>      = dao.getAllTriggers()

    // ── CRUD ──────────────────────────────────────────────────────────────────

    suspend fun saveMacro(macro: Macro, steps: List<MacroStep>): Long {
        val id = dao.insertMacro(macro.copy(id = 0))
        if (steps.isNotEmpty())
            dao.insertSteps(steps.mapIndexed { i, s -> s.copy(id = 0, macroId = id, stepIndex = i) })
        return id
    }

    suspend fun updateMacro(macro: Macro, steps: List<MacroStep>) {
        dao.updateMacro(macro)
        dao.deleteStepsForMacro(macro.id)
        if (steps.isNotEmpty())
            dao.insertSteps(steps.mapIndexed { i, s -> s.copy(id = 0, macroId = macro.id, stepIndex = i) })
    }

    suspend fun deleteMacro(macroId: Long)                       = dao.deleteMacroById(macroId)
    suspend fun getMacroWithSteps(id: Long): MacroWithSteps?     = dao.getMacroWithSteps(id)
    fun getStepsForMacro(macroId: Long): Flow<List<MacroStep>>   = dao.getStepsForMacro(macroId)

    // Fix #4: enable/disable toggle
    suspend fun setMacroEnabled(id: Long, enabled: Boolean)      = dao.setEnabled(id, enabled)

    // ── Scheduling ────────────────────────────────────────────────────────────

    suspend fun addTrigger(trigger: ScheduledTrigger): Long           = dao.insertTrigger(trigger)
    suspend fun removeTrigger(id: Long)                               = dao.deleteTrigger(id)
    suspend fun updateTrigger(trigger: ScheduledTrigger)              = dao.updateTrigger(trigger)
    suspend fun getEnabledTriggers(): List<ScheduledTrigger>          = dao.getEnabledTriggers()
    fun getTriggersForMacro(macroId: Long): Flow<List<ScheduledTrigger>> =
        dao.getTriggersForMacro(macroId)

    // ── Export / Import ───────────────────────────────────────────────────────

    fun exportToJson(macro: Macro, steps: List<MacroStep>): String =
        jsonCodec.encodeToString(MacroExportBundle(
            macro.copy(id = 0),
            steps.map { it.copy(id = 0, macroId = 0) }
        ))

    fun exportAllToJson(all: List<MacroWithSteps>): String =
        jsonCodec.encodeToString(all.map { mws ->
            MacroExportBundle(
                mws.macro.copy(id = 0),
                mws.steps.map { it.copy(id = 0, macroId = 0) }
            )
        })

    suspend fun importFromJson(jsonStr: String): Long {
        val bundle = jsonCodec.decodeFromString<MacroExportBundle>(jsonStr)
        return saveMacro(bundle.macro.copy(id = 0), bundle.steps)
    }

    suspend fun importAllFromJson(jsonStr: String): Int {
        val bundles = jsonCodec.decodeFromString<List<MacroExportBundle>>(jsonStr)
        bundles.forEach { saveMacro(it.macro.copy(id = 0), it.steps) }
        return bundles.size
    }
}
