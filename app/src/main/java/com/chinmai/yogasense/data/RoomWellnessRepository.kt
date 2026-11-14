package com.chinmai.yogasense.data

import com.chinmai.yogasense.data.db.AssessmentResponseEntity
import com.chinmai.yogasense.data.db.ConsentDecision
import com.chinmai.yogasense.data.db.ConsentRecordEntity
import com.chinmai.yogasense.data.db.ConsentType
import com.chinmai.yogasense.data.db.Instrument
import com.chinmai.yogasense.data.db.WellnessAssessmentEntity
import com.chinmai.yogasense.data.db.WellnessCheckInEntity
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.wellness.DailyCheckIn
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import com.chinmai.yogasense.wellness.Questionnaires
import com.chinmai.yogasense.wellness.WellnessConsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/** Pure mapping between the Phase 7 wellness models and the Room entities (JVM-testable). */
object WellnessMappers {
    /** Wording source/version of the instrument text shown in the app (see Questionnaires.kt). */
    fun instrumentVersion(t: QuestionnaireType) = when (t) {
        QuestionnaireType.PHQ9 -> "phqscreeners-en-2010"
        QuestionnaireType.GAD7 -> "phqscreeners-en-2010-v2"
    }

    /** Version of the in-app wellness storage consent text (Phase 7). */
    const val CONSENT_VERSION = "wellness-storage-v1"

    fun instrument(t: QuestionnaireType) = when (t) { QuestionnaireType.PHQ9 -> Instrument.PHQ9; QuestionnaireType.GAD7 -> Instrument.GAD7 }
    fun type(i: Instrument) = when (i) { Instrument.PHQ9 -> QuestionnaireType.PHQ9; Instrument.GAD7 -> QuestionnaireType.GAD7 }

    fun DailyCheckIn.toEntity(userId: String, checkInId: String) = WellnessCheckInEntity(
        checkInId = checkInId, userId = userId, epochDay = epochDay, recordedAt = updatedAtMillis,
        mood = mood, stress = stress, sleepHours = sleepHours, sleepQuality = sleepQuality,
        reflectionCiphertext = null, reflection = reflection,
    )

    fun WellnessCheckInEntity.toDomain() = DailyCheckIn(epochDay, mood, stress, sleepHours, sleepQuality, reflection, recordedAt)

    /** PHQ-9 keeps its item-9 flag; instruments without that item store null unless it was (unexpectedly) set. */
    fun safetyFlag(r: QuestionnaireRecord): Boolean? = when (r.type) {
        QuestionnaireType.PHQ9 -> r.item9Positive
        QuestionnaireType.GAD7 -> if (r.item9Positive) true else null
    }

    fun QuestionnaireRecord.toRows(userId: String, assessmentId: String, responseId: (questionNumber: Int) -> String) =
        WellnessAssessmentEntity(
            assessmentId = assessmentId, userId = userId, instrument = instrument(type), instrumentVersion = instrumentVersion(type),
            completedAt = completedAtMillis, totalScore = total, safetyFlag = safetyFlag(this), bandLabel = bandLabel, difficulty = difficulty,
        ) to answers.mapIndexed { i, v -> AssessmentResponseEntity(responseId(i + 1), assessmentId, i + 1, v) }

    fun toDomain(a: WellnessAssessmentEntity, responses: List<AssessmentResponseEntity>): QuestionnaireRecord {
        val t = type(a.instrument)
        return QuestionnaireRecord(
            id = a.assessmentId, type = t, completedAtMillis = a.completedAt,
            answers = responses.sortedBy { it.questionNumber }.map { it.responseValue },
            total = a.totalScore,
            bandLabel = a.bandLabel ?: Questionnaires.of(t).band(a.totalScore).label,
            difficulty = a.difficulty, item9Positive = a.safetyFlag == true,
        )
    }

    fun consent(active: ConsentRecordEntity?): WellnessConsent = when (active?.decision) {
        null -> WellnessConsent.UNKNOWN
        ConsentDecision.GRANTED -> WellnessConsent.GRANTED
        ConsentDecision.DECLINED -> WellnessConsent.DECLINED
    }
}

/**
 * Room implementation of the existing [WellnessRepository] contract, scoped to ONE profile.
 * Every read and write includes [userId]; consent is per profile and checked against the database
 * (not a cached value) before each write. [scope] is the profile session scope: cancelling it stops
 * the observers when the profile is switched.
 */
class RoomWellnessRepository(
    private val db: YogaSenseDatabase,
    private val userId: String,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Runs after [deleteAll] (erases the verified legacy copy for the import owner). */
    private val afterDeleteAll: suspend () -> Unit = {},
) : WellnessRepository {
    private val dao = db.wellness()
    private val type = ConsentType.WELLNESS_DATA_STORAGE

    override val checkIns: StateFlow<List<DailyCheckIn>> = dao.observeCheckIns(userId)
        .map { l -> with(WellnessMappers) { l.map { it.toDomain() } } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val questionnaires: StateFlow<List<QuestionnaireRecord>> =
        combine(dao.observeAssessments(userId), dao.observeAllResponses(userId)) { a, r ->
            val byId = r.groupBy { it.assessmentId }
            a.map { WellnessMappers.toDomain(it, byId[it.assessmentId].orEmpty()) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val consent: StateFlow<WellnessConsent> = dao.observeActiveConsent(userId, type)
        .map(WellnessMappers::consent)
        .stateIn(scope, SharingStarted.Eagerly, WellnessConsent.UNKNOWN)

    private suspend fun requireConsent() {
        if (WellnessMappers.consent(dao.observeActiveConsent(userId, type).first()) != WellnessConsent.GRANTED) throw ConsentRequiredException()
    }

    override suspend fun upsertCheckIn(checkIn: DailyCheckIn) {
        requireConsent()
        with(WellnessMappers) { dao.upsertCheckInForDay(checkIn.toEntity(userId, newId())) }
    }

    override suspend fun deleteCheckIn(epochDay: Long) { dao.deleteCheckIn(userId, epochDay) }

    override suspend fun addQuestionnaire(record: QuestionnaireRecord): String {
        requireConsent()
        val id = newId()
        val (a, responses) = with(WellnessMappers) { record.toRows(userId, id) { newId() } }
        dao.insertAssessmentWithResponses(a, responses)
        return id
    }

    override suspend fun setConsent(consent: WellnessConsent) {
        val now = clock()
        val next = when (consent) {
            WellnessConsent.UNKNOWN -> null
            WellnessConsent.GRANTED -> ConsentRecordEntity(newId(), userId, type, WellnessMappers.CONSENT_VERSION, now, null, ConsentDecision.GRANTED)
            WellnessConsent.DECLINED -> ConsentRecordEntity(newId(), userId, type, WellnessMappers.CONSENT_VERSION, now, null, ConsentDecision.DECLINED)
        }
        dao.replaceConsent(userId, type, now, next)
    }

    override suspend fun deleteAll() {
        dao.deleteAllFor(userId)
        afterDeleteAll()
    }
}
