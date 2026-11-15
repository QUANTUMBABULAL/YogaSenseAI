package com.chinmai.yogasense.ui.practice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.camera.CameraLensPolicy
import com.chinmai.yogasense.camera.CameraPreview
import com.chinmai.yogasense.camera.LensResolution
import com.chinmai.yogasense.camera.CameraStatus
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.coaching.VoiceState
import com.chinmai.yogasense.pose.FramingGuidance
import com.chinmai.yogasense.pose.InferenceStats
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.pose.PoseLandmarkerEngine
import com.chinmai.yogasense.ui.theme.Accent
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.Warn
import com.chinmai.yogasense.ui.theme.WarmWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.chinmai.yogasense.coaching.Cue
import com.chinmai.yogasense.coaching.CuePriority
import com.chinmai.yogasense.coaching.VoiceStatus
import com.chinmai.yogasense.session.GuidedEffect
import com.chinmai.yogasense.session.GuidedEvent
import com.chinmai.yogasense.session.GuidedSessionMachine
import com.chinmai.yogasense.session.GuidedStage
import com.chinmai.yogasense.session.PauseReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * Immersive live practice.
 *
 * Layout: the camera preview fills the whole screen. It keeps FIT_CENTER with 4:3 streams (see
 * CameraPreview) so the full sensor field of view is shown, nothing is cropped and the skeleton maps
 * 1:1. On tall portrait phones this leaves bands above/below the image, which the HUD and controls
 * occupy, so they rarely cover the body.
 *
 * @param onClose exit action (navigation).
 * @param targetName selected asana name for the HUD (display only).
 * @param targetAsana selected asana for coaching content and reference; recognition still detects all poses.
 */
