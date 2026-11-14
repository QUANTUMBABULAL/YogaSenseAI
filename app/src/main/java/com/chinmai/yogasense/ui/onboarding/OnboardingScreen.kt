package com.chinmai.yogasense.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.profile.BODY_SAFETY_NOTE
import com.chinmai.yogasense.profile.BodyArea
import com.chinmai.yogasense.profile.BodyAreaIntent
import com.chinmai.yogasense.profile.BodyMetrics
import com.chinmai.yogasense.profile.HeightUnit
import com.chinmai.yogasense.profile.Measurement
import com.chinmai.yogasense.profile.OnboardingAnswers
import com.chinmai.yogasense.profile.OnboardingRules
import com.chinmai.yogasense.profile.OnboardingStep
import com.chinmai.yogasense.profile.PracticeFrequency
import com.chinmai.yogasense.profile.WeightUnit
import com.chinmai.yogasense.profile.WellnessGoal
import com.chinmai.yogasense.ui.components.AsanaFigure
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.Coral
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.CoralTint
import com.chinmai.yogasense.ui.theme.ErrorRed
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.InkSubtle
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.LimeTint
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.SurfaceMuted
import com.chinmai.yogasense.ui.theme.SystemBarIcons
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite

/**
 * Full-screen, one-question-at-a-time onboarding. [onExit] is called when the flow finishes or is
 * left (optional/edit modes). In Required mode the profile gate switches to Home by itself once
 * completion is stored, so [onExit] is a no-op there.
 */
@Composable
fun OnboardingScreen(vm: OnboardingViewModel, profileName: String, onExit: () -> Unit, onLeave: () -> Unit = onExit) {
    val c = vm.controller
    val s by c.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.finished) { if (s.finished) onExit() }
    BackHandler(enabled = !s.finished) { if (!c.back()) onLeave() }

    val dark = s.step == OnboardingStep.WELCOME || s.step == OnboardingStep.DONE
    SystemBarIcons(darkIcons = !dark)
    val bg by animateColorAsState(if (dark) Midnight else WarmWhite, tween(350), label = "bg")

    Box(Modifier.fillMaxSize().background(bg)) {
        AnimatedContent(
            targetState = s.step,
            transitionSpec = {
                val dir = if (c.state.value.direction >= 0) 1 else -1
                (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it * dir / 3 } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(260)) { -it * dir / 3 } + fadeOut(tween(200)))
            },
            label = "onboarding-step",
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep(c.mode, onBegin = vm::next, onLater = c::leave)
                OnboardingStep.DONE -> DoneStep(s.answers, profileName, saving = s.saving, error = s.error, onFinish = vm::next)
                else -> QuestionScaffold(
                    step = step,
                    canBack = c.canGoBack(step),
                    canSkip = c.canSkip(step),
                    canContinue = s.canContinue,
                    continueLabel = if (c.isEdit) "Save" else "Continue",
                    error = s.error,
                    onBack = { if (!c.back()) onLeave() },
                    onSkip = vm::skip,
                    onContinue = vm::next,
                ) {
                    when (step) {
                        OnboardingStep.HEIGHT -> HeightStep(s, c)
                        OnboardingStep.WEIGHT -> WeightStep(s, c)
                        OnboardingStep.EXPERIENCE -> ExperienceStep(s.answers, c)
                        OnboardingStep.BODY_AREAS -> BodyAreasStep(s.answers, c)
                        OnboardingStep.GOALS -> GoalsStep(s.answers, c)
                        OnboardingStep.DURATION -> DurationStep(s.answers, c)
                        OnboardingStep.FREQUENCY -> FrequencyStep(s.answers, c)
                        else -> Unit
                    }
                }
            }
        }
    }
}

// ---- Shared chrome ------------------------------------------------------------------------------

private val questionCopy = mapOf(
    OnboardingStep.HEIGHT to ("How tall are you?" to "This helps us frame your practice. Optional — you can skip it."),
    OnboardingStep.WEIGHT to ("What's your current weight?" to "Used only to personalise your experience on this phone. Optional."),
    OnboardingStep.EXPERIENCE to ("How familiar are you with yoga?" to "We'll suggest poses that suit where you are today."),
    OnboardingStep.BODY_AREAS to ("Which areas would you like us to pay extra attention to?" to "Tap the figure or choose below. Pick as many as you like."),
    OnboardingStep.GOALS to ("What would you like to improve?" to "Choose everything that matters to you."),
    OnboardingStep.DURATION to ("How much time can you give yourself?" to "Short sessions count. You can change this anytime."),
    OnboardingStep.FREQUENCY to ("How often would you like to practise?" to "A gentle intention, not a commitment."),
)

