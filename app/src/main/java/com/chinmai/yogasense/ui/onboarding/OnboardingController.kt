package com.chinmai.yogasense.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinmai.yogasense.data.RoomPersonalisationRepository
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.profile.BodyArea
import com.chinmai.yogasense.profile.BodyAreaIntent
import com.chinmai.yogasense.profile.BodyMetrics
import com.chinmai.yogasense.profile.HeightUnit
import com.chinmai.yogasense.profile.Measurement
import com.chinmai.yogasense.profile.OnboardingAnswers
import com.chinmai.yogasense.profile.OnboardingRules
import com.chinmai.yogasense.profile.OnboardingStatus
import com.chinmai.yogasense.profile.OnboardingStep
import com.chinmai.yogasense.profile.PersonalisationView
import com.chinmai.yogasense.profile.PracticeFrequency
import com.chinmai.yogasense.profile.WeightUnit
import com.chinmai.yogasense.profile.WellnessGoal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How onboarding was opened. */
sealed interface OnboardingMode {
    /** New profile: must finish before Home. Resumes at the stored step. */
    data object Required : OnboardingMode
    /** Existing profile, opened from the Home prompt or Profile tab. Can be left at any time. */
    data object Optional : OnboardingMode
    /** Edit one answer from the Profile tab, then return. */
    data class Edit(val step: OnboardingStep) : OnboardingMode
}

data class OnboardingUiState(
    val step: OnboardingStep,
    val answers: OnboardingAnswers,
    val heightCmText: String = "",
    val feetText: String = "",
    val inchesText: String = "",
    val weightText: String = "",
    /** +1 forward, -1 back (for the transition direction). */
    val direction: Int = 1,
    val saving: Boolean = false,
    val error: String? = null,
    /** Set when the flow should close (finished, left, or edit saved). */
    val finished: Boolean = false,
) {
    val height: Measurement get() = if (answers.heightUnit == HeightUnit.CM) BodyMetrics.heightCm(heightCmText) else BodyMetrics.heightFtIn(feetText, inchesText)
    val weight: Measurement get() = BodyMetrics.weightKg(weightText, answers.weightUnit)
    val canContinue: Boolean get() = !saving && OnboardingRules.canContinue(step, answers, height, weight)
}

/**
 * Onboarding state machine. Pure apart from [save], which persists one step (Room in the app,
 * a fake in tests). Answers live in memory across Back/Continue; each step is persisted when the
 * user presses Continue (its answer) or Skip (position only — the stored value is not cleared).
 */
