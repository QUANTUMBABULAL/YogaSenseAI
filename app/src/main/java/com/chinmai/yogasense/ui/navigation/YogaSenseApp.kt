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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chinmai.yogasense.app.AppGraph
import com.chinmai.yogasense.app.LocalProfileSession
import com.chinmai.yogasense.app.ProfileSession
import com.chinmai.yogasense.data.LegacyWellnessStatus
import com.chinmai.yogasense.profile.CreateResult
import com.chinmai.yogasense.profile.ProfileGate
import com.chinmai.yogasense.profile.UserProfile
import com.chinmai.yogasense.ui.components.LoadingState
import com.chinmai.yogasense.ui.home.HomeViewModel
import com.chinmai.yogasense.ui.practice.PracticeProfileBinding
import com.chinmai.yogasense.ui.profile.ProfileChooserScreen
import com.chinmai.yogasense.ui.profile.ProfileCreateScreen
import com.chinmai.yogasense.ui.progress.ProgressViewModel
import com.chinmai.yogasense.ui.wellness.WellnessGate
import com.chinmai.yogasense.ui.onboarding.OnboardingMode
import com.chinmai.yogasense.ui.onboarding.OnboardingScreen
import com.chinmai.yogasense.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch
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
import com.chinmai.yogasense.ui.wellness.CheckInScreen
import com.chinmai.yogasense.ui.wellness.QuestionnaireRecordScreen
import com.chinmai.yogasense.ui.wellness.QuestionnaireScreen
import com.chinmai.yogasense.ui.wellness.SupportScreen
import com.chinmai.yogasense.ui.wellness.WellnessHistoryScreen
import com.chinmai.yogasense.ui.wellness.WellnessOverviewScreen
import com.chinmai.yogasense.wellness.QuestionnaireType
import com.chinmai.yogasense.ui.learning.PoseLearningScreen
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.ui.practice.LiveTrackingScreen
import com.chinmai.yogasense.ui.prepare.PracticePrepScreen
import com.chinmai.yogasense.ui.prepare.PracticeSettingsScreen
import com.chinmai.yogasense.ui.profile.ProfileScreen
import com.chinmai.yogasense.ui.progress.ProgressScreen
import com.chinmai.yogasense.ui.theme.PracticeTheme
import com.chinmai.yogasense.ui.theme.SystemBarIcons
import com.chinmai.yogasense.ui.theme.WarmWhite


