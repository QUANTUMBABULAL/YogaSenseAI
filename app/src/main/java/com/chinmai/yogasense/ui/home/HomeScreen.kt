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
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
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
import com.chinmai.yogasense.ui.components.PracticeFormat
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
    onCheckIn: () -> Unit = {},
    profileName: String? = null,
    /** Active profile's wellness store; null while the one-time wellness import is unresolved. */
    wellness: com.chinmai.yogasense.data.WellnessRepository? = null,
    personalisation: com.chinmai.yogasense.profile.PersonalisationView? = null,
    onPersonalise: () -> Unit = {},
    onDismissPersonalise: () -> Unit = {},
    /** Hands-free guided session over the planned poses. */
    onStartGuided: (List<com.chinmai.yogasense.asana.Asana>) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dayOfYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }
    // Computed once per personalisation change (pure, cheap), not on every recomposition.
    val suggestion = remember(personalisation, dayOfYear) {
        personalisation?.let { com.chinmai.yogasense.profile.PracticeRecommender.suggest(it.experienceLevel, it.personalisation, it.preferredMinutes, dayOfYear) }
    }
    val featured = suggestion?.featured ?: state.featured
    val explore = suggestion?.ordered ?: state.explore
    LazyColumn(
        Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item { Header(state, profileName, onOpenProfile) }
        if (personalisation?.showPrompt == true) {
            item(key = "personalise") { PersonalisePrompt(onPersonalise, onDismissPersonalise, Modifier.padding(horizontal = Spacing.gutter)) }
        }
        item(key = "today") {
            // Personalised session plan when available (Phase 7B.5 recommender), otherwise all poses.
            val plan = (suggestion?.sessionPlan?.poses ?: explore).map { it.asana }
            TodayCard(featured, onStartPractice, onOpenAsana, Modifier.padding(horizontal = Spacing.gutter), onGuided = { onStartGuided(plan) }, guidedCount = plan.size)
        }
        if (suggestion != null && personalisation != null && (suggestion.sessionPlan != null || personalisation.p.bodyAreas.isNotEmpty() || personalisation.p.goals.isNotEmpty())) {
            item(key = "foryou") { ForYouCard(suggestion, personalisation, onOpenAsana, Modifier.padding(horizontal = Spacing.gutter)) }
        }
        item { CheckIn(Modifier.padding(horizontal = Spacing.gutter), wellness, onCheckIn) }
        item { WeeklyOverview(state, onStartPractice, Modifier.padding(horizontal = Spacing.gutter)) }
        item {
            SectionHeader(
                "Explore yoga", Modifier.padding(horizontal = Spacing.gutter),
                subtitle = if (suggestion != null) "Ordered for your level and goals" else "Recommended poses to learn", actionLabel = "See all", onAction = onOpenExplore,
            )
            Spacer(Modifier.height(Spacing.sm))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md - 4.dp),
            ) {
                items(explore, key = { it.id }) { a ->
                    AsanaCard(a, onClick = { onOpenAsana(a.id) }, modifier = Modifier.width(164.dp))
                }
            }
        }
        item { RecentActivity(state, Modifier.padding(horizontal = Spacing.gutter)) }
    }
}

@Composable
private fun PersonalisePrompt(onStart: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    YsCard(modifier.fillMaxWidth(), style = CardStyle.Lime) {
        Text("PERSONALISE", style = MaterialTheme.typography.labelSmall, color = Midnight)
        Spacer(Modifier.height(Spacing.xs))
        Text("Make YogaSenseAI yours", style = MaterialTheme.typography.titleLarge, color = Midnight)
        Text("About 2 minutes. Every question is optional and your history stays as it is.", style = MaterialTheme.typography.bodySmall, color = Midnight)
        Spacer(Modifier.height(Spacing.md - 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).background(Midnight).clickable(onClick = onStart).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("Start", style = MaterialTheme.typography.labelLarge, color = WarmWhite)
            }
            Spacer(Modifier.width(Spacing.sm))
            TextButton(onClick = onDismiss) { Text("Not now", style = MaterialTheme.typography.labelLarge, color = Midnight) }
        }
    }
}

