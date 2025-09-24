package com.example.puffs.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Web app vibe
private val Emerald = Color(0xFF10B981) // #10b981
private val Slate950 = Color(0xFF0B0F14) // background
private val Slate900 = Color(0xFF111827) // cards
private val Text = Color(0xFFE5E7EB)    // text
private val Muted = Color(0xFF94A3B8)
private val Danger = Color(0xFFEF4444)

private val DarkColors = darkColorScheme(
    primary = Emerald,
    onPrimary = Color(0xFF06281D),
    secondary = Emerald,
    onSecondary = Color(0xFF06281D),
    background = Slate950,
    onBackground = Text,
    surface = Slate900,
    onSurface = Text,
    error = Danger,
    onError = Color.White,
    outline = Color(0xFF1F2937)
)

@Composable
fun PuffsTheme(content: @Composable () -> Unit) {
    // No dynamic color (keeps it consistent)
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}