private val tabs = listOf(
    BottomNavItem(Routes.HOME, "Home", YsIcons.Home),
    BottomNavItem(Routes.EXPLORE, "Explore", YsIcons.Explore),
    BottomNavItem(Routes.PROGRESS, "Progress", YsIcons.Progress),
    BottomNavItem(Routes.WELLNESS, "Wellness", YsIcons.Leaf),
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
 * Root: the profile gate. Profile-scoped screens (the whole NavHost) exist ONLY under an active
 * profile, so no route can be reached without one.
 * - No profile → create. Profiles but none active → choose. Active → main shell.
 * - Each active profile gets a [ProfileSession] (repositories bound to its id, its own
 *   ViewModelStore). Switching or deleting ends that session: observers are cancelled and every
 *   ViewModel created under it is cleared, so no previous profile's state can be shown.
 */
@Composable
fun YogaSenseApp() {
    val context = LocalContext.current
    val graph = remember {
        val t0 = android.os.SystemClock.uptimeMillis()
        AppGraph.get(context).also { com.chinmai.yogasense.perf.PerfLog.log { "AppGraph.get took ${android.os.SystemClock.uptimeMillis() - t0} ms" } }
    }
    val gate by graph.profiles.gate.collectAsStateWithLifecycle()
    val creating by graph.profiles.creating.collectAsStateWithLifecycle()
    val wellnessStatus by graph.wellnessStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var addingProfile by rememberSaveable { mutableStateOf(false) }
    // Profile the user was in when they opened "Switch profile", so the chooser can go back to it.
    var returnToProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var createError by remember { mutableStateOf<String?>(null) }
    val hasLegacySettings by produceState(false, gate) { value = graph.legacySettingsPending() }

    fun create(name: String, level: com.chinmai.yogasense.data.db.ExperienceLevel) {
        graph.appScope.launch(kotlinx.coroutines.Dispatchers.Main) { // app scope: a create in progress survives leaving the screen
            when (val r = graph.profiles.create(name, level)) {
                is CreateResult.Invalid -> createError = r.message
                is CreateResult.Created -> { createError = null; addingProfile = false }
                CreateResult.Ignored -> Unit // repeated tap while creating
            }
        }
    }

    SystemBarIcons(darkIcons = true)
    // Startup marker: the first non-loading screen is "fully drawn" (logcat: Fully drawn …).
    val activity = context as? android.app.Activity
    val firstScreen = gate !is ProfileGate.Loading
    LaunchedEffect(firstScreen) {
        if (firstScreen) {
            com.chinmai.yogasense.perf.PerfLog.mark("profile gate resolved: ${gate::class.simpleName}")
            activity?.reportFullyDrawn()
        }
    }
    when (val g = gate) {
        // Static placeholder for the brief profile restore: no spinner/infinite animation on the startup path.
        ProfileGate.Loading -> StartupPlaceholder()
        ProfileGate.NoProfiles -> ProfileCreateScreen(
            isFirstProfile = true,
            legacyWellnessRecords = (wellnessStatus as? LegacyWellnessStatus.AwaitingFirstProfile)?.records,
            hasLegacySettings = hasLegacySettings,
            creating = creating, error = createError, onCreate = ::create, onCancel = null,
        )
        is ProfileGate.ChooseProfile ->
            if (addingProfile) {
                androidx.activity.compose.BackHandler { addingProfile = false }
                ProfileCreateScreen(false, null, false, creating, createError, ::create, onCancel = { addingProfile = false; createError = null })
            } else {
                val previous = g.profiles.firstOrNull { it.userId == returnToProfileId }
                ProfileChooserScreen(
                g.profiles, busy = creating,
                current = previous,
                onBack = previous?.let { p -> { scope.launch { graph.profiles.select(p.userId) } } },
                onSelect = { p -> scope.launch { graph.profiles.select(p.userId) } },
                onAdd = { addingProfile = true },
                onDelete = { p -> graph.appScope.launch { graph.profiles.delete(p.userId) } },
            )
            }
        is ProfileGate.Active -> {
            val userId = g.profile.userId
            val session = remember(userId) { graph.sessionFor(userId) }
            DisposableEffect(session) { onDispose { graph.endSessionIfInactive(session.userId) } }
            val shellStart = remember { android.os.SystemClock.uptimeMillis() }
            LaunchedEffect(Unit) {
                androidx.compose.runtime.withFrameNanos { }
                com.chinmai.yogasense.perf.PerfLog.log { "MainShell first composition+frame took ${android.os.SystemClock.uptimeMillis() - shellStart} ms" }
            }
            CompositionLocalProvider(LocalProfileSession provides session, LocalViewModelStoreOwner provides session) {
                val pv by session.personalisationView.collectAsStateWithLifecycle()
                val view = pv
                when {
                    view == null -> StartupPlaceholder()
                    // New profiles complete onboarding before Home; the gate opens by itself when it's stored as completed.
                    view.mustOnboard -> {
                        val vm = viewModel(key = "onboarding-required") { OnboardingViewModel(session.personalisation, view, OnboardingMode.Required) }
                        OnboardingScreen(vm, g.profile.displayName, onExit = {}, onLeave = { scope.launch { graph.profiles.clear() } })
                    }
                    else -> {
                        // "Switch profile" opens the chooser ON TOP of the app without leaving the profile,
                        // so Back returns to the exact page it was opened from (Profile), not to Home.
                        var switching by rememberSaveable { mutableStateOf(false) }
                        Box(Modifier.fillMaxSize()) {
                            // key: a different profile always gets a fresh shell (navigation + ViewModels),
                            // so nothing from the previous profile can remain on screen.
                            androidx.compose.runtime.key(userId) {
                                MainShell(
                                    profile = g.profile,
                                    profileCount = g.profiles.size,
                                    session = session,
                                    graph = graph,
                                    wellnessStatus = wellnessStatus,
                                    personalisation = view,
                                    onSwitchProfile = { addingProfile = false; createError = null; switching = true },
                                    onDeleteProfile = { graph.appScope.launch { graph.profiles.delete(userId) } },
                                )
                            }
                            if (switching) {
                                if (addingProfile) {
                                    androidx.activity.compose.BackHandler { addingProfile = false }
                                    ProfileCreateScreen(false, null, false, creating, createError, ::create, onCancel = { addingProfile = false; createError = null })
                                } else {
                                    ProfileChooserScreen(
                                        g.profiles, busy = creating,
                                        current = g.profile,
                                        onBack = { switching = false },
                                        onSelect = { p ->
                                            switching = false
                                            if (p.userId != userId) scope.launch { graph.profiles.select(p.userId) }
                                        },
                                        onAdd = { addingProfile = true },
                                        onDelete = { p -> graph.appScope.launch { graph.profiles.delete(p.userId) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Main app shell for the active profile.
 * - Four bottom tabs (Home, Explore, Progress, Profile); back from a tab returns to Home.
 * - Asana detail and Live Practice are full-screen destinations without the bottom bar.
 * - Live Practice hosts the existing LiveTrackingScreen inside the dark PracticeTheme; leaving it
 *   disposes the screen, which releases CameraX and MediaPipe through its own DisposableEffect.
 */
@Composable
private fun MainShell(
    profile: UserProfile,
    profileCount: Int,
    session: ProfileSession,
    graph: AppGraph,
    wellnessStatus: LegacyWellnessStatus,
    personalisation: com.chinmai.yogasense.profile.PersonalisationView,
    onSwitchProfile: () -> Unit,
    onDeleteProfile: () -> Unit,
) {
    val nav = rememberNavController()
    val uiScope = rememberCoroutineScope()
    // Wellness routes use the Room store only after the one-time legacy import is resolved.
    val gated: @Composable (back: (() -> Unit)?, content: @Composable () -> Unit) -> Unit = { back, content ->
        WellnessGate(wellnessStatus, profile, onRetry = graph::retryLegacyWellness, onAssign = { graph.assignLegacyWellness(profile.userId) }, onBack = back, content = content)
    }
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
                    onStartPractice = { nav.navigate(Routes.prepare(it)) },
                    onOpenAsana = { nav.navigate(Routes.asana(it)) },
                    onOpenExplore = { nav.navigateToTab(Routes.EXPLORE) },
                    onOpenProfile = { nav.navigateToTab(Routes.PROFILE) },
                    onCheckIn = { nav.navigate(Routes.CHECK_IN) },
                    onOpenProgress = { nav.navigateToTab(Routes.PROGRESS) },
                    profileName = profile.displayName,
                    wellness = session.wellness.takeIf { wellnessStatus.isResolved },
                    personalisation = personalisation,
                    onPersonalise = { nav.navigate(Routes.onboarding()) },
                    onStartGuided = { plan -> nav.navigate(Routes.guided(plan)) },
                    onDismissPersonalise = { uiScope.launch { session.personalisation.dismissPrompt() } },
                    viewModel = viewModel { HomeViewModel(session.practiceHistory) },
                )
            }
            composable(Routes.EXPLORE) {
                SystemBarIcons(darkIcons = true)
                ExploreScreen(onOpenAsana = { nav.navigate(Routes.asana(it)) })
            }
            composable(Routes.PROGRESS) {
                SystemBarIcons(darkIcons = true)
                ProgressScreen(
                    onStartPractice = { nav.navigate(Routes.prepare()) },
                    onOpenWellness = { nav.navigateToTab(Routes.WELLNESS) },
                    viewModel = viewModel { ProgressViewModel(session.practiceHistory) },
                )
            }
            composable(Routes.PROFILE) {
                SystemBarIcons(darkIcons = true)
                ProfileScreen(
                    onWellnessData = { nav.navigate(Routes.WELLNESS_HISTORY) },
                    onPracticeSettings = { nav.navigate(Routes.PRACTICE_SETTINGS) },
                    profile = profile, profileCount = profileCount,
                    onSwitchProfile = onSwitchProfile, onDeleteProfile = onDeleteProfile,
                    personalisation = personalisation,
                    onEditPersonalisation = { step -> nav.navigate(Routes.onboarding(step?.ordinal ?: -1)) },
                )
            }
            composable(Routes.WELLNESS) {
                SystemBarIcons(darkIcons = true)
                gated(null) {
                    WellnessOverviewScreen(
                        onCheckIn = { nav.navigate(Routes.CHECK_IN) },
                        onQuestionnaire = { nav.navigate(Routes.questionnaire(it.name)) },
                        onHistory = { nav.navigate(Routes.WELLNESS_HISTORY) },
                        onSupport = { nav.navigate(Routes.SUPPORT) },
                        onRecord = { nav.navigate(Routes.record(it)) },
                    )
                }
            }
            composable(
                Routes.ONBOARDING,
                arguments = listOf(navArgument("step") { type = NavType.IntType; defaultValue = -1 }),
                enterTransition = { slideInVertically(tween(280)) { it / 6 } + fadeIn(tween(280)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) { e ->
                val stepArg = e.arguments?.getInt("step") ?: -1
                val mode = com.chinmai.yogasense.profile.OnboardingStep.entries.getOrNull(stepArg)?.let { OnboardingMode.Edit(it) } ?: OnboardingMode.Optional
                val vm = viewModel { OnboardingViewModel(session.personalisation, session.personalisationView.value ?: personalisation, mode) }
                OnboardingScreen(vm, profile.displayName, onExit = { nav.popBackStack() })
            }
            composable(Routes.CHECK_IN) {
                SystemBarIcons(darkIcons = true)
                gated({ nav.popBackStack() }) { CheckInScreen(onBack = { nav.popBackStack() }) }
            }
            composable(Routes.QUESTIONNAIRE, arguments = listOf(navArgument("type") { type = NavType.StringType })) { e ->
                SystemBarIcons(darkIcons = true)
                val t = runCatching { QuestionnaireType.valueOf(e.arguments?.getString("type").orEmpty()) }.getOrNull()
                if (t == null) androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
                else gated({ nav.popBackStack() }) { QuestionnaireScreen(t, onDone = { nav.popBackStack() }) }
            }
            composable(Routes.RECORD, arguments = listOf(navArgument("recordId") { type = NavType.StringType })) { e ->
                SystemBarIcons(darkIcons = true)
                gated({ nav.popBackStack() }) { QuestionnaireRecordScreen(e.arguments?.getString("recordId").orEmpty(), onBack = { nav.popBackStack() }) }
            }
            composable(Routes.WELLNESS_HISTORY) {
                SystemBarIcons(darkIcons = true)
                gated({ nav.popBackStack() }) { WellnessHistoryScreen(onBack = { nav.popBackStack() }, onRecord = { nav.navigate(Routes.record(it)) }) }
            }
            composable(Routes.SUPPORT) {
                SystemBarIcons(darkIcons = true)
                SupportScreen(onBack = { nav.popBackStack() })
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
                    onPracticeNow = { nav.navigate(Routes.prepare(it.id)) },
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
                Routes.PREPARE,
                arguments = listOf(navArgument(Routes.ARG_ASANA) { type = NavType.StringType; nullable = true; defaultValue = null }),
                enterTransition = { slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                popExitTransition = { slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(200)) },
            ) { e ->
                SystemBarIcons(darkIcons = true)
                PracticePrepScreen(
                    initialAsanaId = e.arguments?.getString(Routes.ARG_ASANA),
                    onBack = { nav.popBackStack() },
                    onOpenLesson = { id -> nav.navigate(Routes.learn(id)) { popUpTo(Routes.PREPARE) { inclusive = true } } },
                    onStart = { id -> nav.navigate(Routes.practice(id)) },
                )
            }
            composable(
                Routes.PRACTICE_SETTINGS,
                enterTransition = { slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                popExitTransition = { slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(200)) },
            ) {
                SystemBarIcons(darkIcons = true)
                PracticeSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.GUIDED,
                arguments = listOf(navArgument("plan") { type = NavType.StringType; nullable = true; defaultValue = null }),
                enterTransition = { slideInVertically(tween(280)) { it / 4 } + fadeIn(tween(280)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) { e ->
                SystemBarIcons(darkIcons = false)
                val plan = Routes.parsePlan(e.arguments?.getString("plan")).ifEmpty { AsanaCatalog.all.map { it.asana } }
                val prefs by produceState<com.chinmai.yogasense.profile.UserPreferences?>(null, session) { value = session.loadPreferences() }
                PracticeTheme {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                        val p = prefs
                        if (p != null) {
                            LiveTrackingScreen(
                                onClose = { nav.popBackStack() },
                                profile = PracticeProfileBinding(
                                    preferences = p,
                                    diagnostics = session.deviceSettings.diagnostics,
                                    savePreferences = session::savePreferences,
                                    saveDiagnostics = { session.deviceSettings.diagnostics = it },
                                    record = session::recordPractice,
                                ),
                                guidedPlan = plan,
                            )
                        }
                    }
                }
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
                // Settings of the profile active when practice opens; writes stay bound to its id.
                val prefs by produceState<com.chinmai.yogasense.profile.UserPreferences?>(null, session) { value = session.loadPreferences() }
                PracticeTheme {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                        val p = prefs
                        if (p != null) {
                            LiveTrackingScreen(
                                onClose = { nav.popBackStack() }, targetName = target?.name, targetAsana = target?.asana,
                                onFinished = { if (!nav.popBackStack(Routes.PREPARE, inclusive = true)) nav.popBackStack() },
                                profile = PracticeProfileBinding(
                                    preferences = p,
                                    diagnostics = session.deviceSettings.diagnostics,
                                    savePreferences = session::savePreferences,
                                    saveDiagnostics = { session.deviceSettings.diagnostics = it },
                                    record = session::recordPractice,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Brand mark shown while the active profile is restored (usually a few hundred ms). */
@Composable
private fun StartupPlaceholder() {
    Box(Modifier.fillMaxSize().background(WarmWhite), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Text("YogaSense", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = com.chinmai.yogasense.ui.theme.Ink)
            androidx.compose.material3.Text("AI", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = com.chinmai.yogasense.ui.theme.Coral)
        }
    }
}