/** General suggestions from the user's own answers — clearly labelled, never medical. */
@Composable
private fun ForYouCard(
    s: com.chinmai.yogasense.profile.PracticeRecommender.Suggestion,
    v: com.chinmai.yogasense.profile.PersonalisationView,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    YsCard(modifier.fillMaxWidth()) {
        Text("FOR YOU", style = MaterialTheme.typography.labelSmall, color = InkMuted)
        Spacer(Modifier.height(Spacing.xs))
        val plan = s.sessionPlan
        if (plan != null) {
            Text("A ${plan.minutes}-minute session idea", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(Spacing.sm))
            plan.poses.forEach { a ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(a.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(SurfaceMuted), contentAlignment = Alignment.Center) { AsanaFigure(a.figure, Modifier.size(28.dp)) }
                    Spacer(Modifier.width(Spacing.md - 4.dp))
                    Text(a.name, style = MaterialTheme.typography.titleSmall, color = Ink, modifier = Modifier.weight(1f))
                    Text(a.holdLabel, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            }
        } else {
            Text("Suggested for you", style = MaterialTheme.typography.titleMedium, color = Ink)
        }
        if (s.reasons.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(s.reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        val areas = v.p.bodyAreas.keys
        if (areas.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Extra attention: ${areas.joinToString { it.label }}. Move gently and skip anything that causes pain.",
                style = MaterialTheme.typography.bodySmall, color = Ink,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text("General wellness suggestions, not medical advice.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

@Composable
private fun Header(state: HomeUiState, profileName: String?, onOpenProfile: () -> Unit) {
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
                if (profileName != null) com.chinmai.yogasense.ui.profile.ProfileAvatar(profileName, size = 40)
                else IconBadge(YsIcons.Profile, size = 40.dp, container = SurfaceMuted)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(state.dateLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkMuted)
        Text(if (profileName != null) "${state.greeting}, $profileName" else state.greeting, style = MaterialTheme.typography.displaySmall, color = Ink)
        Text("Move better. Feel better.", style = MaterialTheme.typography.bodyLarge, color = InkMuted)
    }
}

@Composable
private fun TodayCard(a: AsanaInfo, onStart: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier, onGuided: () -> Unit = {}, guidedCount: Int = 0) {
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
        PrimaryButton("Guided session · $guidedCount poses", onGuided, icon = YsIcons.Play, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onStart, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Free practice", style = MaterialTheme.typography.labelLarge, color = Lime)
        }
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
private fun CheckIn(modifier: Modifier, repo: com.chinmai.yogasense.data.WellnessRepository?, onCheckIn: () -> Unit) {
    // Today's real check-in for the active profile (nothing shown that wasn't entered).
    val empty = androidx.compose.runtime.remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.chinmai.yogasense.wellness.DailyCheckIn>()) }
    val all by (repo?.checkIns ?: empty).collectAsStateWithLifecycle()
    val today = com.chinmai.yogasense.wellness.WellnessDates.epochDay(System.currentTimeMillis())
    val t = all.firstOrNull { it.epochDay == today }
    Column(modifier) {
        SectionHeader(
            "Daily check-in",
            subtitle = when {
                repo == null -> "Preparing your wellness data · tap for details"
                t == null -> "How are you today? Tap to check in"
                else -> "Checked in today · tap to edit"
            },
        )
        Spacer(Modifier.height(Spacing.sm + 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 4.dp)) {
            CheckInTile(YsIcons.Mood, "Mood", t?.mood?.let { com.chinmai.yogasense.wellness.Scales.MOOD.label(it) }, onCheckIn, Modifier.weight(1f))
            CheckInTile(YsIcons.Stress, "Stress", t?.stress?.let { com.chinmai.yogasense.wellness.Scales.STRESS.label(it) }, onCheckIn, Modifier.weight(1f))
            CheckInTile(YsIcons.Sleep, "Sleep", t?.sleepHours?.let { com.chinmai.yogasense.ui.wellness.formatHours(it) }, onCheckIn, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CheckInTile(icon: ImageVector, label: String, value: String?, onClick: () -> Unit, modifier: Modifier) {
    YsCard(modifier, contentPadding = Spacing.md - 4.dp, onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, size = 44.dp, tint = if (value == null) InkMuted else Midnight, container = if (value == null) SurfaceMuted else Lime)
            Spacer(Modifier.height(Spacing.sm))
            Text(label, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(value ?: "Not logged", style = MaterialTheme.typography.bodySmall, color = InkMuted, maxLines = 1)
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
                    "Finish a live practice session and your sessions, practice time and streak appear here.",
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
            val o = state.overview
            if (o.isEmpty) {
                EmptyState(YsIcons.Calendar, "No activity yet", "Your recorded practice sessions will be listed here.")
            } else {
                Row {
                    StatTile("Completed", o.completedSessions.toString(), Modifier.weight(1f))
                    StatTile("All sessions", o.recordedSessions.toString(), Modifier.weight(1f))
                    StatTile("Last session", o.lastSession?.let { PracticeFormat.duration(it.activeDurationMs) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.sm))
                o.recent.forEach { s ->
                    androidx.compose.material3.HorizontalDivider(color = com.chinmai.yogasense.ui.theme.Hairline)
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.asanas.joinToString { it.displayName }.ifEmpty { "No pose recognised" },
                                style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1,
                            )
                            Text("${PracticeFormat.date(s.startedAtMillis)} · ${PracticeFormat.duration(s.activeDurationMs)}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        }
                        Tag(PracticeFormat.completion(s.completion), style = if (s.completion == com.chinmai.yogasense.data.db.SessionCompletion.COMPLETED) TagStyle.Lime else TagStyle.Neutral)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Tag("Wellness tool · not a medical device", style = TagStyle.Outline)
    }
}
