package com.borgorninja.androtask.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.borgorninja.androtask.FloatingOverlayService
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StepEditDialog(
    step:      MacroStep,
    onSave:    (MacroStep) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var type        by remember { mutableStateOf(step.type) }
    var x           by remember { mutableStateOf(step.x.toInt().toString()) }
    var y           by remember { mutableStateOf(step.y.toInt().toString()) }
    var x2          by remember { mutableStateOf(step.x2.toInt().toString()) }
    var y2          by remember { mutableStateOf(step.y2.toInt().toString()) }
    var duration    by remember { mutableStateOf(step.duration.toString()) }
    var delayBefore by remember { mutableStateOf(step.delayBefore.toString()) }
    var jitter      by remember { mutableStateOf(step.jitter.toString()) }
    var text        by remember { mutableStateOf(step.text) }
    var label       by remember { mutableStateOf(step.label) }

    // Listen for coordinate picker results
    val pickResult  by FloatingOverlayService.coordPickResult.collectAsState()
    LaunchedEffect(pickResult) {
        pickResult?.let { (field, coords) ->
            when (field) {
                FloatingOverlayService.FIELD_XY  -> { x = coords.first.toInt().toString(); y = coords.second.toInt().toString() }
                FloatingOverlayService.FIELD_XY2 -> { x2= coords.first.toInt().toString(); y2= coords.second.toInt().toString() }
            }
            FloatingOverlayService.coordPickResult.value = null
        }
    }

    val needsCoords  = type in setOf(StepType.TAP, StepType.LONG_PRESS, StepType.SWIPE, StepType.PINCH,
                                     StepType.SCROLL_UP, StepType.SCROLL_DOWN)
    val needsCoords2 = type in setOf(StepType.SWIPE, StepType.PINCH)
    val needsDuration= type in setOf(StepType.TAP, StepType.LONG_PRESS, StepType.SWIPE, StepType.PINCH, StepType.WAIT,
                                     StepType.SCROLL_UP, StepType.SCROLL_DOWN)
    val needsText    = type == StepType.TYPE_TEXT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Step") },
        text  = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // ── Step type selector ──────────────────────────────────────
                Text("Step Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(6.dp)) {
                    StepType.entries.forEach { t ->
                        FilterChip(
                            selected  = type == t,
                            onClick   = { type = t },
                            label     = { Text(t.name.replace('_',' ').lowercase()
                                .replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // ── Coordinates ─────────────────────────────────────────────
                if (needsCoords) {
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Start (X, Y)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            context.startService(Intent(context, FloatingOverlayService::class.java)
                                .putExtra(FloatingOverlayService.CMD, FloatingOverlayService.CMD_PICK_START)
                                .putExtra(FloatingOverlayService.EXTRA_PICK_FIELD, FloatingOverlayService.FIELD_XY))
                        }) { Icon(Icons.Default.MyLocation, "Pick on screen", modifier = Modifier.size(20.dp)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value=x, onValueChange={x=it}, label={Text("X")},
                            modifier=Modifier.weight(1f), singleLine=true)
                        OutlinedTextField(value=y, onValueChange={y=it}, label={Text("Y")},
                            modifier=Modifier.weight(1f), singleLine=true)
                    }
                }

                if (needsCoords2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("End (X2, Y2)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            context.startService(Intent(context, FloatingOverlayService::class.java)
                                .putExtra(FloatingOverlayService.CMD, FloatingOverlayService.CMD_PICK_START)
                                .putExtra(FloatingOverlayService.EXTRA_PICK_FIELD, FloatingOverlayService.FIELD_XY2))
                        }) { Icon(Icons.Default.MyLocation, "Pick end point", modifier = Modifier.size(20.dp)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value=x2, onValueChange={x2=it}, label={Text("X2")},
                            modifier=Modifier.weight(1f), singleLine=true)
                        OutlinedTextField(value=y2, onValueChange={y2=it}, label={Text("Y2")},
                            modifier=Modifier.weight(1f), singleLine=true)
                    }
                }

                // ── Text (for TYPE_TEXT) ─────────────────────────────────────
                if (needsText) {
                    Divider()
                    OutlinedTextField(value=text, onValueChange={text=it},
                        label={Text("Text to type")}, modifier=Modifier.fillMaxWidth())
                }

                // ── Timing ───────────────────────────────────────────────────
                if (needsDuration) {
                    Divider()
                    Text("Timing", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value=duration, onValueChange={duration=it},
                        label={Text("Duration (ms)")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                }
                OutlinedTextField(value=delayBefore, onValueChange={delayBefore=it},
                    label={Text("Delay before (ms)")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                OutlinedTextField(value=jitter, onValueChange={jitter=it},
                    label={Text("Jitter ±ms  (human-like randomness)")},
                    modifier=Modifier.fillMaxWidth(), singleLine=true)

                // ── Label ────────────────────────────────────────────────────
                Divider()
                OutlinedTextField(value=label, onValueChange={label=it},
                    label={Text("Step label / comment (optional)")},
                    modifier=Modifier.fillMaxWidth(), singleLine=true)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(step.copy(
                    type        = type,
                    x           = x.toFloatOrNull() ?: step.x,
                    y           = y.toFloatOrNull() ?: step.y,
                    x2          = x2.toFloatOrNull() ?: step.x2,
                    y2          = y2.toFloatOrNull() ?: step.y2,
                    duration    = duration.toLongOrNull()?.coerceAtLeast(0L) ?: step.duration,
                    delayBefore = delayBefore.toLongOrNull()?.coerceAtLeast(0L) ?: step.delayBefore,
                    jitter      = jitter.toLongOrNull()?.coerceAtLeast(0L) ?: step.jitter,
                    text        = text,
                    label       = label
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
