package com.chinmai.yogasense.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.ui.theme.Accent
import com.chinmai.yogasense.ui.theme.AccentContainer
import com.chinmai.yogasense.ui.theme.Background
import com.chinmai.yogasense.ui.theme.Outline
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.Surface2
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.TextPrimary
import com.chinmai.yogasense.ui.theme.TextSubtle
import com.chinmai.yogasense.ui.theme.Warn

// ---- Buttons ---------------------------------------------------------------------------

@Composable
fun YsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent, contentColor = Background,
            disabledContainerColor = Surface2, disabledContentColor = TextSubtle,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun YsSecondaryButton(
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
        shape = CircleShape,
        border = BorderStroke(1.dp, if (enabled) AccentContainer.copy(alpha = 1f) else Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent, disabledContentColor = TextSubtle),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ---- Cards -----------------------------------------------------------------------------

enum class CardTone { Default, Raised, Accent }

@Composable
fun YsCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Default,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = when (tone) { CardTone.Default -> Surface1; CardTone.Raised -> Surface2; CardTone.Accent -> AccentContainer }
    val border = BorderStroke(1.dp, if (tone == CardTone.Accent) AccentContainer else Outline)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = color, border = border, content = inner)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = color, border = border, content = inner)
    }
}

// ---- Headers, badges, tags -------------------------------------------------------------

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        trailing?.invoke()
    }
}

/** Icon in a soft circular container — the single icon treatment used across the app. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Accent,
    container: Color = AccentContainer,
) {
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

enum class TagTone { Neutral, Accent, Warn }

/** Small label, e.g. "Coming soon" or "Sample data". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, tone: TagTone = TagTone.Neutral) {
    val (bg, fg) = when (tone) {
        TagTone.Neutral -> Surface2 to TextMuted
        TagTone.Accent -> AccentContainer to Accent
        TagTone.Warn -> Warn.copy(alpha = 0.14f) to Warn
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---- Empty state & rows ----------------------------------------------------------------

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconBadge(icon, size = 48.dp, tint = TextMuted, container = Surface2)
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.size(Spacing.xs)); action() }
    }
}

/** Settings/list row with consistent icon, text and trailing slot; min 56dp touch height. */
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
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, size = 36.dp, tint = if (enabled) Accent else TextSubtle, container = if (enabled) AccentContainer else Surface2)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) TextPrimary else TextMuted)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            if (trailing != null) { Spacer(Modifier.width(Spacing.sm)); trailing() }
        }
    }
    if (onClick != null && enabled) {
        Surface(onClick = onClick, color = Color.Transparent, modifier = modifier, content = content)
    } else {
        Box(modifier) { content() }
    }
}
