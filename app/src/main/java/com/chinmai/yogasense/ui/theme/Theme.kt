package com.chinmai.yogasense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Single dark forest-green scheme used app-wide (camera screens read best on a dark surround).
private val YogaColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    primaryContainer = AccentContainer,
    onPrimaryContainer = Accent,
    secondary = AccentDim,
    onSecondary = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    outline = Outline,
    outlineVariant = Outline,
    error = Danger,
)

@Composable
fun YogaSenseAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YogaColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
