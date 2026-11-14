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
import com.chinmai.yogasense.profile.MeasurementScales
import com.chinmai.yogasense.profile.RulerScale
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
    /** Canonical height (cm) / weight (kg) live in [answers]; null = not chosen (rulers show a muted default). */
    val answers: OnboardingAnswers,
    /** +1 forward, -1 back (for the transition direction). */
    val direction: Int = 1,
    val saving: Boolean = false,
    val error: String? = null,
    /** Set when the flow should close (finished, left, or edit saved). */
    val finished: Boolean = false,
) {
    val height: Measurement get() = BodyMetrics.checkHeightCm(answers.heightCm)
    val weight: Measurement get() = BodyMetrics.checkWeightKg(answers.weightKg)
    val heightScale: RulerScale get() = MeasurementScales.height(answers.heightUnit)
    val weightScale: RulerScale get() = MeasurementScales.weight(answers.weightUnit)
    /** Ruler position for the current unit, or null if no height has been chosen. */
    val heightIndex: Int? get() = answers.heightCm?.let { heightScale.fromCanonical(it) }
    val weightIndex: Int? get() = answers.weightKg?.let { weightScale.fromCanonical(it) }
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
        _state = MutableStateFlow(OnboardingUiState(step = start, answers = answers))
        state = _state.asStateFlow()
    }

    val isEdit: Boolean get() = mode is OnboardingMode.Edit
    fun canSkip(s: OnboardingStep = state.value.step) = s.optional && !isEdit
    fun canGoBack(s: OnboardingStep = state.value.step) = isEdit || mode == OnboardingMode.Optional || s != OnboardingStep.WELCOME

    private fun answers(f: (OnboardingAnswers) -> OnboardingAnswers) = _state.update { it.copy(answers = f(it.answers), error = null) }

    // ---- inputs (rulers) ----
    /** The user moved the height ruler to [index] of the current unit's scale; stores the exact canonical cm. */
    fun setHeightIndex(index: Int) = answers { a -> MeasurementScales.height(a.heightUnit).let { sc -> a.copy(heightCm = sc.toCanonical(sc.clamp(index))) } }
    fun setWeightIndex(index: Int) = answers { a -> MeasurementScales.weight(a.weightUnit).let { sc -> a.copy(weightKg = sc.toCanonical(sc.clamp(index))) } }

    /** Switching units changes only the display: the canonical value is untouched (no drift). */
    fun setHeightUnit(u: HeightUnit) = answers { it.copy(heightUnit = u) }
    fun setWeightUnit(u: WeightUnit) = answers { it.copy(weightUnit = u) }

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
        // canContinue already guarantees an in-range height/weight on those steps.
        persist(s.step, s.answers, skipped = s.step == OnboardingStep.WELCOME)
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
