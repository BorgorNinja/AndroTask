package com.borgorninja.androtask.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(navController: NavController, macroId: Long) {
    val steps = remember { mutableStateListOf<MacroStep>() }
    var macroName by remember { mutableStateOf(if (macroId == 0L) "New Macro" else "Macro #$macroId") }
    var loopCount by remember { mutableStateOf("1") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (macroId == 0L) "New Macro" else "Edit Macro") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = macroName,
                onValueChange = { macroName = it },
                label = { Text("Macro Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = loopCount,
                onValueChange = { loopCount = it },
                label = { Text("Loop Count") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Steps", style = MaterialTheme.typography.titleMedium)

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(steps) { index, step ->
                    StepRow(step = step, index = index, onDelete = { steps.removeAt(index) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepType.entries.filter { it != StepType.WAIT }.forEach { type ->
                    AssistChip(
                        onClick = {
                            steps.add(
                                MacroStep(
                                    macroId = macroId,
                                    stepIndex = steps.size,
                                    type = type
                                )
                            )
                        },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
                AssistChip(
                    onClick = { steps.add(MacroStep(macroId = macroId, stepIndex = steps.size, type = StepType.WAIT, duration = 500L)) },
                    label = { Text("Wait") }
                )
            }

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Macro")
            }
        }
    }
}

@Composable
fun StepRow(step: MacroStep, index: Int, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${index + 1}. ${step.type.name}", style = MaterialTheme.typography.titleSmall)
                Text(
                    when (step.type) {
                        StepType.SWIPE, StepType.PINCH -> "(${step.x.toInt()}, ${step.y.toInt()}) → (${step.x2.toInt()}, ${step.y2.toInt()})"
                        StepType.WAIT -> "Wait ${step.duration}ms"
                        else -> "(${step.x.toInt()}, ${step.y.toInt()}) ${step.duration}ms"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete step")
            }
        }
    }
}
