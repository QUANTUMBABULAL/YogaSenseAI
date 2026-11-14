package com.chinmai.yogasense.ui.practice

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.coaching.assessment.SessionSummary
import com.chinmai.yogasense.data.db.SessionCompletion
import com.chinmai.yogasense.profile.CompletedPracticeSession
import com.chinmai.yogasense.profile.CorrectionOutcome
import com.chinmai.yogasense.profile.PoseOutcome

/** Structured correction events of one session: stable id, time, and whether improvement was measured. */
class CorrectionLog {
    data class Entry(val asana: Asana, val correctionId: String, val atUptimeMs: Long, val improved: Boolean = false)

    private val entries = mutableListOf<Entry>()

    fun corrected(asana: Asana, correctionId: String, atUptimeMs: Long) { entries += Entry(asana, correctionId, atUptimeMs) }

    /** Marks the most recent not-yet-improved correction with this id as improved (measured by AssessmentCoach). */
    fun improved(correctionId: String) {
        val i = entries.indexOfLast { it.correctionId == correctionId && !it.improved }
        if (i >= 0) entries[i] = entries[i].copy(improved = true)
    }

    fun snapshot(): List<Entry> = entries.toList()
}

/** How the session screen was left. */
enum class SessionExit {
    /** The user tapped End and confirmed with Done on the summary. */
    ENDED_BY_USER,
    /** Close/back/navigation away without confirming End (incl. leaving after "Continue"). */
    LEFT,
}

/**
 * Completion policy for saving a practice session (pure, JVM-tested).
 *
 * | How it ended                          | ≥1 counted stable hold | Saved as     |
 * |---------------------------------------|------------------------|--------------|
 * | End + Done                            | yes                    | COMPLETED    |
 * | End + Done                            | no                     | ENDED_EARLY  |
 * | Left (close/back) without End         | yes                    | ENDED_EARLY  |
 * | Left (close/back) without End         | no                     | ABANDONED    |
 * | Any, < 10 s active and no pose seen   | —                      | not saved    |
 *
 * ABANDONED sessions are stored but never counted in totals or streaks. A session is never saved
 * as COMPLETED unless the user ended it AND a stable hold was actually counted. Scores are passed
 * through as measured (null = not enough samples), never defaulted to 0.
 */
object SessionRecordPolicy {
    const val MIN_ACTIVE_MS_WITHOUT_POSE = 10_000L

    fun completion(exit: SessionExit, summary: SessionSummary): SessionCompletion {
        val held = summary.poses.any { it.stableHoldTotalMs > 0 }
        return when (exit) {
            SessionExit.ENDED_BY_USER -> if (held) SessionCompletion.COMPLETED else SessionCompletion.ENDED_EARLY
            SessionExit.LEFT -> if (held) SessionCompletion.ENDED_EARLY else SessionCompletion.ABANDONED
        }
    }

    fun shouldSave(summary: SessionSummary): Boolean =
        summary.poses.isNotEmpty() || summary.durationMs >= MIN_ACTIVE_MS_WITHOUT_POSE

    /**
     * Builds the record. Uptime timestamps are converted to wall-clock time relative to the session
     * start ([startedAtWallMs] taken at the same moment as [startUptimeMs]).
     */
    fun build(
        sessionId: String,
        startedAtWallMs: Long,
        startUptimeMs: Long,
        endUptimeMs: Long,
        exit: SessionExit,
        summary: SessionSummary,
        corrections: List<CorrectionLog.Entry>,
    ): CompletedPracticeSession? {
        if (!shouldSave(summary)) return null
        fun wall(uptime: Long) = startedAtWallMs + (uptime - startUptimeMs).coerceAtLeast(0)
        return CompletedPracticeSession(
            sessionId = sessionId,
            startedAt = startedAtWallMs,
            endedAt = wall(endUptimeMs),
            activeDurationMs = summary.durationMs,
            completion = completion(exit, summary),
            poses = summary.poses.map { PoseOutcome(it.asana, it.meanAlignment, it.meanStability, it.stableHoldTotalMs, it.longestHoldMs) },
            corrections = corrections.map { CorrectionOutcome(it.asana, it.correctionId, wall(it.atUptimeMs), it.improved) },
        )
    }
}
