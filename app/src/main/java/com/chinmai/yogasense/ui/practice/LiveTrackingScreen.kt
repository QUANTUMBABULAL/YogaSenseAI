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
import com.chinmai.yogasense.camera.CameraPreview
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            PracticeContent(onClose, targetName, targetAsana, profile)
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
private fun PracticeContent(onClose: (() -> Unit)?, targetName: String?, targetAsana: Asana?, profile: PracticeProfileBinding) {
    val context = LocalContext.current
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraStatus by remember { mutableStateOf<CameraStatus>(CameraStatus.Starting) }
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
    val uiState = session.ui.collectAsStateWithLifecycle()
    val coach by session.coachSettings.collectAsStateWithLifecycle()
    val visual by session.visualSettings.collectAsStateWithLifecycle()
    val voice: VoiceState by session.voice.state.collectAsStateWithLifecycle()

    // Snapshots from real recognition + framing feed the coach (main thread; lightweight).
    LaunchedEffect(session, controller, engine) {
        val e = engine ?: return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        combine(c.state, e.frames) { r, f -> r.recognition to f }.collect { (r, f) ->
            session.onSnapshot(f, r, SystemClock.uptimeMillis())
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
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_PAUSE) session.pause(SystemClock.uptimeMillis()) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    var sheet by remember { mutableStateOf<PracticeSheet?>(null) }
    val targetInfo = targetAsana?.let { AsanaCatalog.of(it) }
    val currentClose by rememberUpdatedState(onClose)

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
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            TopHud(
                targetName = targetName,
                camera = cameraStatus,
                recognitionLabel = recognitionLabel,
                recognised = recognised,
                voiceMode = coach.mode,
                voiceStatus = voice.status,
                speaking = voice.speaking,
                onClose = currentClose,
                onFlip = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                    else CameraSelector.LENS_FACING_BACK
                },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraPreview(
                lensFacing = lensFacing,
                onEngineReady = { engine = it },
                onStatus = { cameraStatus = it },
                modifier = Modifier.fillMaxSize(),
            )
            if (visual.liveSkeleton) {
                SkeletonOverlay(
                    frame = { frameState.value },
                    mirror = lensFacing == CameraSelector.LENS_FACING_FRONT,
                    highlightWeak = visual.correctionHighlights,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Developer diagnostics may overlap the image; they are off by default.
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
                onPauseToggle = { val t = SystemClock.uptimeMillis(); if (uiState.value.paused) session.resume(t) else session.pause(t) },
                onVoice = { sheet = PracticeSheet.Voice },
                onVisual = { sheet = PracticeSheet.Visual },
                onEnd = { session.end(SystemClock.uptimeMillis()) },
            )
        }
    }

    sheet?.let { s ->
        PracticeSettingsSheet(
            sheet = s, coach = coach, visual = visual, voiceStatus = voice.status, hasTarget = targetInfo != null,
            onCoach = session::updateCoach, onVisual = session::updateVisual, onDismiss = { sheet = null },
        )
    }
    summary?.let { sm ->
        SessionSummaryDialog(
            sm,
            onDone = { session.confirmEnd(); currentClose?.invoke() ?: session.continueAfterSummary(SystemClock.uptimeMillis()) },
            onContinue = { session.continueAfterSummary(SystemClock.uptimeMillis()) },
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
