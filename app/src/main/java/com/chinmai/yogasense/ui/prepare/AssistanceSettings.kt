package com.chinmai.yogasense.ui.prepare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinmai.yogasense.ui.components.ScreenTopBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinmai.yogasense.app.LocalProfileSession
import com.chinmai.yogasense.app.ProfileSession
import com.chinmai.yogasense.profile.PracticePreferences.coach
import com.chinmai.yogasense.profile.PracticePreferences.visual
import com.chinmai.yogasense.profile.PracticePreferences.with
import com.chinmai.yogasense.profile.UserPreferences
import kotlinx.coroutines.launch
import com.chinmai.yogasense.coaching.CoachSettings
import com.chinmai.yogasense.coaching.VisualSettings
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.ui.components.IconBadge
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.InkSubtle
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.LimeTint
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.SurfaceMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice + visual assistance preferences of the active profile, shared by Practice preparation
 * and Profile. Reads and writes the same per-profile [UserPreferences] the live session uses;
 * diagnostics is the device-level developer toggle.
 */
class AssistanceViewModel(private val session: ProfileSession) : ViewModel() {
    private var prefs = UserPreferences()
    private val _coach = MutableStateFlow(prefs.coach())
    val coach: StateFlow<CoachSettings> = _coach.asStateFlow()
    private val _visual = MutableStateFlow(prefs.visual(session.deviceSettings.diagnostics))
    val visual: StateFlow<VisualSettings> = _visual.asStateFlow()

    init { refresh() }

    fun setCoach(c: CoachSettings) { _coach.value = c; save() }
    fun setVisual(v: VisualSettings) {
        _visual.value = v
        session.deviceSettings.diagnostics = v.diagnostics
        save()
    }

    /** Re-read (e.g. after a live session changed them). */
    fun refresh() {
        viewModelScope.launch {
            prefs = session.loadPreferences()
            _coach.value = prefs.coach()
            _visual.value = prefs.visual(session.deviceSettings.diagnostics)
        }
    }

    private fun save() {
        prefs = prefs.with(_coach.value, _visual.value)
        session.savePreferences(prefs)
    }
}

/** Assistance settings bound to the active profile session. */
@Composable
fun assistanceViewModel(): AssistanceViewModel {
    val session = LocalProfileSession.current
    return viewModel(key = "assistance") { AssistanceViewModel(session) }
}

@Composable
fun VoiceCoachCard(c: CoachSettings, onChange: (CoachSettings) -> Unit, modifier: Modifier = Modifier) {
    YsCard(modifier.fillMaxWidth()) {
        CardHeader(YsIcons.Voice, "Voice coach", "Spoken guidance while you practise")
        Spacer(Modifier.padding(top = Spacing.md))
        VoiceModeSelector(c.mode) { onChange(c.copy(mode = it)) }
        Text(
            c.mode.description, style = MaterialTheme.typography.bodySmall, color = InkMuted,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
        )
        val on = c.mode != VoiceMode.OFF
        HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = Spacing.xs))
        SettingSwitch("Spoken encouragement", "Positive feedback when you hold steady", c.encouragement, on) { onChange(c.copy(encouragement = it)) }
        SettingSwitch("Voice countdown", "Hold-time call-outs (Guided mode)", c.countdown, on && c.mode == VoiceMode.GUIDED) { onChange(c.copy(countdown = it)) }
        SettingSwitch("Spoken corrections", "One alignment correction at a time", c.corrections, on) { onChange(c.copy(corrections = it)) }
        Text("Speech speed", style = MaterialTheme.typography.titleSmall, color = if (on) Ink else InkSubtle, modifier = Modifier.padding(top = Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Slower", style = MaterialTheme.typography.bodySmall, color = InkMuted)
            Slider(
                value = c.speechRate, onValueChange = { onChange(c.copy(speechRate = Math.round(it * 10) / 10f)) },
                valueRange = 0.8f..1.2f, steps = 3, enabled = on, modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
                colors = SliderDefaults.colors(thumbColor = Midnight, activeTrackColor = Midnight, inactiveTrackColor = SurfaceMuted, activeTickColor = Lime),
            )
            Text("Faster", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}

@Composable
fun VisualGuidanceCard(v: VisualSettings, hasTarget: Boolean, onChange: (VisualSettings) -> Unit, modifier: Modifier = Modifier, showDiagnostics: Boolean = false) {
    YsCard(modifier.fillMaxWidth()) {
        CardHeader(YsIcons.Eye, "Visual guidance", "What appears on the camera screen")
        Spacer(Modifier.padding(top = Spacing.sm))
        SettingSwitch(
            "Animated pose reference",
            if (hasTarget) "A small illustration of the correct pose beside the camera" else "Available when you practise a chosen pose",
            v.referenceFigure && hasTarget, hasTarget,
        ) { onChange(v.copy(referenceFigure = it)) }
        SettingSwitch("Live skeleton", "Your detected body landmarks over the camera", v.liveSkeleton, true) { onChange(v.copy(liveSkeleton = it)) }
        SettingSwitch(
            "Target skeleton", "Reference body position over the camera",
            false, VisualSettings.TARGET_SKELETON_AVAILABLE, badge = "Not yet available",
        ) {}
        SettingSwitch("Visibility highlights", "Ring joints the camera can't see clearly", v.correctionHighlights, true) { onChange(v.copy(correctionHighlights = it)) }
        if (showDiagnostics) SettingSwitch("Diagnostics", "FPS, latency and recognition details", v.diagnostics, true) { onChange(v.copy(diagnostics = it)) }
        Text(
            "Written corrections always appear on screen, even with voice off.",
            style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 36.dp)
        Spacer(Modifier.width(Spacing.md - 4.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}

/** Three-way segmented control: Guided / Minimal / Off. */
@Composable
fun VoiceModeSelector(selected: VoiceMode, onSelect: (VoiceMode) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceMuted) {
        Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            VoiceMode.entries.forEach { m ->
                val isSel = m == selected
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) Midnight else SurfaceMuted,
                    contentColor = if (isSel) WarmWhite else Ink,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        .selectable(selected = isSel, onClick = { onSelect(m) }, role = Role.RadioButton),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                        Text(m.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    badge: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Ink else InkMuted)
                if (badge != null) { Spacer(Modifier.width(Spacing.sm)); Tag(badge) }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Lime, checkedTrackColor = Midnight,
                uncheckedThumbColor = InkSubtle, uncheckedTrackColor = CardWhite, uncheckedBorderColor = Hairline,
                disabledUncheckedTrackColor = SurfaceMuted, disabledUncheckedBorderColor = Hairline,
            ),
        )
    }
}

/** Small lime check row used in setup checklists. */
@Composable
fun CheckRow(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 32.dp, container = LimeTint)
        Spacer(Modifier.width(Spacing.md - 4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

/** Profile → Practice settings: same controls as preparation, plus developer diagnostics. */
@Composable
fun PracticeSettingsScreen(onBack: () -> Unit, vm: AssistanceViewModel = assistanceViewModel()) {
    val coach by vm.coach.collectAsStateWithLifecycle()
    val visual by vm.visual.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Practice settings", onBack, subtitle = "Used every time you start practice")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            VoiceCoachCard(coach, vm::setCoach)
            VisualGuidanceCard(visual, hasTarget = true, onChange = vm::setVisual, showDiagnostics = true)
            Text(
                "The pose reference appears only when you practise a chosen pose.",
                style = MaterialTheme.typography.bodySmall, color = InkMuted,
            )
        }
    }
}
