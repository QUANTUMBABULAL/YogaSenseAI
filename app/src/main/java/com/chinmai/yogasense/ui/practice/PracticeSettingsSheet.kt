package com.chinmai.yogasense.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chinmai.yogasense.coaching.CoachSettings
import com.chinmai.yogasense.coaching.VisualSettings
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.coaching.VoiceStatus
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.Surface2
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.Warn
import com.chinmai.yogasense.ui.theme.WarmWhite

enum class PracticeSheet { Voice, Visual }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSettingsSheet(
    sheet: PracticeSheet,
    coach: CoachSettings,
    visual: VisualSettings,
    voiceStatus: VoiceStatus,
    hasTarget: Boolean,
    onCoach: (CoachSettings) -> Unit,
    onVisual: (VisualSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1,
        contentColor = WarmWhite,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (sheet) {
                PracticeSheet.Voice -> VoiceSettings(coach, voiceStatus, onCoach)
                PracticeSheet.Visual -> VisualSettingsList(visual, hasTarget, onVisual)
            }
        }
    }
}

@Composable
private fun Title(text: String, sub: String? = null) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WarmWhite)
    if (sub != null) Text(sub, fontSize = 13.sp, color = TextMuted)
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun VoiceSettings(c: CoachSettings, status: VoiceStatus, onChange: (CoachSettings) -> Unit) {
    Title("Voice coach", "Changes apply immediately and are saved on this phone")
    if (status == VoiceStatus.Unavailable) {
        Text("Text-to-speech is unavailable on this device. On-screen guidance still works.", color = Warn, fontSize = 13.sp)
        Spacer(Modifier.size(8.dp))
    }
    VoiceMode.entries.forEach { m ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .selectable(selected = c.mode == m, onClick = { onChange(c.copy(mode = m)) }, role = Role.RadioButton),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = c.mode == m, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Lime, unselectedColor = TextMuted))
            Spacer(Modifier.size(12.dp))
            Column {
                Text(m.label, color = WarmWhite, fontWeight = FontWeight.SemiBold)
                Text(m.description, color = TextMuted, fontSize = 12.sp)
            }
        }
    }
    val voiceOn = c.mode != VoiceMode.OFF
    Toggle("Spoken encouragement", "Praise when you reach a hold", c.encouragement, voiceOn) { onChange(c.copy(encouragement = it)) }
    Toggle("Voice countdown", "Hold-time call-outs every 10 s (Guided)", c.countdown, voiceOn && c.mode == VoiceMode.GUIDED) { onChange(c.copy(countdown = it)) }
    Toggle("Correction announcements", "Spoken alignment corrections when available", c.corrections, voiceOn) { onChange(c.copy(corrections = it)) }
    Text("Speech speed", color = if (voiceOn) WarmWhite else TextMuted, modifier = Modifier.padding(top = 8.dp))
    Slider(
        value = c.speechRate, onValueChange = { onChange(c.copy(speechRate = (Math.round(it * 10) / 10f))) },
        valueRange = 0.8f..1.2f, steps = 3, enabled = voiceOn,
        colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = Surface2),
    )
    Text("Corrections come from prototype alignment checks and are spoken one at a time. On-screen guidance continues when voice is off.", color = TextMuted, fontSize = 12.sp)
}

@Composable
private fun VisualSettingsList(v: VisualSettings, hasTarget: Boolean, onChange: (VisualSettings) -> Unit) {
    Title("Visual guidance")
    Toggle("Live skeleton", "Your detected body landmarks", v.liveSkeleton, true) { onChange(v.copy(liveSkeleton = it)) }
    Toggle(
        "Target skeleton", "Not available yet — needs calibrated target landmarks for your camera view",
        false, VisualSettings.TARGET_SKELETON_AVAILABLE,
    ) {}
    Toggle("Visibility highlights", "Ring joints the camera cannot see clearly", v.correctionHighlights, true) { onChange(v.copy(correctionHighlights = it)) }
    Toggle(
        "Reference illustration", if (hasTarget) "Small labelled pose illustration (separate from the camera)" else "Choose a pose from Explore to see a reference",
        v.referenceFigure, hasTarget,
    ) { onChange(v.copy(referenceFigure = it)) }
    Toggle("Diagnostics", "FPS, latency, landmarks and recognition details", v.diagnostics, true) { onChange(v.copy(diagnostics = it)) }
}

@Composable
private fun Toggle(title: String, sub: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) WarmWhite else TextMuted, fontWeight = FontWeight.SemiBold)
            Text(sub, color = TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Midnight, checkedTrackColor = Lime,
                uncheckedThumbColor = TextMuted, uncheckedTrackColor = Surface2, uncheckedBorderColor = TextMuted,
            ),
        )
    }
}
