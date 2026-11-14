package com.chinmai.yogasense.ui.progress

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.chinmai.yogasense.ui.components.GlowCard
import com.chinmai.yogasense.ui.components.MeterBar
import com.chinmai.yogasense.ui.components.RingProgress
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.data.NoPracticeHistory
import com.chinmai.yogasense.data.PracticeHistoryRepository
import com.chinmai.yogasense.data.WeeklySummary
import com.chinmai.yogasense.data.AsanaHistory
import com.chinmai.yogasense.data.PracticeOverview
import com.chinmai.yogasense.ui.components.PracticeFormat
import com.chinmai.yogasense.ui.components.TagStyle
import kotlinx.coroutines.flow.combine
import com.chinmai.yogasense.ui.components.ComingSoonTag
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.ListRow
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.ProgressCard
import com.chinmai.yogasense.ui.components.SectionHeader
import com.chinmai.yogasense.ui.components.StatTile
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.WeekStrip
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.home.HomeStateFactory
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    val weekLabels: List<String>,
    val todayIndex: Int,
    val weekly: WeeklySummary? = null,
    val asanas: List<AsanaInfo> = AsanaCatalog.all,
    val overview: PracticeOverview = PracticeOverview.EMPTY,
) {
    /** Any counted session ever (abandoned sessions are never counted). */
    val hasData: Boolean get() = !overview.isEmpty
    fun history(a: AsanaInfo): AsanaHistory? = overview.perAsana.firstOrNull { it.asana == a.asana }
}

/** Reads only the active profile's real history from [PracticeHistoryRepository] (Room). */
class ProgressViewModel(repository: PracticeHistoryRepository = NoPracticeHistory) : ViewModel() {
    private val base = HomeStateFactory.create(System.currentTimeMillis()).let { ProgressUiState(it.weekLabels, it.todayIndex) }
    val state: StateFlow<ProgressUiState> = combine(repository.weeklySummary(), repository.overview()) { w, o -> base.copy(weekly = w, overview = o) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), base)
}

@Composable
fun ProgressScreen(onStartPractice: () -> Unit, onOpenWellness: () -> Unit = {}, viewModel: ProgressViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.padding(top = Spacing.md, bottom = Spacing.sm)) {
                Text("YOUR JOURNEY", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Text("Progress", style = MaterialTheme.typography.displaySmall, color = Ink)
            }
        }
        item {
            val w = state.weekly
            val days = w?.practicedDaysOfWeek?.size ?: 0
            GlowCard(Modifier.fillMaxWidth()) {
                Text("THIS WEEK", style = MaterialTheme.typography.labelSmall, color = Lime)
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingProgress(days / 7f, size = 104.dp, stroke = 11.dp, color = Lime, track = Color.White.copy(alpha = 0.12f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$days", style = MaterialTheme.typography.headlineMedium, color = WarmWhite)
                            Text("of 7 days", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md - 4.dp)) {
                        DarkStat(w?.sessions?.toString() ?: "0", "sessions")
                        DarkStat(w?.totalMinutes?.toString() ?: "0", "minutes")
                        DarkStat(w?.streakDays?.toString() ?: "0", "day streak")
                    }
                }
            }
        }
        item {
            ProgressCard("Weekly activity", YsIcons.Calendar) {
                WeekStrip(state.weekLabels, state.todayIndex, state.weekly?.practicedDaysOfWeek.orEmpty())
                if (!state.hasData) {
                    Spacer(Modifier.height(Spacing.md))
                    EmptyState(
                        YsIcons.Practice, "Your progress starts with one session",
                        "Totals and your streak appear after you finish a live practice session.",
                        action = { PrimaryButton("Start practice", onStartPractice, icon = YsIcons.Play) },
                    )
                }
            }
        }
        if (state.hasData) item {
            ProgressCard("All time", YsIcons.Progress) {
                val o = state.overview
                Row(Modifier.fillMaxWidth()) {
                    StatTile("Completed", o.completedSessions.toString(), Modifier.weight(1f))
                    StatTile("All sessions", o.recordedSessions.toString(), Modifier.weight(1f))
                    StatTile("Minutes", o.totalActiveMinutes.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Completed = you ended the session and held at least one pose steadily. A day counts towards your streak " +
                        "when it has a completed or ended-early session; the streak continues until a full day passes without practice.",
                    style = MaterialTheme.typography.bodySmall, color = InkMuted,
                )
            }
        }
        item {
            ProgressCard("Asana performance", YsIcons.Spark) {
                Text(
                    "From your recorded sessions. Scores are averages of the sessions where they could be measured; " +
                        "\"Not measured\" means there weren't enough clear samples — it is not a score of zero.",
                    style = MaterialTheme.typography.bodySmall, color = InkMuted,
                )
                Spacer(Modifier.height(Spacing.sm))
                state.asanas.forEachIndexed { i, a ->
                    val h = state.history(a)
                    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm + 2.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.titleSmall, color = Ink)
                                Text(a.sanskrit, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                            }
                            if (h == null) Tag("No sessions yet")
                            else Tag("${h.sessions} session${if (h.sessions == 1) "" else "s"}", style = TagStyle.Lime)
                        }
                        if (h != null) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                "Steady hold ${PracticeFormat.duration(h.totalStableHoldMs)} total · longest ${PracticeFormat.duration(h.longestHoldMs)} · last ${PracticeFormat.date(h.lastPracticedAtMillis)}",
                                style = MaterialTheme.typography.bodySmall, color = InkMuted,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                MeterBar("Alignment", h.averageAlignment, Modifier.weight(1f))
                                MeterBar("Stability", h.averageStability, Modifier.weight(1f))
                            }
                        }
                    }
                    if (i < state.asanas.lastIndex) HorizontalDivider(color = Hairline)
                }
            }
        }
        item { SectionHeader("Insights", Modifier.padding(top = Spacing.sm)) }
        item {
            YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
                ListRow(YsIcons.Leaf, "Wellness overview", subtitle = "Mood, stress, sleep and questionnaires", onClick = onOpenWellness, trailing = { androidx.compose.material3.Icon(YsIcons.ChevronRight, null, tint = InkMuted) })
                HorizontalDivider(color = Hairline)
                ListRow(YsIcons.Report, "Weekly report", subtitle = "A summary of your practice and wellbeing", enabled = false, trailing = { ComingSoonTag() })
            }
        }
        item {
            Text(
                "Trends will show associations, not causes, and are not a medical assessment.",
                style = MaterialTheme.typography.bodySmall, color = InkMuted,
            )
        }
    }
}

@Composable
private fun DarkStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = WarmWhite)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(bottom = 3.dp))
    }
}
