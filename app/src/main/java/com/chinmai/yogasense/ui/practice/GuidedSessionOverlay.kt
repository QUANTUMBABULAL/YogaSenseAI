package com.chinmai.yogasense.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.coaching.assessment.SessionScoring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.coaching.assessment.SessionSummary
import com.chinmai.yogasense.session.GuidedPoseResult
import com.chinmai.yogasense.session.GuidedStage
import com.chinmai.yogasense.session.GuidedState
import com.chinmai.yogasense.session.PauseReason
import com.chinmai.yogasense.ui.components.AsanaFigure
import com.chinmai.yogasense.ui.learning.player.AsanaAnimationPlayer
import com.chinmai.yogasense.ui.learning.player.LessonMediaResolver
import com.chinmai.yogasense.ui.theme.Accent
import com.chinmai.yogasense.ui.theme.Coral
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import kotlin.math.ceil

/** Everything the overlay can ask the guided session to do. */
class GuidedActions(
    val onDemoEnded: (epoch: Int) -> Unit,
    val onSkipDemo: () -> Unit,
    val onSkipPose: () -> Unit,
    val onRetry: () -> Unit,
    val onResume: () -> Unit,
    val onPause: () -> Unit,
    val onExit: () -> Unit,
    val onDone: () -> Unit,
)

private fun name(a: Asana?) = a?.let { AsanaCatalog.of(it)?.name ?: it.displayName }.orEmpty()

/**
 * Stage UI for a guided session, drawn OVER the live practice screen. The camera, MediaPipe and
 * the recognition pipeline underneath keep running in every stage (they are never recreated).
 * Full panels: intro, demonstration, recovery, timeout, pause, completion. Translucent: preparation.
 * During detection and hold only a top banner with the hold progress is shown.
 */
@Composable
fun GuidedSessionOverlay(
    s: GuidedState,
    remainingMs: Long?,
    holdTargetMs: Long,
    summary: SessionSummary?,
    actions: GuidedActions,
) {
    val secondsLeft = remainingMs?.let { ceil(it / 1000.0).toInt() } ?: 0
    when {
        s.stage == GuidedStage.SESSION_COMPLETED -> Panel(null) { Completion(s, summary, actions, holdTargetMs) }
        s.paused -> Panel(null) { Paused(s, actions) }
        s.stage == GuidedStage.INTRO -> Panel(actions) { Intro(s, secondsLeft) }
        s.stage == GuidedStage.DEMONSTRATION -> Panel(actions) { Demonstration(s, actions) }
        s.stage == GuidedStage.PREPARATION_COUNTDOWN -> Preparation(s, secondsLeft, actions)
        s.stage == GuidedStage.RECOVERY -> Panel(actions) { Recovery(s, remainingMs ?: 0, secondsLeft) }
        s.stage == GuidedStage.DETECTION_TIMEOUT -> Panel(actions) { Timeout(s, secondsLeft, actions) }
        else -> PoseBanner(s, holdTargetMs, actions)
    }
}

/** Full-screen stage panel. It consumes touches so nothing underneath can be tapped by accident. */
@Composable
private fun Panel(controls: GuidedActions?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Midnight).blockTouches().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Box(Modifier.fillMaxSize().padding(top = if (controls != null) 40.dp else 0.dp)) { content() }
        if (controls != null) SessionControls(controls, Modifier.align(Alignment.TopEnd))
    }
}

private fun Modifier.blockTouches() = pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }

/** Always-visible pause and safe exit. */
@Composable
private fun SessionControls(a: GuidedActions, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.height(44.dp).clip(CircleShape).background(Surface1).clickable(onClick = a.onPause).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(YsIcons.Pause, null, tint = WarmWhite, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pause", color = WarmWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier.height(44.dp).clip(CircleShape).background(Coral.copy(alpha = 0.16f)).clickable(onClick = a.onExit).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Text("End", color = Coral, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Progress(s: GuidedState) {
    Text("POSE ${(s.index + 1).coerceAtMost(s.plan.size)} OF ${s.plan.size}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        s.plan.indices.forEach { i ->
            val c = when { i < s.index -> Accent; i == s.index -> Coral; else -> Surface1 }
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(c))
        }
    }
}

@Composable
private fun BigTitle(text: String) =
    Text(text, color = WarmWhite, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, modifier = Modifier.semantics { heading() })

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    Button(onClick, modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Midnight)) { Text(text, fontWeight = FontWeight.Bold) }

@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    OutlinedButton(onClick, modifier.fillMaxWidth().height(48.dp)) { Text(text, color = WarmWhite) }

