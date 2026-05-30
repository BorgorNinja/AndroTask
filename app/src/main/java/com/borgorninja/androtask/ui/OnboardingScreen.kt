package com.borgorninja.androtask.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.borgorninja.androtask.MacroAccessibilityService
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current

    var accessOk  by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(false) }
    var notifOk   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            accessOk  = isAccessibilityEnabled(context)
            overlayOk = Settings.canDrawOverlays(context)
            notifOk   = if (Build.VERSION.SDK_INT >= 33)
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            else true
            delay(1_000)
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Setup", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = OnBg
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, "Close", tint = OnBg)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("AndroTask needs these permissions to record and replay macros.",
                style = MaterialTheme.typography.bodyLarge, color = OnBg)

            PermissionRow(
                icon     = Icons.Default.Accessibility,
                title    = "Accessibility Service",
                desc     = "Required to dispatch gestures and detect taps during passive recording.",
                granted  = accessOk,
                onEnable = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )

            PermissionRow(
                icon     = Icons.Default.Layers,
                title    = "Draw Over Other Apps",
                desc     = "Required to show the floating bubble and recording overlay.",
                granted  = overlayOk,
                onEnable = {
                    context.startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ))
                }
            )

            if (Build.VERSION.SDK_INT >= 33) {
                PermissionRow(
                    icon     = Icons.Default.Notifications,
                    title    = "Post Notifications",
                    desc     = "Shows a persistent notification while the overlay service is running.",
                    granted  = notifOk,
                    onEnable = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }
                )
            }

            if (accessOk && overlayOk && notifOk) {
                Card(
                    shape  = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Green400, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("All set!", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color.White)
                            Text("You can now record and replay macros.",
                                style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green700)
                ) { Text("Get Started", fontWeight = FontWeight.SemiBold) }
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))
            Text("Quick start guide", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = OnBg)
            Text("""
1. Enable all permissions above.
2. Tap + on the home screen to create a macro.
3. Choose recording mode:
   • Passive – use the app normally; taps are detected automatically.
   • Overlay – a transparent screen captures every touch precisely.
4. Tap "Record Gestures", perform your actions, then tap "Stop".
5. Save the macro. Tap ▶ to run it any time.
6. Use the floating bubble (visible from any app) to toggle recording.
7. Schedule macros via the ⋮ menu → Schedule.
            """.trimIndent(),
                style = MaterialTheme.typography.bodySmall, color = OnBgVariant)
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector, title: String, desc: String,
    granted: Boolean, onEnable: () -> Unit
) {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null,
                tint = if (granted) Green400 else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = OnBg)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = OnBgVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Icon(Icons.Default.CheckCircle, "Granted",
                    tint = Green400, modifier = Modifier.size(28.dp))
            } else {
                TextButton(onClick = onEnable) { Text("Enable", color = Green400) }
            }
        }
    }
}

fun isAccessibilityEnabled(context: Context): Boolean {
    if (MacroAccessibilityService.instance != null) return true
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':').also { it.setString(enabled) }
    val target   = ComponentName(context, MacroAccessibilityService::class.java)
    while (splitter.hasNext()) {
        val cn = ComponentName.unflattenFromString(splitter.next()) ?: continue
        if (cn == target) return true
    }
    return false
}
