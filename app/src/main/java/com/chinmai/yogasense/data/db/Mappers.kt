package com.chinmai.yogasense.data.db

import com.chinmai.yogasense.data.AsanaHistory
import com.chinmai.yogasense.data.PracticeOverview
import com.chinmai.yogasense.data.SessionRecord
import com.chinmai.yogasense.data.SessionSummaryItem
import com.chinmai.yogasense.data.WeeklySummary
import com.chinmai.yogasense.profile.CompletedPracticeSession
import com.chinmai.yogasense.profile.UserPreferences
import com.chinmai.yogasense.profile.UserProfile
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/** Pure entity ↔ domain mapping (JVM-testable). Nullable scores are passed through, never defaulted. */
object Mappers {
    fun UserProfileEntity.toDomain() = UserProfile(userId, displayName, experienceLevel, createdAt, updatedAt, status)

    fun UserPreferencesEntity.toDomain() = UserPreferences(
        voiceMode, spokenEncouragement, voiceCountdown, correctionAnnouncements, speechRate,
        liveSkeletonEnabled, targetSkeletonEnabled, visualCorrectionsEnabled, referenceFigureEnabled, preferredPracticeMinutes,
    )

    fun UserPreferences.toEntity(userId: String, now: Long) = UserPreferencesEntity(
        userId, voiceMode, spokenEncouragement, voiceCountdown, correctionAnnouncements, speechRate,
        liveSkeletonEnabled, targetSkeletonEnabled, visualCorrectionsEnabled, referenceFigureEnabled, preferredPracticeMinutes, now,
    )

    data class SessionRows(
        val session: PracticeSessionEntity,
        val results: List<PoseResultEntity>,
        val corrections: List<PracticeCorrectionEntity>,
    )

    fun CompletedPracticeSession.toRows(userId: String, newId: () -> String = { UUID.randomUUID().toString() }) = SessionRows(
        PracticeSessionEntity(sessionId, userId, startedAt, endedAt, activeDurationMs, completion),
        poses.map { PoseResultEntity(newId(), sessionId, it.asana, it.alignmentScore, it.stabilityScore, it.stableHoldTotalMs, it.longestHoldMs) },
        corrections.map { PracticeCorrectionEntity(newId(), sessionId, it.asana, it.correctionType, it.timestamp, it.improvementObserved) },
    )

    /** Recent pose results joined with their sessions → existing [SessionRecord] (duration = stable hold). */
    fun recentRecords(sessions: List<PracticeSessionEntity>, results: List<PoseResultEntity>, limit: Int): List<SessionRecord> {
        val byId = sessions.associateBy { it.sessionId }
        return results.mapNotNull { r -> byId[r.sessionId]?.let { s -> SessionRecord(r.asana, s.startedAt, (r.stableHoldTotalMs / 1000).toInt()) } }
            .sortedByDescending { it.startedAtMillis }
            .take(limit)
    }
}

/**
 * Practice totals from real sessions only.
 *
 * Date rules (device time zone, local calendar days, Monday-first week):
 *  - A "counted" session is COMPLETED or ENDED_EARLY with active duration > 0. ABANDONED sessions
 *    are stored but never counted.
 *  - Streak = number of consecutive local days with ≥1 counted session, ending today; if nothing
 *    is recorded today yet, the streak ending yesterday is still shown (it breaks once a whole
 *    day passes without practice).
 */
object PracticeStats {
    fun isCounted(s: PracticeSessionEntity) = s.completion != SessionCompletion.ABANDONED && s.durationMs > 0

    fun streak(counted: List<PracticeSessionEntity>, nowMillis: Long, tz: TimeZone): Int {
        val days = counted.map { localDay(it.startedAt, tz) }.toSet()
        var d = localDay(nowMillis, tz)
        if (d !in days) d-- // today not practised yet: yesterday's streak is still alive
        var streak = 0
        while (d in days) { streak++; d-- }
        return streak
    }

    /** All-time overview. Pose results whose session is not in [sessions] are ignored. */
    fun overview(sessions: List<PracticeSessionEntity>, results: List<PoseResultEntity>, recentLimit: Int = 5): PracticeOverview {
        val counted = sessions.filter(::isCounted).sortedByDescending { it.startedAt }
        val byId = counted.associateBy { it.sessionId }
        val resultsBySession = results.groupBy { it.sessionId }
        fun item(s: PracticeSessionEntity) = SessionSummaryItem(
            s.sessionId, s.startedAt, s.durationMs, s.completion,
            resultsBySession[s.sessionId].orEmpty().map { it.asana }.distinct(),
        )
        val perAsana = results.filter { it.sessionId in byId }.groupBy { it.asana }.map { (asana, rs) ->
            val align = rs.mapNotNull { it.alignmentScore }
            val stab = rs.mapNotNull { it.stabilityScore }
            AsanaHistory(
                asana = asana,
                sessions = rs.map { it.sessionId }.distinct().size,
                totalStableHoldMs = rs.sumOf { it.stableHoldTotalMs },
                longestHoldMs = rs.maxOf { it.longestHoldMs },
                lastPracticedAtMillis = rs.maxOf { byId.getValue(it.sessionId).startedAt },
                averageAlignment = align.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
                averageStability = stab.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
                measuredAlignmentSessions = align.size,
                measuredStabilitySessions = stab.size,
            )
        }.sortedByDescending { it.lastPracticedAtMillis }
        return PracticeOverview(
            completedSessions = counted.count { it.completion == SessionCompletion.COMPLETED },
            recordedSessions = counted.size,
            totalActiveMinutes = (counted.sumOf { it.durationMs } / 60_000).toInt(),
            lastSession = counted.firstOrNull()?.let(::item),
            recent = counted.take(recentLimit).map(::item),
            perAsana = perAsana,
        )
    }

    fun weekly(sessions: List<PracticeSessionEntity>, nowMillis: Long, tz: TimeZone = TimeZone.getDefault()): WeeklySummary? {
        val counted = sessions.filter(::isCounted)
        if (counted.isEmpty()) return null
        val weekStart = startOfWeek(nowMillis, tz)
        val thisWeek = counted.filter { it.startedAt >= weekStart && it.startedAt <= nowMillis }
        val streak = streak(counted, nowMillis, tz)
        val dayIndexes = thisWeek.map { ((localDay(it.startedAt, tz) - localDay(weekStart, tz))).toInt() }.filter { it in 0..6 }.toSet()
        return WeeklySummary(thisWeek.size, (thisWeek.sumOf { it.durationMs } / 60_000).toInt(), streak, dayIndexes)
    }

    fun localDay(millis: Long, tz: TimeZone): Long = Math.floorDiv(millis + tz.getOffset(millis), 86_400_000L)

    fun startOfWeek(nowMillis: Long, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val offset = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        c.add(Calendar.DAY_OF_MONTH, -offset)
        return c.timeInMillis
    }
}
