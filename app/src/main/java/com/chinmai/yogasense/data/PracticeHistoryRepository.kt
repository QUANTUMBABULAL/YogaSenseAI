package com.chinmai.yogasense.data

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.data.db.SessionCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Weekly totals; only produced from real recorded sessions. */
data class WeeklySummary(
    val sessions: Int,
    val totalMinutes: Int,
    val streakDays: Int,
    val practicedDaysOfWeek: Set<Int>,   // 0 = Monday … 6 = Sunday
)

data class SessionRecord(
    val asana: Asana,
    val startedAtMillis: Long,
    val durationSeconds: Int,
)

/** One recorded session as shown in history lists. */
data class SessionSummaryItem(
    val sessionId: String,
    val startedAtMillis: Long,
    val activeDurationMs: Long,
    val completion: SessionCompletion,
    val asanas: List<Asana>,
)

/**
 * Per-asana history from stored pose results. Scores are averages over the sessions in which the
 * score was actually measured; null means "never measured", which is different from a score of 0.
 */
data class AsanaHistory(
    val asana: Asana,
    val sessions: Int,
    val totalStableHoldMs: Long,
    val longestHoldMs: Long,
    val lastPracticedAtMillis: Long,
    val averageAlignment: Int?,
    val averageStability: Int?,
    val measuredAlignmentSessions: Int,
    val measuredStabilitySessions: Int,
)

/** All-time practice overview for one profile. */
data class PracticeOverview(
    val completedSessions: Int,          // completion == COMPLETED
    val recordedSessions: Int,           // COMPLETED + ENDED_EARLY (abandoned excluded)
    val totalActiveMinutes: Int,         // over recorded sessions
    val lastSession: SessionSummaryItem?,
    val recent: List<SessionSummaryItem>,
    val perAsana: List<AsanaHistory>,
) {
    val isEmpty: Boolean get() = recordedSessions == 0

    companion object { val EMPTY = PracticeOverview(0, 0, 0, null, emptyList(), emptyList()) }
}

/**
 * Practice history for ONE profile. Implementations are constructed per profile and scope every
 * query to that profile's id ([RoomPracticeHistoryRepository]). [NoPracticeHistory] is the
 * empty implementation used by previews/tests.
 */
interface PracticeHistoryRepository {
    fun weeklySummary(): Flow<WeeklySummary?>
    fun recentSessions(limit: Int = 5): Flow<List<SessionRecord>>
    fun overview(): Flow<PracticeOverview> = flowOf(PracticeOverview.EMPTY)
}

object NoPracticeHistory : PracticeHistoryRepository {
    override fun weeklySummary(): Flow<WeeklySummary?> = flowOf(null)
    override fun recentSessions(limit: Int): Flow<List<SessionRecord>> = flowOf(emptyList())
}
