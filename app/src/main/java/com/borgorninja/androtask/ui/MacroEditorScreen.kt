package com.borgorninja.androtask.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.borgorninja.androtask.FloatingOverlayService
import com.borgorninja.androtask.MacroAccessibilityService
import com.borgorninja.androtask.MacroAccessibilityService.Companion.RecordingMode
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType
import com.borgorninja.androtask.viewmodel.MacroViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class,
       ExperimentalFoundationApi::class)
@Composable
fun MacroEditorScreen(navController: NavController, macroId: Long, vm: MacroViewModel) {
    val context     = LocalContext.current
    val currentMacro by vm.currentMacro.collectAsState()

    // ── Editor state ──────────────────────────────────────────────────────────
    var name         by remember { mutableStateOf("New Macro") }
    var desc         by remember { mutableStateOf("") }
    var loopCount    by remember { mutableStateOf("1") }
    var infiniteLoop by remember { mutableStateOf(false) }
    var loopDelay    by remember { mutableStateOf("0") }
    var speed        by remember { mutableFloatStateOf(1f) }
    var recMode      by remember { mutableStateOf(RecordingMode.PASSIVE) }
    val steps        = remember { mutableStateListOf<MacroStep>() }
    val undoStack    = remember { ArrayDeque<List<MacroStep>>() }
    var isRecording  by remember { mutableStateOf(false) }
    var editingStep  by remember { mutableStateOf<Pair<Int, MacroStep>?>(null) }
    var stepMenuIdx  by remember { mutableStateOf<Int?>(null) }
    var initialized  by remember { mutableStateOf(false) }

    // ── Load existing macro ───────────────────────────────────────────────────
    LaunchedEffect(macroId) { if (macroId != 0L) vm.loadMacro(macroId) }
    LaunchedEffect(currentMacro) {
        if (!initialized && macroId != 0L && currentMacro != null) {
            currentMacro!!.let { mws ->
                name         = mws.macro.name
                desc         = mws.macro.description
                infiniteLoop = mws.macro.loopCount < 0
                loopCount    = if (mws.macro.loopCount < 0) "1" else mws.macro.loopCount.toString()
                loopDelay    = mws.macro.loopDelay.toString()
                speed        = mws.macro.speedMultiplier
                steps.clear(); steps.addAll(mws.steps)
            }
            initialized = true
        }
    }

    // ── Poll service state ────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            val svcRec = MacroAccessibilityService.isRecording
            if (isRecording != svcRec) isRecording = svcRec
            if (isRecording && recMode == RecordingMode.PASSIVE) {
                val recorded = MacroAccessibilityService.recordedSteps.toList()
                if (recorded.size != steps.size) {
                    steps.clear()
                    steps.addAll(recorded.map { it.copy(macroId = macroId) })
                }
            } else if (isRecording && recMode == RecordingMode.OVERLAY) {
                val recorded = MacroAccessibilityService.recordedSteps.toList()
                if (recorded.size != steps.size) {
                    steps.clear()
                    steps.addAll(recorded.map { it.copy(macroId = macroId) })
                }
            }
            delay(300)
        }
    }

    // ── Undo helper ───────────────────────────────────────────────────────────
    fun pushUndo() { undoStack.addLast(steps.toList()); if (undoStack.size > 50) undoStack.removeFirst() }
    fun undo() { if (undoStack.isNotEmpty()) { steps.clear(); steps.addAll(undoStack.removeLast()) } }

    // ── Move helpers ──────────────────────────────────────────────────────────
    fun moveUp(i: Int)   { if (i > 0) { pushUndo(); val t=steps[i]; steps[i]=steps[i-1]; steps[i-1]=t } }
    fun moveDown(i: Int) { if (i < steps.size-1) { pushUndo(); val t=steps[i]; steps[i]=steps[i+1]; steps[i+1]=t } }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (macroId == 0L) "New Macro" else "Edit Macro") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (undoStack.isNotEmpty()) {
                        IconButton(onClick = { undo() }) { Icon(Icons.Default.Undo, "Undo") }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal=14.dp, vertical=6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Name / desc
            OutlinedTextField(value=name, onValueChange={name=it}, label={Text("Macro Name")},
                modifier=Modifier.fillMaxWidth(), singleLine=true)
            OutlinedTextField(value=desc, onValueChange={desc=it}, label={Text("Description (optional)")},
                modifier=Modifier.fillMaxWidth(), singleLine=true)

            // Loop row
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                if (!infiniteLoop) {
                    OutlinedTextField(value=loopCount, onValueChange={loopCount=it},
                        label={Text("Loops")}, modifier=Modifier.weight(1f), singleLine=true)
                }
                OutlinedTextField(value=loopDelay, onValueChange={loopDelay=it},
                    label={Text("Loop delay ms")}, modifier=Modifier.weight(1f), singleLine=true)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=infiniteLoop, onCheckedChange={infiniteLoop=it})
                    Text("∞", style=MaterialTheme.typography.titleMedium)
                }
            }

            // Speed slider
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Speed:", style=MaterialTheme.typography.labelMedium, modifier=Modifier.width(52.dp))
                Slider(value=speed, onValueChange={speed=it}, valueRange=0.25f..4f,
                    modifier=Modifier.weight(1f))
                Text("%.2fx".format(speed), style=MaterialTheme.typography.labelMedium,
                    modifier=Modifier.width(44.dp))
            }

            // Recording mode toggle
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Mode:", style=MaterialTheme.typography.labelMedium)
                RecordingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = recMode == mode,
                        onClick  = {
                            recMode = mode
                            MacroAccessibilityService.recordingMode = mode
                        },
                        label = { Text(mode.name.lowercase().replaceFirstChar{it.uppercase()}) }
                    )
                }
                if (recMode == RecordingMode.PASSIVE) {
                    Icon(Icons.Default.Info, null, modifier=Modifier.size(16.dp),
                        tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Record button
            Button(
                onClick = {
                    if (MacroAccessibilityService.instance == null) {
                        Toast.makeText(context, "Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val cmd = if (isRecording) FloatingOverlayService.CMD_STOP else FloatingOverlayService.CMD_START
                    if (!isRecording) {
                        MacroAccessibilityService.recordingMode = recMode
                        MacroAccessibilityService.recordedSteps.clear()
                        steps.clear()
                    }
                    context.startService(Intent(context, FloatingOverlayService::class.java)
                        .putExtra(FloatingOverlayService.CMD, cmd))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFD32F2F) else MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (isRecording) Icons.Default.Stop else Icons.Default.RadioButtonChecked, null)
                Spacer(Modifier.width(6.dp))
                Text(if (isRecording) "Stop  (${steps.size} steps captured)" else "Record Gestures")
            }

            // Steps header
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Steps (${steps.size})", style=MaterialTheme.typography.titleSmall,
                    modifier=Modifier.weight(1f))
                if (!isRecording && steps.isNotEmpty()) {
                    TextButton(onClick={
                        val svc = MacroAccessibilityService.instance
                        if (svc == null) {
                            Toast.makeText(context,"Enable Accessibility Service",Toast.LENGTH_SHORT).show()
                        } else {
                            svc.playSteps(steps.toList(),1,0L,speed)
                            Toast.makeText(context,"▶ preview",Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Preview") }
                }
            }

            // Step list
            LazyColumn(modifier=Modifier.weight(1f)) {
                itemsIndexed(steps, key={i,_->i}) { index, step ->
                    EnhancedStepRow(
                        step      = step,
                        index     = index,
                        total     = steps.size,
                        isRecording = isRecording,
                        onEdit    = { editingStep = Pair(index, step) },
                        onDelete  = { pushUndo(); steps.removeAt(index) },
                        onMoveUp  = { moveUp(index) },
                        onMoveDown= { moveDown(index) },
                        onPlayFrom= {
                            val svc = MacroAccessibilityService.instance ?: return@EnhancedStepRow
                            svc.playSteps(steps.drop(index), 1, 0L, speed)
                            Toast.makeText(context,"▶ from step ${index+1}",Toast.LENGTH_SHORT).show()
                        },
                        onDuplicate= {
                            pushUndo()
                            steps.add(index+1, step.copy(id=0, stepIndex=index+1))
                        }
                    )
                }
            }

            // Manual add chips
            if (!isRecording) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),
                        verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    StepType.entries.forEach { t ->
                        AssistChip(onClick={
                            pushUndo()
                            steps.add(MacroStep(macroId=macroId, stepIndex=steps.size, type=t,
                                duration=when(t){StepType.LONG_PRESS->650L;StepType.WAIT->500L;else->100L}))
                        }, label={ Text(t.name.replace('_',' ').lowercase()
                            .replaceFirstChar{it.uppercase()}, style=MaterialTheme.typography.labelSmall) })
                    }
                }
            }

            // Save
            Button(
                onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) { Toast.makeText(context,"Enter a name",Toast.LENGTH_SHORT).show(); return@Button }
                    vm.saveMacro(
                        name=n, description=desc.trim(),
                        loopCount=if(infiniteLoop)-1 else loopCount.toIntOrNull()?.coerceAtLeast(1)?:1,
                        loopDelay=loopDelay.toLongOrNull()?.coerceAtLeast(0L)?:0L,
                        speedMultiplier=speed,
                        steps=steps.toList(),
                        macroId=macroId
                    ) {
                        Toast.makeText(context,"Saved!",Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                modifier=Modifier.fillMaxWidth(),
                enabled=!isRecording
            ) {
                Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Save Macro")
            }
        }
    }

    // ── Step edit dialog ──────────────────────────────────────────────────────
    editingStep?.let { (index, step) ->
        StepEditDialog(
            step = step,
            onSave = { updated ->
                pushUndo()
                steps[index] = updated.copy(macroId=macroId, stepIndex=index)
                editingStep = null
            },
            onDismiss = { editingStep = null }
        )
    }
}

