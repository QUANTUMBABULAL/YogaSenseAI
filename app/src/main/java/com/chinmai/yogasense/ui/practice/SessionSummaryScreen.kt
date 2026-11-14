package com.chinmai.yogasense.ui.practice

import androidx.compose.runtime.remember
import com.chinmai.yogasense.coaching.assessment.PoseScore
import com.chinmai.yogasense.coaching.assessment.SessionScore
import com.chinmai.yogasense.coaching.assessment.SessionScoring
import com.chinmai.yogasense.ui.components.RingProgress
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.coaching.assessment.PoseSummary
import com.chinmai.yogasense.coaching.assessment.SessionSummary
import com.chinmai.yogasense.ui.components.AsanaFigure
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.Coral
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.Surface2
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.Warn
import com.chinmai.yogasense.ui.theme.WarmWhite

/**
 * End-of-session summary (dark, full screen over the paused camera). Every number comes from
 * [SessionSummary]; unmeasured values are shown as "Not enough data", never as 0.
 *
 * @param willSave false when the session is too short to be recorded in history.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionSummaryScreen(
    summary: SessionSummary,
    target: Asana?,
    willSave: Boolean,
    onFinish: () -> Unit,
    onKeepPractising: () -> Unit,
) {
    val totalHold = summary.poses.sumOf { it.stableHoldTotalMs }
    // Hold goal = the pose's own minimum recommended hold from the catalogue.
    val score = remember(summary) { SessionScoring.score(summary) { a -> (AsanaCatalog.of(a)?.holdSeconds?.first ?: 20) * 1000L } }
    Column(Modifier.fillMaxSize().background(Midnight)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(56.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                Icon(YsIcons.Spark, null, tint = Midnight, modifier = Modifier.size(28.dp))
            }
            Column {
                Text("SESSION COMPLETE", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(
                    if (summary.poses.isEmpty()) "Nice work showing up" else "Well practised",
                    color = WarmWhite, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp,
                )
            }
            ScoreHero(score)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1).padding(vertical = 16.dp),
            ) {
                BigStat("Practice time", PracticeGuidance.formatClock(summary.durationMs), Modifier.weight(1f))
                BigStat("Poses", summary.poses.size.toString(), Modifier.weight(1f))
                BigStat("Steady hold", PracticeGuidance.formatClock(totalHold), Modifier.weight(1f))
            }

            if (summary.poses.isEmpty()) {
                Text(
                    "No pose was confirmed this session, so there is no alignment or stability data.",
                    color = TextMuted, fontSize = 14.sp,
                )
            } else {
                Text("Your poses", color = WarmWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                summary.poses.sortedByDescending { it.stableHoldTotalMs }.forEach { PoseCard(it, score.of(it.asana)) }
            }

            val suggestions = SessionSuggestions.of(summary, target)
            if (suggestions.isNotEmpty()) {
                Text("For next time", color = WarmWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    suggestions.forEach { s ->
                        Row {
                            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(Coral))
                            Spacer(Modifier.width(12.dp))
                            Text(s, color = WarmWhite, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
            Text(
                "Scores are prototype estimates from a single phone camera (alignment 50%, stability 25%, hold 25%), not clinical or validated measurements." +
                    if (willSave) " This summary is saved on this phone only." else " Sessions under 10 seconds aren't saved to your history.",
                color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onKeepPractising,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Surface2),
            ) { Text("Keep practising", color = WarmWhite, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onFinish,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Midnight),
            ) { Text("Finish", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = WarmWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoseCard(p: PoseSummary, score: PoseScore?) {
    val info = AsanaCatalog.of(p.asana)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
                info?.let { AsanaFigure(it.figure, Modifier.size(40.dp), color = Lime) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(PracticeGuidance.name(p.asana), color = WarmWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Steady ${PracticeGuidance.formatClock(p.stableHoldTotalMs)} · longest ${p.longestHoldMs / 1000} s" +
                        (info?.let { " · goal ${it.holdSeconds.first} s" } ?: ""),
                    color = TextMuted, fontSize = 13.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(score?.total?.toString() ?: "—", color = if (score?.total != null) Lime else TextMuted, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(score?.band?.label ?: "Not scored", color = TextMuted, fontSize = 11.sp)
            }
        }
        ScoreBar("Alignment · 50%", p.meanAlignment)
        ScoreBar("Stability · 25%", p.meanStability)
        ScoreBar("Hold · 25%", score?.hold)
        score?.note?.let { note ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface2).padding(12.dp)) {
                Icon(YsIcons.Leaf, null, tint = Lime, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(note, color = WarmWhite, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        if (p.corrections.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Corrections", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                p.corrections.entries.sortedByDescending { it.value }.forEach { (msg, n) ->
                    Text("• $msg" + if (n > 1) "  ×$n" else "", color = WarmWhite, fontSize = 13.sp)
                }
            }
        }
        if (p.improvements.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.improvements.distinct().forEach { label ->
                    Row(
                        Modifier.clip(CircleShape).background(Lime.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(YsIcons.Spark, null, tint = Lime, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Improved: $label", color = Lime, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Overall posture score: ring, grade and the coach's one-line verdict. */
@Composable
private fun ScoreHero(score: SessionScore) {
    val total = score.total
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface1).padding(18.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Posture score ${total?.let { "$it out of 100, ${score.band?.label}" } ?: "not available"}. ${score.headline}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingProgress((total ?: 0) / 100f, size = 104.dp, stroke = 10.dp, color = Lime, track = Surface2) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total?.toString() ?: "—", color = WarmWhite, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("of 100", color = TextMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("POSTURE SCORE", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(score.band?.label ?: "Not scored", color = WarmWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(score.headline, color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
            if (total != null && score.scoredPoses < score.poses.size) {
                Text("Based on ${score.scoredPoses} of ${score.poses.size} poses that could be measured.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 0–100 bar; null shows an explicit "not enough data" state. */
@Composable
fun ScoreBar(label: String, score: Int?, modifier: Modifier = Modifier) {
    val color = when {
        score == null -> TextMuted
        score >= 70 -> Lime
        score >= 40 -> Color(0xFFFFD27A)
        else -> Warn
    }
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label ${score?.let { "$it out of 100" } ?: "not enough data"}" }) {
        Row {
            Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(score?.let { "$it / 100" } ?: "Not enough data", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Surface2)) {
            if (score != null) Box(Modifier.fillMaxWidth(score.coerceIn(0, 100) / 100f).height(6.dp).clip(CircleShape).background(color))
        }
    }
}
