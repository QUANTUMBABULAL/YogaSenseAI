package com.chinmai.yogasense.ui.wellness

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chinmai.yogasense.app.LocalProfileSession
import com.chinmai.yogasense.ui.components.CardStyle
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.ListRow
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.SectionHeader
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.TagStyle
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import com.chinmai.yogasense.wellness.Questionnaires
import com.chinmai.yogasense.wellness.Scales
import com.chinmai.yogasense.wellness.WellnessDates
import com.chinmai.yogasense.wellness.WellnessInsights

@Composable
fun WellnessOverviewScreen(
    onCheckIn: () -> Unit,
    onQuestionnaire: (QuestionnaireType) -> Unit,
    onHistory: () -> Unit,
    onSupport: () -> Unit,
    onRecord: (String) -> Unit,
) {
    val context = LocalContext.current
    val wellnessRepo = LocalProfileSession.current.wellness
    val vm: WellnessViewModel = viewModel { WellnessViewModel(wellnessRepo) }
    val s by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.padding(top = Spacing.md)) {
                Text("Wellness", style = MaterialTheme.typography.displaySmall, color = Ink)
                Text("Mood, stress and sleep — for your own reflection", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
        }
        item { TodayCard(s, onCheckIn) }
        item { WeeklyCard(s) }
        item { SectionHeader("Daily trends", Modifier.padding(top = Spacing.sm), subtitle = "Self-reported ratings, not clinical measures") }
        item { DailyBarChart(s.mood, 5f, { WellnessInsights.describeAverage(Scales.MOOD, it) + " (${"%.1f".format(it)})" }) }
        item { DailyBarChart(s.stress, 5f, { WellnessInsights.describeAverage(Scales.STRESS, it) + " (${"%.1f".format(it)})" }) }
        item { DailyBarChart(s.sleep, 12f, { formatHours(Math.round(it * 2) / 2f) }) }
        item {
            SectionHeader(
                "Questionnaires", Modifier.padding(top = Spacing.sm),
                subtitle = "Optional screening tools about the past two weeks — not daily",
            )
        }
        item { QuestionnaireCard(QuestionnaireType.PHQ9, "Depression symptom screener", s.lastPhq9, s.questionnaires, onQuestionnaire, onRecord) }
        item { QuestionnaireCard(QuestionnaireType.GAD7, "Anxiety symptom screener", s.lastGad7, s.questionnaires, onQuestionnaire, onRecord) }
        item {
            YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
                ListRow(YsIcons.Calendar, "History", subtitle = "All check-ins and questionnaire results", onClick = onHistory)
                HorizontalDivider(color = Hairline)
                ListRow(YsIcons.Shield, "Support resources", subtitle = "Tele-MANAS 14416 and emergency help", onClick = onSupport)
            }
        }
        item { NotDiagnosisNote() }
    }
}

@Composable
private fun TodayCard(s: WellnessOverviewState, onCheckIn: () -> Unit) {
    val t = s.today
    YsCard(Modifier.fillMaxWidth(), style = CardStyle.Dark, contentPadding = Spacing.lg - 4.dp) {
        Text("TODAY · ${WellnessDates.dayLabel(s.todayEpochDay).uppercase()}", style = MaterialTheme.typography.labelSmall, color = Lime)
        Spacer(Modifier.height(Spacing.xs))
        if (t == null) {
            Text("How are you today?", style = MaterialTheme.typography.headlineSmall, color = WarmWhite)
            Text("A short check-in on mood, stress and sleep. Skip anything you like.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton("Check in", onCheckIn, icon = YsIcons.Leaf, modifier = Modifier.fillMaxWidth())
        } else {
            Text("Checked in", style = MaterialTheme.typography.headlineSmall, color = WarmWhite)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                listOfNotNull(
                    t.mood?.let { "Mood: ${Scales.MOOD.label(it)}" },
                    t.stress?.let { "Stress: ${Scales.STRESS.label(it)}" },
                    t.sleepHours?.let { "Sleep: ${formatHours(it)}" },
                    t.sleepQuality?.let { "Sleep quality: ${Scales.SLEEP_QUALITY.label(it)}" },
                ).joinToString(" · ").ifEmpty { "Reflection saved" },
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
            Spacer(Modifier.height(Spacing.md))
            SecondaryButtonOnDark("Edit today's check-in", onCheckIn)
        }
    }
}

@Composable
private fun SecondaryButtonOnDark(text: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Lime),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ) { Text(text, color = Lime, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun WeeklyCard(s: WellnessOverviewState) {
    val w = s.weekly
    YsCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
            Text("${WellnessDates.shortDay(w.startDay)} – ${WellnessDates.shortDay(w.endDay)}", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        }
        Spacer(Modifier.height(Spacing.sm))
        if (w.isEmpty) {
            EmptyState(YsIcons.Leaf, "No check-ins this week", "Whenever you check in, a gentle weekly reflection will appear here.")
            return@YsCard
        }
        Text("${w.checkIns} of 7 days checked in", style = MaterialTheme.typography.bodyMedium, color = Ink)
        if (w.hasEnoughForPatterns) {
            w.averageMood?.let { Text("Mood mostly: ${WellnessInsights.describeAverage(Scales.MOOD, it)}", style = MaterialTheme.typography.bodyMedium, color = Ink) }
            w.averageStress?.let { Text("Stress mostly: ${WellnessInsights.describeAverage(Scales.STRESS, it)}", style = MaterialTheme.typography.bodyMedium, color = Ink) }
            w.averageSleepHours?.let { Text("Average sleep recorded: ${formatHours(Math.round(it * 2) / 2f)}", style = MaterialTheme.typography.bodyMedium, color = Ink) }
        } else {
            Text("Patterns are shown after at least ${WellnessInsights.MIN_ENTRIES} check-ins in a week.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Text(
            if (w.practiceSessions == null) "Practice sessions aren't recorded yet, so they're not included." else "${w.practiceSessions} practice sessions",
            style = MaterialTheme.typography.bodySmall, color = InkMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        Tag("Reflect", style = TagStyle.Lime, icon = YsIcons.Leaf)
        Spacer(Modifier.height(Spacing.xs))
        Text(w.prompt, style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}

@Composable
private fun QuestionnaireCard(
    type: QuestionnaireType,
    subtitle: String,
    last: QuestionnaireRecord?,
    history: List<QuestionnaireRecord>,
    onStart: (QuestionnaireType) -> Unit,
    onRecord: (String) -> Unit,
) {
    val q = Questionnaires.of(type)
    val due = WellnessInsights.isQuestionnaireDue(type, history, System.currentTimeMillis())
    YsCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(q.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            if (last != null) Tag("${last.total} / ${q.maxScore}", style = TagStyle.Neutral)
        }
        Spacer(Modifier.height(Spacing.sm))
        if (last == null) {
            Text("Not taken yet.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        } else {
            Text("Last: ${last.bandLabel} · ${WellnessDates.dateLabel(last.completedAtMillis)}", style = MaterialTheme.typography.bodyMedium, color = Ink)
            if (!due) Text("It asks about the past two weeks, so it's most useful about every 2 weeks.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton(if (last == null) "Take ${q.title}" else "Take again", { onStart(type) }, modifier = Modifier.weight(1f))
            if (last != null) SecondaryButton("View result", { onRecord(last.id) }, modifier = Modifier.weight(1f))
        }
    }
}
