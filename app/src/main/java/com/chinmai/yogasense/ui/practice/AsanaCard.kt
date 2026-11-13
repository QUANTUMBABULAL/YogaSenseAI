package com.chinmai.yogasense.ui.practice

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chinmai.yogasense.ui.practice.RecognitionPresenter.Headline
import com.chinmai.yogasense.ui.theme.Accent
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.Warn

/**
 * Recognition status. Shows WHICH pose is detected and how sure the classifier is.
 * It deliberately shows no alignment/quality score (Phase 4).
 */
@Composable
fun AsanaCard(state: LiveRecognitionState, poseVisible: Boolean, modifier: Modifier = Modifier) {
    val r = state.recognition
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .animateContentSize(),
    ) {
        when (val h = RecognitionPresenter.headline(r)) {
            is Headline.Confirmed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.name, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Confirmed", color = Accent, fontSize = 12.sp)
                }
                h.sideLabel?.let { Text(it, color = Color.White, fontSize = 13.sp) }
            }
            is Headline.Pending -> {
                Text("Detecting ${h.name}…", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { h.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Accent,
                    trackColor = Color(0x33FFFFFF),
                )
            }
            is Headline.NotRecognised -> {
                Text("Pose not recognised", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(if (poseVisible) h.reason else "Waiting for body", color = TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric("Recognition confidence", if (r.isStable) RecognitionPresenter.percent(r.confidence) else "—")
            Metric("Stability", RecognitionPresenter.percent(r.stability))
            Metric("Recognition time", "%.1f ms".format(state.recognitionMs))
        }
        Text(
            "Confidence = how sure we are which pose this is, not how well it is performed.",
            color = TextMuted, fontSize = 10.sp,
        )
        RecognitionPresenter.landmarkHint(r.frame?.lowConfidenceLandmarks.orEmpty())?.takeIf { poseVisible }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Warn, fontSize = 12.sp)
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            EvidenceDetails(state)
        } else {
            Text("Tap for details", color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}

/** Per-feature evidence for the closest template: measured value vs. reference range. */
@Composable
private fun EvidenceDetails(state: LiveRecognitionState) {
    val f = state.recognition.frame
    val best = f?.best
    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
        if (best == null) {
            Text(f?.reason ?: "No frame yet", color = TextMuted, fontSize = 11.sp)
            return@Column
        }
        Text(
            "Closest template: ${best.asana.displayName}${if (best.side.name != "NONE") " (${best.side.name.lowercase()})" else ""} — match ${RecognitionPresenter.percent(best.confidence)}",
            color = Color.White, fontSize = 11.sp,
        )
        Text("Frame: ${f.reason}", color = TextMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        best.evidence.forEach { e ->
            val v = e.value?.let { "%.2f".format(it) } ?: "missing"
            val ok = e.score >= 1f
            Row {
                Text(if (ok) "✓" else if (e.score > 0f) "~" else "✗", color = if (ok) Accent else Warn, fontSize = 11.sp, modifier = Modifier.width(14.dp))
                Text(
                    "${e.name}: $v  [${fmt(e.min)}–${fmt(e.max)} ${e.unit}]",
                    color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                )
            }
        }
        Text("Frames processed: ${state.framesProcessed}", color = TextMuted, fontSize = 10.sp)
    }
}

private fun fmt(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.2f".format(v)
