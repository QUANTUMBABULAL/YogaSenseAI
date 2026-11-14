package com.chinmai.yogasense.data

import com.chinmai.yogasense.asana.Asana
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

/**
 * Seam for practice history. Session recording and Room persistence arrive in a later phase;
 * until then [NoPracticeHistory] returns nothing and every screen shows an empty state.
 */
interface PracticeHistoryRepository {
    fun weeklySummary(): Flow<WeeklySummary?>
    fun recentSessions(limit: Int = 5): Flow<List<SessionRecord>>
}

object NoPracticeHistory : PracticeHistoryRepository {
    override fun weeklySummary(): Flow<WeeklySummary?> = flowOf(null)
    override fun recentSessions(limit: Int): Flow<List<SessionRecord>> = flowOf(emptyList())
}
