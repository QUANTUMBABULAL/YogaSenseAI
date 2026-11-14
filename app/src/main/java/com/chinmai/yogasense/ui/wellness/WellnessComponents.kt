package com.chinmai.yogasense.ui.wellness

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.CoralTint
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.InkSubtle
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.WarmWhite
import com.chinmai.yogasense.wellness.CrisisSupport
import com.chinmai.yogasense.wellness.RatingScale
import com.chinmai.yogasense.wellness.Scales
import com.chinmai.yogasense.wellness.TrendSeries
import com.chinmai.yogasense.wellness.WellnessDates

/** 1–5 rating as five labelled, selectable options. Tapping the selected option clears it (optional field). */
@Composable
fun RatingSelector(scale: RatingScale, value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(scale.title, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
            Text(value?.let { scale.label(it) } ?: "Not answered", style = MaterialTheme.typography.labelMedium, color = if (value == null) InkSubtle else Ink)
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            scale.range.forEach { v ->
                val selected = value == v
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(if (selected) null else v) })
                        .semantics { contentDescription = "${scale.title}: ${scale.label(v)}, $v of ${scale.labels.size}" },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) Midnight else CardWhite,
                    contentColor = if (selected) WarmWhite else Ink,
                    border = if (selected) null else BorderStroke(1.dp, Hairline),
                ) {
                    Column(Modifier.padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$v", style = MaterialTheme.typography.titleMedium)
                        Text(scale.label(v), style = MaterialTheme.typography.labelSmall, maxLines = 2, color = if (selected) Lime else InkMuted)
                    }
                }
            }
        }
    }
}

/** Sleep duration in half hours; null = not recorded. */
@Composable
fun SleepStepper(hours: Float?, onChange: (Float?) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sleep duration", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
            if (hours != null) TextButton(onClick = { onChange(null) }) { Text("Clear", color = CoralDeep) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Step(YsIcons.Back, "Less sleep", enabled = hours == null || hours > Scales.SLEEP_MIN_HOURS) {
                onChange(if (hours == null) 7f else (hours - Scales.SLEEP_STEP).coerceAtLeast(Scales.SLEEP_MIN_HOURS))
            }
            Text(
                hours?.let { formatHours(it) } ?: "Not recorded",
                style = MaterialTheme.typography.headlineSmall, color = if (hours == null) InkSubtle else Ink,
                modifier = Modifier.width(150.dp).semantics { stateDescription = hours?.let { "${formatHours(it)} of sleep" } ?: "Not recorded" },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Step(YsIcons.ChevronRight, "More sleep", enabled = hours == null || hours < Scales.SLEEP_MAX_HOURS) {
                onChange(if (hours == null) 7f else (hours + Scales.SLEEP_STEP).coerceAtMost(Scales.SLEEP_MAX_HOURS))
            }
        }
    }
}

@Composable
private fun Step(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = CardWhite, contentColor = Ink),
    ) { Icon(icon, desc) }
}

fun formatHours(h: Float): String {
    val whole = h.toInt(); val half = h - whole >= 0.5f
    return if (half) "$whole h 30 min" else "$whole h"
}

/**
 * Single-series daily bar chart. One y-axis; missing days stay empty (never interpolated);
 * tap a bar to read its value; "Show values" gives a text table for screen readers and precision.
 */
