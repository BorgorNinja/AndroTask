package com.borgorninja.androtask.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.borgorninja.androtask.MacroAccessibilityService
import com.borgorninja.androtask.MacroAlarmReceiver
import com.borgorninja.androtask.data.Macro
import com.borgorninja.androtask.data.ScheduledTrigger
import com.borgorninja.androtask.viewmodel.MacroViewModel
import java.util.Calendar

// ── Home screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: MacroViewModel) {
    val context = LocalContext.current
    val macros  by viewModel.macros.collectAsState()

    var showImportDialog   by remember { mutableStateOf(false) }
    var importText         by remember { mutableStateOf("") }
    var scheduleTargetId   by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AndroTask") },
                actions = {
                    // Export all
                    IconButton(onClick = {
                        viewModel.exportAllJson { json ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("androtask_macros", json))
                            Toast.makeText(context, "All macros copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Default.Share, "Export all") }
                    // Import
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Menu, "Import")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("editor/0") }) {
                Icon(Icons.Default.Add, "New Macro")
            }
        }
    ) { padding ->
        if (macros.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No macros yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(macros, key = { it.id }) { macro ->
                    MacroCard(
                        macro     = macro,
                        onEdit    = { navController.navigate("editor/${macro.id}") },
                        onPlay    = {
                            val svc = MacroAccessibilityService.instance
                            if (svc == null) {
                                Toast.makeText(context,
                                    "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
                            } else {
                                val mws = viewModel.macrosWithSteps.value.find { it.macro.id == macro.id }
                                if (mws != null) {
                                    svc.playSteps(mws.steps, mws.macro.loopCount, mws.macro.loopDelay)
                                    Toast.makeText(context, "▶ ${macro.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete  = { viewModel.deleteMacro(macro.id) },
                        onSchedule = { scheduleTargetId = macro.id },
                        onExport  = {
                            viewModel.exportMacroJson(macro.id) { json ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("androtask_macro", json))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // ── Import dialog ─────────────────────────────────────────────────────────
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Macro(s)") },
            text  = {
                OutlinedTextField(
                    value         = importText,
                    onValueChange = { importText = it },
                    label         = { Text("Paste JSON here") },
                    modifier      = Modifier.fillMaxWidth().height(200.dp),
                    maxLines      = 12
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importText.isBlank()) return@TextButton
                    viewModel.importJson(importText) { count ->
                        Toast.makeText(context, "Imported $count macro(s)", Toast.LENGTH_SHORT).show()
                        showImportDialog = false
                        importText = ""
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Schedule dialog ───────────────────────────────────────────────────────
    scheduleTargetId?.let { macroId ->
        ScheduleDialog(
            macroId    = macroId,
            viewModel  = viewModel,
            onDismiss  = { scheduleTargetId = null }
        )
    }
}

// ── MacroCard ─────────────────────────────────────────────────────────────────

@Composable
fun MacroCard(
    macro:      Macro,
    onEdit:     () -> Unit,
    onPlay:     () -> Unit,
    onDelete:   () -> Unit,
    onSchedule: () -> Unit,
    onExport:   () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        onClick  = onEdit
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(macro.name, style = MaterialTheme.typography.titleMedium)
                if (macro.description.isNotBlank()) {
                    Text(macro.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Loop ×${macro.loopCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, "Run",
                    tint = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text         = { Text("Schedule") },
                        onClick      = { menuOpen = false; onSchedule() },
                        leadingIcon  = { Icon(Icons.Default.Notifications, null) }
                    )
                    DropdownMenuItem(
                        text         = { Text("Export JSON") },
                        onClick      = { menuOpen = false; onExport() },
                        leadingIcon  = { Icon(Icons.Default.Share, null) }
                    )
                    DropdownMenuItem(
                        text         = { Text("Delete") },
                        onClick      = { menuOpen = false; onDelete() },
                        leadingIcon  = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}

// ── Schedule dialog ───────────────────────────────────────────────────────────

@Composable
fun ScheduleDialog(macroId: Long, viewModel: MacroViewModel, onDismiss: () -> Unit) {
    val context  = LocalContext.current
    val triggers by remember(macroId) { viewModel.getTriggersForMacro(macroId) }
        .collectAsState(initial = emptyList())

    var hour   by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    // Mon–Sun toggles
    val days = remember { mutableStateListOf(true, true, true, true, true, false, false) }
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Macro") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = hour.toString(),
                        onValueChange = { hour = it.toIntOrNull()?.coerceIn(0, 23) ?: hour },
                        label         = { Text("Hour (0–23)") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = minute.toString().padStart(2, '0'),
                        onValueChange = { minute = it.toIntOrNull()?.coerceIn(0, 59) ?: minute },
                        label         = { Text("Minute") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                }

                Text("Repeat on:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEachIndexed { i, lbl ->
                        FilterChip(
                            selected  = days[i],
                            onClick   = { days[i] = !days[i] },
                            label     = { Text(lbl) }
                        )
                    }
                }

                if (triggers.isNotEmpty()) {
                    Divider()
                    Text("Active schedules:", style = MaterialTheme.typography.labelMedium)
                    triggers.forEach { trigger ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%02d:%02d".format(trigger.hourOfDay, trigger.minute),
                                modifier = Modifier.weight(1f),
                                style    = MaterialTheme.typography.bodySmall
                            )
                            IconButton(
                                onClick  = {
                                    viewModel.removeTrigger(trigger.id)
                                    MacroAlarmReceiver.cancel(context, trigger.id, trigger.macroId)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, "Remove schedule",
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val repeatStr = days
                    .mapIndexedNotNull { i, on -> if (on) (i + 1).toString() else null }
                    .joinToString(",")
                val trigger = ScheduledTrigger(
                    macroId   = macroId,
                    hourOfDay = hour,
                    minute    = minute,
                    repeatDays = repeatStr,
                    label     = "%02d:%02d".format(hour, minute)
                )
                viewModel.addTrigger(trigger) { newId ->
                    MacroAlarmReceiver.schedule(context, newId, macroId, hour, minute)
                    Toast.makeText(context, "Scheduled at %02d:%02d".format(hour, minute),
                        Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text("Add Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