@Composable
fun LiveTrackingScreen(
    onClose: (() -> Unit)? = null,
    targetName: String? = null,
    targetAsana: Asana? = null,
    profile: PracticeProfileBinding = PracticeProfileBinding(),
    /** Called after Finish on the summary (the session is confirmed and saved first); defaults to [onClose]. */
    onFinished: (() -> Unit)? = onClose,
    /** Non-null = hands-free guided session over these asanas (same camera/recognition pipeline). */
    guidedPlan: List<Asana>? = null,
) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(granted()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        askedOnce = true
    }

    // Re-check when returning from system Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) hasPermission = granted() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { if (!hasPermission && !askedOnce) launcher.launch(Manifest.permission.CAMERA) }

    // A guided session's state machine lives in composition; rotating would recreate the activity and
    // restart the whole session. Its layouts are portrait, so the orientation is held while it's open
    // and the previous setting is restored when leaving.
    if (!guidedPlan.isNullOrEmpty()) {
        DisposableEffect(Unit) {
            val activity = context as? Activity
            val previous = activity?.requestedOrientation
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            onDispose { if (activity != null && previous != null) activity.requestedOrientation = previous }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            PracticeContent(onClose, onFinished, targetName, targetAsana, profile, guidedPlan)
        } else {
            val activity = context as? Activity
            val permanentlyDenied = askedOnce && activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            PermissionState(
                permanentlyDenied = permanentlyDenied,
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
            )
            if (onClose != null) {
                Box(Modifier.statusBarsPadding().padding(12.dp)) {
                    androidx.compose.material3.IconButton(onClick = onClose) {
                        androidx.compose.material3.Icon(com.chinmai.yogasense.ui.components.YsIcons.Close, "Close practice", tint = WarmWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeContent(onClose: (() -> Unit)?, onFinished: (() -> Unit)?, targetName: String?, targetAsana: Asana?, profile: PracticeProfileBinding, guidedPlan: List<Asana>? = null) {
    val context = LocalContext.current
    val isGuided = !guidedPlan.isNullOrEmpty()
    // Guided sessions default to the front camera; the choice is kept for the whole session.
    // Guided: start from a camera the device declares (front if it has one).
    val declared = remember { declaredLenses(context) }
    var lensFacing by rememberSaveable {
        mutableIntStateOf(CameraLensPolicy.defaultLens(isGuided).let { d -> if (isGuided) CameraLensPolicy.resolve(d, declared)?.bound ?: d else d })
    }
    // The lens CameraX actually bound (differs from lensFacing only after a fallback).
    var lensResolution by remember { mutableStateOf<LensResolution?>(null) }
    val boundLens = lensResolution?.takeIf { it.requested == lensFacing }?.bound ?: lensFacing
    // Guided only: the session waits on the camera choice before it starts.
    var cameraConfirmed by remember { mutableStateOf(!isGuided) }
    var cameraStatus by remember { mutableStateOf<CameraStatus>(CameraStatus.Starting) }
    // Bumped by "Retry camera" to rebind after a failed start (CameraPreview never retries by itself).
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var engine by remember { mutableStateOf<PoseLandmarkerEngine?>(null) }

    // Phase 7B.5: per-frame values are held as State objects and read only where needed
    // (skeleton: draw phase; strip: its own scope; everything else via derivedStateOf), so a new pose
    // result no longer recomposes the whole practice screen.
    val framesFlow: StateFlow<PoseFrame?> = engine?.frames ?: remember { MutableStateFlow(null) }
    val frameState = framesFlow.collectAsStateWithLifecycle()
    val statsFlow: StateFlow<InferenceStats> = engine?.stats ?: remember { MutableStateFlow(InferenceStats()) }

    // One recognizer per camera session: a new engine (lens flip, rotation, restart) => fresh state.
    val debugLogging = remember { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
    val controller = remember(engine) {
        engine?.let { LiveRecognitionController(log = if (debugLogging) { m -> Log.d("AsanaDebug", m) } else null) }
    }
    LaunchedEffect(controller) {
        val e = engine
        if (controller != null && e != null) controller.run(e.frames)
    }
    val recFlow: StateFlow<LiveRecognitionState> = controller?.state ?: remember { MutableStateFlow(LiveRecognitionState()) }
    val recStateHolder = recFlow.collectAsStateWithLifecycle()

    // One coaching session per practice screen; TextToSpeech is created once and released on exit.
    // Bound to the profile active when practice started: its settings, and its id for the saved record.
    val session = remember {
        PracticeSession(
            context, targetAsana, SystemClock.uptimeMillis(),
            initialPreferences = profile.preferences, diagnostics = profile.diagnostics,
            onPreferencesChanged = profile.savePreferences, onDiagnosticsChanged = profile.saveDiagnostics,
            onRecord = profile.record,
        )
    }
    DisposableEffect(session) { onDispose { session.release(SystemClock.uptimeMillis()) } }

    // ---- Guided session (Phase 9): one authoritative state machine over the SAME camera session ----
    val guided = remember { guidedPlan?.takeIf { it.isNotEmpty() }?.let { plan -> GuidedSessionMachine(plan) { a -> AsanaCatalog.of(a)?.name ?: a.displayName } } }
    var guidedState by remember { mutableStateOf(guided?.state) }
    var guidedRemaining by remember { mutableStateOf<Long?>(null) }
    fun runGuided(effects: List<GuidedEffect>) {
        val m = guided ?: return
        val now = SystemClock.uptimeMillis()
        // End the practice session first (it stops speech), then speak the completion line.
        effects.filterIsInstance<GuidedEffect.Completed>().forEach { _ -> session.end(now) }
        effects.filterIsInstance<GuidedEffect.Speak>().forEach { e ->
            if (e.interrupt) session.voice.stop() // cancel obsolete countdowns/announcements
            session.voice.speak(Cue(e.key, e.text, if (e.interrupt) CuePriority.FRAMING else CuePriority.INFO))
        }
        guidedState = m.state
        guidedRemaining = m.remainingMs()
        // Only the pose stages use the regular alignment coach; demos/countdowns/rests stay quiet.
        session.coachingMuted = m.state.stage != GuidedStage.WAITING_FOR_POSE && m.state.stage != GuidedStage.HOLDING
    }
    fun guide(e: GuidedEvent) { val m = guided ?: return; if (!cameraConfirmed) return; runGuided(m.onEvent(e)) }
    /** A tapped action, applied only if the stage the user saw is still current (no double skips). */
    fun guideAt(epoch: Int, e: GuidedEvent) { val m = guided ?: return; if (!cameraConfirmed) return; runGuided(m.onEventAt(epoch, e)) }

    // The camera (CameraX + MediaPipe) runs only when needed: always in free practice; in a guided
    // session only after Start and only in the stages where the user is in front of it. Leaving
    // composition unbinds CameraX and closes MediaPipe (CameraPreview's onDispose), so the camera
    // surface never sits under the intro, the demonstration video, the rest screen or the summary.
    val cameraOn = guided == null || (cameraConfirmed && guidedState?.needsCamera == true)
    LaunchedEffect(cameraOn) { if (!cameraOn) cameraStatus = CameraStatus.Starting } // no stale error/active state

    // End-session confirmation (guided). The session is paused while the dialog is open so nothing
    // advances behind it, and resumed only if the dialog paused it.
    var confirmExit by remember { mutableStateOf(false) }
    var pausedForConfirm by remember { mutableStateOf(false) }
    fun requestExit() {
        val m = guided ?: return
        if (confirmExit || m.state.stage == GuidedStage.SESSION_COMPLETED) return
        confirmExit = true
        if (!m.state.paused) { session.pause(SystemClock.uptimeMillis()); guide(GuidedEvent.Pause(PauseReason.USER)); pausedForConfirm = true }
    }
    fun keepGoing() {
        confirmExit = false
        if (pausedForConfirm) { pausedForConfirm = false; guide(GuidedEvent.Resume); session.resume(SystemClock.uptimeMillis()) }
    }
    fun confirmEndSession() { confirmExit = false; pausedForConfirm = false; guide(GuidedEvent.Exit) }
    // While the camera is being chosen, the practice clock is held and coaching stays quiet.
    LaunchedEffect(Unit) { if (!cameraConfirmed) { session.coachingMuted = true; session.pause(SystemClock.uptimeMillis()) } }
    LaunchedEffect(guided, cameraConfirmed) {
        val m = guided ?: return@LaunchedEffect
        if (!cameraConfirmed) return@LaunchedEffect
        session.resume(SystemClock.uptimeMillis())
        // Give TextToSpeech up to 3 s to initialise so the welcome isn't dropped.
        kotlinx.coroutines.withTimeoutOrNull(3_000) { session.voice.state.first { it.status != VoiceStatus.Initializing } }
        runGuided(m.start(SystemClock.uptimeMillis()))
        while (m.state.stage != GuidedStage.SESSION_COMPLETED) { guide(GuidedEvent.Tick(SystemClock.uptimeMillis())); delay(100) }
    }
    val uiState = session.ui.collectAsStateWithLifecycle()
    val coach by session.coachSettings.collectAsStateWithLifecycle()
    val visual by session.visualSettings.collectAsStateWithLifecycle()
    val voice: VoiceState by session.voice.state.collectAsStateWithLifecycle()

    // Snapshots from real recognition + framing feed the coach (main thread; lightweight).
    LaunchedEffect(session, controller, engine) {
        val e = engine ?: return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        combine(c.state, e.frames) { r, f -> r.recognition to f }.collect { (r, f) ->
            val t = SystemClock.uptimeMillis()
            session.onSnapshot(f, r, t)
            if (guided != null) {
                val hold = session.ui.value.live?.hold
                guide(GuidedEvent.Frame(t, r.asana.takeIf { r.isStable }, hold?.counting == true, hold?.asana))
            }
        }
    }
    var nowMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(session) {
        var lastPerfLog = 0L
        while (true) {
            nowMs = SystemClock.uptimeMillis()
            session.tick(nowMs)
            if (nowMs - lastPerfLog >= 5_000) {
                lastPerfLog = nowMs
                val st = engine?.stats?.value
                val rs = controller?.state?.value
                com.chinmai.yogasense.perf.PerfLog.log {
                    "practice inferenceFps=%.1f latencyMs=%d recognitionMs=%.2f snapshotMainMs=%.2f snapshots=%d pose=%s".format(
                        st?.inferenceFps ?: 0f, st?.latencyMs ?: 0L, rs?.recognitionMs ?: 0f, session.snapshotCostEmaMs, session.snapshots,
                        engine?.frames?.value?.hasPose == true,
                    )
                }
            }
            delay(1000)
        }
    }
    // Auto-pause when the app goes to the background; the user resumes explicitly.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_PAUSE) { session.pause(SystemClock.uptimeMillis()); guide(GuidedEvent.Pause(PauseReason.BACKGROUND)) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // Camera lost → pause safely; camera back → resume automatically (only if the camera caused the pause).
    LaunchedEffect(cameraStatus, guided, cameraConfirmed) {
        if (guided == null || !cameraConfirmed) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        when (cameraStatus) {
            is CameraStatus.Error -> { session.pause(now); guide(GuidedEvent.Pause(PauseReason.CAMERA)) }
            CameraStatus.Active -> if (guided.state.pausedBy == PauseReason.CAMERA) { guide(GuidedEvent.Resume); session.resume(now) }
            else -> Unit
        }
    }

    var sheet by remember { mutableStateOf<PracticeSheet?>(null) }
    val targetInfo = targetAsana?.let { AsanaCatalog.of(it) }
    val currentClose by rememberUpdatedState(onClose)
    // Navigation away happens at most once (a double tap on Close/Done must not pop two screens).
    var closing by remember { mutableStateOf(false) }
    fun closeOnce() { if (!closing) { closing = true; currentClose?.invoke() } }

    // Values the screen layout needs, recomputed per frame but only invalidating when they change.
    val cameraNow by rememberUpdatedState(cameraStatus)
    val recognitionLabel by remember { derivedStateOf(structuralEqualityPolicy()) { PracticeGuidance.recognitionLabel(recStateHolder.value.recognition) } }
    val recognised by remember { derivedStateOf { recStateHolder.value.recognition.isStable } }
    val paused by remember { derivedStateOf { uiState.value.paused } }
    val elapsedMs by remember { derivedStateOf { uiState.value.elapsedMs / 1000 * 1000 } }
    val summary by remember { derivedStateOf(referentialEqualityPolicy()) { uiState.value.summary } }
    val guidance by remember {
        derivedStateOf(structuralEqualityPolicy()) {
            val f = frameState.value
            val ui = uiState.value
            PracticeGuidance.of(cameraNow, f?.hasPose == true, FramingGuidance.evaluate(f), recStateHolder.value.recognition, ui.live, ui.paused)
        }
    }
    val spoken by remember {
        derivedStateOf(structuralEqualityPolicy()) { uiState.value.let { u -> u.lastCue?.takeIf { nowMs - u.lastCueAtMs < 6_000 }?.text } }
    }

    // The camera surface takes all space between the top HUD and the bottom controls. FIT_CENTER
    // (4:3) inside that area shows the whole field of view and keeps every overlay off the body,
    // on any portrait aspect ratio. Preview and skeleton share the same box, so mapping stays exact.
    // Not composed at all behind the guided camera step (nothing to see, tap or read with TalkBack).
    if (cameraConfirmed) Column(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            TopHud(
                targetName = targetName ?: guidedState?.current?.let { AsanaCatalog.of(it)?.name ?: it.displayName },
                camera = cameraStatus,
                recognitionLabel = recognitionLabel,
                recognised = recognised,
                voiceMode = coach.mode,
                voiceStatus = voice.status,
                speaking = voice.speaking,
                // Guided: the bottom bar's End (with confirmation) is the only exit, and the camera
                // chosen before the start is kept; free practice keeps its close and flip buttons.
                onClose = if (guided != null) null else currentClose,
                onFlip = if (guided != null) null else {
                    {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                        else CameraSelector.LENS_FACING_BACK
                    }
                },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (cameraOn) androidx.compose.runtime.key(cameraAttempt) {
                CameraPreview(
                    lensFacing = lensFacing,
                    onEngineReady = { engine = it },
                    onStatus = { cameraStatus = it },
                    modifier = Modifier.fillMaxSize(),
                    onLensResolved = { lensResolution = it },
                )
            }
            if (cameraOn && visual.liveSkeleton) {
                SkeletonOverlay(
                    frame = { frameState.value },
                    mirror = boundLens == CameraSelector.LENS_FACING_FRONT,
                    highlightWeak = visual.correctionHighlights,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Developer diagnostics may overlap the image; they are off by default.
            // Requested camera missing: say which one is used instead (the setup card shows it itself).
            val fallback = lensResolution?.takeIf { cameraConfirmed && it.requested == lensFacing }?.let(CameraLensPolicy::fallbackMessage)
            if (fallback != null) {
                Text(
                    fallback, color = WarmWhite, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (visual.diagnostics) {
                val stats by statsFlow.collectAsStateWithLifecycle()
                DiagnosticsPanel(cameraStatus, stats, frameState.value, recStateHolder.value, Modifier.align(Alignment.TopStart))
            }
        }

        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (recognised && !paused) LiveAssessmentStrip { uiState.value.live }
            GuidancePanel(
                guidance,
                PracticeGuidance.caption(spoken, guidance.text),
                reference = targetInfo?.takeIf { visual.referenceFigure },
            )
            PracticeControls(
                elapsedMs = elapsedMs,
                paused = paused,
                onPauseToggle = {
                    val t = SystemClock.uptimeMillis()
                    if (guided != null) {
                        if (guided.state.paused) { guide(GuidedEvent.Resume); session.resume(t) } else { session.pause(t); guide(GuidedEvent.Pause(PauseReason.USER)) }
                    } else if (uiState.value.paused) session.resume(t) else session.pause(t)
                },
                onVoice = { sheet = PracticeSheet.Voice },
                onVisual = { sheet = PracticeSheet.Visual },
                onEnd = { if (guided != null) requestExit() else session.end(SystemClock.uptimeMillis()) },
            )
        }
    }

    sheet?.let { s ->
        PracticeSettingsSheet(
            sheet = s, coach = coach, visual = visual, voiceStatus = voice.status, hasTarget = targetInfo != null,
            onCoach = session::updateCoach, onVisual = session::updateVisual, onDismiss = { sheet = null },
        )
    }
    val gs = guidedState
    if (guided != null && gs != null) {
        // System Back: closes the camera step, asks before ending a running session, and acts as
        // Done on the summary. While the confirmation is open, the dialog itself handles Back.
        androidx.activity.compose.BackHandler(enabled = !confirmExit) {
            when {
                !cameraConfirmed -> closeOnce()
                gs.stage == GuidedStage.SESSION_COMPLETED -> { if (!gs.endedEarly) session.confirmEnd(); closeOnce() }
                else -> requestExit()
            }
        }
    }
    if (guided != null && !cameraConfirmed) {
        GuidedCameraSetup(
            poseCount = guided.state.plan.size,
            selected = lensFacing,
            available = declared,
            onSelect = { lensFacing = it }, // selection only; no camera is opened here
            onStart = { if (!cameraConfirmed) cameraConfirmed = true },
            onClose = ::closeOnce,
        )
    }
    if (guided != null && gs != null && cameraConfirmed) {
        val e = gs.epoch
        GuidedSessionOverlay(
            gs, guidedRemaining, guided.config.holdTargetMs, summary,
            GuidedActions(
                onDemoEnded = { epoch -> guide(GuidedEvent.DemoEnded(epoch)) },
                onSkipDemo = { guideAt(e, GuidedEvent.SkipDemo) },
                onSkipPose = { guideAt(e, GuidedEvent.SkipPose) },
                onRetry = { guideAt(e, GuidedEvent.RetryPose) },
                onResume = { guide(GuidedEvent.Resume); session.resume(SystemClock.uptimeMillis()) },
                onPause = { session.pause(SystemClock.uptimeMillis()); guide(GuidedEvent.Pause(PauseReason.USER)) },
                onExit = ::requestExit,
                // A finished plan counts as "ended by the user" for the existing completion policy; an early exit does not.
                onDone = { if (!gs.endedEarly) session.confirmEnd(); closeOnce() },
                onRetryCamera = { cameraStatus = CameraStatus.Starting; cameraAttempt++ },
            ),
        )
        if (confirmExit) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = ::keepGoing,
                containerColor = com.chinmai.yogasense.ui.theme.Surface1,
                titleContentColor = WarmWhite,
                textContentColor = TextMuted,
                title = { Text("End this session?") },
                text = { Text("You'll see a summary of the poses you've done so far.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = ::confirmEndSession) {
                        Text("End session", color = com.chinmai.yogasense.ui.theme.Coral, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = ::keepGoing) { Text("Keep going", color = WarmWhite) }
                },
            )
        }
    }
    if (guided == null) summary?.let { sm ->
        val currentFinished by rememberUpdatedState(onFinished)
        val keepPractising = { session.continueAfterSummary(SystemClock.uptimeMillis()) }
        androidx.activity.compose.BackHandler(onBack = keepPractising)
        SessionSummaryScreen(
            summary = sm,
            target = targetAsana,
            willSave = SessionRecordPolicy.shouldSave(sm),
            onFinish = { session.confirmEnd(); currentFinished?.invoke() ?: keepPractising() },
            onKeepPractising = keepPractising,
        )
    }
}

@Composable
private fun PermissionState(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Midnight)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access needed", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(
            "YogaSense AI uses the camera to track your body pose. Video is processed on this phone only and is never stored or uploaded.",
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            Text("Permission was denied. Enable Camera in app settings.", color = Warn, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
                Text("Open settings")
            }
        } else {
            Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
                Text("Allow camera")
            }
        }
    }
}

/** Reads the live assessment in its own scope, so only the strip recomposes on each pose result. */
@Composable
private fun LiveAssessmentStrip(live: () -> com.chinmai.yogasense.coaching.assessment.LiveAssessment?) {
    AssessmentStrip(live())
}

/**
 * What practice needs from the active profile. All callbacks are already bound to that profile's id
 * by the caller, so a session can only read and write its own profile's data.
 */
data class PracticeProfileBinding(
    val preferences: com.chinmai.yogasense.profile.UserPreferences = com.chinmai.yogasense.profile.UserPreferences(),
    val diagnostics: Boolean = false,
    val savePreferences: (com.chinmai.yogasense.profile.UserPreferences) -> Unit = {},
    val saveDiagnostics: (Boolean) -> Unit = {},
    val record: (com.chinmai.yogasense.profile.CompletedPracticeSession) -> Unit = {},
)