@Composable
private fun QuestionScaffold(
    step: OnboardingStep,
    canBack: Boolean,
    canSkip: Boolean,
    canContinue: Boolean,
    continueLabel: String,
    error: String?,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (title, subtitle) = questionCopy.getValue(step)
    val index = OnboardingStep.questions.indexOf(step) + 1
    val progress by animateFloatAsState(OnboardingRules.progress(step), tween(450, easing = FastOutSlowInEasing), label = "progress")
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Top: back · progress · skip
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp)) { if (canBack) IconButton(onClick = onBack) { Icon(YsIcons.Back, "Back", tint = Ink) } }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                ProgressTrack(progress)
                Spacer(Modifier.height(6.dp))
                Text("$index of ${OnboardingStep.questions.size}", style = MaterialTheme.typography.labelMedium, color = InkMuted,
                    modifier = Modifier.semantics { contentDescription = "Question $index of ${OnboardingStep.questions.size}" })
            }
            Box(Modifier.widthIn(min = 64.dp), contentAlignment = Alignment.CenterEnd) {
                if (canSkip) TextButton(onClick = onSkip) { Text("Skip", style = MaterialTheme.typography.labelLarge, color = InkMuted) }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        ) {
            Spacer(Modifier.height(Spacing.md))
            Text(title, style = QuestionStyle, color = Ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = InkMuted)
            Spacer(Modifier.height(Spacing.lg))
            content()
            Spacer(Modifier.height(Spacing.md))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            AnimatedVisibility(error != null) { Text(error.orEmpty(), color = ErrorRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.sm)) }
            PrimaryButton(continueLabel, onContinue, enabled = canContinue, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val QuestionStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp)

@Composable
private fun ProgressTrack(progress: Float) {
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val y = size.height / 2
        drawLine(SurfaceMuted, Offset(y, y), Offset(size.width - y, y), strokeWidth = size.height, cap = StrokeCap.Round)
        if (progress > 0f) drawLine(Coral, Offset(y, y), Offset(y + (size.width - 2 * y) * progress, y), strokeWidth = size.height, cap = StrokeCap.Round)
    }
}

/** Selectable card with animated colour, border and check mark. */
@Composable
private fun ChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    multi: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val bg by animateColorAsState(if (selected) Midnight else CardWhite, tween(220), label = "card-bg")
    val fg by animateColorAsState(if (selected) WarmWhite else Ink, tween(220), label = "card-fg")
    val sub by animateColorAsState(if (selected) TextMuted else InkMuted, tween(220), label = "card-sub")
    val scale by animateFloatAsState(if (selected) 1f else 0.985f, tween(220), label = "card-scale")
    Row(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(BorderStroke(1.5.dp, if (selected) Midnight else Hairline), RoundedCornerShape(20.dp))
            .clickable(role = if (multi) Role.Checkbox else Role.RadioButton) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .semantics { this.selected = selected; stateDescription = if (selected) "Selected" else "Not selected" }
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(Spacing.md - 4.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = sub)
        }
        AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box(Modifier.padding(start = Spacing.sm).size(26.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                Icon(CheckIcon, null, tint = Midnight, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private val CheckIcon by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("check", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathParser().parsePathString("M5 12.5 L10 17.5 L19 7").toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round,
        )
    }.build()
}

@Composable
private fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.clip(CircleShape).background(SurfaceMuted).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Midnight else Color.Transparent, tween(200), label = "seg")
            Box(
                Modifier.clip(CircleShape).background(bg)
                    .clickable(role = Role.RadioButton) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) }
                    .semantics { this.selected = on }
                    .heightIn(min = 40.dp).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) WarmWhite else Ink) }
        }
    }
}

// ---- Welcome & Done -----------------------------------------------------------------------------

