package com.chinmai.yogasense.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.chinmai.yogasense.ui.theme.WarningAmber
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.catalog.Difficulty
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.Coral
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.CoralTint
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.InkSubtle
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.LimeDeep
import com.chinmai.yogasense.ui.theme.LimeTint
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Radius
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.SurfaceMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import com.chinmai.yogasense.ui.theme.YogaSenseAITheme

private val ButtonShape = RoundedCornerShape(28.dp)

// ---- Buttons ---------------------------------------------------------------------------

/** Main call to action: coral with midnight label (6.4:1 contrast). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Coral, contentColor = Midnight,
            disabledContainerColor = SurfaceMuted, disabledContentColor = InkSubtle,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
    ) { ButtonContent(text, icon) }
}

/** Outlined secondary action on light surfaces. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Spacing.minTouch),
        shape = ButtonShape,
        border = BorderStroke(1.5.dp, if (enabled) Midnight else Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Midnight, disabledContentColor = InkSubtle),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
    ) { ButtonContent(text, icon) }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
    }
    Text(text, style = MaterialTheme.typography.labelLarge)
}

// ---- Surfaces --------------------------------------------------------------------------

enum class CardStyle { White, Muted, Dark, Lime }

/** Base rounded surface (20 dp radius). Optional click with ripple and button semantics. */
@Composable
fun YsCard(
    modifier: Modifier = Modifier,
    style: CardStyle = CardStyle.White,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (bg, fg) = when (style) {
        CardStyle.White -> CardWhite to Ink
        CardStyle.Muted -> SurfaceMuted to Ink
        CardStyle.Dark -> Midnight to WarmWhite
        CardStyle.Lime -> Lime to Midnight
    }
    val border: BorderStroke? = null
    val shape = RoundedCornerShape(Radius.card)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = bg, contentColor = fg, border = border, content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = bg, contentColor = fg, border = border, content = inner)
    }
}

// ---- Headers, tags, badges -------------------------------------------------------------

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = CoralDeep) }
        }
    }
}

enum class TagStyle { Neutral, Lime, Coral, Dark, Outline }

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, style: TagStyle = TagStyle.Neutral, icon: ImageVector? = null) {
    val (bg, fg) = when (style) {
        TagStyle.Neutral -> SurfaceMuted to InkMuted
        TagStyle.Lime -> LimeTint to LimeDeep
        TagStyle.Coral -> CoralTint to CoralDeep
        TagStyle.Dark -> Midnight to WarmWhite
        TagStyle.Outline -> Color.Transparent to InkMuted
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (style == TagStyle.Outline) Modifier.border(1.dp, Hairline, CircleShape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun DifficultyTag(difficulty: Difficulty, modifier: Modifier = Modifier) =
    Tag(difficulty.label, modifier, style = if (difficulty == Difficulty.BEGINNER) TagStyle.Lime else TagStyle.Coral)

/** "Coming soon" marker for entry points that are not functional yet. */
@Composable
fun ComingSoonTag(modifier: Modifier = Modifier) = Tag("Coming soon", modifier, TagStyle.Outline, YsIcons.Lock)

@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Midnight,
    container: Color = LimeTint,
) {
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Selectable filter chip with 40 dp height and selection semantics. */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = CircleShape,
        color = if (selected) Midnight else CardWhite,
        contentColor = if (selected) WarmWhite else Ink,
        border = if (selected) null else BorderStroke(1.dp, Hairline),
    ) {
        Box(Modifier.padding(horizontal = Spacing.md, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---- States ----------------------------------------------------------------------------

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.md, horizontal = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconBadge(icon, size = 52.dp, tint = InkMuted, container = SurfaceMuted)
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = InkMuted, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.size(Spacing.xs)); action() }
    }
}

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator(color = Coral, trackColor = SurfaceMuted, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
    }
}

// ---- Progress building blocks ----------------------------------------------------------

/** Card with an icon header; body is either real content or an explanatory empty state. */
@Composable
fun ProgressCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    YsCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, size = 36.dp)
            Spacer(Modifier.width(Spacing.md - 4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.size(Spacing.sm))
        content()
    }
}

