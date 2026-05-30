package com.borgorninja.androtask.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.borgorninja.androtask.data.StepType

// ── Macrorify-inspired palette ────────────────────────────────────────────────
val Green400       = Color(0xFF66BB6A)
val Green700       = Color(0xFF388E3C)
val BgDark         = Color(0xFF0F0F0F)
val SurfaceDark    = Color(0xFF1A1A1A)
val CardDark       = Color(0xFF202020)
val OnBg           = Color(0xFFEEEEEE)
val OnBgVariant    = Color(0xFF9E9E9E)

private val DarkColors = darkColorScheme(
    primary            = Green400,
    onPrimary          = Color(0xFF003300),
    primaryContainer   = Green700,
    onPrimaryContainer = Color(0xFFCCFFCC),
    secondary          = Color(0xFF80CBC4),
    onSecondary        = Color(0xFF003733),
    background         = BgDark,
    onBackground       = OnBg,
    surface            = SurfaceDark,
    onSurface          = OnBg,
    surfaceVariant     = CardDark,
    onSurfaceVariant   = OnBgVariant,
    error              = Color(0xFFCF6679),
    errorContainer     = Color(0xFF8B1A2A),
    onErrorContainer   = Color(0xFFFFDAD9),
)

@Composable
fun AndroTaskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = Typography(),
        content     = content
    )
}

// ── Step-type accent colors (used in editor) ──────────────────────────────────
fun stepTypeColor(type: StepType): Color = when (type) {
    StepType.TAP           -> Color(0xFF2196F3)
    StepType.LONG_PRESS    -> Color(0xFF9C27B0)
    StepType.SWIPE         -> Color(0xFFFF9800)
    StepType.PINCH         -> Color(0xFFE91E63)
    StepType.WAIT          -> Color(0xFF607D8B)
    StepType.SCROLL_UP,
    StepType.SCROLL_DOWN   -> Color(0xFF00BCD4)
    StepType.BACK,
    StepType.HOME,
    StepType.RECENTS,
    StepType.NOTIFICATIONS -> Color(0xFF8BC34A)
    StepType.VOLUME_UP,
    StepType.VOLUME_DOWN   -> Color(0xFF4CAF50)
    StepType.TYPE_TEXT     -> Color(0xFFFF5722)
}