@Composable
private fun WelcomeStep(mode: OnboardingMode, onBegin: () -> Unit, onLater: () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Midnight, Surface1)))
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Spacer(Modifier.weight(0.6f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(230.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Lime.copy(alpha = 0.28f), Lime.copy(alpha = 0.06f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) {
                AsanaCatalog.of(com.chinmai.yogasense.asana.Asana.TREE)?.let { AsanaFigure(it.figure, Modifier.size(170.dp), color = Lime) }
            }
        }
        Spacer(Modifier.weight(0.5f))
        Text("Let's make yoga yours.", style = QuestionStyle.copy(fontSize = 36.sp, lineHeight = 42.sp), color = WarmWhite, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Tell us a little about yourself so we can personalise your practice and help you progress at your own pace.",
            style = MaterialTheme.typography.bodyLarge, color = TextMuted,
        )
        Spacer(Modifier.height(Spacing.xl))
        PrimaryButton("Let's begin", onBegin, modifier = Modifier.fillMaxWidth())
        if (mode == OnboardingMode.Optional) {
            TextButton(onClick = onLater, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Not now", style = MaterialTheme.typography.labelLarge, color = WarmWhite)
            }
        } else Spacer(Modifier.height(Spacing.md))
        Text("About 2 minutes · everything stays on this phone", style = MaterialTheme.typography.bodySmall, color = TextMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun DoneStep(a: OnboardingAnswers, profileName: String, saving: Boolean, error: String?, onFinish: () -> Unit) {
    val items = buildList {
        a.experience?.let { add("Level" to experienceCopy.getValue(it).first) }
        a.minutes?.let { add("Session length" to "$it minutes") }
        a.frequency?.let { add("Rhythm" to it.label) }
        if (a.goals.isNotEmpty()) add("Focus" to a.goals.joinToString { it.label })
        if (a.bodyAreas.isNotEmpty()) add("Extra attention" to a.bodyAreas.keys.joinToString { it.label })
    }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Midnight, Surface1)))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Spacing.xl))
            Box(Modifier.size(64.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                Icon(CheckIcon, null, tint = Midnight, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(Spacing.lg))
            Text("Your journey starts here.", style = QuestionStyle.copy(fontSize = 36.sp, lineHeight = 42.sp), color = WarmWhite, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            Text("Here's what we'll use to suggest poses and sessions for you, $profileName.", style = MaterialTheme.typography.bodyLarge, color = TextMuted)
            Spacer(Modifier.height(Spacing.lg))
            if (items.isEmpty()) {
                Text("You skipped the optional questions — that's fine. You can add details anytime from your profile.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            items.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(k, style = MaterialTheme.typography.labelLarge, color = Lime, modifier = Modifier.width(120.dp))
                    Text(v, style = MaterialTheme.typography.bodyLarge, color = WarmWhite, modifier = Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Surface1))
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Suggestions are general wellness guidance based on your answers — not a medical assessment. Edit them anytime in Profile.",
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
            )
        }
        if (error != null) Text(error, color = Coral, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.sm))
        PrimaryButton("Explore my YogaSenseAI", onFinish, enabled = !saving, modifier = Modifier.fillMaxWidth())
    }
}

// ---- Height & weight ----------------------------------------------------------------------------

@Composable
private fun BigNumberField(value: String, onValue: (String) -> Unit, unit: String, placeholder: String, label: String, modifier: Modifier = Modifier, focus: FocusRequester? = null, imeAction: ImeAction = ImeAction.Done, onIme: () -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
        BasicTextField(
            value = value,
            onValueChange = { t -> onValue(t.filter { it.isDigit() || it == '.' || it == ',' }) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Ink, textAlign = TextAlign.End, letterSpacing = (-1.5).sp),
            cursorBrush = SolidColor(Coral),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onIme() }),
            modifier = Modifier.widthIn(min = 72.dp, max = 220.dp).then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .semantics { contentDescription = "$label in $unit" },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text(placeholder, style = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = InkSubtle.copy(alpha = 0.45f), letterSpacing = (-1.5).sp))
                    inner()
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(unit, style = MaterialTheme.typography.headlineSmall, color = InkMuted, modifier = Modifier.padding(bottom = 14.dp))
    }
}

@Composable
private fun MeasurementHint(m: Measurement, okText: String?) {
    val (text, color) = when (m) {
        is Measurement.Invalid -> m.message to ErrorRed
        is Measurement.Valid -> (okText ?: "") to InkMuted
        Measurement.Empty -> "" to InkMuted
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp))
}

