package com.chinmai.yogasense.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.ui.components.CardTone
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.IconBadge
import com.chinmai.yogasense.ui.components.SectionHeader
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.TagTone
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.components.YsPrimaryButton
import com.chinmai.yogasense.ui.components.YsSecondaryButton
import com.chinmai.yogasense.ui.theme.Accent
import com.chinmai.yogasense.ui.theme.AccentContainer
import com.chinmai.yogasense.ui.theme.Outline
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.Surface2
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.TextPrimary
import com.chinmai.yogasense.ui.theme.TextSubtle

@Composable
fun HomeScreen(onStartPractice: () -> Unit, onOpenProgress: () -> Unit) {
    val state = remember { HomeStateFactory.create(System.currentTimeMillis()) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Greeting(state)
        StartPracticeCard(onStartPractice)
        CheckInSection()
        WeeklySummary(state)
        RecentActivity()
        WellnessOverview(onOpenProgress)
        Recommended(state, onStartPractice)
        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun Greeting(state: HomeUiState) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YogaSense", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Text(" AI", style = MaterialTheme.typography.labelLarge, color = Accent)
            Spacer(Modifier.weight(1f))
            Text(state.dateLabel, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(state.greeting, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text("Take a few mindful minutes for your body today.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}

@Composable
private fun StartPracticeCard(onStart: () -> Unit) {
    YsCard(tone = CardTone.Accent, contentPadding = Spacing.xl) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(YsIcons.Practice, size = 44.dp, container = Surface2)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Live pose practice", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("On-device tracking for 5 foundational asanas", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        YsPrimaryButton("Start practice", onStart, icon = YsIcons.Play, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Practise in a clear space. Stop if you feel pain or dizziness.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
        )
    }
}

@Composable
private fun CheckInSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("Daily check-in", subtitle = "Mood, stress and sleep in under a minute", trailing = { Tag("Coming soon") })
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CheckInTile(YsIcons.Mood, "Mood", Modifier.weight(1f))
            CheckInTile(YsIcons.Stress, "Stress", Modifier.weight(1f))
            CheckInTile(YsIcons.Sleep, "Sleep", Modifier.weight(1f))
        }
    }
}

/** Disabled until check-ins are implemented (Phase 6); visibly not interactive. */
@Composable
private fun CheckInTile(icon: ImageVector, label: String, modifier: Modifier) {
    YsCard(modifier = modifier, contentPadding = Spacing.md) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, size = 40.dp, tint = TextMuted, container = Surface2)
            Spacer(Modifier.height(Spacing.sm))
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text("Not logged", style = MaterialTheme.typography.labelSmall, color = TextSubtle)
        }
    }
}

@Composable
private fun WeeklySummary(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("This week", subtitle = "${state.sessionsThisWeek} sessions recorded")
        YsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.weekDays.forEach { d -> DayDot(d) }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                if (state.hasPracticeHistory) "" else "Your practice days will be marked here once session recording is available.",
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
            )
        }
    }
}

@Composable
private fun DayDot(d: WeekDay) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.label, style = MaterialTheme.typography.labelSmall, color = if (d.isToday) Accent else TextMuted)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (d.practiced) AccentContainer else Surface2)
                .border(1.dp, if (d.isToday) Accent else Outline, CircleShape),
        )
    }
}

@Composable
private fun RecentActivity() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("Recent activity")
        YsCard {
            EmptyState(
                YsIcons.Calendar,
                "No sessions yet",
                "Completed practice sessions will appear here.",
            )
        }
    }
}

@Composable
private fun WellnessOverview(onOpenProgress: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("Wellness overview")
        YsCard {
            EmptyState(
                YsIcons.Leaf,
                "No check-ins yet",
                "Mood, stress and sleep trends appear after a few daily check-ins.",
                action = { YsSecondaryButton("View progress", onOpenProgress) },
            )
            HorizontalDivider(color = Outline)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "YogaSense AI supports wellbeing. It is not a medical device and does not diagnose any condition.",
                style = MaterialTheme.typography.bodySmall, color = TextSubtle, textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun Recommended(state: HomeUiState, onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("Recommended practice", subtitle = "Foundations flow · beginner", trailing = { Tag("Guide", tone = TagTone.Accent) })
        YsCard {
            state.recommended.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Accent)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(p.asana.displayName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(listOfNotNull(p.focus, p.note).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                if (i < state.recommended.lastIndex) HorizontalDivider(color = Outline)
            }
            Spacer(Modifier.height(Spacing.md))
            YsSecondaryButton("Begin with Tadasana", onStart, icon = YsIcons.Play, modifier = Modifier.fillMaxWidth())
        }
    }
}