class OnboardingController(
    initial: PersonalisationView,
    val mode: OnboardingMode,
    private val save: suspend (step: OnboardingStep, answers: OnboardingAnswers, next: OnboardingStep, skipped: Boolean, editOnly: Boolean) -> Boolean,
) {
    private val _state: MutableStateFlow<OnboardingUiState>
    val state: StateFlow<OnboardingUiState>

    init {
        // Experience is prefilled only for existing profiles (new profiles choose explicitly).
        val answers = OnboardingAnswers.from(initial, prefillExperience = mode != OnboardingMode.Required)
        val start = when (mode) {
            is OnboardingMode.Edit -> mode.step
            OnboardingMode.Required, OnboardingMode.Optional -> {
                val p = initial.personalisation
                if (p != null && p.status == OnboardingStatus.IN_PROGRESS) OnboardingStep.entries.getOrElse(p.step) { OnboardingStep.WELCOME }
                else OnboardingStep.WELCOME
            }
        }
        val (ft, inch) = BodyMetrics.feetInches(answers.heightCm)
        _state = MutableStateFlow(
            OnboardingUiState(
                step = start, answers = answers,
                heightCmText = BodyMetrics.heightText(answers.heightCm), feetText = ft, inchesText = inch,
                weightText = BodyMetrics.weightText(answers.weightKg, answers.weightUnit),
            ),
        )
        state = _state.asStateFlow()
    }

    val isEdit: Boolean get() = mode is OnboardingMode.Edit
    fun canSkip(s: OnboardingStep = state.value.step) = s.optional && !isEdit
    fun canGoBack(s: OnboardingStep = state.value.step) = isEdit || mode == OnboardingMode.Optional || s != OnboardingStep.WELCOME

    private fun answers(f: (OnboardingAnswers) -> OnboardingAnswers) = _state.update { it.copy(answers = f(it.answers), error = null) }

    // ---- inputs ----
    fun setHeightCmText(t: String) = _state.update { it.copy(heightCmText = t.take(6), error = null) }
    fun setFeetText(t: String) = _state.update { it.copy(feetText = t.take(1), error = null) }
    fun setInchesText(t: String) = _state.update { it.copy(inchesText = t.take(4), error = null) }
    fun setWeightText(t: String) = _state.update { it.copy(weightText = t.take(6), error = null) }

    /** Switching units converts a valid entry instead of discarding it. */
    fun setHeightUnit(u: HeightUnit) = _state.update { s ->
        if (s.answers.heightUnit == u) return@update s
        val cm = (s.height as? Measurement.Valid)?.value
        val (ft, inch) = BodyMetrics.feetInches(cm)
        s.copy(answers = s.answers.copy(heightUnit = u), heightCmText = if (cm != null) BodyMetrics.heightText(cm) else s.heightCmText.takeIf { u == HeightUnit.CM }.orEmpty(),
            feetText = if (cm != null) ft else "", inchesText = if (cm != null) inch else "")
    }

    fun setWeightUnit(u: WeightUnit) = _state.update { s ->
        if (s.answers.weightUnit == u) return@update s
        val kg = (s.weight as? Measurement.Valid)?.value
        s.copy(answers = s.answers.copy(weightUnit = u), weightText = if (kg != null) BodyMetrics.weightText(kg, u) else "")
    }

    fun setExperience(e: ExperienceLevel) = answers { it.copy(experience = e) }
    fun toggleArea(a: BodyArea) = answers { it.toggleArea(a) }
    fun setIntent(a: BodyArea, i: BodyAreaIntent?) = answers { it.setIntent(a, i) }
    fun toggleNoParticular() = answers { it.toggleNoParticular() }
    fun toggleGoal(g: WellnessGoal) = answers { it.toggleGoal(g) }
    fun setMinutes(m: Int) = answers { it.copy(minutes = m) }
    fun setFrequency(f: PracticeFrequency) = answers { it.copy(frequency = f) }

    // ---- navigation ----
    suspend fun next() {
        val s = state.value
        if (!s.canContinue) return
        val answers = when (s.step) {
            OnboardingStep.HEIGHT -> s.answers.copy(heightCm = (s.height as Measurement.Valid).value)
            OnboardingStep.WEIGHT -> s.answers.copy(weightKg = (s.weight as Measurement.Valid).value)
            else -> s.answers
        }
        persist(s.step, answers, skipped = s.step == OnboardingStep.WELCOME)
    }

    suspend fun skip() {
        val s = state.value
        if (!canSkip(s.step)) return
        persist(s.step, s.answers, skipped = true)
    }

    /**
     * Back never loses answers. Edit mode and the optional flow's first screen close the flow
     * (via [OnboardingUiState.finished]). Returns false only on the first screen of the REQUIRED
     * flow, where the caller decides (switch profile).
     */
    fun back(): Boolean {
        val s = state.value
        if (isEdit || (mode == OnboardingMode.Optional && s.step == OnboardingStep.WELCOME)) { _state.update { it.copy(finished = true) }; return true }
        if (s.step == OnboardingStep.WELCOME) return false
        _state.update { it.copy(step = OnboardingRules.back(it.step), direction = -1, error = null) }
        return true
    }

    /** Optional mode: leave now; the position is kept so it can be resumed from the Profile tab. */
    fun leave() = _state.update { it.copy(finished = true) }

    private suspend fun persist(step: OnboardingStep, answers: OnboardingAnswers, skipped: Boolean) {
        val next = if (isEdit) step else OnboardingRules.next(step)
        _state.update { it.copy(saving = true, answers = answers) }
        val ok = try { save(step, answers, next, skipped, isEdit) } catch (e: Exception) { false }
        _state.update {
            when {
                !ok -> it.copy(saving = false, error = "That couldn't be saved. Please try again.")
                isEdit || step == OnboardingStep.DONE -> it.copy(saving = false, finished = true)
                else -> it.copy(saving = false, step = next, direction = 1)
            }
        }
    }
}

/** Android ViewModel wrapper (survives rotation; owned by the profile session). */
class OnboardingViewModel(repo: RoomPersonalisationRepository, initial: PersonalisationView, mode: OnboardingMode) : ViewModel() {
    val controller = OnboardingController(initial, mode) { step, answers, next, skipped, editOnly ->
        repo.saveStep(step, answers, next, skipped, editOnly)
    }
    fun next() { viewModelScope.launch { controller.next() } }
    fun skip() { viewModelScope.launch { controller.skip() } }
}