@Composable
private fun ColumnScope.HeightStep(s: OnboardingUiState, c: OnboardingController) {
    val focus = remember { FocusRequester() }
    val inchFocus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SegmentedToggle(listOf("cm", "ft · in"), if (s.answers.heightUnit == HeightUnit.CM) 0 else 1) { c.setHeightUnit(if (it == 0) HeightUnit.CM else HeightUnit.FT_IN) }
    }
    Spacer(Modifier.height(Spacing.xl))
    if (s.answers.heightUnit == HeightUnit.CM) {
        BigNumberField(s.heightCmText, c::setHeightCmText, "cm", "170", "Height", Modifier.fillMaxWidth(), focus, onIme = { fm.clearFocus() })
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            BigNumberField(s.feetText, { c.setFeetText(it.filter(Char::isDigit)) }, "ft", "5", "Feet", focus = focus, imeAction = ImeAction.Next, onIme = { inchFocus.requestFocus() })
            Spacer(Modifier.width(Spacing.md))
            BigNumberField(s.inchesText, c::setInchesText, "in", "7", "Inches", focus = inchFocus, onIme = { fm.clearFocus() })
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    MeasurementHint(s.height, okText = (s.height as? Measurement.Valid)?.let { if (s.answers.heightUnit == HeightUnit.FT_IN) "≈ ${BodyMetrics.heightText(it.value)} cm" else null })
    LaunchedEffect(s.answers.heightUnit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun ColumnScope.WeightStep(s: OnboardingUiState, c: OnboardingController) {
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SegmentedToggle(listOf("kg", "lb"), if (s.answers.weightUnit == WeightUnit.KG) 0 else 1) { c.setWeightUnit(if (it == 0) WeightUnit.KG else WeightUnit.LB) }
    }
    Spacer(Modifier.height(Spacing.xl))
    BigNumberField(s.weightText, c::setWeightText, if (s.answers.weightUnit == WeightUnit.KG) "kg" else "lb", if (s.answers.weightUnit == WeightUnit.KG) "65" else "143", "Weight", Modifier.fillMaxWidth(), focus, onIme = { fm.clearFocus() })
    Spacer(Modifier.height(Spacing.sm))
    MeasurementHint(s.weight, okText = null)
    Spacer(Modifier.height(Spacing.lg))
    Text("We don't calculate anything from your weight or judge it.", style = MaterialTheme.typography.bodySmall, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

// ---- Experience ---------------------------------------------------------------------------------

private val experienceCopy = mapOf(
    ExperienceLevel.BEGINNER to ("Beginner" to "I'm just getting started."),
    ExperienceLevel.INTERMEDIATE to ("Intermediate" to "I know the basics and practise sometimes."),
    ExperienceLevel.ADVANCED to ("Advanced" to "I practise regularly and know multiple asanas."),
)

@Composable
private fun ExperienceStep(a: OnboardingAnswers, c: OnboardingController) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md - 4.dp)) {
        ExperienceLevel.entries.forEachIndexed { i, e ->
            val (t, d) = experienceCopy.getValue(e)
            ChoiceCard(t, a.experience == e, { c.setExperience(e) }, Modifier.fillMaxWidth(), subtitle = d, leading = { LevelBars(i + 1, a.experience == e) })
        }
    }
}

