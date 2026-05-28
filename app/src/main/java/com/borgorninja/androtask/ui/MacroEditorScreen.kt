package com.borgorninja.androtask.ui

import android.content.Intent
import android.widget.Toast
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
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType
import com.borgorninja.androtask.viewmodel.MacroViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(navController: NavController, macroId: Long, viewModel: MacroViewModel) {
    val context     = LocalContext.current
    val currentMacro by viewModel.currentMacro.collectAsState()

    // ── local editor state ────────────────────────────────────────────────────
    var macroName   by remember { mutableStateOf("New Macro") }
    var description by remember { mutableStateOf("") }
    var loopCount   by remember { mutableStateOf("1") }
    var loopDelay   by remember { mutableStateOf("0") }
    val steps = remember { mutableStateListOf<MacroStep>() }
    var isRecording by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    // Load existing macro once on entry
    LaunchedEffect(macroId) {
        if (macroId != 0L) viewModel.loadMacro(macroId)
    }

    // Populate form when the DB record arrives
    LaunchedEffect(currentMacro) {
        if (!initialized && macroId != 0L && currentMacro != null) {
            currentMacro!!.let { mws ->
                macroName   = mws.macro.name
                description = mws.macro.description
                loopCount   = mws.macro.loopCount.toString()
                loopDelay   = mws.macro.loopDelay.toString()
                steps.clear()
                steps.addAll(mws.steps)
            }
            initialized = true
        }
    }

    // Poll service state + recorded steps while screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            val svcRecording = MacroAccessibilityService.isRecording
            if (isRecording != svcRecording) isRecording = svcRecording

            if (isRecording) {
                val recorded = MacroAccessibilityService.recordedSteps.toList()
                if (recorded.size != steps.size) {
                    steps.clear()
                    steps.addAll(recorded.map { it.copy(macroId = macroId) })
                }
            }
            delay(250)
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (macroId == 0L) "New Macro" else "Edit Macro") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // Name
            OutlinedTextField(
                value = macroName, onValueChange = { macroName = it },
                label = { Text("Macro Name") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Description
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Loop settings
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = loopCount, onValueChange = { loopCount = it },
                    label = { Text("Loops") }, modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedTextField(
                    value = loopDelay, onValueChange = { loopDelay = it },
                    label = { Text("Loop delay (ms)") }, modifier = Modifier.weight(1f), singleLine = true
                )
            }

            // ── Recording control ─────────────────────────────────────────────
            Button(
                onClick = {
                    if (MacroAccessibilityService.instance == null) {
                        Toast.makeText(context, "Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val cmd = if (isRecording) FloatingOverlayService.CMD_STOP
                              else             FloatingOverlayService.CMD_START
                    if (!isRecording) {
                        // Clear previous recorded steps
                        MacroAccessibilityService.recordedSteps.clear()
                        steps.clear()
                    }
                    context.startService(
                        Intent(context, FloatingOverlayService::class.java)
                            .putExtra(FloatingOverlayService.CMD, cmd)
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFD32F2F)
                                     else MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.RadioButtonChecked,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isRecording) "Stop Recording  (${steps.size} steps)" else "Record Gestures")
            }

            // ── Step list ─────────────────────────────────────────────────────
            Text("Steps (${steps.size})", style = MaterialTheme.typography.titleSmall)

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(steps, key = { i, _ -> i }) { index, step ->
                    StepRow(step = step, index = index, onDelete = {
                        if (!isRecording) {
                            steps.removeAt(index)
                            // also remove from service list if present
                            if (MacroAccessibilityService.recordedSteps.size > index)
                                MacroAccessibilityService.recordedSteps.removeAt(index)
                        }
                    })
                }
            }

            // ── Manual step add chips (hidden while recording) ────────────────
            if (!isRecording) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepType.entries.forEach { type ->
                        AssistChip(
                            onClick = {
                                steps.add(
                                    MacroStep(
                                        macroId    = macroId,
                                        stepIndex  = steps.size,
                                        type       = type,
                                        duration   = when (type) {
                                            StepType.LONG_PRESS -> 600L
                                            StepType.WAIT       -> 500L
                                            else                -> 100L
                                        }
                                    )
                                )
                            },
                            label = {
                                Text(type.name.lowercase().replaceFirstChar { it.uppercaseChar() })
                            }
                        )
                    }
                }
            }

            // ── Save ──────────────────────────────────────────────────────────
            Button(
                onClick = {
                    val name = macroName.trim()
                    if (name.isEmpty()) {
                        Toast.makeText(context, "Please enter a macro name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.saveMacro(
                        name        = name,
                        description = description.trim(),
                        loopCount   = loopCount.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        loopDelay   = loopDelay.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        steps       = steps.toList(),
                        macroId     = macroId
                    ) {
                        Toast.makeText(context, "Macro saved!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = !isRecording
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Macro")
            }
        }
    }
}

// ── Step row ──────────────────────────────────────────────────────────────────

@Composable
fun StepRow(step: MacroStep, index: Int, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${index + 1}. ${step.type.name}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    when (step.type) {
                        StepType.SWIPE, StepType.PINCH ->
                            "(${step.x.toInt()},${step.y.toInt()}) → (${step.x2.toInt()},${step.y2.toInt()})  ${step.duration}ms"
                        StepType.WAIT ->
                            "Wait ${step.duration}ms"
                        else ->
                            "(${step.x.toInt()},${step.y.toInt()})  ${step.duration}ms"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (step.delayBefore > 0) {
                    Text("delay before: ${step.delayBefore}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete step")
            }
        }
    }
}
