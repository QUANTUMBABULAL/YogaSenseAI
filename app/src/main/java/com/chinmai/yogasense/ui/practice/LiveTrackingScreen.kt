package com.chinmai.yogasense.ui.practice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.chinmai.yogasense.camera.CameraPreview
import com.chinmai.yogasense.camera.CameraStatus
import com.chinmai.yogasense.pose.FramingGuidance
import com.chinmai.yogasense.pose.FramingStatus
import com.chinmai.yogasense.pose.InferenceStats
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.pose.PoseLandmarkerEngine
import com.chinmai.yogasense.ui.theme.Accent
import com.chinmai.yogasense.ui.theme.AccentDim
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.Warn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LiveTrackingScreen() {
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Header()
        if (hasPermission) {
            TrackingContent()
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
        }
    }
}

@Composable
private fun Header() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("YogaSense", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(" AI", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text("Pose recognition", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ColumnScope.TrackingContent() {
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraStatus by remember { mutableStateOf<CameraStatus>(CameraStatus.Starting) }
    var engine by remember { mutableStateOf<PoseLandmarkerEngine?>(null) }

    val framesFlow: StateFlow<PoseFrame?> = engine?.frames ?: remember { MutableStateFlow(null) }
    val frame by framesFlow.collectAsStateWithLifecycle()
    val statsFlow: StateFlow<InferenceStats> = engine?.stats ?: remember { MutableStateFlow(InferenceStats()) }
    val stats by statsFlow.collectAsStateWithLifecycle()

    // One recognizer per camera session: a new engine (lens flip, rotation, restart) => fresh state.
    val context = LocalContext.current
    val debugLogging = remember {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val controller = remember(engine) {
        engine?.let { LiveRecognitionController(log = if (debugLogging) { m -> Log.d("AsanaDebug", m) } else null) }
    }
    LaunchedEffect(controller) {
        val e = engine
        if (controller != null && e != null) controller.run(e.frames)
    }
    val recFlow: StateFlow<LiveRecognitionState> = controller?.state ?: remember { MutableStateFlow(LiveRecognitionState()) }
    val recState by recFlow.collectAsStateWithLifecycle()

    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
    ) {
        CameraPreview(
            lensFacing = lensFacing,
            onEngineReady = { engine = it },
            onStatus = { cameraStatus = it },
            modifier = Modifier.fillMaxSize(),
        )
        SkeletonOverlay(
            frame = frame,
            mirror = lensFacing == CameraSelector.LENS_FACING_FRONT,
            modifier = Modifier.fillMaxSize(),
        )
        StatusChips(cameraStatus, stats, frame, Modifier.align(Alignment.TopStart).padding(12.dp))
        (cameraStatus as? CameraStatus.Error)?.let {
            Text(
                it.message,
                color = Warn,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
    AsanaCard(recState, poseVisible = frame?.hasPose == true)
    GuidanceBar(
        status = FramingGuidance.evaluate(frame),
        stats = stats,
        onFlip = {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
        },
    )
}

@Composable
private fun StatusChips(camera: CameraStatus, stats: InferenceStats, frame: PoseFrame?, modifier: Modifier) {
    val camText = when (camera) {
        CameraStatus.Starting -> "Camera starting"
        CameraStatus.Active -> "Camera on"
        is CameraStatus.Error -> "Camera error"
    }
    val poseText = when {
        stats.error != null && !stats.ready -> "Model error"
        !stats.ready -> "Loading model"
        frame?.hasPose == true -> "Pose tracked"
        else -> "No pose"
    }
    val valid = FramingGuidance.reliableCount(frame)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(camText, camera is CameraStatus.Active)
        Chip(poseText, frame?.hasPose == true)
        Chip("%.1f inference FPS".format(stats.inferenceFps), stats.inferenceFps >= 15f)
        Chip("$valid / 33 landmarks", valid >= 25)
    }
}

@Composable
private fun Chip(text: String, good: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xB3101512))
            .border(1.dp, if (good) AccentDim else Color(0x33FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (good) Accent else Warn))
        Spacer(Modifier.size(6.dp))
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun GuidanceBar(status: FramingStatus, stats: InferenceStats, onFlip: () -> Unit) {
    val ok = status == FramingStatus.FULL_BODY
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(status.message, color = if (ok) Accent else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (ok) "Hold still to keep tracking steady." else "Place the phone at hip height, 2–3 m away, so head to feet fit inside the frame.",
                color = TextMuted,
                fontSize = 12.sp,
            )
            if (stats.ready && stats.latencyMs > 0) {
                Text("Latency ${stats.latencyMs} ms", color = TextMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.size(12.dp))
        OutlinedButton(onClick = onFlip) { Text("Flip", color = Accent) }
    }
}

@Composable
private fun PermissionState(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
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
