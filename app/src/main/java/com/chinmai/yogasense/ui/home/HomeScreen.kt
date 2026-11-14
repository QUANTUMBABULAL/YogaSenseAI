package com.chinmai.yogasense.ui.home

import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.chinmai.yogasense.ui.components.GlowCard
import com.chinmai.yogasense.ui.components.RingProgress
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.LimeDeep
import com.chinmai.yogasense.ui.theme.Radius
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
    /** Receives the suggested asana id (null = free practice). */
    onStartPractice: (String?) -> Unit,
    onOpenAsana: (String) -> Unit,
    onOpenExplore: () -> Unit,
    onOpenProfile: () -> Unit,
    onCheckIn: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    profileName: String? = null,
    /** Active profile's wellness store; null while the one-time wellness import is unresolved. */
    wellness: com.chinmai.yogasense.data.WellnessRepository? = null,
    personalisation: com.chinmai.yogasense.profile.PersonalisationView? = null,
    onPersonalise: () -> Unit = {},
    onDismissPersonalise: () -> Unit = {},
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
        item(key = "today") { TodayCard(featured, { onStartPractice(featured.id) }, onOpenAsana, Modifier.padding(horizontal = Spacing.gutter)) }
        if (suggestion != null && personalisation != null && (suggestion.sessionPlan != null || personalisation.p.bodyAreas.isNotEmpty() || personalisation.p.goals.isNotEmpty())) {
            item(key = "foryou") { ForYouCard(suggestion, personalisation, onOpenAsana, Modifier.padding(horizontal = Spacing.gutter)) }
        }
        item { CheckIn(Modifier.padding(horizontal = Spacing.gutter), wellness, onCheckIn) }
        item { WeeklyOverview(state, onOpenProgress, Modifier.padding(horizontal = Spacing.gutter)) }
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
                    AsanaCard(a, onClick = { onOpenAsana(a.id) }, modifier = Modifier.width(172.dp))
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
    Row(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(state.dateLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkMuted)
            Spacer(Modifier.height(2.dp))
            Text(state.greeting + if (profileName != null) "," else "", style = MaterialTheme.typography.displaySmall, color = Ink)
            if (profileName != null) Text(profileName, style = MaterialTheme.typography.displaySmall, color = CoralDeep, maxLines = 1)
        }
        IconButton(onClick = onOpenProfile, modifier = Modifier.size(56.dp)) {
            if (profileName != null) com.chinmai.yogasense.ui.profile.ProfileAvatar(profileName, size = 48)
            else Box(Modifier.size(48.dp).clip(CircleShape).background(Midnight), contentAlignment = Alignment.Center) {
                Icon(YsIcons.Profile, "Profile", tint = Lime, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Signature hero: today's suggested pose on a glowing midnight card with an oversized figure. */
@Composable
private fun TodayCard(a: AsanaInfo, onStart: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxWidth()) {
        GlowCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
            Row(
                Modifier.clip(CircleShape).background(Lime).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(YsIcons.Spark, null, tint = Midnight, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("TODAY'S PRACTICE", style = MaterialTheme.typography.labelSmall, color = Midnight)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(a.name, style = MaterialTheme.typography.displaySmall, color = WarmWhite, modifier = Modifier.fillMaxWidth(0.6f))
            Text(a.sanskrit, style = MaterialTheme.typography.bodyLarge, color = TextMuted)
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DarkPill(a.difficulty.label)
                DarkPill(a.holdLabel, YsIcons.Clock)
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) {
                PrimaryButton("Start practice", onStart, icon = YsIcons.Play, modifier = Modifier.weight(1f))
                Surface(
                    onClick = { onOpen(a.id) }, shape = CircleShape, color = Color.White.copy(alpha = 0.10f), contentColor = WarmWhite,
                    modifier = Modifier.size(56.dp).semantics { contentDescription = "How to do ${a.name}" },
                ) { Box(contentAlignment = Alignment.Center) { Icon(YsIcons.Info, null, modifier = Modifier.size(22.dp)) } }
            }
        }
        AsanaFigure(a.figure, Modifier.align(Alignment.TopEnd).padding(top = Spacing.lg, end = Spacing.xs).size(140.dp), color = Lime)
    }
}

@Composable
private fun DarkPill(text: String, icon: ImageVector? = null) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Lime, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = WarmWhite)
    }
}

