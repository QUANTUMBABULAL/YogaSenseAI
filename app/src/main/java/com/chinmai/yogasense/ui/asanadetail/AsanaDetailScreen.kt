package com.chinmai.yogasense.ui.asanadetail

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.catalog.CameraView
import com.chinmai.yogasense.ui.components.AsanaFigure
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.components.accentFor
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.CoralTint
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.LimeTint
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.PoseAccent
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.SystemBarIcons
import com.chinmai.yogasense.ui.theme.WarmWhite

private val HeroHeight = 380.dp
private val SheetOverlap = 36.dp

/**
 * Asana overview. An edge-to-edge hero in the pose's accent colour (figure drifts up and fades as
 * you scroll) with a warm-white sheet sliding over it: facts, summary, steps, focus, safety.
 * Sticky actions: "Start learning" (primary) and "Practice now".
 */
@OptIn(ExperimentalLayoutApi::class)
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
            RoundIconButton(YsIcons.Back, "Back", CardWhite, Ink, onBack)
            EmptyState(YsIcons.Search, "Pose not found", "This pose is not in the library.", action = { SecondaryButton("Go back", onBack) })
        }
        return
    }
    val accent = accentFor(info)
    SystemBarIcons(darkIcons = !accent.dark)
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize().background(accent.bg)) {
        Hero(info, accent, scroll.value)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                Spacer(Modifier.height(HeroHeight - SheetOverlap))
                Surface(color = WarmWhite, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.md, bottom = Spacing.lg)) {
                        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 44.dp, height = 5.dp).clip(CircleShape).background(Color(0xFFD8D3C8)))
                        Spacer(Modifier.height(Spacing.md + 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) {
                            Fact(YsIcons.Clock, "Hold", info.holdLabel, Modifier.weight(1.25f))
                            if (info.liveSupported) Fact(YsIcons.Camera, "Camera", if (info.cameraView == CameraView.SIDE) "Side-on" else "Facing", Modifier.weight(1f))
                            else Fact(YsIcons.Play, "Format", "Lesson", Modifier.weight(1f))
                            Fact(YsIcons.Practice, "Sides", if (info.perSide) "Both" else "One", Modifier.weight(0.9f))
                        }
                        Spacer(Modifier.height(Spacing.lg))
                        Text(info.summary, style = MaterialTheme.typography.bodyLarge, color = Ink)
                        if (!info.liveSupported) {
                            Spacer(Modifier.height(Spacing.md))
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LimeTint).padding(Spacing.md - 2.dp)) {
                                Icon(YsIcons.Info, null, tint = Midnight, modifier = Modifier.padding(top = 1.dp).size(18.dp))
                                Spacer(Modifier.width(Spacing.sm + 2.dp))
                                Text(
                                    "Video lesson only. Live camera feedback is not available for this pose yet.",
                                    style = MaterialTheme.typography.bodyMedium, color = Ink,
                                )
                            }
                        }

                        Section("How to do it")
                        YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md + 4.dp) {
                            info.steps.forEachIndexed { i, step -> StepRow(i + 1, step, last = i == info.steps.lastIndex) }
                        }

                        Section("Focus on")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            info.focusPoints.forEach { f ->
                                Row(
                                    Modifier.clip(CircleShape).background(LimeTint).padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(YsIcons.Eye, null, tint = Midnight, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(f, style = MaterialTheme.typography.titleSmall, color = Ink)
                                }
                            }
                        }

                        Section("Safety")
                        Surface(color = CoralTint, shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(Spacing.md + 4.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) {
                                info.safety.forEach { s ->
                                    Row {
                                        Icon(YsIcons.Shield, null, tint = CoralDeep, modifier = Modifier.padding(top = 1.dp).size(18.dp))
                                        Spacer(Modifier.width(Spacing.sm + 2.dp))
                                        Text(s, style = MaterialTheme.typography.bodyMedium, color = Ink)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.sm + 2.dp))
                        Text(
                            "General wellness guidance, not medical advice. Stop if you feel pain, dizziness or shortness of breath.",
                            style = MaterialTheme.typography.bodySmall, color = InkMuted,
                        )
                    }
                }
            }
            // Sticky action bar.
            Surface(color = WarmWhite) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.sm + 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (info.liveSupported) {
                        SecondaryButton("Practice now", { onPracticeNow(info) }, modifier = Modifier.weight(1f).height(56.dp))
                        Spacer(Modifier.width(Spacing.sm + 4.dp))
                    }
                    PrimaryButton(if (info.liveSupported) "Start learning" else "Watch the lesson", { onStartLearning(info) }, icon = YsIcons.Play, modifier = Modifier.weight(1.3f))
                }
            }
        }
        Box(Modifier.statusBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            RoundIconButton(
                YsIcons.Back, "Back",
                container = if (accent.dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.7f),
                content = accent.onBg, onClick = onBack,
            )
        }
    }
}

@Composable
private fun Hero(info: AsanaInfo, accent: PoseAccent, scrollPx: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .drawBehind {
                val halo = if (accent.dark) Lime.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.42f)
                drawCircle(halo, radius = size.width * 0.42f, center = Offset(size.width * 0.66f, size.height * 0.5f))
            }
            .graphicsLayer {
                translationY = -scrollPx * 0.35f
                alpha = (1f - scrollPx / (size.height * 0.9f)).coerceIn(0f, 1f)
            },
    ) {
        AsanaFigure(
            info.figure,
            Modifier.align(Alignment.CenterEnd).offset(x = 24.dp, y = 4.dp).size(210.dp),
            color = accent.figure,
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = Spacing.gutter, bottom = SheetOverlap + Spacing.lg).fillMaxWidth(0.6f)) {
            Text(
                "${info.primaryCategory.label} · ${info.difficulty.label}".uppercase(),
                style = MaterialTheme.typography.labelSmall, color = accent.onBgMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                info.name, color = accent.onBg, maxLines = 2,
                style = if (info.name.length > 11) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
            )
            Text(info.englishName, style = MaterialTheme.typography.titleMedium, color = accent.onBgMuted)
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, container: Color, content: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = content),
    ) { Icon(icon, contentDescription = description) }
}

@Composable
private fun Fact(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(CardWhite).padding(horizontal = 14.dp, vertical = 14.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Midnight), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Lime, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(Spacing.sm + 2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(Spacing.xl))
    Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink)
    Spacer(Modifier.height(Spacing.md - 2.dp))
}

/** Numbered step on a vertical timeline. */
@Composable
private fun StepRow(n: Int, text: String, last: Boolean) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Midnight), contentAlignment = Alignment.Center) {
                Text("$n", style = MaterialTheme.typography.labelLarge, color = Lime)
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(Color(0xFFE3DFD6)))
        }
        Spacer(Modifier.width(Spacing.md - 2.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.padding(top = 3.dp, bottom = if (last) 0.dp else Spacing.md + 2.dp))
    }
}
