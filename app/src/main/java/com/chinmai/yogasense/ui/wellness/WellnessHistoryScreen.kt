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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chinmai.yogasense.app.LocalProfileSession
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.SectionHeader
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.wellness.Questionnaires
import com.chinmai.yogasense.wellness.Scales
import com.chinmai.yogasense.wellness.WellnessDates

@Composable
fun WellnessHistoryScreen(onBack: () -> Unit, onRecord: (String) -> Unit) {
    val context = LocalContext.current
    val wellnessRepo = LocalProfileSession.current.wellness
    val vm: WellnessViewModel = viewModel { WellnessViewModel(wellnessRepo) }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = CardWhite, contentColor = Ink)) {
                Icon(YsIcons.Back, "Back")
            }
            Spacer(Modifier.size(Spacing.sm))
            Text("Wellness history", style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { SectionHeader("Questionnaires", subtitle = "Screening results — not diagnoses") }
            if (s.questionnaires.isEmpty()) {
                item { YsCard(Modifier.fillMaxWidth()) { EmptyState(YsIcons.Report, "No questionnaire results", "Results you choose to save will be listed here.") } }
            } else items(s.questionnaires, key = { "q${it.id}" }) { r ->
                val q = Questionnaires.of(r.type)
                YsCard(Modifier.fillMaxWidth(), onClick = { onRecord(r.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${q.title} · ${WellnessDates.dateLabel(r.completedAtMillis)}", style = MaterialTheme.typography.titleSmall, color = Ink)
                            Text(r.bandLabel, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        }
                        Tag("${r.total} / ${q.maxScore}")
                    }
                }
            }
            item { SectionHeader("Daily check-ins", Modifier.padding(top = Spacing.md), subtitle = "Self-reported ratings") }
            if (s.checkIns.isEmpty()) {
                item { YsCard(Modifier.fillMaxWidth()) { EmptyState(YsIcons.Calendar, "No check-ins yet", "Your daily check-ins will appear here, newest first.") } }
            } else items(s.checkIns, key = { "c${it.epochDay}" }) { c ->
                YsCard(Modifier.fillMaxWidth()) {
                    Text(WellnessDates.dayLabel(c.epochDay), style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text(
                        listOfNotNull(
                            c.mood?.let { "Mood ${Scales.MOOD.label(it)}" },
                            c.stress?.let { "Stress ${Scales.STRESS.label(it)}" },
                            c.sleepHours?.let { "Sleep ${formatHours(it)}" },
                            c.sleepQuality?.let { "Quality ${Scales.SLEEP_QUALITY.label(it)}" },
                        ).joinToString(" · ").ifEmpty { "Reflection only" },
                        style = MaterialTheme.typography.bodySmall, color = InkMuted,
                    )
                    c.reflection?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
            }
            item {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    "Deleting removes every check-in, questionnaire result and your storage choice from this phone. This can't be undone.",
                    style = MaterialTheme.typography.bodySmall, color = InkMuted,
                )
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton("Delete all wellness data", { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), icon = YsIcons.Trash)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all wellness data?") },
            text = { Text("All check-ins and questionnaire results will be permanently removed from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAll() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
