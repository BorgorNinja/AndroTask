package com.borgorninja.androtask.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borgorninja.androtask.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MacroViewModel(app: Application) : AndroidViewModel(app) {

    private val db   = AppDatabase.getDatabase(app)
    private val repo = MacroRepository(db.macroDao())

    // ── Exposed state ─────────────────────────────────────────────────────────

    val macros: StateFlow<List<Macro>> = repo.allMacros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val macrosWithSteps: StateFlow<List<MacroWithSteps>> = repo.allMacrosWithSteps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val triggers: StateFlow<List<ScheduledTrigger>> = repo.allTriggers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentMacro = MutableStateFlow<MacroWithSteps?>(null)
    val currentMacro: StateFlow<MacroWithSteps?> = _currentMacro

    // ── Macro CRUD ────────────────────────────────────────────────────────────

    fun loadMacro(id: Long) {
        viewModelScope.launch {
            _currentMacro.value = if (id == 0L) null else repo.getMacroWithSteps(id)
        }
    }

    fun saveMacro(
        name: String,
        description: String,
        loopCount: Int,
        loopDelay: Long,
        steps: List<MacroStep>,
        macroId: Long = 0L,
        onDone: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val macro = Macro(
                id          = macroId,
                name        = name,
                description = description,
                loopCount   = loopCount,
                loopDelay   = loopDelay
            )
            val id = if (macroId == 0L) {
                repo.saveMacro(macro, steps)
            } else {
                repo.updateMacro(macro, steps)
                macroId
            }
            onDone(id)
        }
    }

    fun deleteMacro(macroId: Long) {
        viewModelScope.launch { repo.deleteMacro(macroId) }
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    fun addTrigger(trigger: ScheduledTrigger, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch { onDone(repo.addTrigger(trigger)) }
    }

    fun removeTrigger(triggerId: Long) {
        viewModelScope.launch { repo.removeTrigger(triggerId) }
    }

    fun getTriggersForMacro(macroId: Long): Flow<List<ScheduledTrigger>> =
        repo.getTriggersForMacro(macroId)

    // ── Export / Import ───────────────────────────────────────────────────────

    fun exportMacroJson(macroId: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val mws = repo.getMacroWithSteps(macroId) ?: return@launch
            onResult(repo.exportToJson(mws.macro, mws.steps))
        }
    }

    fun exportAllJson(onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(repo.exportAllToJson(macrosWithSteps.value)) }
    }

    fun importJson(json: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val count = try {
                repo.importAllFromJson(json)          // try list format first
            } catch (_: Exception) {
                try { repo.importFromJson(json); 1 }  // fall back to single
                catch (_: Exception) { 0 }
            }
            onDone(count)
        }
    }
}