/** Single statistic; shows an em dash instead of a number when [value] is null (no data). */
@Composable
fun StatTile(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value ?: "—", style = MaterialTheme.typography.headlineSmall, color = if (value == null) InkSubtle else Ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

/** Monday-first week strip of capsules. [marked] days are real practice days; today is outlined. */
@Composable
fun WeekStrip(labels: List<String>, todayIndex: Int, marked: Set<Int>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i in marked
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) Lime else SurfaceMuted)
                    .then(if (i == todayIndex) Modifier.border(2.dp, Midnight, RoundedCornerShape(18.dp)) else Modifier)
                    .semantics { contentDescription = label + if (on) ", practised" else "" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (on || i == todayIndex) Ink else InkMuted)
                    Spacer(Modifier.size(4.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Midnight else Color.Transparent))
                }
            }
        }
    }
}

/** Circular progress (0..1) with a centre slot. Used only with real, counted values. */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    color: Color = Lime,
    track: Color = SurfaceMuted,
    content: @Composable () -> Unit = {},
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = Size(this.size.width - w, this.size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, tl, inset, style = Stroke(w))
            if (progress > 0f) drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, inset, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Thin labelled 0–100 meter for light surfaces; null = not measured (never drawn as 0). */
@Composable
fun MeterBar(label: String, value: Int?, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label ${value?.let { "$it out of 100" } ?: "not measured"}" }) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.weight(1f))
            Text(value?.toString() ?: "—", style = MaterialTheme.typography.labelMedium, color = if (value != null) Ink else InkSubtle)
        }
        Spacer(Modifier.size(4.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 6.dp).clip(CircleShape).background(SurfaceMuted)) {
            if (value != null) Box(Modifier.fillMaxWidth(value.coerceIn(0, 100) / 100f).heightIn(min = 6.dp).clip(CircleShape).background(if (value >= 70) LimeDeep else if (value >= 40) WarningAmber else CoralDeep))
        }
    }
}

/** Midnight hero surface with a soft lime + coral glow. The signature surface of the app. */
@Composable
fun GlowCard(modifier: Modifier = Modifier, radius: Dp = Radius.hero, contentPadding: Dp = Spacing.lg, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(Midnight)
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(Lime.copy(alpha = 0.30f), Color.Transparent), Offset(size.width * 0.92f, size.height * 0.08f), size.width * 0.75f), size.width * 0.75f, Offset(size.width * 0.92f, size.height * 0.08f))
                drawCircle(Brush.radialGradient(listOf(Coral.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.05f, size.height * 1.05f), size.width * 0.6f), size.width * 0.6f, Offset(size.width * 0.05f, size.height * 1.05f))
            }
            .padding(contentPadding),
        content = content,
    )
}

// ---- Rows ------------------------------------------------------------------------------

/** List/settings row, min 56 dp. Disabled rows are visibly muted and not clickable. */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Spacing.sm, horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, size = 36.dp, tint = if (enabled) Midnight else InkSubtle, container = if (enabled) LimeTint else SurfaceMuted)
            Spacer(Modifier.width(Spacing.md - 4.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Ink else InkMuted)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            if (trailing != null) { Spacer(Modifier.width(Spacing.sm)); trailing() }
        }
    }
    if (onClick != null && enabled) {
        Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = modifier, content = content)
    } else {
        Box(modifier) { content() }
    }
}

// ---- Previews --------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE)
@Composable
private fun ComponentsPreview() = YogaSenseAITheme {
    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PrimaryButton("Start practice", {}, icon = YsIcons.Play, modifier = Modifier.fillMaxWidth())
        SecondaryButton("See all", {})
        SectionHeader("Explore yoga", actionLabel = "See all", onAction = {})
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DifficultyTag(Difficulty.BEGINNER); DifficultyTag(Difficulty.INTERMEDIATE); ComingSoonTag()
        }
        YsCard { EmptyState(YsIcons.Calendar, "No sessions yet", "Completed sessions will appear here.") }
        LoadingState("Loading")
    }
}
