package com.chinmai.yogasense.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chinmai.yogasense.ui.asanadetail.AsanaDetailScreen
import com.chinmai.yogasense.ui.components.BottomNavItem
import com.chinmai.yogasense.ui.components.BottomNavigationBar
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.explore.ExploreScreen
import com.chinmai.yogasense.ui.home.HomeScreen
import com.chinmai.yogasense.ui.learning.PoseLearningScreen
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.ui.practice.LiveTrackingScreen
import com.chinmai.yogasense.ui.profile.ProfileScreen
import com.chinmai.yogasense.ui.progress.ProgressScreen
import com.chinmai.yogasense.ui.theme.PracticeTheme
import com.chinmai.yogasense.ui.theme.SystemBarIcons
import com.chinmai.yogasense.ui.theme.WarmWhite


private val tabs = listOf(
    BottomNavItem(Routes.HOME, "Home", YsIcons.Home),
    BottomNavItem(Routes.EXPLORE, "Explore", YsIcons.Explore),
    BottomNavItem(Routes.PROGRESS, "Progress", YsIcons.Progress),
    BottomNavItem(Routes.PROFILE, "Profile", YsIcons.Profile),
)
private val tabRoutes = tabs.map { it.route }.toSet()

/** Switch tabs keeping one Home-rooted back stack and each tab's saved state. */
private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * App shell.
 * - Four bottom tabs (Home, Explore, Progress, Profile); back from a tab returns to Home.
 * - Asana detail and Live Practice are full-screen destinations without the bottom bar.
 * - Live Practice hosts the existing LiveTrackingScreen inside the dark PracticeTheme; leaving it
 *   disposes the screen, which releases CameraX and MediaPipe through its own DisposableEffect.
 */
@Composable
fun YogaSenseApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in tabRoutes

    Scaffold(
        containerColor = WarmWhite,
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // each screen handles its own system-bar insets
        bottomBar = {
            AnimatedVisibility(showBar, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                BottomNavigationBar(tabs, route) { nav.navigateToTab(it.route) }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(150)) },
        ) {
            composable(Routes.HOME) {
                SystemBarIcons(darkIcons = true)
                HomeScreen(
                    onStartPractice = { nav.navigate(Routes.practice()) },
                    onOpenAsana = { nav.navigate(Routes.asana(it)) },
                    onOpenExplore = { nav.navigateToTab(Routes.EXPLORE) },
                    onOpenProfile = { nav.navigateToTab(Routes.PROFILE) },
                )
            }
            composable(Routes.EXPLORE) {
                SystemBarIcons(darkIcons = true)
                ExploreScreen(onOpenAsana = { nav.navigate(Routes.asana(it)) })
            }
            composable(Routes.PROGRESS) {
                SystemBarIcons(darkIcons = true)
                ProgressScreen(onStartPractice = { nav.navigate(Routes.practice()) })
            }
            composable(Routes.PROFILE) {
                SystemBarIcons(darkIcons = true)
                ProfileScreen()
            }
            composable(
                Routes.ASANA,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                popExitTransition = { slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(200)) },
            ) { e ->
                SystemBarIcons(darkIcons = true)
                AsanaDetailScreen(
                    asanaId = e.arguments?.getString(Routes.ARG_ID),
                    onBack = { nav.popBackStack() },
                    onStartLearning = { nav.navigate(Routes.learn(it.id)) },
                    onPracticeNow = { nav.navigate(Routes.practice(it.id)) },
                )
            }
            composable(
                Routes.LEARN,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                popExitTransition = { slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(200)) },
            ) { e ->
                SystemBarIcons(darkIcons = true)
                // Skip and Continue both replace the lesson with practice, so Back from practice
                // returns to the asana detail page (never to Explore).
                val toPractice: (String) -> Unit = { route ->
                    nav.navigate(route) { popUpTo(Routes.LEARN) { inclusive = true } }
                }
                PoseLearningScreen(
                    asanaId = e.arguments?.getString(Routes.ARG_ID),
                    onBack = { nav.popBackStack() },
                    onSkip = { toPractice(it.practiceRoute) },
                    onContinue = { toPractice(it.practiceRoute) },
                )
            }
            composable(
                Routes.PRACTICE,
                arguments = listOf(navArgument(Routes.ARG_ASANA) { type = NavType.StringType; nullable = true; defaultValue = null }),
                enterTransition = { slideInVertically(tween(280)) { it / 4 } + fadeIn(tween(280)) },
                popExitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) { e ->
                SystemBarIcons(darkIcons = false)
                // The selected asana is shown as the practice target only; recognition still
                // detects all five supported poses and does not use this argument.
                val target = AsanaCatalog.byId(e.arguments?.getString(Routes.ARG_ASANA))
                PracticeTheme {
                    Box(Modifier.fillMaxSize()) {
                        LiveTrackingScreen(onClose = { nav.popBackStack() }, targetName = target?.name, targetAsana = target?.asana)
                    }
                }
            }
        }
    }
}
