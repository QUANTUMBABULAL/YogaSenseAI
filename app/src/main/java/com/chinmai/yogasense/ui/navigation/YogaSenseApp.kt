package com.chinmai.yogasense.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.home.HomeScreen
import com.chinmai.yogasense.ui.practice.LiveTrackingScreen
import com.chinmai.yogasense.ui.profile.ProfileScreen
import com.chinmai.yogasense.ui.progress.ProgressScreen
import com.chinmai.yogasense.ui.theme.Accent
import com.chinmai.yogasense.ui.theme.AccentContainer
import com.chinmai.yogasense.ui.theme.Background
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.TextMuted

enum class AppDestination(val label: String, val icon: ImageVector) {
    HOME("Home", YsIcons.Home),
    PRACTICE("Practice", YsIcons.Practice),
    PROGRESS("Progress", YsIcons.Progress),
    PROFILE("Profile", YsIcons.Profile),
}

/**
 * App shell: four top-level tabs with state-based navigation (no extra dependency).
 * - Selected tab survives rotation/process death (rememberSaveable).
 * - Each tab's saveable UI state (e.g. scroll) is kept via a SaveableStateHolder.
 * - Practice hosts the existing LiveTrackingScreen unchanged; leaving the tab disposes it,
 *   which unbinds CameraX and closes MediaPipe through its existing DisposableEffect.
 * - Back from any tab returns to Home; back on Home exits.
 */
@Composable
fun YogaSenseApp() {
    var current by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    val holder = rememberSaveableStateHolder()
    BackHandler(enabled = current != AppDestination.HOME) { current = AppDestination.HOME }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                AppDestination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = d == current,
                        onClick = { current = d },
                        icon = { Icon(d.icon, contentDescription = null) },
                        label = { Text(d.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = AccentContainer,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner),
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab",
            ) { dest ->
                holder.SaveableStateProvider(dest.name) {
                    when (dest) {
                        AppDestination.HOME -> HomeScreen(
                            onStartPractice = { current = AppDestination.PRACTICE },
                            onOpenProgress = { current = AppDestination.PROGRESS },
                        )
                        AppDestination.PRACTICE -> LiveTrackingScreen()
                        AppDestination.PROGRESS -> ProgressScreen(onStartPractice = { current = AppDestination.PRACTICE })
                        AppDestination.PROFILE -> ProfileScreen()
                    }
                }
            }
        }
    }
}

