package com.chinmai.yogasense.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chinmai.yogasense.profile.RulerMath
import com.chinmai.yogasense.profile.RulerScale
import com.chinmai.yogasense.profile.TickLevel
import com.chinmai.yogasense.ui.theme.Coral
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.SurfaceMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Horizontal measurement ruler: the ticks move under a fixed centre indicator.
 *
 * - One Canvas; the scroll offset is read only in the draw phase, so dragging redraws but does
 *   not recompose. [onIndexChange] fires once per increment crossed (not per pixel).
 * - Drag, then fling with exponential decay; it always settles exactly on an increment
 *   ([RulerMath.snap]) inside the scale range.
 * - Haptics only on medium/major ticks and rate-limited ([RulerMath.shouldTick]).
 * - [onIndexChange] is called only for user interaction, never for the initial position, so an
 *   untouched ruler doesn't invent a measurement.
 * - Accessibility: exposed as an adjustable range (TalkBack swipe/volume), plus increase/decrease actions.
 */
@Composable
fun MeasurementRuler(
    scale: RulerScale,
    index: Int,
    onIndexChange: (Int) -> Unit,
    description: String,
    valueDescription: String,
    modifier: Modifier = Modifier,
    tickSpacing: Dp = 10.dp,
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { tickSpacing.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val onChange by rememberUpdatedState(onIndexChange)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)

    // A new scale (unit switch) starts a fresh ruler positioned at the current value.
    val offset = remember(scale, spacingPx) { Animatable(RulerMath.offsetFor(index, scale, spacingPx)) }
    var interacted by remember(scale) { mutableStateOf(false) }
    var dragging by remember(scale) { mutableStateOf(false) }

    // External changes (−/+ buttons, accessibility, restored state) move the ruler when it is idle.
    LaunchedEffect(index, scale) {
        val target = RulerMath.offsetFor(index, scale, spacingPx)
        if (!dragging && !offset.isRunning && RulerMath.indexAt(offset.value, scale, spacingPx) != index) {
            offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }
    // Report increments crossed by the user (+ subtle haptics on meaningful ticks).
    LaunchedEffect(scale) {
        var lastTick = 0L
        snapshotFlow { RulerMath.indexAt(offset.value, scale, spacingPx) }.drop(1).collect { i ->
            if (!interacted) return@collect
            onChange(i)
            val now = System.currentTimeMillis()
            if (RulerMath.shouldTick(scale.tickLevel(i), now, lastTick)) { lastTick = now; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
    }

    val maxOffset = RulerMath.maxOffset(scale, spacingPx)
    val dragState = rememberDraggableState { delta ->
        scope.launch { offset.snapTo((offset.value - delta).coerceIn(0f, maxOffset)) }
    }

    fun step(by: Int) { interacted = true; val i = scale.clamp(index + by); onChange(i) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(104.dp)
            .draggable(
                dragState, Orientation.Horizontal,
                onDragStarted = { interacted = true; dragging = true; offset.stop() },
                onDragStopped = { velocity ->
                    val target = exponentialDecay<Float>(frictionMultiplier = 1.6f).calculateTargetValue(offset.value, -velocity)
                    val snapped = RulerMath.snap(target.coerceIn(0f, maxOffset), scale, spacingPx)
                    try {
                        offset.animateTo(snapped, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow), initialVelocity = -velocity)
                    } finally { dragging = false }
                },
            )
            .semantics {
                contentDescription = description
                stateDescription = valueDescription
                progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), scale.min.toFloat()..scale.max.toFloat(), steps = scale.count - 2)
                setProgress { v -> interacted = true; onChange(scale.clamp(v.roundToInt())); true }
                customActions = listOf(
                    CustomAccessibilityAction("Increase") { step(1); true },
                    CustomAccessibilityAction("Decrease") { step(-1); true },
                )
            },
    ) {
        val cx = size.width / 2f
        val baseY = 8.dp.toPx()
        val o = offset.value
        val first = scale.clamp(scale.min + floor((o - cx) / spacingPx).toInt() - 1)
        val last = scale.clamp(scale.min + ceil((o + cx) / spacingPx).toInt() + 1)
        val minorH = 14.dp.toPx(); val mediumH = 24.dp.toPx(); val majorH = 38.dp.toPx()
        val thin = 1.2.dp.toPx(); val thick = 2.dp.toPx()
        for (i in first..last) {
            val x = cx + (i - scale.min) * spacingPx - o
            val alpha = RulerMath.edgeAlpha(x - cx, cx)
            val level = scale.tickLevel(i)
            val h = when (level) { TickLevel.MAJOR -> majorH; TickLevel.MEDIUM -> mediumH; TickLevel.MINOR -> minorH }
            val color = if (level == TickLevel.MINOR) InkMuted else Ink
            drawLine(color.copy(alpha = alpha * if (level == TickLevel.MINOR) 0.55f else 0.9f), Offset(x, baseY), Offset(x, baseY + h),
                strokeWidth = if (level == TickLevel.MAJOR) thick else thin, cap = StrokeCap.Round)
            if (level == TickLevel.MAJOR) {
                val layout = textMeasurer.measure(scale.label(i), labelStyle)
                val left = x - layout.size.width / 2f
                if (left >= 0f && left + layout.size.width <= size.width) { // never draw a clipped label
                    drawText(layout, color = InkMuted.copy(alpha = alpha), topLeft = Offset(left, baseY + majorH + 8.dp.toPx()))
                }
            }
        }
        // Baseline under the ticks for a crafted, physical-ruler feel.
        drawLine(SurfaceMuted, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.dp.toPx())
        // Fixed centre indicator: coral bar with a small pointer.
        val barW = 3.dp.toPx(); val barH = majorH + 6.dp.toPx() // ends above the labels
        drawRoundRect(Coral, Offset(cx - barW / 2, baseY - 2.dp.toPx()), Size(barW, barH), CornerRadius(barW, barW))
        val p = Path().apply {
            moveTo(cx - 6.dp.toPx(), baseY - 8.dp.toPx()); lineTo(cx + 6.dp.toPx(), baseY - 8.dp.toPx()); lineTo(cx, baseY - 1.dp.toPx()); close()
        }
        drawPath(p, Coral)
    }
}

