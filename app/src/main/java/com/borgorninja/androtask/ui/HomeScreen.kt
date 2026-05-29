package com.borgorninja.androtask.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: MacroViewModel) {
    val context = LocalContext.current
    val macros  by vm.macros.collectAsState()

    var showImport     by remember { mutableStateOf(false) }
    var importText     by remember { mutableStateOf("") }
    var scheduleTarget by remember { mutableStateOf<Long?>(null) }

    // Permission banner visibility
    val missingPerms = !isAccessibilityEnabled(context) || !android.provider.Settings.canDrawOverlays(context)

    // SAF export-to-file launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.exportAllJson { json ->
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
        }
    }
    // SAF import-from-file launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@rememberLauncherForActivityResult
        vm.importJson(json) { count ->
            Toast.makeText(context, "Imported $count macro(s)", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AndroTask") },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("androtask_macros.json") }) {
                        Icon(Icons.Default.Share, "Export to file")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json","text/plain","*/*")) }) {
                        Icon(Icons.Default.FolderOpen, "Import from file")
                    }
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.ContentPaste, "Import from clipboard")
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
    ) { pad ->
        LazyColumn(contentPadding = pad) {

            // ── Permission banner ─────────────────────────────────────────────
            if (missingPerms) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        onClick = { navController.navigate("onboarding") }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null,
                                tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Permissions required",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                Text("Tap here to complete setup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }

            if (macros.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No macros yet — tap + to create one",
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                items(macros, key = { it.id }) { macro ->
                    MacroCard(
                        macro      = macro,
                        onEdit     = { navController.navigate("editor/${macro.id}") },
                        onPlay     = {
                            if (MacroAccessibilityService.instance == null) {
                                Toast.makeText(context, "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
                            } else {
                                vm.playMacro(macro.id)
                                Toast.makeText(context, "▶ ${macro.name}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete   = { vm.deleteMacro(macro.id) },
                        onSchedule = { scheduleTarget = macro.id },
                        onExport   = {
                            vm.exportMacroJson(macro.id) { json ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("macro", json))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // ── Import from clipboard dialog ──────────────────────────────────────────
    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import Macro(s)") },
            text  = {
                OutlinedTextField(value=importText, onValueChange={importText=it},
                    label={Text("Paste JSON")}, modifier=Modifier.fillMaxWidth().height(200.dp))
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.importJson(importText) { count ->
                        Toast.makeText(context, "Imported $count macro(s)", Toast.LENGTH_SHORT).show()
                        showImport = false; importText = ""
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick={showImport=false}) { Text("Cancel") } }
        )
    }

    // ── Schedule dialog ───────────────────────────────────────────────────────
    scheduleTarget?.let { id ->
        ScheduleDialog(macroId=id, vm=vm, onDismiss={ scheduleTarget=null })
    }
}

// ── MacroCard ─────────────────────────────────────────────────────────────────
@Composable
fun MacroCard(
    macro: Macro, onEdit: ()->Unit, onPlay: ()->Unit,
    onDelete: ()->Unit, onSchedule: ()->Unit, onExport: ()->Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=5.dp), onClick=onEdit) {
        Row(Modifier.padding(start=16.dp, end=4.dp, top=10.dp, bottom=10.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(macro.name, style=MaterialTheme.typography.titleMedium)
                if (macro.description.isNotBlank())
                    Text(macro.description, style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                val loopLabel = if (macro.loopCount < 0) "∞ loop" else "×${macro.loopCount}"
                val speed     = if (macro.speedMultiplier != 1f) "  ${macro.speedMultiplier}×" else ""
                Text("$loopLabel$speed", style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=onPlay) {
                Icon(Icons.Default.PlayArrow, "Run", tint=MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick={menuOpen=true}) { Icon(Icons.Default.MoreVert,"More") }
                DropdownMenu(expanded=menuOpen, onDismissRequest={menuOpen=false}) {
                    DropdownMenuItem(text={Text("Schedule")}, leadingIcon={Icon(Icons.Default.Alarm,null)},
                        onClick={menuOpen=false; onSchedule()})
                    DropdownMenuItem(text={Text("Export JSON")}, leadingIcon={Icon(Icons.Default.Share,null)},
                        onClick={menuOpen=false; onExport()})
                    DropdownMenuItem(text={Text("Delete")}, leadingIcon={Icon(Icons.Default.Delete,null)},
                        onClick={menuOpen=false; onDelete()})
                }
            }
        }
    }
}

// ── Schedule dialog ───────────────────────────────────────────────────────────
@Composable
fun ScheduleDialog(macroId: Long, vm: MacroViewModel, onDismiss: ()->Unit) {
    val context  = LocalContext.current
    val triggers by remember(macroId) { vm.getTriggersForMacro(macroId) }.collectAsState(emptyList())
    var hour     by remember { mutableIntStateOf(8) }
    var minute   by remember { mutableIntStateOf(0) }
    val days     = remember { mutableStateListOf(true,true,true,true,true,false,false) }
    val lbls     = listOf("M","T","W","T","F","S","S")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Macro") },
        text  = {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value=hour.toString(), onValueChange={hour=it.toIntOrNull()?.coerceIn(0,23)?:hour},
                        label={Text("Hour")}, modifier=Modifier.weight(1f), singleLine=true)
                    OutlinedTextField(value=minute.toString().padStart(2,'0'),
                        onValueChange={minute=it.toIntOrNull()?.coerceIn(0,59)?:minute},
                        label={Text("Minute")}, modifier=Modifier.weight(1f), singleLine=true)
                }
                Text("Repeat:", style=MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    lbls.forEachIndexed { i, l ->
                        FilterChip(selected=days[i], onClick={days[i]=!days[i]}, label={Text(l)})
                    }
                }
                if (triggers.isNotEmpty()) {
                    Divider()
                    triggers.forEach { t ->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("%02d:%02d".format(t.hourOfDay,t.minute), modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.bodySmall)
                            IconButton(onClick={
                                vm.removeTrigger(t.id)
                                MacroAlarmReceiver.cancel(context,t.id,t.macroId)
                            }, modifier=Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close,null,modifier=Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val repeatStr = days.mapIndexedNotNull { i,on -> if(on) (i+1).toString() else null }.joinToString(",")
                vm.addTrigger(ScheduledTrigger(macroId=macroId, hourOfDay=hour, minute=minute,
                    repeatDays=repeatStr, label="%02d:%02d".format(hour,minute))) { id ->
                    MacroAlarmReceiver.schedule(context,id,macroId,hour,minute)
                    Toast.makeText(context,"Scheduled %02d:%02d".format(hour,minute),Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick=onDismiss) { Text("Close") } }
    )
}
