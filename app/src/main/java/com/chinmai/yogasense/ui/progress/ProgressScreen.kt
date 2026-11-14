package com.chinmai.yogasense.ui.progress

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    val weekLabels: List<String>,
    val todayIndex: Int,
    val weekly: WeeklySummary? = null,
    val asanas: List<AsanaInfo> = AsanaCatalog.all,
) {
    val hasData: Boolean get() = weekly != null && weekly.sessions > 0
}

/** Reads only real history from [PracticeHistoryRepository]; Room plugs in here later. */
class ProgressViewModel(repository: PracticeHistoryRepository = NoPracticeHistory) : ViewModel() {
    private val base = HomeStateFactory.create(System.currentTimeMillis()).let { ProgressUiState(it.weekLabels, it.todayIndex) }
    val state: StateFlow<ProgressUiState> = repository.weeklySummary()
        .map { base.copy(weekly = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), base)
}

@Composable
fun ProgressScreen(onStartPractice: () -> Unit, viewModel: ProgressViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.padding(top = Spacing.md, bottom = Spacing.sm)) {
                Text("Progress", style = MaterialTheme.typography.displaySmall, color = Ink)
                Text("Practice quality and wellness, side by side", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
        }
        item {
            ProgressCard("Practice overview", YsIcons.Progress) {
                val w = state.weekly
                Row(Modifier.fillMaxWidth()) {
                    StatTile("Sessions", w?.sessions?.toString(), Modifier.weight(1f))
                    StatTile("Minutes", w?.totalMinutes?.toString(), Modifier.weight(1f))
                    StatTile("Day streak", w?.streakDays?.toString(), Modifier.weight(1f))
                }
                if (!state.hasData) {
                    Spacer(Modifier.height(Spacing.md))
                    EmptyState(
                        YsIcons.Practice, "Your progress starts with one session",
                        "Totals, streaks and trends appear after practice sessions are recorded.",
                        action = { PrimaryButton("Start practice", onStartPractice, icon = YsIcons.Play) },
                    )
                }
            }
        }
        item {
            ProgressCard("Weekly activity", YsIcons.Calendar) {
                WeekStrip(state.weekLabels, state.todayIndex, state.weekly?.practicedDaysOfWeek.orEmpty())
                if (!state.hasData) {
                    Spacer(Modifier.height(Spacing.sm + 4.dp))
                    Text("Practice days will be highlighted here.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            }
        }
        item {
            ProgressCard("Asana performance", YsIcons.Spark) {
                Text(
                    "Alignment, balance and hold time per pose will be scored from your live sessions.",
                    style = MaterialTheme.typography.bodySmall, color = InkMuted,
                )
                Spacer(Modifier.height(Spacing.sm))
                state.asanas.forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm + 2.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleSmall, color = Ink)
                            Text(a.sanskrit, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        }
                        Tag("No sessions yet")
                    }
                    if (i < state.asanas.lastIndex) HorizontalDivider(color = Hairline)
                }
            }
        }
        item { SectionHeader("Insights", Modifier.padding(top = Spacing.sm)) }
        item {
            YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
                ListRow(YsIcons.Leaf, "Wellness overview", subtitle = "Mood, stress and sleep trends", enabled = false, trailing = { ComingSoonTag() })
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

