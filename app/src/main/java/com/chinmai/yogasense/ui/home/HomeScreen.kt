package com.chinmai.yogasense.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.ui.components.AsanaCard
import com.chinmai.yogasense.ui.components.AsanaFigure
import com.chinmai.yogasense.ui.components.CardStyle
import com.chinmai.yogasense.ui.components.ComingSoonTag
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.IconBadge
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SectionHeader
import com.chinmai.yogasense.ui.components.StatTile
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.TagStyle
import com.chinmai.yogasense.ui.components.WeekStrip
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.Coral
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.SurfaceMuted
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite

@Composable
fun HomeScreen(
    onStartPractice: () -> Unit,
    onOpenAsana: (String) -> Unit,
    onOpenExplore: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item { Header(state, onOpenProfile) }
        item { TodayCard(state.featured, onStartPractice, onOpenAsana, Modifier.padding(horizontal = Spacing.gutter)) }
        item { CheckIn(Modifier.padding(horizontal = Spacing.gutter)) }
        item { WeeklyOverview(state, onStartPractice, Modifier.padding(horizontal = Spacing.gutter)) }
        item {
            SectionHeader(
                "Explore yoga", Modifier.padding(horizontal = Spacing.gutter),
                subtitle = "Recommended poses to learn", actionLabel = "See all", onAction = onOpenExplore,
            )
            Spacer(Modifier.height(Spacing.sm))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md - 4.dp),
            ) {
                items(state.explore, key = { it.id }) { a ->
                    AsanaCard(a, onClick = { onOpenAsana(a.id) }, modifier = Modifier.width(164.dp))
                }
            }
        }
        item { RecentActivity(state, Modifier.padding(horizontal = Spacing.gutter)) }
    }
}

@Composable
private fun Header(state: HomeUiState, onOpenProfile: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Midnight), contentAlignment = Alignment.Center) {
                Icon(YsIcons.Practice, null, tint = Lime, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
            Text("YogaSense", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("AI", style = MaterialTheme.typography.titleMedium, color = Coral)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenProfile, modifier = Modifier.size(48.dp)) {
                IconBadge(YsIcons.Profile, size = 40.dp, container = SurfaceMuted)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(state.dateLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkMuted)
        Text(state.greeting, style = MaterialTheme.typography.displaySmall, color = Ink)
        Text("Move better. Feel better.", style = MaterialTheme.typography.bodyLarge, color = InkMuted)
    }
}

@Composable
private fun TodayCard(a: AsanaInfo, onStart: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier) {
    YsCard(modifier.fillMaxWidth(), style = CardStyle.Dark, contentPadding = Spacing.lg - 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TODAY'S PRACTICE", style = MaterialTheme.typography.labelSmall, color = Lime)
                Spacer(Modifier.height(Spacing.xs))
                Text(a.name, style = MaterialTheme.typography.headlineSmall, color = WarmWhite)
                Text(a.sanskrit, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Spacer(Modifier.height(Spacing.md - 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DarkPill(a.difficulty.label)
                    DarkPill(a.holdLabel, YsIcons.Clock)
                }
            }
            Box(Modifier.size(104.dp).clip(CircleShape).background(Surface1), contentAlignment = Alignment.Center) {
                AsanaFigure(a.figure, Modifier.size(84.dp), color = Lime)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton("Start practice", onStart, icon = YsIcons.Play, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { onOpen(a.id) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("How to do this pose", style = MaterialTheme.typography.labelLarge, color = WarmWhite)
        }
    }
}

@Composable
private fun DarkPill(text: String, icon: ImageVector? = null) {
    Row(
        Modifier.clip(CircleShape).background(Surface1).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Lime, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = WarmWhite)
    }
}

@Composable
private fun CheckIn(modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Daily check-in", Modifier.weight(1f), subtitle = "How are you today?")
            ComingSoonTag()
        }
        Spacer(Modifier.height(Spacing.sm + 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 4.dp)) {
            CheckInTile(YsIcons.Mood, "Mood", Modifier.weight(1f))
            CheckInTile(YsIcons.Stress, "Stress", Modifier.weight(1f))
            CheckInTile(YsIcons.Sleep, "Sleep", Modifier.weight(1f))
        }
    }
}

/** Entry point only; check-in flows arrive in a later phase, so the tile is not interactive. */
@Composable
private fun CheckInTile(icon: ImageVector, label: String, modifier: Modifier) {
    YsCard(modifier, contentPadding = Spacing.md - 4.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, size = 44.dp, tint = InkMuted, container = SurfaceMuted)
            Spacer(Modifier.height(Spacing.sm))
            Text(label, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text("Not logged", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}

@Composable
private fun WeeklyOverview(state: HomeUiState, onStart: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        SectionHeader("This week")
        Spacer(Modifier.height(Spacing.sm + 4.dp))
        YsCard(Modifier.fillMaxWidth()) {
            val w = state.weekly
            if (w != null) {
                Row {
                    StatTile("Sessions", w.sessions.toString(), Modifier.weight(1f))
                    StatTile("Minutes", w.totalMinutes.toString(), Modifier.weight(1f))
                    StatTile("Day streak", w.streakDays.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.md))
            }
            WeekStrip(state.weekLabels, state.todayIndex, w?.practicedDaysOfWeek.orEmpty())
            if (w == null) {
                Spacer(Modifier.height(Spacing.md))
                Text("No sessions recorded yet", style = MaterialTheme.typography.titleSmall, color = Ink)
                Text(
                    "Sessions, practice time and your streak will appear here once session recording is available.",
                    style = MaterialTheme.typography.bodySmall, color = InkMuted,
                )
            }
        }
    }
}

@Composable
private fun RecentActivity(state: HomeUiState, modifier: Modifier) {
    Column(modifier) {
        SectionHeader("Recent activity")
        Spacer(Modifier.height(Spacing.sm + 4.dp))
        YsCard(Modifier.fillMaxWidth()) {
            if (state.recent.isEmpty()) {
                EmptyState(YsIcons.Calendar, "No activity yet", "Your completed practice sessions will be listed here.")
            } else {
                state.recent.forEach { s ->
                    Text("${s.asana.displayName} · ${s.durationSeconds / 60} min", style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Tag("Wellness tool · not a medical device", style = TagStyle.Outline)
    }
}
