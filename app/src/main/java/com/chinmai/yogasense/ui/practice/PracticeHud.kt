package com.chinmai.yogasense.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chinmai.yogasense.camera.CameraStatus
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.coaching.VoiceStatus
import com.chinmai.yogasense.coaching.assessment.LiveAssessment
import com.chinmai.yogasense.coaching.assessment.SessionSummary
import com.chinmai.yogasense.pose.FramingGuidance
import com.chinmai.yogasense.pose.InferenceStats
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.ui.components.AsanaFigure
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.Coral
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.Warn
import com.chinmai.yogasense.ui.theme.WarmWhite

/** Dark translucent surface readable over any camera image. */
val HudSurface = Color(0xD9171C28)

@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.clip(CircleShape).background(HudSurface).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))

@Composable
private fun HudIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = WarmWhite) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = HudSurface, contentColor = tint),
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp)) }
}

/** Top: close, selected asana, camera + recognition state, coach status, camera flip. */
@Composable
fun TopHud(
    targetName: String?,
    camera: CameraStatus,
    recognitionLabel: String,
    recognised: Boolean,
    voiceMode: VoiceMode,
    voiceStatus: VoiceStatus,
    speaking: Boolean,
    onClose: (() -> Unit)?,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onClose != null) HudIconButton(YsIcons.Close, "Close practice", onClose)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(HudSurface).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                targetName ?: "Free practice", color = WarmWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val camColor = when (camera) { CameraStatus.Active -> Lime; CameraStatus.Starting -> TextMuted; is CameraStatus.Error -> Warn }
                Dot(camColor)
                Spacer(Modifier.width(5.dp))
                Text(
                    if (camera is CameraStatus.Active) recognitionLabel else if (camera is CameraStatus.Error) "Camera unavailable" else "Camera starting",
                    color = if (recognised) Lime else TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        CoachBadge(voiceMode, voiceStatus, speaking)
        Spacer(Modifier.width(8.dp))
        HudIconButton(YsIcons.Camera, "Switch camera", onFlip)
    }
}