// ── Enhanced step row ─────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedStepRow(
    step: MacroStep, index: Int, total: Int, isRecording: Boolean,
    onEdit: ()->Unit, onDelete: ()->Unit, onMoveUp: ()->Unit,
    onMoveDown: ()->Unit, onPlayFrom: ()->Unit, onDuplicate: ()->Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical=3.dp)
            .combinedClickable(onClick=onEdit, onLongClick={ if(!isRecording) menuOpen=true })
    ) {
        Row(Modifier.padding(start=12.dp, end=4.dp, top=8.dp, bottom=8.dp),
            verticalAlignment=Alignment.CenterVertically) {

            // Reorder arrows
            if (!isRecording) {
                Column {
                    IconButton(onClick=onMoveUp,   modifier=Modifier.size(28.dp), enabled=index>0) {
                        Icon(Icons.Default.KeyboardArrowUp,   null, modifier=Modifier.size(18.dp))
                    }
                    IconButton(onClick=onMoveDown, modifier=Modifier.size(28.dp), enabled=index<total-1) {
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier=Modifier.size(18.dp))
                    }
                }
            }

            // Step info
            Column(Modifier.weight(1f).padding(horizontal=4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("${index+1}. ", style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(step.type.name.replace('_',' '), style=MaterialTheme.typography.titleSmall)
                    if (step.label.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("(${step.label})", style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    when (step.type) {
                        StepType.SWIPE, StepType.PINCH -> "(${step.x.toInt()},${step.y.toInt()})→(${step.x2.toInt()},${step.y2.toInt()})  ${step.duration}ms"
                        StepType.WAIT                  -> "Wait ${step.duration}ms"
                        StepType.TYPE_TEXT             -> "\"${step.text.take(30)}\""
                        StepType.BACK, StepType.HOME,
                        StepType.RECENTS, StepType.NOTIFICATIONS -> step.type.name.lowercase()
                        StepType.VOLUME_UP, StepType.VOLUME_DOWN -> step.type.name.lowercase().replace('_',' ')
                        StepType.SCROLL_UP, StepType.SCROLL_DOWN -> "${step.type.name.lowercase().replace('_',' ')} at (${step.x.toInt()},${step.y.toInt()})"
                        else                           -> "(${step.x.toInt()},${step.y.toInt()})  ${step.duration}ms"
                    },
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (step.delayBefore > 0 || step.jitter > 0) {
                    val timing = buildList {
                        if (step.delayBefore > 0) add("delay ${step.delayBefore}ms")
                        if (step.jitter > 0)      add("±${step.jitter}ms jitter")
                    }.joinToString("  ")
                    Text(timing, style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Action buttons
            if (!isRecording) {
                Box {
                    IconButton(onClick={menuOpen=true}, modifier=Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, null, modifier=Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded=menuOpen, onDismissRequest={menuOpen=false}) {
                        DropdownMenuItem(text={Text("Edit")}, leadingIcon={Icon(Icons.Default.Edit,null)},
                            onClick={menuOpen=false; onEdit()})
                        DropdownMenuItem(text={Text("Play from here")}, leadingIcon={Icon(Icons.Default.PlayArrow,null)},
                            onClick={menuOpen=false; onPlayFrom()})
                        DropdownMenuItem(text={Text("Duplicate")}, leadingIcon={Icon(Icons.Default.ContentCopy,null)},
                            onClick={menuOpen=false; onDuplicate()})
                        DropdownMenuItem(text={Text("Delete")}, leadingIcon={Icon(Icons.Default.Delete,null)},
                            onClick={menuOpen=false; onDelete()})
                    }
                }
            }
        }
    }
}
