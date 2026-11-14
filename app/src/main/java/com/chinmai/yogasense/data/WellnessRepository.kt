package com.chinmai.yogasense.data

import com.chinmai.yogasense.wellness.DailyCheckIn
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.WellnessConsent
import kotlinx.coroutines.flow.StateFlow

/**
 * Wellness data on this phone only. Contract (all implementations):
 * - At most one check-in per epochDay; saving again for the same day replaces it (edit).
 * - Writes of check-ins and questionnaire results require consent == GRANTED, otherwise
 *   [ConsentRequiredException] is thrown and nothing is written.
 * - [deleteAll] removes every check-in, questionnaire record and the consent decision.
 * - Data is never sent anywhere and never written to logs.
 */
interface WellnessRepository {
    val checkIns: StateFlow<List<DailyCheckIn>>              // newest day first
    val questionnaires: StateFlow<List<QuestionnaireRecord>> // newest first
    val consent: StateFlow<WellnessConsent>

    suspend fun upsertCheckIn(checkIn: DailyCheckIn)
    suspend fun deleteCheckIn(epochDay: Long)
    suspend fun addQuestionnaire(record: QuestionnaireRecord): String
    suspend fun setConsent(consent: WellnessConsent)
    suspend fun deleteAll()
}

class ConsentRequiredException : IllegalStateException("Wellness data can only be saved after consent")