@Composable
private fun LevelBars(level: Int, selected: Boolean) {
    Row(Modifier.height(28.dp).width(30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        (1..3).forEach { b ->
            val on = b <= level
            Box(Modifier.width(6.dp).height((8 + b * 6).dp).clip(RoundedCornerShape(3.dp)).background(if (on) (if (selected) Lime else Coral) else if (selected) Surface1 else SurfaceMuted))
        }
    }
}

// ---- Body areas ---------------------------------------------------------------------------------

/** Hotspots on a front-facing figure (unit box). Paired areas have two spots. */
private val hotspots: List<Pair<BodyArea, Offset>> = listOf(
    BodyArea.NECK to Offset(0.5f, 0.205f),
    BodyArea.SHOULDERS to Offset(0.36f, 0.255f), BodyArea.SHOULDERS to Offset(0.64f, 0.255f),
    BodyArea.UPPER_BACK to Offset(0.5f, 0.32f),
    BodyArea.LOWER_BACK to Offset(0.5f, 0.45f),
    BodyArea.HIPS to Offset(0.41f, 0.525f), BodyArea.HIPS to Offset(0.59f, 0.525f),
    BodyArea.WRISTS to Offset(0.25f, 0.53f), BodyArea.WRISTS to Offset(0.75f, 0.53f),
    BodyArea.KNEES to Offset(0.43f, 0.72f), BodyArea.KNEES to Offset(0.57f, 0.72f),
    BodyArea.ANKLES to Offset(0.435f, 0.9f), BodyArea.ANKLES to Offset(0.565f, 0.9f),
)

@Composable
private fun BodyMap(selected: Set<BodyArea>, onTap: (BodyArea) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val side = minOf(w, h); val ox = (w - side) / 2f; val oy = (h - side) / 2f
                    val hit = hotspots.minByOrNull { (_, o) -> (Offset(ox + o.x * side, oy + o.y * side) - p).getDistance() }
                    if (hit != null && (Offset(ox + hit.second.x * side, oy + hit.second.y * side) - p).getDistance() < side * 0.09f) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTap(hit.first)
                    }
                }
            }
            .semantics { contentDescription = "Body map. Selected: ${selected.joinToString { it.label }.ifEmpty { "none" }}. Use the buttons below to choose areas." },
    ) {
        val s = minOf(size.width, size.height); val ox = (size.width - s) / 2f; val oy = (size.height - s) / 2f
        fun o(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val body = Hairline
        val w = s * 0.075f
        fun limb(a: Offset, b: Offset, k: Float = 1f) = drawLine(body, a, b, strokeWidth = w * k, cap = StrokeCap.Round)
        drawCircle(body, s * 0.065f, o(0.5f, 0.13f))
        limb(o(0.5f, 0.2f), o(0.5f, 0.24f), 0.8f)
        limb(o(0.5f, 0.27f), o(0.5f, 0.5f), 2.6f)                  // torso
        limb(o(0.37f, 0.26f), o(0.63f, 0.26f), 0.9f)                // shoulders
        limb(o(0.36f, 0.27f), o(0.29f, 0.4f)); limb(o(0.29f, 0.4f), o(0.25f, 0.53f), 0.85f)
        limb(o(0.64f, 0.27f), o(0.71f, 0.4f)); limb(o(0.71f, 0.4f), o(0.75f, 0.53f), 0.85f)
        limb(o(0.42f, 0.53f), o(0.43f, 0.72f), 1.25f); limb(o(0.43f, 0.72f), o(0.435f, 0.9f), 1.1f)
        limb(o(0.58f, 0.53f), o(0.57f, 0.72f), 1.25f); limb(o(0.57f, 0.72f), o(0.565f, 0.9f), 1.1f)
        hotspots.forEach { (area, p) ->
            val on = area in selected
            if (on) drawCircle(Coral.copy(alpha = 0.22f), s * 0.055f, o(p.x, p.y))
            drawCircle(if (on) Coral else CardWhite, s * 0.026f, o(p.x, p.y))
            drawCircle(if (on) CoralDeep else InkSubtle, s * 0.026f, o(p.x, p.y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.006f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyAreasStep(a: OnboardingAnswers, c: OnboardingController) {
    BodyMap(a.bodyAreas.keys, c::toggleArea, Modifier.fillMaxWidth().aspectRatio(1.25f))
    Spacer(Modifier.height(Spacing.md))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BodyArea.entries.forEach { area -> Chip(area.label, area in a.bodyAreas) { c.toggleArea(area) } }
        Chip("No particular area", a.noParticularArea) { c.toggleNoParticular() }
    }
    if (a.bodyAreas.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.lg))
        Text("Tell us more (optional)", style = MaterialTheme.typography.titleSmall, color = Ink)
        Spacer(Modifier.height(Spacing.sm))
        a.bodyAreas.forEach { (area, intent) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(area.label, style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Discomfort", intent == BodyAreaIntent.DISCOMFORT, small = true) { c.setIntent(area, if (intent == BodyAreaIntent.DISCOMFORT) null else BodyAreaIntent.DISCOMFORT) }
                    Chip("Mobility & flexibility", intent == BodyAreaIntent.MOBILITY, small = true) { c.setIntent(area, if (intent == BodyAreaIntent.MOBILITY) null else BodyAreaIntent.MOBILITY) }
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CoralTint).padding(Spacing.md)) {
        Icon(YsIcons.Shield, null, tint = CoralDeep, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(BODY_SAFETY_NOTE, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val bg by animateColorAsState(if (selected) Midnight else CardWhite, tween(200), label = "chip")
    Box(
        Modifier.clip(CircleShape).background(bg).border(1.5.dp, if (selected) Midnight else Hairline, CircleShape)
            .clickable(role = Role.Checkbox) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .semantics { this.selected = selected }
            .heightIn(min = if (small) 40.dp else 48.dp).padding(horizontal = if (small) 14.dp else 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = if (selected) WarmWhite else Ink) }
}

// ---- Goals --------------------------------------------------------------------------------------

private fun goalIcon(g: WellnessGoal) = when (g) {
    WellnessGoal.FLEXIBILITY -> YsIcons.Wind
    WellnessGoal.STRENGTH -> YsIcons.Flame
    WellnessGoal.BALANCE -> YsIcons.Practice
    WellnessGoal.POSTURE -> YsIcons.Profile
    WellnessGoal.STRESS -> YsIcons.Stress
    WellnessGoal.SLEEP -> YsIcons.Sleep
    WellnessGoal.GENERAL_WELLNESS -> YsIcons.Leaf
    WellnessGoal.HABIT -> YsIcons.Calendar
}

@Composable
private fun GoalsStep(a: OnboardingAnswers, c: OnboardingController) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WellnessGoal.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { g ->
                    GoalTile(g, g in a.goals, Modifier.weight(1f)) { c.toggleGoal(g) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GoalTile(g: WellnessGoal, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val bg by animateColorAsState(if (selected) Midnight else CardWhite, tween(220), label = "goal-bg")
    val iconBg by animateColorAsState(if (selected) Lime else LimeTint, tween(220), label = "goal-icon")
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .border(1.5.dp, if (selected) Midnight else Hairline, RoundedCornerShape(20.dp))
            .clickable(role = Role.Checkbox) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .semantics { this.selected = selected }
            .heightIn(min = 112.dp).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(goalIcon(g), null, tint = Midnight, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(g.label, style = MaterialTheme.typography.titleSmall, color = if (selected) WarmWhite else Ink, minLines = 2)
    }
}

// ---- Duration & frequency -----------------------------------------------------------------------

@Composable
private fun DurationStep(a: OnboardingAnswers, c: OnboardingController) {
    val haptics = LocalHapticFeedback.current
    val shown = a.minutes
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(shown?.toString() ?: "–", style = TextStyle(fontSize = 88.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-3).sp), color = if (shown == null) InkSubtle else Ink)
            Spacer(Modifier.width(6.dp))
            Text("min", style = MaterialTheme.typography.headlineSmall, color = InkMuted, modifier = Modifier.padding(bottom = 18.dp))
        }
        Text(
            when (shown) {
                null -> "Pick a length below"
                5 -> "A quick reset"
                10 -> "A focused short practice"
                15 -> "Time to settle into a few poses"
                20 -> "A full, unhurried session"
                else -> "Room to explore and breathe"
            },
            style = MaterialTheme.typography.bodyLarge, color = InkMuted,
        )
        Spacer(Modifier.height(Spacing.xl))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OnboardingAnswers.DURATIONS.forEach { m ->
                val on = m == a.minutes
                val bg by animateColorAsState(if (on) Coral else CardWhite, tween(200), label = "dur")
                val sc by animateFloatAsState(if (on) 1.12f else 1f, tween(220), label = "dur-scale")
                Box(
                    Modifier.size(56.dp).scale(sc).clip(CircleShape).background(bg)
                        .border(1.5.dp, if (on) Coral else Hairline, CircleShape)
                        .clickable(role = Role.RadioButton) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); c.setMinutes(m) }
                        .semantics { this.selected = on; contentDescription = "$m minutes" },
                    contentAlignment = Alignment.Center,
                ) { Text("$m", style = MaterialTheme.typography.titleMedium, color = if (on) Midnight else Ink) }
            }
        }
    }
}

@Composable
private fun FrequencyStep(a: OnboardingAnswers, c: OnboardingController) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md - 4.dp)) {
        PracticeFrequency.entries.forEach { f ->
            ChoiceCard(f.label, a.frequency == f, { c.setFrequency(f) }, Modifier.fillMaxWidth(), subtitle = f.detail, leading = {
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (a.frequency == f) Lime else LimeTint), contentAlignment = Alignment.Center) {
                    Icon(YsIcons.Calendar, null, tint = Midnight, modifier = Modifier.size(18.dp))
                }
            })
        }
        Spacer(Modifier.height(Spacing.sm))
        Text("No streak pressure — practise when it suits you.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

