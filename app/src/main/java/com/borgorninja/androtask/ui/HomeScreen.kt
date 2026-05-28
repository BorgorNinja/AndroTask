package com.borgorninja.androtask.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.borgorninja.androtask.data.Macro

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val macros = remember { mutableStateListOf<Macro>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AndroTask") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("editor/0") }) {
                Icon(Icons.Default.Add, contentDescription = "New Macro")
            }
        }
    ) { padding ->
        if (macros.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No macros yet. Tap + to create one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(macros) { macro ->
                    MacroCard(
                        macro = macro,
                        onEdit = { navController.navigate("editor/${macro.id}") },
                        onPlay = { /* trigger playback */ }
                    )
                }
            }
        }
    }
}

@Composable
fun MacroCard(macro: Macro, onEdit: () -> Unit, onPlay: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(macro.name, style = MaterialTheme.typography.titleMedium)
                if (macro.description.isNotEmpty()) {
                    Text(macro.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run")
            }
        }
    }
}
