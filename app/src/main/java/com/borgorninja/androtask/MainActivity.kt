package com.borgorninja.androtask

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.borgorninja.androtask.ui.HomeScreen
import com.borgorninja.androtask.ui.MacroEditorScreen
import com.borgorninja.androtask.ui.SettingsScreen
import com.borgorninja.androtask.viewmodel.MacroViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MacroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        // Start the floating overlay service (requires overlay permission)
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingOverlayService::class.java))
        }

        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(navController, viewModel)
                        }
                        composable("editor/{macroId}") { back ->
                            val macroId = back.arguments?.getString("macroId")?.toLongOrNull() ?: 0L
                            MacroEditorScreen(navController, macroId, viewModel)
                        }
                        composable("settings") {
                            SettingsScreen(navController)
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AndroTask Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while the floating bubble is active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "androtask_overlay"
    }
}
