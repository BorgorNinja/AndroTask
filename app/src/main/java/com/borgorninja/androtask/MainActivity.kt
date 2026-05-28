package com.borgorninja.androtask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.borgorninja.androtask.ui.HomeScreen
import com.borgorninja.androtask.ui.MacroEditorScreen
import com.borgorninja.androtask.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") { HomeScreen(navController) }
                        composable("editor/{macroId}") { back ->
                            val macroId = back.arguments?.getString("macroId")?.toLongOrNull() ?: 0L
                            MacroEditorScreen(navController, macroId)
                        }
                        composable("settings") { SettingsScreen(navController) }
                    }
                }
            }
        }
    }
}