/** Session overview: what is coming, how to set up, and a clear countdown. */
@Composable
private fun Intro(s: GuidedState, secondsLeft: Int) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(20.dp))
            Text(
                "GUIDED SESSION", color = Midnight, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                modifier = Modifier.clip(CircleShape).background(Accent).padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "${s.plan.size} poses", color = WarmWhite, fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 44.sp, letterSpacing = (-1).sp,
                modifier = Modifier.semantics { heading() },
            )
            Text("About ${s.plan.size} min, plus short rests", color = TextMuted, fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface1).padding(horizontal = 14.dp, vertical = 6.dp)) {
                s.plan.forEachIndexed { i, a ->
                    val info = AsanaCatalog.of(a)
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Midnight), contentAlignment = Alignment.Center) {
                            if (info != null) AsanaFigure(info.figure, Modifier.size(34.dp), color = Accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name(a), color = WarmWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            if (info != null) Text(info.englishName, color = TextMuted, fontSize = 13.sp)
                        }
                    }
                    if (i < s.plan.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 76.dp).height(1.dp).background(WarmWhite.copy(alpha = 0.08f)))
                }
            }
            Spacer(Modifier.height(16.dp))
            IntroTip(YsIcons.Camera, "Place the phone so your whole body is visible")
            IntroTip(YsIcons.Voice, "Hands-free: follow the voice, no need to touch the phone")
            Spacer(Modifier.height(12.dp))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface1).padding(horizontal = 18.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = "Starting in $secondsLeft seconds" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Starting in", color = TextMuted, fontSize = 14.sp)
                Text("Get into position", color = WarmWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.size(64.dp).clip(CircleShape).background(Coral), contentAlignment = Alignment.Center) {
                Text("$secondsLeft", color = Midnight, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun IntroTip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Surface1), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun Demonstration(s: GuidedState, a: GuidedActions) {
    val info = s.current?.let { AsanaCatalog.of(it) } ?: return
    val context = LocalContext.current
    val bundled = remember { context.assets.list("asana")?.toSet().orEmpty() }
    val media = remember(info) { LessonMediaResolver.resolve(info) { path -> path.removePrefix("asana/") in bundled } }
    val epoch = s.epoch
    Column(Modifier.fillMaxSize()) {
        Progress(s)
        Spacer(Modifier.height(12.dp))
        BigTitle(info.name)
        Text(info.englishName, color = TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Reuses the Phase 5 player; it labels illustrations honestly ("not a video").
            AsanaAnimationPlayer(
                media = media, fallback = LessonMediaResolver.fallbackFor(info), modifier = Modifier.fillMaxWidth(),
                autoPlay = true, loop = false, caption = info.demoNote, onEnded = { a.onDemoEnded(epoch) },
            )
            if (media !is com.chinmai.yogasense.ui.learning.player.LessonMedia.Video) {
                Spacer(Modifier.height(8.dp))
                Text("Instructional video coming soon — follow the illustration and steps below.", color = TextMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            info.steps.take(4).forEachIndexed { i, st -> Text("${i + 1}. $st", color = WarmWhite, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            info.safety.firstOrNull()?.let { Spacer(Modifier.height(8.dp)); Text("Safety: $it", color = Coral, fontSize = 14.sp) }
            Spacer(Modifier.height(4.dp))
            Text(info.cameraView.label, color = TextMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryAction("Skip pose", a.onSkipPose, Modifier.weight(1f))
            PrimaryAction("Skip demo", a.onSkipDemo, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Preparation(s: GuidedState, secondsLeft: Int, a: GuidedActions) {
    val info = s.current?.let { AsanaCatalog.of(it) }
    Box(Modifier.fillMaxSize().background(Midnight.copy(alpha = 0.55f)).blockTouches().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        SessionControls(a, Modifier.align(Alignment.TopEnd))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("GET READY", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("$secondsLeft", color = WarmWhite, fontSize = 140.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            Text("Move into ${info?.name.orEmpty()}", color = WarmWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(info?.cameraView?.label.orEmpty() + " · keep your whole body in view", color = TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PoseBanner(s: GuidedState, holdTargetMs: Long, a: GuidedActions) {
    val holding = s.stage == GuidedStage.HOLDING
    val progress by animateFloatAsState((s.holdMs.toFloat() / holdTargetMs).coerceIn(0f, 1f), tween(200), label = "hold")
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 64.dp, start = 12.dp, end = 12.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Midnight.copy(alpha = 0.82f)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("POSE ${s.index + 1} OF ${s.plan.size}", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(name(s.current), color = WarmWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (holding) "Pose detected" else if (s.holdMs > 0) "Timer paused" else "Waiting for pose…",
                    color = if (holding) Midnight else WarmWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(if (holding) Accent else Surface1).padding(horizontal = 10.dp, vertical = 5.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Surface1)) {
                Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(CircleShape).background(Accent))
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text(if (!holding && s.holdMs > 0) "Paused at ${s.holdMs / 1000} / ${holdTargetMs / 1000} s · get back into the pose to continue" else "Steady hold ${s.holdMs / 1000} / ${holdTargetMs / 1000} s", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = a.onSkipPose, modifier = Modifier.height(32.dp)) { Text("Skip pose", color = WarmWhite, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun CountdownRing(remainingMs: Long, totalMs: Long, secondsLeft: Int) {
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val d = size.minDimension - stroke
            drawArc(Surface1, 0f, 360f, false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(stroke))
            drawArc(Accent, -90f, 360f * (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f), false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$secondsLeft", color = WarmWhite, fontSize = 84.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun Recovery(s: GuidedState, remainingMs: Long, secondsLeft: Int) {
    val next = s.next?.let { AsanaCatalog.of(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Progress(s)
        Spacer(Modifier.height(16.dp))
        Text("REST & BREATHE", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Completed ${name(s.current)} · ${s.holdMs / 1000} s steady", color = TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        CountdownRing(remainingMs, 20_000L.coerceAtLeast(remainingMs), secondsLeft)
        Spacer(Modifier.height(20.dp))
        if (next != null) {
            Text("UP NEXT", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(next.name, color = WarmWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(Surface1), contentAlignment = Alignment.Center) { AsanaFigure(next.figure, Modifier.size(70.dp), color = Accent) }
                Spacer(Modifier.width(14.dp))
                Text(next.summary, color = WarmWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            next.steps.take(2).forEach { Text("• $it", color = TextMuted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth()) }
            Text("Its demonstration plays next.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

@Composable
private fun Timeout(s: GuidedState, secondsLeft: Int, a: GuidedActions) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        BigTitle("Couldn't detect ${name(s.current)}")
        Spacer(Modifier.height(10.dp))
        Text("Check that your whole body is in view and the lighting is even. You can try again, or skip this pose.", color = TextMuted, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Text("Skipping automatically in $secondsLeft s", color = WarmWhite, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        PrimaryAction("Try again", a.onRetry)
        Spacer(Modifier.height(10.dp))
        SecondaryAction("Skip pose", a.onSkipPose)
    }
}

@Composable
private fun Paused(s: GuidedState, a: GuidedActions) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        BigTitle("Paused")
        Spacer(Modifier.height(10.dp))
        Text(
            when (s.pausedBy) {
                PauseReason.CAMERA -> "The camera stopped. The session resumes automatically when it's available again."
                PauseReason.BACKGROUND -> "The session paused while the app was in the background."
                else -> "Take your time."
            },
            color = TextMuted, fontSize = 15.sp,
        )
        Spacer(Modifier.height(24.dp))
        if (s.pausedBy != PauseReason.CAMERA) PrimaryAction("Resume", a.onResume)
        Spacer(Modifier.height(10.dp))
        SecondaryAction("End session", a.onExit)
    }
}

@Composable
private fun Completion(s: GuidedState, summary: SessionSummary?, a: GuidedActions, holdTargetMs: Long) {
    val done = s.results.filter { it.completed }
    // In a guided session every pose has the same hold target, so that is the hold goal.
    val score = remember(summary) { summary?.let { SessionScoring.score(it) { holdTargetMs } } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(if (s.endedEarly) "SESSION ENDED" else "SESSION COMPLETE", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            BigTitle("${done.size} of ${s.plan.size} poses completed")
            Text("Total steady hold ${done.sumOf { it.stableHoldMs } / 1000} s", color = TextMuted, fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            if (score != null) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(score.total?.toString() ?: "—", color = if (score.total != null) Accent else TextMuted, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text("of 100", color = TextMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("POSTURE SCORE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(score.band?.label ?: "Not scored", color = WarmWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(score.headline, color = TextMuted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            s.plan.forEachIndexed { i, asana ->
                val r: GuidedPoseResult? = s.results.getOrNull(i)?.takeIf { it.asana == asana }
                val p = summary?.poses?.firstOrNull { it.asana == asana }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Surface1).padding(12.dp)) {
                    Row {
                        Text(name(asana), color = WarmWhite, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(when { r == null -> "Not reached"; r.completed -> "Completed"; else -> "Skipped" },
                            color = if (r?.completed == true) Accent else TextMuted, fontSize = 13.sp)
                    }
                    if (r?.completed == true) Text("Steady hold ${r.stableHoldMs / 1000} s", color = TextMuted, fontSize = 13.sp)
                    score?.of(asana)?.let { ps ->
                        Text(
                            "Score: " + (ps.total?.let { "$it/100 · ${ps.band?.label}" } ?: "not scored") + (ps.hold?.let { " · Hold $it/100" } ?: ""),
                            color = if (ps.total != null) WarmWhite else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (p != null) {
                        Text("Alignment: " + (p.meanAlignment?.let { "$it/100" } ?: "not enough data") + " · Stability: " + (p.meanStability?.let { "$it/100" } ?: "not enough data"),
                            color = TextMuted, fontSize = 13.sp)
                    }
                    score?.of(asana)?.note?.let { Text(it, color = WarmWhite, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Scores are prototype estimates from a single camera (alignment 50%, stability 25%, hold 25%) — not clinical measurements.", color = TextMuted, fontSize = 11.sp)
            // Integration point: a post-session meditation can be offered here once that feature exists.
        }
        PrimaryAction("Done", a.onDone)
    }
}

