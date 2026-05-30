package com.borgorninja.androtask.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.borgorninja.androtask.MacroAccessibilityService
import com.borgorninja.androtask.MacroAlarmReceiver
import com.borgorninja.androtask.data.Macro
import com.borgorninja.androtask.data.MacroWithSteps
import com.borgorninja.androtask.data.ScheduledTrigger
import com.borgorninja.androtask.viewmodel.MacroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: MacroViewModel) {
    val context       = LocalContext.current
    val macrosWS      by vm.macrosWithSteps.collectAsState()

    var showImport     by remember { mutableStateOf(false) }
    var importText     by remember { mutableStateOf("") }
    var scheduleTarget by remember { mutableStateOf<Long?>(null) }

    val missingPerms = !isAccessibilityEnabled(context) ||
                       !android.provider.Settings.canDrawOverlays(context)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.exportAllJson { json ->
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.readText() ?: return@rememberLauncherForActivityResult
        vm.importJson(json) { count ->
            Toast.makeText(context, "Imported $count macro(s)", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("AndroTask",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Green400)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                actions = {
                    IconButton(onClick = { exportLauncher.launch("androtask_macros.json") }) {
                        Icon(Icons.Default.Share, "Export", tint = OnBgVariant)
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json","text/plain","*/*")) }) {
                        Icon(Icons.Default.FolderOpen, "Import file", tint = OnBgVariant)
                    }
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.ContentPaste, "Import clipboard", tint = OnBgVariant)
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "Settings", tint = OnBgVariant)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("editor/0") },
                containerColor = Green400,
                contentColor   = Color(0xFF003300)
            ) {
                Icon(Icons.Default.Add, "New Macro")
            }
        }
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 80.dp,
                start = 14.dp, end = 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── Permission banner ─────────────────────────────────────────────
            if (missingPerms) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { navController.navigate("onboarding") }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Permissions required",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                Text("Tap to complete setup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Icon(Icons.Default.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            if (macrosWS.isEmpty()) {
                item {
                    Box(
                        Modifier.fillParentMaxSize().padding(top = 60.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                               verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PlayCircle, null,
                                modifier = Modifier.size(64.dp), tint = OnBgVariant)
                            Text("No macros yet",
                                style = MaterialTheme.typography.titleMedium, color = OnBg)
                            Text("Tap + to create your first macro",
                                style = MaterialTheme.typography.bodySmall, color = OnBgVariant)
                        }
                    }
                }
            } else {
                items(macrosWS, key = { it.macro.id }) { mws ->
                    MacroCard(
                        mws        = mws,
                        onEdit     = { navController.navigate("editor/${mws.macro.id}") },
                        onPlay     = {
                            if (MacroAccessibilityService.instance == null) {
                                Toast.makeText(context, "Enable Accessibility Service first",
                                    Toast.LENGTH_LONG).show()
                            } else {
                                vm.playMacro(mws.macro.id)
                                Toast.makeText(context, "▶ ${mws.macro.name}",
                                    Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete   = { vm.deleteMacro(mws.macro.id) },
                        onSchedule = { scheduleTarget = mws.macro.id },
                        onToggle   = { vm.setMacroEnabled(mws.macro.id, it) },
                        onExport   = {
                            vm.exportMacroJson(mws.macro.id) { json ->
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
            containerColor   = CardDark,
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

// ── MacroCard (Macrorify-style) ───────────────────────────────────────────────
@Composable
fun MacroCard(
    mws: MacroWithSteps,
    onEdit: ()->Unit, onPlay: ()->Unit,
    onDelete: ()->Unit, onSchedule: ()->Unit,
    onToggle: (Boolean)->Unit, onExport: ()->Unit
) {
    val macro    = mws.macro
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = CardDark),
        onClick  = onEdit
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left accent bar (green/grey based on enabled state)
            Box(
                Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (macro.isEnabled) Green400 else OnBgVariant)
            )
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(macro.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (macro.isEnabled) OnBg else OnBgVariant)
                if (macro.description.isNotBlank()) {
                    Text(macro.description,
                        style = MaterialTheme.typography.bodySmall, color = OnBgVariant,
                        maxLines = 1)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    // Step count badge
                    StepCountBadge(mws.steps.size)
                    // Loop badge
                    val loopLabel = if (macro.loopCount < 0) "∞ loop" else "×${macro.loopCount}"
                    SmallBadge(loopLabel, Color(0xFF37474F))
                    if (macro.speedMultiplier != 1f)
                        SmallBadge("${macro.speedMultiplier}×", Color(0xFF4A148C))
                }
            }

            // Enable/disable toggle (Fix #6)
            Switch(
                checked  = macro.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = Green700,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF333333)
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Play button
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayCircle, "Run",
                    tint = if (macro.isEnabled) Green400 else OnBgVariant,
                    modifier = Modifier.size(36.dp))
            }

            // More menu
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, "More", tint = OnBgVariant,
                        modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = menuOpen, onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(CardDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuOpen=false; onEdit() })
                    DropdownMenuItem(
                        text = { Text("Schedule") },
                        leadingIcon = { Icon(Icons.Default.Alarm, null) },
                        onClick = { menuOpen=false; onSchedule() })
                    DropdownMenuItem(
                        text = { Text("Export JSON") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { menuOpen=false; onExport() })
                    HorizontalDivider(color = Color(0xFF333333))
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen=false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun StepCountBadge(count: Int) {
    SmallBadge("$count step${if (count == 1) "" else "s"}", Color(0xFF1B5E20))
}

@Composable
private fun SmallBadge(text: String, bg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White)
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
        containerColor   = CardDark,
        title = { Text("Schedule Macro") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value=hour.toString(),
                        onValueChange={hour=it.toIntOrNull()?.coerceIn(0,23)?:hour},
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
                    HorizontalDivider(color = Color(0xFF333333))
                    triggers.forEach { t ->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("%02d:%02d".format(t.hourOfDay,t.minute),
                                modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.bodySmall)
                            IconButton(onClick={
                                vm.removeTrigger(t.id)
                                MacroAlarmReceiver.cancel(context, t.id, t.macroId)
                            }, modifier=Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, null, modifier=Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val repeatStr = days.mapIndexedNotNull { i,on -> if(on) (i+1).toString() else null }
                    .joinToString(",")
                vm.addTrigger(ScheduledTrigger(
                    macroId=macroId, hourOfDay=hour, minute=minute,
                    repeatDays=repeatStr, label="%02d:%02d".format(hour,minute)
                )) { id ->
                    MacroAlarmReceiver.schedule(context, id, macroId, hour, minute, repeatStr)
                    Toast.makeText(context,"Scheduled %02d:%02d".format(hour,minute),
                        Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick=onDismiss) { Text("Close") } }
    )
}
