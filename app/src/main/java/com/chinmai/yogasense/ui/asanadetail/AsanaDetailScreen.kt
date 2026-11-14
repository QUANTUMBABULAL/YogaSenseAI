package com.chinmai.yogasense.ui.asanadetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.ui.components.AsanaVisual
import com.chinmai.yogasense.ui.components.CardStyle
import com.chinmai.yogasense.ui.components.DifficultyTag
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.CoralTint
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.WarmWhite

/**
 * Reusable detail page for any catalogue asana.
 * The hero keeps the static [AsanaVisual]; the full lesson (player, breathing, mistakes) lives in
 * PoseLearningScreen, opened by "Start learning".
 */
@Composable
fun AsanaDetailScreen(
    asanaId: String?,
    onBack: () -> Unit,
    onStartLearning: (AsanaInfo) -> Unit,
    onPracticeNow: (AsanaInfo) -> Unit,
) {
    val info = AsanaCatalog.byId(asanaId)
    if (info == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.gutter)) {
            BackButton(onBack)
            EmptyState(YsIcons.Search, "Pose not found", "This pose is not in the library.", action = { SecondaryButton("Go back", onBack) })
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Spacing.gutter),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack)
                Spacer(Modifier.weight(1f))
                Tag(info.primaryCategory.label)
            }
            AsanaVisual(info, Modifier.fillMaxWidth().aspectRatio(1.15f), showLabel = true)
            Spacer(Modifier.height(Spacing.md + 4.dp))
            Text(info.name, style = MaterialTheme.typography.displaySmall, color = Ink)
            Text(info.sanskrit, style = MaterialTheme.typography.titleMedium, color = InkMuted)
            Spacer(Modifier.height(Spacing.sm + 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DifficultyTag(info.difficulty)
                info.categories.forEach { Tag(it.label) }
            }
            Spacer(Modifier.height(Spacing.md))
            FactsRow(info)
            Spacer(Modifier.height(Spacing.md + 4.dp))
            Text(info.summary, style = MaterialTheme.typography.bodyLarge, color = Ink)

            Section("How to do it") {
                info.steps.forEachIndexed { i, step -> StepRow(i + 1, step, last = i == info.steps.lastIndex) }
            }
            Section("Focus on") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    info.focusPoints.forEach { Tag(it, icon = YsIcons.Eye) }
                }
            }
            Section("Safety") {
                Surface(color = CoralTint, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        info.safety.forEach { s ->
                            Row {
                                Icon(YsIcons.Shield, null, tint = CoralDeep, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Spacing.sm))
                                Text(s, style = MaterialTheme.typography.bodyMedium, color = Ink)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "General wellness guidance, not medical advice. Stop if you feel pain, dizziness or shortness of breath.",
                    style = MaterialTheme.typography.bodySmall, color = InkMuted,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        // Sticky action bar.
        Surface(color = CardWhite) {
            Column {
                HorizontalDivider(color = Hairline)
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.sm + 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecondaryButton("Practice now", { onPracticeNow(info) }, modifier = Modifier.weight(1f).height(56.dp))
                    Spacer(Modifier.width(Spacing.sm + 4.dp))
                    PrimaryButton("Start learning", { onStartLearning(info) }, icon = YsIcons.Play, modifier = Modifier.weight(1.3f))
                }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = CardWhite, contentColor = Ink),
    ) { Icon(YsIcons.Back, contentDescription = "Back") }
}

@Composable
private fun FactsRow(info: AsanaInfo) {
    YsCard(Modifier.fillMaxWidth(), style = CardStyle.Dark, contentPadding = Spacing.md) {
        Row(Modifier.fillMaxWidth()) {
            Fact(YsIcons.Clock, "Hold", info.holdLabel, Modifier.weight(1.2f))
            Fact(YsIcons.Camera, "Camera", if (info.cameraView.name == "SIDE") "Side-on" else "Facing", Modifier.weight(1f))
            Fact(YsIcons.Practice, "Sides", if (info.perSide) "Both" else "—", Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Icon(icon, null, tint = Lime, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = WarmWhite)
        Text(label, style = MaterialTheme.typography.bodySmall, color = WarmWhite.copy(alpha = 0.7f))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(Spacing.lg))
    Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
    Spacer(Modifier.height(Spacing.sm + 4.dp))
    content()
}

@Composable
private fun StepRow(n: Int, text: String, last: Boolean) {
    Row(Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else Spacing.md - 4.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Midnight), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelMedium, color = Lime)
        }
        Spacer(Modifier.width(Spacing.md - 4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 4.dp))
    }
}

