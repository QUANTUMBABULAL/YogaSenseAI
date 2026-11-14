package com.chinmai.yogasense.ui.learning

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.ui.components.DifficultyTag
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.LoadingState
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.learning.player.AsanaAnimationPlayer
import com.chinmai.yogasense.ui.learning.player.LessonMedia
import com.chinmai.yogasense.ui.learning.player.LessonMediaResolver
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.CoralTint
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.LimeDeep
import com.chinmai.yogasense.ui.theme.LimeTint
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.SurfaceMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lesson for one asana: demonstration player, then intro, steps, breathing, focus points,
 * common mistakes and safety. "Skip lesson" and "Continue to practice" are always visible.
 */
@Composable
fun PoseLearningScreen(
    asanaId: String?,
    onBack: () -> Unit,
    onSkip: (LessonContent) -> Unit,
    onContinue: (LessonContent) -> Unit,
) {
    val lesson = LessonContent.of(asanaId)
    if (lesson == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.gutter)) {
            BackButton(onBack)
            EmptyState(YsIcons.Search, "Lesson not found", "This pose is not in the library.", action = { SecondaryButton("Go back", onBack) })
        }
        return
    }

    // Resolve media off the main thread (asset lookup); show a loading state meanwhile.
    val context = LocalContext.current
    val media by produceState<LessonMedia?>(null, lesson.id) {
        value = withContext(Dispatchers.IO) {
            val bundled = runCatching { context.assets.list("asana")?.toSet() }.getOrNull().orEmpty()
            LessonMediaResolver.resolve(lesson.info) { path -> path.removePrefix("asana/") in bundled }
        }
    }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Header(lesson, scrollProgress = if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f, onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.sm))
            val m = media
            if (m == null) {
                LoadingState("Preparing demonstration…", Modifier.fillMaxWidth().height(300.dp))
            } else {
                AsanaAnimationPlayer(
                    media = m,
                    fallback = LessonMediaResolver.fallbackFor(lesson.info),
                    modifier = Modifier.fillMaxWidth(),
                    caption = lesson.demoNote,
                )
            }
            Spacer(Modifier.height(Spacing.md + 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DifficultyTag(lesson.info.difficulty)
                Tag(lesson.info.holdLabel, icon = YsIcons.Clock)
                Tag(lesson.info.cameraView.label, icon = YsIcons.Camera)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(lesson.intro, style = MaterialTheme.typography.bodyLarge, color = Ink)

            Section("Step by step", "${lesson.steps.size} steps") {
                lesson.steps.forEachIndexed { i, s -> NumberedRow(i + 1, s, i == lesson.steps.lastIndex) }
            }
            Section("Breathing") {
                Panel(LimeTint) { lesson.breathing.forEach { IconRow(YsIcons.Wind, LimeDeep, it) } }
            }
            Section("Key focus points") {
                Panel(CardWhite) { lesson.focusPoints.forEach { IconRow(YsIcons.Eye, Midnight, it) } }
            }
            Section("Common mistakes") {
                Panel(CardWhite) { lesson.commonMistakes.forEach { IconRow(YsIcons.Close, CoralDeep, it) } }
            }
            Section("Safety") {
                Panel(CoralTint) { lesson.safety.forEach { IconRow(YsIcons.Shield, CoralDeep, it) } }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "General wellness guidance, not medical advice. Stop if you feel pain, dizziness or shortness of breath.",
                    style = MaterialTheme.typography.bodySmall, color = InkMuted,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        BottomActions(onSkip = { onSkip(lesson) }, onContinue = { onContinue(lesson) })
    }
}

@Composable
private fun Header(lesson: LessonContent, scrollProgress: Float, onBack: () -> Unit) {
    Surface(color = WarmWhite) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("LESSON", style = MaterialTheme.typography.labelSmall, color = CoralDeep)
                    Text(lesson.info.name, style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(lesson.info.sanskrit, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            }
            LinearProgressIndicator(
                progress = { scrollProgress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Lime, trackColor = SurfaceMuted, drawStopIndicator = {},
            )
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
private fun Section(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Spacer(Modifier.height(Spacing.lg))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = InkMuted)
    }
    Spacer(Modifier.height(Spacing.sm + 4.dp))
    content()
}

@Composable
private fun Panel(color: Color, content: @Composable () -> Unit) {
    Surface(
        color = color, shape = MaterialTheme.shapes.large,
        border = if (color == CardWhite) androidx.compose.foundation.BorderStroke(1.dp, Hairline) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) { content() }
    }
}

@Composable
private fun IconRow(icon: ImageVector, tint: Color, text: String) {
    Row {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(Spacing.sm + 2.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@Composable
private fun NumberedRow(n: Int, text: String, last: Boolean) {
    Row(Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else Spacing.md - 4.dp)) {
        Surface(color = Midnight, shape = CircleShape, modifier = Modifier.size(28.dp)) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$n", style = MaterialTheme.typography.labelMedium, color = Lime)
            }
        }
        Spacer(Modifier.width(Spacing.md - 4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun BottomActions(onSkip: () -> Unit, onContinue: () -> Unit) {
    Surface(color = CardWhite) {
        Column {
            HorizontalDivider(color = Hairline)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.sm + 4.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton("Skip lesson", onSkip, modifier = Modifier.weight(1f).height(56.dp))
                PrimaryButton("Continue to practice", onContinue, icon = YsIcons.Play, modifier = Modifier.weight(1.6f))
            }
        }
    }
}

