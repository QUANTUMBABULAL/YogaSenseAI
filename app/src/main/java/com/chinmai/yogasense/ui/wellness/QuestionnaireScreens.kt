package com.chinmai.yogasense.ui.wellness

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chinmai.yogasense.app.LocalProfileSession
import com.chinmai.yogasense.ui.components.CardStyle
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.SurfaceMuted
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import com.chinmai.yogasense.wellness.Questionnaire
import com.chinmai.yogasense.wellness.QuestionnaireFlow.State
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import com.chinmai.yogasense.wellness.Questionnaires
import com.chinmai.yogasense.wellness.WellnessDates
import com.chinmai.yogasense.wellness.WellnessInsights

@Composable
private fun TopBar(title: String, onClose: () -> Unit, closeLabel: String = "Close") {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = CardWhite, contentColor = Ink)) {
            Icon(YsIcons.Close, closeLabel)
        }
        Spacer(Modifier.size(Spacing.sm))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
    }
}

@Composable
fun QuestionnaireScreen(type: QuestionnaireType, onDone: () -> Unit) {
    val context = LocalContext.current
    val wellnessRepo = LocalProfileSession.current.wellness
    val vm: QuestionnaireViewModel = viewModel(key = "q-$type") { QuestionnaireViewModel(wellnessRepo, type) }
    val s by vm.state.collectAsStateWithLifecycle()
    val q = vm.questionnaire
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val inProgress = s.flowState is State.Question || s.flowState is State.Difficulty || s.flowState is State.Review

    androidx.compose.runtime.LaunchedEffect(s.flowState) { if (s.flowState == State.Exited) onDone() }
    BackHandler(enabled = inProgress) { confirmExit = true }

    Column(Modifier.fillMaxSize()) {
        TopBar(q.title, onClose = { if (inProgress) confirmExit = true else onDone() }, closeLabel = "Exit questionnaire")
        when (val st = s.flowState) {
            State.Intro -> Intro(q, onStart = vm::start, onDecline = vm::decline)
            is State.Question -> QuestionPage(q, st.index, s.answers[st.index], s.progress, onAnswer = { vm.answer(st.index, it) }, onBack = vm::back)
            is State.Support -> SupportPage(onContinue = vm::continueAfterSupport, onStop = vm::exit)
            State.Difficulty -> DifficultyPage(q, s.difficulty, onChoose = vm::chooseDifficulty, onBack = vm::back)
            State.Review -> ReviewPage(q, s.answers, onFinish = vm::finish, onBack = vm::back)
            is State.Result -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
                ResultContent(q, st.total, st.band.label, st.band.explanation, System.currentTimeMillis(), s.item9Positive, s.comparisonText)
                if (s.notSaved) Text("This result was not saved.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                if (s.savedRecord != null) Text("Saved on this phone.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                Spacer(Modifier.height(Spacing.md))
                PrimaryButton("Done", onDone, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.lg))
            }
            State.Exited -> Unit
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave the questionnaire?") },
            text = { Text("Your answers so far won't be saved. You can take it another time.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.exit() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } },
        )
    }
    if (s.needsConsent) ConsentDialog(onAnswer = vm::answerConsent)
}

@Composable
private fun Intro(q: Questionnaire, onStart: () -> Unit, onDecline: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            if (q.type == QuestionnaireType.PHQ9) "A short screening questionnaire about mood over the past two weeks."
            else "A short screening questionnaire about worry and anxiety over the past two weeks.",
            style = MaterialTheme.typography.headlineSmall, color = Ink,
        )
        YsCard(Modifier.fillMaxWidth()) {
            listOf(
                "${q.items.size} questions, about 2 minutes.",
                "Questions use the standard ${q.title} wording and scoring.",
                "It looks back over the last 2 weeks, so it isn't meant to be taken every day.",
                "Your score is a screening result, not a diagnosis, and the app does not recommend treatment.",
                "It's completely optional, and you can stop at any time.",
                "Nothing is saved unless you agree to keep it on this phone.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        Text(q.source, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        PrimaryButton("Start", onStart, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Not now", onDecline, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun QuestionPage(q: Questionnaire, index: Int, current: Int?, progress: Float, onAnswer: (Int) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
        Text("Question ${index + 1} of ${q.items.size}", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (index + 1f) / q.items.size },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Midnight, trackColor = SurfaceMuted, drawStopIndicator = {},
        )
        Spacer(Modifier.height(Spacing.md))
        Text(q.stem, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        Spacer(Modifier.height(Spacing.sm))
        Text(q.items[index], style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.md))
        q.options.forEach { o ->
            val selected = current == o.points
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).heightIn(min = 56.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onAnswer(o.points) }),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) Midnight else CardWhite,
                contentColor = if (selected) WarmWhite else Ink,
                border = if (selected) null else BorderStroke(1.dp, Hairline),
            ) {
                Row(Modifier.padding(horizontal = Spacing.md, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Lime, unselectedColor = InkMuted))
                    Spacer(Modifier.size(Spacing.sm))
                    Text(o.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${o.points}", style = MaterialTheme.typography.labelMedium, color = if (selected) TextMuted else InkMuted)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        TextButton(onClick = onBack) { Text(if (index == 0) "Back to introduction" else "Previous question", color = CoralDeep) }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun SupportPage(onContinue: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
        SupportContent()
        Spacer(Modifier.height(Spacing.lg))
        Text("Would you like to finish the questionnaire?", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(Spacing.sm))
        SecondaryButton("Continue questionnaire", onContinue, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.sm))
        SecondaryButton("Stop here", onStop, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun DifficultyPage(q: Questionnaire, current: Int?, onChoose: (Int?) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
        Text("Optional · not scored", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Spacer(Modifier.height(Spacing.sm))
        Text(q.difficultyQuestion.orEmpty(), style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.md))
        q.difficultyOptions.forEachIndexed { i, label ->
            val selected = current == i
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).heightIn(min = 56.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onChoose(i) }),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) Midnight else CardWhite, contentColor = if (selected) WarmWhite else Ink,
                border = if (selected) null else BorderStroke(1.dp, Hairline),
            ) { Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(Spacing.md)) }
        }
        Spacer(Modifier.height(Spacing.md))
        SecondaryButton("Skip this question", { onChoose(null) }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onBack) { Text("Previous question", color = CoralDeep) }
    }
}

