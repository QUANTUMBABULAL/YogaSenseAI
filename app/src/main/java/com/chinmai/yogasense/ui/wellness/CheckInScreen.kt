package com.chinmai.yogasense.ui.wellness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chinmai.yogasense.data.WellnessGraph
import com.chinmai.yogasense.ui.components.CardStyle
import com.chinmai.yogasense.ui.components.IconBadge
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.ErrorRed
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import com.chinmai.yogasense.wellness.Scales
import com.chinmai.yogasense.wellness.WellnessDates

@Composable
fun CheckInScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: CheckInViewModel = viewModel { CheckInViewModel(WellnessGraph.repository(context.applicationContext)) }
    val s by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = CardWhite, contentColor = Ink)) {
                Icon(YsIcons.Back, "Back")
            }
            Spacer(Modifier.size(Spacing.sm))
            Column {
                Text("Daily check-in", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text("Today · ${WellnessDates.dayLabel(s.epochDay)}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (s.saved && !s.editing) {
                YsCard(Modifier.fillMaxWidth(), style = CardStyle.Dark, contentPadding = Spacing.lg - 4.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(YsIcons.Leaf, size = 44.dp, tint = Midnight, container = Lime)
                        Spacer(Modifier.size(Spacing.md))
                        Column {
                            Text("Saved for today", style = MaterialTheme.typography.titleLarge, color = WarmWhite)
                            Text("Thank you for taking a moment for yourself.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                    }
                }
                Summary(s.form)
                SecondaryButton("Edit today's check-in", vm::edit, modifier = Modifier.fillMaxWidth())
            } else {
                Text("Answer only what you'd like to — every question is optional.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                RatingSelector(Scales.MOOD, s.form.mood, { v -> vm.update { it.copy(mood = v) } })
                RatingSelector(Scales.STRESS, s.form.stress, { v -> vm.update { it.copy(stress = v) } })
                YsCard(Modifier.fillMaxWidth()) {
                    SleepStepper(s.form.sleepHours) { v -> vm.update { it.copy(sleepHours = v) } }
                    Spacer(Modifier.height(Spacing.md))
                    RatingSelector(Scales.SLEEP_QUALITY, s.form.sleepQuality, { v -> vm.update { it.copy(sleepQuality = v) } })
                }
                Column {
                    Text("Reflection (optional)", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = s.form.reflection,
                        onValueChange = { t -> if (t.length <= Scales.REFLECTION_MAX) vm.update { it.copy(reflection = t) } },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Anything on your mind? Only you can see this.") },
                        supportingText = { Text("${s.form.reflection.length} / ${Scales.REFLECTION_MAX}") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                            focusedBorderColor = Midnight, unfocusedBorderColor = Hairline, cursorColor = Midnight,
                        ),
                    )
                }
                s.errors.forEach { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        if (!(s.saved && !s.editing)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.gutter)) {
                PrimaryButton(if (s.existing) "Save changes" else "Save check-in", vm::save, modifier = Modifier.fillMaxWidth())
                Text("Saved only on this phone.", style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    if (s.needsConsent) ConsentDialog(onAnswer = vm::answerConsent)
    if (s.consentDeclined) {
        AlertDialog(
            onDismissRequest = vm::dismissDeclined,
            title = { Text("Not saved") },
            text = { Text("You chose not to keep wellness data on this phone, so this check-in wasn't saved. You can change this any time by saving again and choosing “Save on this phone”.") },
            confirmButton = { TextButton(onClick = { vm.dismissDeclined(); vm.answerConsent(true) }) { Text("Save on this phone") } },
            dismissButton = { TextButton(onClick = vm::dismissDeclined) { Text("OK") } },
        )
    }
}

@Composable
private fun Summary(f: CheckInForm) {
    YsCard(Modifier.fillMaxWidth()) {
        fun row(label: String, value: String?) = label to (value ?: "Not answered")
        listOf(
            row("Mood", f.mood?.let { Scales.MOOD.label(it) }),
            row("Stress", f.stress?.let { Scales.STRESS.label(it) }),
            row("Sleep", f.sleepHours?.let { formatHours(it) }),
            row("Sleep quality", f.sleepQuality?.let { Scales.SLEEP_QUALITY.label(it) }),
        ).forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.weight(1f))
                Text(v, style = MaterialTheme.typography.titleSmall, color = Ink)
            }
        }
        if (f.reflection.isNotBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(f.reflection, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
    }
}

/** Consent before any wellness data is stored. */
@Composable
fun ConsentDialog(onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Keep this on your phone?") },
        text = {
            Text(
                "YogaSense AI can save your check-ins and questionnaire results on this phone so you can see trends. " +
                    "They are never uploaded, are excluded from backups, and you can delete them at any time from Wellness → History.",
            )
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Save on this phone") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Don't save") } },
    )
}
