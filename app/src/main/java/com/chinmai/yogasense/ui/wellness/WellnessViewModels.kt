package com.chinmai.yogasense.ui.wellness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinmai.yogasense.data.ConsentRequiredException
import com.chinmai.yogasense.data.WellnessRepository
import com.chinmai.yogasense.wellness.CheckInValidation
import com.chinmai.yogasense.wellness.CheckInValidator
import com.chinmai.yogasense.wellness.DailyCheckIn
import com.chinmai.yogasense.wellness.QuestionnaireFlow
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import com.chinmai.yogasense.wellness.Questionnaires
import com.chinmai.yogasense.wellness.TrendSeries
import com.chinmai.yogasense.wellness.WeeklyReflection
import com.chinmai.yogasense.wellness.WellnessConsent
import com.chinmai.yogasense.wellness.WellnessDates
import com.chinmai.yogasense.wellness.WellnessInsights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WellnessOverviewState(
    val todayEpochDay: Long,
    val today: DailyCheckIn?,
    val mood: TrendSeries,
    val stress: TrendSeries,
    val sleep: TrendSeries,
    val weekly: WeeklyReflection,
    val lastPhq9: QuestionnaireRecord?,
    val lastGad7: QuestionnaireRecord?,
    val checkIns: List<DailyCheckIn>,
    val questionnaires: List<QuestionnaireRecord>,
    val consent: WellnessConsent,
)

/** Builds overview state purely from stored data (no fabricated values). */
object WellnessOverviewBuilder {
    const val TREND_DAYS = 14

    fun build(today: Long, checkIns: List<DailyCheckIn>, qs: List<QuestionnaireRecord>, consent: WellnessConsent) = WellnessOverviewState(
        todayEpochDay = today,
        today = checkIns.firstOrNull { it.epochDay == today },
        mood = WellnessInsights.series("Mood", checkIns, today, TREND_DAYS) { it.mood?.toFloat() },
        stress = WellnessInsights.series("Stress", checkIns, today, TREND_DAYS) { it.stress?.toFloat() },
        sleep = WellnessInsights.series("Sleep", checkIns, today, TREND_DAYS) { it.sleepHours },
        weekly = WellnessInsights.weekly(checkIns, today, practiceSessions = null), // practice history not recorded yet
        lastPhq9 = qs.firstOrNull { it.type == QuestionnaireType.PHQ9 },
        lastGad7 = qs.firstOrNull { it.type == QuestionnaireType.GAD7 },
        checkIns = checkIns,
        questionnaires = qs,
        consent = consent,
    )
}

class WellnessViewModel(private val repo: WellnessRepository, clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val today = WellnessDates.epochDay(clock())
    val state: StateFlow<WellnessOverviewState> =
        combine(repo.checkIns, repo.questionnaires, repo.consent) { c, q, k -> WellnessOverviewBuilder.build(today, c, q, k) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WellnessOverviewBuilder.build(today, emptyList(), emptyList(), WellnessConsent.UNKNOWN))

    fun deleteAll() = viewModelScope.launch { repo.deleteAll() }
}

// ---- Daily check-in ------------------------------------------------------------------------------

data class CheckInForm(
    val mood: Int? = null,
    val stress: Int? = null,
    val sleepHours: Float? = null,
    val sleepQuality: Int? = null,
    val reflection: String = "",
)

data class CheckInUiState(
    val epochDay: Long,
    val form: CheckInForm = CheckInForm(),
    val existing: Boolean = false,
    val saved: Boolean = false,
    val editing: Boolean = true,
    val errors: List<String> = emptyList(),
    val needsConsent: Boolean = false,
    val consentDeclined: Boolean = false,
)