/** Rounded unit selector (keeps the onboarding's existing segmented style). */
@Composable
fun MeasurementUnitSelector(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(modifier.clip(CircleShape).background(SurfaceMuted).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Midnight else Color.Transparent, tween(200), label = "unit")
            Box(
                Modifier.clip(CircleShape).background(bg)
                    .clickable(role = Role.RadioButton) { if (!on) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) } }
                    .semantics { this.selected = on; contentDescription = "Unit $label" }
                    .heightIn(min = 44.dp).padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) WarmWhite else Ink) }
        }
    }
}

/**
 * Large live value + ruler + converted value, shared by height and weight.
 * [index] null = nothing chosen yet: the ruler rests at the scale default and the value is shown muted.
 */
@Composable
fun MeasurementSelector(
    scale: RulerScale,
    index: Int?,
    onIndexChange: (Int) -> Unit,
    converted: String?,
    description: String,
    tickSpacing: Dp,
) {
    val shown = index ?: scale.defaultIndex
    val set = index != null
    val valueColor by animateColorAsState(if (set) Ink else InkMuted.copy(alpha = 0.45f), tween(250), label = "value")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            StepButton("−", "Decrease", enabled = shown > scale.min) { onIndexChange(scale.clamp(shown - 1)) }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(scale.format(shown), style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, fontSize = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp), color = valueColor)
                if (scale.unitLabel.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(scale.unitLabel, style = MaterialTheme.typography.headlineSmall, color = InkMuted, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            StepButton("+", "Increase", enabled = shown < scale.max) { onIndexChange(scale.clamp(shown + 1)) }
        }
        Text(
            if (set) converted.orEmpty() else "Slide the ruler to choose",
            style = MaterialTheme.typography.bodyLarge, color = InkMuted, modifier = Modifier.heightIn(min = 24.dp),
        )
        Spacer(Modifier.height(20.dp))
        MeasurementRuler(
            scale, shown, onIndexChange, description,
            valueDescription = if (set) scale.format(shown) + " " + scale.unitLabel else "Not set",
            tickSpacing = tickSpacing,
        )
    }
}

@Composable
private fun StepButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(SurfaceMuted)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge, color = if (enabled) Ink else InkMuted.copy(alpha = 0.4f)) }
}
