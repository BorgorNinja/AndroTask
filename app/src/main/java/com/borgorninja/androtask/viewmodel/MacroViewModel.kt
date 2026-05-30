package com.borgorninja.androtask.viewmodel

import android.app.Application
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.borgorninja.androtask.MacroAccessibilityService
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
        name: String, description: String,
        loopCount: Int, loopDelay: Long,
        speedMultiplier: Float,
        steps: List<MacroStep>,
        macroId: Long = 0L,
        onDone: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            // Fix #1: deprecated defaultDisplay.getRealMetrics
            val wm = getApplication<Application>().getSystemService(WindowManager::class.java)
            val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.currentWindowMetrics.bounds
                Pair(b.width(), b.height())
            } else {
                @Suppress("DEPRECATION")
                val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
                Pair(dm.widthPixels, dm.heightPixels)
            }

            val macro = Macro(
                id              = macroId,
                name            = name,
                description     = description,
                loopCount       = loopCount,
                loopDelay       = loopDelay,
                speedMultiplier = speedMultiplier,
                recordedWidth   = w,
                recordedHeight  = h
            )
            val id = if (macroId == 0L) repo.saveMacro(macro, steps)
                     else { repo.updateMacro(macro, steps); macroId }
            onDone(id)
        }
    }

    fun deleteMacro(id: Long) { viewModelScope.launch { repo.deleteMacro(id) } }

    // Fix #4: toggle enabled
    fun setMacroEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repo.setMacroEnabled(id, enabled) }
    }

    // ── Playback ──────────────────────────────────────────────────────────────
    fun playMacro(macroId: Long, startFromStep: Int = 0) {
        viewModelScope.launch {
            val svc = MacroAccessibilityService.instance ?: return@launch
            val mws = repo.getMacroWithSteps(macroId) ?: return@launch
            svc.playSteps(
                steps           = mws.steps,
                loopCount       = mws.macro.loopCount,
                loopDelay       = mws.macro.loopDelay,
                speedMultiplier = mws.macro.speedMultiplier,
                recordedWidth   = mws.macro.recordedWidth,
                recordedHeight  = mws.macro.recordedHeight,
                startFromIndex  = startFromStep
            )
        }
    }

    // ── Scheduling ────────────────────────────────────────────────────────────
    fun addTrigger(t: ScheduledTrigger, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch { onDone(repo.addTrigger(t)) }
    }
    fun removeTrigger(id: Long) { viewModelScope.launch { repo.removeTrigger(id) } }
    fun getTriggersForMacro(id: Long): Flow<List<ScheduledTrigger>> =
        repo.getTriggersForMacro(id)

    // ── Export / Import ───────────────────────────────────────────────────────
    fun exportMacroJson(id: Long, cb: (String) -> Unit) {
        viewModelScope.launch {
            val mws = repo.getMacroWithSteps(id) ?: return@launch
            cb(repo.exportToJson(mws.macro, mws.steps))
        }
    }
    fun exportAllJson(cb: (String) -> Unit) {
        viewModelScope.launch { cb(repo.exportAllToJson(macrosWithSteps.value)) }
    }
    fun importJson(json: String, cb: (Int) -> Unit) {
        viewModelScope.launch {
            val n = try { repo.importAllFromJson(json) }
                    catch (_: Exception) {
                        try { repo.importFromJson(json); 1 }
                        catch (_: Exception) { 0 }
                    }
            cb(n)
        }
    }
}