/** Shows whether the coach is speaking, silent, off, or unavailable. */
@Composable
fun CoachBadge(mode: VoiceMode, status: VoiceStatus, speaking: Boolean) {
    val (text, color) = when {
        mode == VoiceMode.OFF -> "Voice off" to TextMuted
        status == VoiceStatus.Unavailable -> "No voice" to Warn
        speaking -> "Speaking" to Lime
        else -> "Coach" to WarmWhite
    }
    Pill(Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Voice coach: $text" }) {
        Icon(YsIcons.Voice, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

/** The one current instruction, plus the last spoken cue as a caption. */
@Composable
fun GuidancePanel(
    guidance: PracticeGuidance.Guidance,
    caption: String?,
    modifier: Modifier = Modifier,
    reference: AsanaInfo? = null,
) {
    val accent = when (guidance.tone) {
        PracticeGuidance.Tone.Good -> Lime
        PracticeGuidance.Tone.Warn -> Warn
        PracticeGuidance.Tone.Info -> WarmWhite
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HudSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    if (reference != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 10.dp)) {
            AsanaFigure(reference.figure, Modifier.size(52.dp), color = Lime)
            Text("Reference", color = TextMuted, fontSize = 10.sp)
        }
    }
    Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(accent)
            Spacer(Modifier.width(8.dp))
            Text(guidance.text, color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        AnimatedVisibility(caption != null, enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(YsIcons.Voice, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("“${caption.orEmpty()}”", color = TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    }
}

/** Bottom bar: pause/resume, voice, visual, timer, end. */
@Composable
fun PracticeControls(
    elapsedMs: Long,
    paused: Boolean,
    onPauseToggle: () -> Unit,
    onVoice: () -> Unit,
    onVisual: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HudSurface).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onPauseToggle,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Lime, contentColor = Midnight),
        ) { Icon(if (paused) YsIcons.Play else YsIcons.Pause, if (paused) "Resume" else "Pause", modifier = Modifier.size(22.dp)) }
        IconButton(onClick = onVoice, modifier = Modifier.size(48.dp)) { Icon(YsIcons.Voice, "Voice settings", tint = WarmWhite) }
        IconButton(onClick = onVisual, modifier = Modifier.size(48.dp)) { Icon(YsIcons.Eye, "Visual settings", tint = WarmWhite) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(PracticeGuidance.formatClock(elapsedMs), color = WarmWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(if (paused) "Paused" else "Session", color = if (paused) Warn else TextMuted, fontSize = 11.sp)
        }
        TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("End", color = Coral, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}


/** Developer diagnostics (off by default): status chips + the existing recognition card. */
@Composable
fun DiagnosticsPanel(
    camera: CameraStatus,
    stats: InferenceStats,
    frame: PoseFrame?,
    rec: LiveRecognitionState,
    modifier: Modifier = Modifier,
) {
    Column(modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill { Text("%.1f FPS".format(stats.inferenceFps), color = WarmWhite, fontSize = 12.sp) }
            Pill { Text("${stats.latencyMs} ms", color = WarmWhite, fontSize = 12.sp) }
            Pill { Text("${FramingGuidance.reliableCount(frame)}/33", color = WarmWhite, fontSize = 12.sp) }
            if (camera is CameraStatus.Error) Pill { Text("cam err", color = Warn, fontSize = 12.sp) }
        }
        AsanaCard(rec, poseVisible = frame?.hasPose == true)
    }
}

/**
 * Compact assessment readout. Every value shows an explicit unavailable state instead of 0.
 * Alignment is a prototype geometric heuristic, labelled as such in the summary.
 */
@Composable
fun AssessmentStrip(live: LiveAssessment?, modifier: Modifier = Modifier) {
    val a = live?.alignment
    val st = live?.stability
    val hold = live?.hold
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(
            "Posture",
            a?.score?.toString() ?: "—",
            when {
                a == null -> "waiting"
                a.score == null -> if (a.cameraHint != null) "adjust camera" else "not enough visible"
                else -> "${a.evaluated.size}/${a.metrics.size} measured"
            },
            when { a?.score == null -> TextMuted; a.score >= 80 -> Lime; a.score >= 60 -> WarmWhite; else -> Warn },
            Modifier.weight(1f),
        )
        Stat(
            "Stability", st?.label ?: "—", st?.score?.let { "score $it" } ?: "measuring",
            when { st?.score == null -> TextMuted; st.score >= 70 -> Lime; st.score >= 40 -> WarmWhite; else -> Warn },
            Modifier.weight(1f),
        )
        Stat(
            "Steady hold", PracticeGuidance.formatClock(hold?.currentHoldMs ?: 0), if (hold?.counting == true) "counting" else "paused",
            if (hold?.counting == true) Lime else TextMuted, Modifier.weight(1f),
        )
    }
    // Phase 8: confidence and coverage are reported separately from the posture score.
    a?.let { AssessmentCoverageLine(it) }
    }
}

/** "Confidence High · 5/6 measured · Not measured: Front knee (hidden)". Unavailable is never shown as a value. */
@Composable
private fun AssessmentCoverageLine(a: com.chinmai.yogasense.coaching.assessment.AlignmentResult) {
    val missing = a.unavailable.joinToString { "${it.label} (${it.reason?.label ?: "unavailable"})" }
    val text = buildString {
        append("Confidence ${a.confidence.level.label}")
        append(" · ${a.evaluated.size}/${a.metrics.size} measured")
        if (a.evaluated.any { it.oneSideOnly }) append(" · some on one side only")
        if (missing.isNotEmpty()) append(" · Not measured: $missing")
    }
    Text(text, color = TextMuted, fontSize = 11.sp, maxLines = 2,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(HudSurface).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun Stat(label: String, value: String, sub: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(HudSurface).padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(sub, color = TextMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
fun SessionSummaryDialog(summary: SessionSummary, onDone: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text("Session complete") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Practice time: ${PracticeGuidance.formatClock(summary.durationMs)}")
                Spacer(Modifier.size(8.dp))
                if (summary.poses.isEmpty()) {
                    Text("No pose was confirmed this session.")
                }
                summary.poses.forEach { p ->
                    Text(PracticeGuidance.name(p.asana), fontWeight = FontWeight.SemiBold)
                    Text("Steady hold: ${PracticeGuidance.formatClock(p.stableHoldTotalMs)} total · longest ${p.longestHoldMs / 1000} s", fontSize = 13.sp)
                    Text("Alignment: " + (p.meanAlignment?.let { "$it / 100 (average while holding)" } ?: "not enough data"), fontSize = 13.sp)
                    Text("Stability: " + (p.meanStability?.let { "$it / 100" } ?: "not enough data"), fontSize = 13.sp)
                    if (p.corrections.isNotEmpty()) {
                        Text("Corrections given:", fontSize = 13.sp)
                        p.corrections.forEach { (msg, n) -> Text("• $msg" + if (n > 1) " (×$n)" else "", fontSize = 12.sp) }
                    }
                    if (p.improvements.isNotEmpty()) {
                        Text("Measured improvement: " + p.improvements.distinct().joinToString(), fontSize = 13.sp, color = Lime)
                    }
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    "Alignment and stability are prototype estimates from a single camera — not clinical or validated measurements.",
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
        dismissButton = { TextButton(onClick = onContinue) { Text("Keep practising") } },
    )
}