@Composable
fun DailyBarChart(
    series: TrendSeries,
    maxValue: Float,
    valueLabel: (Float) -> String,
    modifier: Modifier = Modifier,
    barColor: Color = Midnight,
) {
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    val summary = if (series.recordedDays == 0) "${series.title}: no entries in the ${series.periodLabel.lowercase()}"
    else "${series.title}, ${series.periodLabel.lowercase()}: ${series.recordedDays} entries" +
        (series.average?.let { ", average ${valueLabel(it)}" } ?: "")
    YsCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(series.title, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
            Text(series.periodLabel, style = MaterialTheme.typography.labelMedium, color = InkMuted)
        }
        val sel = selected?.let { series.points.getOrNull(it) }
        Text(
            when {
                sel != null -> "${WellnessDates.dayLabel(sel.epochDay)} · " + (sel.value?.let(valueLabel) ?: "no entry")
                series.average != null -> "Average ${valueLabel(series.average)} over ${series.recordedDays} days"
                series.recordedDays > 0 -> "${series.recordedDays} of ${series.points.size} days recorded — average shown after 3"
                else -> "No entries yet"
            },
            style = MaterialTheme.typography.bodySmall, color = InkMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics { contentDescription = summary }
                .pointerInput(series) {
                    detectTapGestures { o ->
                        val i = (o.x / (size.width / series.points.size)).toInt().coerceIn(0, series.points.lastIndex)
                        selected = if (selected == i) null else i
                    }
                },
        ) { drawBars(series, maxValue, selected, barColor) }
        Row(Modifier.fillMaxWidth()) {
            Text(WellnessDates.shortDay(series.points.first().epochDay), style = MaterialTheme.typography.labelSmall, color = InkSubtle, modifier = Modifier.weight(1f))
            Text("Today", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
        }
        if (series.recordedDays > 0) {
            TextButton(onClick = { showTable = !showTable }) {
                Text(if (showTable) "Hide values" else "Show values", style = MaterialTheme.typography.labelLarge, color = CoralDeep)
            }
            if (showTable) {
                series.points.filter { it.value != null }.reversed().forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(WellnessDates.dayLabel(p.epochDay), style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.weight(1f))
                        Text(valueLabel(p.value!!), style = MaterialTheme.typography.bodySmall, color = Ink)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBars(series: TrendSeries, maxValue: Float, selected: Int?, color: Color) {
    val n = series.points.size
    val slot = size.width / n
    val gap = 2.dp.toPx()
    val barW = (slot * 0.62f).coerceAtMost(14.dp.toPx())
    val r = 4.dp.toPx()
    // Recessive baseline.
    drawLine(Hairline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    series.points.forEachIndexed { i, p ->
        val cx = i * slot + slot / 2f
        val v = p.value
        if (v == null) {
            drawCircle(Hairline, radius = 2.dp.toPx(), center = Offset(cx, size.height - 4.dp.toPx()))
            return@forEachIndexed
        }
        val h = (v / maxValue).coerceIn(0.02f, 1f) * (size.height - gap)
        val left = cx - barW / 2f
        val top = size.height - h
        val path = Path().apply {
            // Rounded top, square bottom anchored to the baseline.
            addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + barW, size.height, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r)))
        }
        drawPath(path, if (selected == null || selected == i) color else color.copy(alpha = 0.35f))
    }
}

/** Compassionate support content (PHQ-9 item 9). Call buttons only open the phone dialer. */
@Composable
fun SupportContent(modifier: Modifier = Modifier, afterItem9: Boolean = true) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (afterItem9) {
            Text(CrisisSupport.TITLE, style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(CrisisSupport.INTRO, style = MaterialTheme.typography.bodyLarge, color = Ink)
        } else {
            Text("You don't have to manage alone", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text("If things feel hard right now, talking to someone can help. These services are free and confidential.", style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
        Surface(color = CoralTint, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(CrisisSupport.IMMEDIATE_HEADER, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(CrisisSupport.IMMEDIATE, style = MaterialTheme.typography.bodyMedium, color = Ink)
                CallButton(context, CrisisSupport.EMERGENCY, emphasised = true)
            }
        }
        YsCard(Modifier.fillMaxWidth()) {
            Text(CrisisSupport.NOT_IMMEDIATE_HEADER, style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(Spacing.xs))
            Text(CrisisSupport.NOT_IMMEDIATE, style = MaterialTheme.typography.bodyMedium, color = Ink)
            Spacer(Modifier.height(Spacing.sm))
            CallButton(context, CrisisSupport.TELE_MANAS, emphasised = false)
            Spacer(Modifier.height(Spacing.xs))
            CallButton(context, CrisisSupport.TELE_MANAS_TOLL_FREE, emphasised = false)
        }
        Text(CrisisSupport.LIMITS, style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

@Composable
private fun CallButton(context: Context, line: CrisisSupport.Line, emphasised: Boolean) {
    Surface(
        onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${line.dial}"))) },
        shape = RoundedCornerShape(16.dp),
        color = if (emphasised) Midnight else CardWhite,
        contentColor = if (emphasised) WarmWhite else Ink,
        border = if (emphasised) null else BorderStroke(1.5.dp, Midnight),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "Open dialer for ${line.name}, ${line.number}" },
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${line.name} · ${line.number}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(line.detail, style = MaterialTheme.typography.bodySmall, color = if (emphasised) Lime else InkMuted)
            }
            Text("Call", style = MaterialTheme.typography.labelLarge, color = if (emphasised) Lime else CoralDeep)
        }
    }
}

@Composable
fun NotDiagnosisNote(modifier: Modifier = Modifier) {
    Row(modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.Top) {
        Icon(YsIcons.Info, null, tint = InkMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "YogaSense AI is a wellness companion, not a medical device. Questionnaire scores are screening results, not a diagnosis.",
            style = MaterialTheme.typography.bodySmall, color = InkMuted,
        )
    }
}