class CheckInViewModel(private val repo: WellnessRepository, clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val now = clock
    private val today = WellnessDates.epochDay(clock())
    private val _state = MutableStateFlow(CheckInUiState(today))
    val state: StateFlow<CheckInUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.checkIns.collect { list ->
                val e = list.firstOrNull { it.epochDay == today }
                if (e != null && !_state.value.existing) {
                    _state.value = _state.value.copy(
                        form = CheckInForm(e.mood, e.stress, e.sleepHours, e.sleepQuality, e.reflection.orEmpty()),
                        existing = true, saved = true, editing = false,
                    )
                }
            }
        }
    }

    fun update(f: (CheckInForm) -> CheckInForm) { _state.value = _state.value.copy(form = f(_state.value.form), errors = emptyList()) }
    fun edit() { _state.value = _state.value.copy(editing = true, saved = false) }

    fun save() {
        val f = _state.value.form
        val candidate = DailyCheckIn(today, f.mood, f.stress, f.sleepHours, f.sleepQuality, f.reflection, now())
        when (val v = CheckInValidator.validate(candidate)) {
            is CheckInValidation.Invalid -> _state.value = _state.value.copy(errors = v.reasons)
            is CheckInValidation.Valid -> when (repo.consent.value) {
                WellnessConsent.GRANTED -> viewModelScope.launch {
                    try {
                        repo.upsertCheckIn(v.checkIn)
                        _state.value = _state.value.copy(saved = true, editing = false, existing = true, errors = emptyList())
                    } catch (_: ConsentRequiredException) {
                        _state.value = _state.value.copy(needsConsent = true)
                    }
                }
                WellnessConsent.UNKNOWN -> _state.value = _state.value.copy(needsConsent = true)
                WellnessConsent.DECLINED -> _state.value = _state.value.copy(consentDeclined = true)
            }
        }
    }

    fun answerConsent(granted: Boolean) {
        viewModelScope.launch {
            repo.setConsent(if (granted) WellnessConsent.GRANTED else WellnessConsent.DECLINED)
            _state.value = _state.value.copy(needsConsent = false, consentDeclined = !granted)
            if (granted) save()
        }
    }

    fun dismissDeclined() { _state.value = _state.value.copy(consentDeclined = false) }
}

// ---- Questionnaires --------------------------------------------------------------------------------

data class QuestionnaireUiState(
    val flowState: QuestionnaireFlow.State,
    val answers: List<Int?>,
    val difficulty: Int?,
    val progress: Float,
    val item9Positive: Boolean,
    val needsConsent: Boolean = false,
    val savedRecord: QuestionnaireRecord? = null,
    val notSaved: Boolean = false,
    val comparisonText: String? = null,
)

class QuestionnaireViewModel(
    private val repo: WellnessRepository,
    type: QuestionnaireType,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val questionnaire = Questionnaires.of(type)
    private val flow = QuestionnaireFlow(questionnaire)
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<QuestionnaireUiState> = _state.asStateFlow()

    private fun snapshot(extra: (QuestionnaireUiState) -> QuestionnaireUiState = { it }) = extra(
        QuestionnaireUiState(flow.state, flow.answers.toList(), flow.difficulty, flow.progress, flow.item9EverPositive),
    )

    private fun publish() { val prev = _state.value; _state.value = snapshot { it.copy(needsConsent = prev.needsConsent, savedRecord = prev.savedRecord, notSaved = prev.notSaved, comparisonText = prev.comparisonText) } }

    fun start() { flow.start(); publish() }
    fun decline() { flow.decline(); publish() }
    fun exit() { flow.exit(); publish() }
    fun answer(index: Int, points: Int) { flow.answer(index, points); publish() }
    fun continueAfterSupport() { flow.continueAfterSupport(); publish() }
    fun back() { flow.back(); publish() }
    fun chooseDifficulty(option: Int?) { flow.chooseDifficulty(option); publish() }

    /** Scores, then saves only with consent. Result is shown either way. */
    fun finish() {
        flow.submit(); publish()
        when (repo.consent.value) {
            WellnessConsent.GRANTED -> save()
            WellnessConsent.UNKNOWN -> _state.value = _state.value.copy(needsConsent = true)
            WellnessConsent.DECLINED -> _state.value = _state.value.copy(notSaved = true)
        }
    }

    fun answerConsent(granted: Boolean) {
        viewModelScope.launch {
            repo.setConsent(if (granted) WellnessConsent.GRANTED else WellnessConsent.DECLINED)
            _state.value = _state.value.copy(needsConsent = false)
            if (granted) save() else _state.value = _state.value.copy(notSaved = true)
        }
    }

    private fun save() {
        val record = flow.toRecord(clock()) ?: return
        viewModelScope.launch {
            val previous = repo.questionnaires.value
            val id = repo.addQuestionnaire(record)
            val saved = record.copy(id = id)
            val cmp = WellnessInsights.comparison(saved, previous)?.let { it.describe(WellnessDates.dateLabel(it.previousAtMillis)) }
            _state.value = _state.value.copy(savedRecord = saved, comparisonText = cmp)
        }
    }
}