@Composable
private fun ReviewPage(q: Questionnaire, answers: List<Int?>, onFinish: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
            Text("All questions answered", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text("Check your answers, then see your result.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            Spacer(Modifier.height(Spacing.md))
            q.items.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("${i + 1}. $item", style = MaterialTheme.typography.bodySmall, color = Ink, modifier = Modifier.weight(1f))
                    Spacer(Modifier.size(Spacing.sm))
                    Text(answers[i]?.let { a -> q.options.first { it.points == a }.label } ?: "—", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                }
            }
            TextButton(onClick = onBack) { Text("Go back", color = CoralDeep) }
        }
        Column(Modifier.navigationBarsPadding().padding(Spacing.gutter)) {
            PrimaryButton("See my result", onFinish, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Result body, shared by the live flow and history. */
@Composable
fun ResultContent(q: Questionnaire, total: Int, bandLabel: String, explanation: String, completedAt: Long, item9Positive: Boolean, comparison: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        YsCard(Modifier.fillMaxWidth(), style = CardStyle.Dark, contentPadding = Spacing.lg) {
            Text("${q.title} · ${WellnessDates.dateLabel(completedAt)}", style = MaterialTheme.typography.labelSmall, color = Lime)
            Spacer(Modifier.height(Spacing.xs))
            Text("$total / ${q.maxScore}", style = MaterialTheme.typography.displaySmall, color = WarmWhite)
            Text(bandLabel, style = MaterialTheme.typography.titleLarge, color = WarmWhite)
        }
        Text(explanation, style = MaterialTheme.typography.bodyLarge, color = Ink)
        comparison?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = InkMuted) }
        Text(
            "Score ranges: " + q.bands.joinToString(" · ") { "${it.min}–${it.max} ${it.label.lowercase()}" },
            style = MaterialTheme.typography.bodySmall, color = InkMuted,
        )
        if (item9Positive) {
            YsCard(Modifier.fillMaxWidth()) { SupportContent() }
        }
        NotDiagnosisNote()
    }
}

@Composable
fun QuestionnaireRecordScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val wellnessRepo = LocalProfileSession.current.wellness
    val vm: WellnessViewModel = viewModel { WellnessViewModel(wellnessRepo) }
    val s by vm.state.collectAsStateWithLifecycle()
    val record: QuestionnaireRecord? = s.questionnaires.firstOrNull { it.id == id }
    Column(Modifier.fillMaxSize()) {
        TopBar("Result", onBack, "Back")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
            if (record == null) {
                EmptyState(YsIcons.Report, "Result not found", "It may have been deleted.")
            } else {
                val q = Questionnaires.of(record.type)
                val band = q.band(record.total)
                val cmp = WellnessInsights.comparison(record, s.questionnaires)?.let { it.describe(WellnessDates.dateLabel(it.previousAtMillis)) }
                ResultContent(q, record.total, band.label, band.explanation, record.completedAtMillis, record.item9Positive, cmp)
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
fun SupportScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Support", onBack, "Back")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
            SupportContent()
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
