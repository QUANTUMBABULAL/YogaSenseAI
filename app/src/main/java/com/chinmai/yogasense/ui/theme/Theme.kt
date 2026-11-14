package com.chinmai.yogasense.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Light brand scheme for Home, Explore, Asana detail, Progress and Profile. */
private val LightScheme = lightColorScheme(
    primary = Midnight,
    onPrimary = WarmWhite,
    primaryContainer = Lime,
    onPrimaryContainer = Midnight,
    secondary = Coral,
    onSecondary = Midnight,
    secondaryContainer = CoralTint,
    onSecondaryContainer = CoralDeep,
    tertiary = LimeDeep,
    background = WarmWhite,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = InkMuted,
    surfaceContainer = CardWhite,
    surfaceContainerLow = WarmWhite,
    surfaceContainerHigh = SurfaceMuted,
    outline = Hairline,
    outlineVariant = Hairline,
    error = ErrorRed,
)

/** Dark immersive scheme for the live practice experience. */
private val PracticeScheme = darkColorScheme(
    primary = Lime,
    onPrimary = Midnight,
    primaryContainer = Surface2,
    onPrimaryContainer = Lime,
    secondary = Coral,
    onSecondary = Midnight,
    background = Midnight,
    onBackground = WarmWhite,
    surface = Surface1,
    onSurface = WarmWhite,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMuted,
    outline = Surface2,
    error = Warn,
)

@Composable
fun YogaSenseAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, typography = Typography, shapes = AppShapes, content = content)
}

@Composable
fun PracticeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PracticeScheme, typography = Typography, shapes = AppShapes, content = content)
}

/**
 * Sets status/navigation bar icon colour for the current destination
 * (dark icons on light screens, light icons on the dark practice screen).
 */
@Composable
fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(darkIcons) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
        }
        onDispose { }
    }
}