/** One card for the daily check-in: a lime prompt before, today's three real answers after. */
@Composable
private fun CheckIn(modifier: Modifier, repo: com.chinmai.yogasense.data.WellnessRepository?, onCheckIn: () -> Unit) {
    // Today's real check-in for the active profile (nothing shown that wasn't entered).
    val empty = androidx.compose.runtime.remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.chinmai.yogasense.wellness.DailyCheckIn>()) }
    val all by (repo?.checkIns ?: empty).collectAsStateWithLifecycle()
    val day = com.chinmai.yogasense.wellness.WellnessDates.epochDay(System.currentTimeMillis())
    val t = all.firstOrNull { it.epochDay == day }
    val pending = t == null
    Surface(
        onClick = onCheckIn, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.hero),
        color = if (pending) Lime else CardWhite, contentColor = Midnight,
    ) {
        Column(Modifier.padding(Spacing.lg - 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DAILY CHECK-IN", style = MaterialTheme.typography.labelSmall, color = if (pending) OnLimeMuted else InkMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            repo == null -> "Preparing your wellness data"
                            pending -> "How are you today?"
                            else -> "Checked in today"
                        },
                        style = MaterialTheme.typography.headlineSmall, color = Midnight,
                    )
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Midnight), contentAlignment = Alignment.Center) {
                    Icon(if (pending) YsIcons.ChevronRight else YsIcons.Leaf, null, tint = Lime, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CheckInChip(YsIcons.Mood, "Mood", t?.mood?.let { com.chinmai.yogasense.wellness.Scales.MOOD.label(it) }, pending, Modifier.weight(1f))
                CheckInChip(YsIcons.Stress, "Stress", t?.stress?.let { com.chinmai.yogasense.wellness.Scales.STRESS.label(it) }, pending, Modifier.weight(1f))
                CheckInChip(YsIcons.Sleep, "Sleep", t?.sleepHours?.let { com.chinmai.yogasense.ui.wellness.formatHours(it) }, pending, Modifier.weight(1f))
            }
        }
    }
}

private val OnLimeMuted = Color(0xFF3B4A22)

@Composable
private fun CheckInChip(icon: ImageVector, label: String, value: String?, onLime: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(if (onLime) Color.White.copy(alpha = 0.45f) else SurfaceMuted)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: ${value ?: "not logged"}" },
    ) {
        Icon(icon, null, tint = Midnight, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(value ?: "—", style = MaterialTheme.typography.titleSmall, color = Midnight, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (onLime) OnLimeMuted else InkMuted)
    }
}

/** Week at a glance: ring of days practised (a count, not a target) beside real totals. */
@Composable
private fun WeeklyOverview(state: HomeUiState, onOpenProgress: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        SectionHeader("This week", actionLabel = "Progress", onAction = onOpenProgress)
        Spacer(Modifier.height(Spacing.sm))
        YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg - 4.dp) {
            val w = state.weekly
            val days = w?.practicedDaysOfWeek?.size ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingProgress(days / 7f, size = 92.dp, color = LimeDeep) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$days", style = MaterialTheme.typography.headlineSmall, color = Ink)
                        Text("of 7 days", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                }
                Spacer(Modifier.width(Spacing.lg - 4.dp))
                if (w != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row {
                            StatTile("Sessions", w.sessions.toString(), Modifier.weight(1f))
                            StatTile("Minutes", w.totalMinutes.toString(), Modifier.weight(1f))
                        }
                        Tag("${w.streakDays}-day streak", style = TagStyle.Coral, icon = YsIcons.Flame)
                    }
                } else {
                    Column {
                        Text("No sessions yet this week", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text("Finish a live practice to start filling your ring.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md + 4.dp))
            WeekStrip(state.weekLabels, state.todayIndex, w?.practicedDaysOfWeek.orEmpty())
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
