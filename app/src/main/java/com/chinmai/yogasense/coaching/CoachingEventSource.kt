package com.chinmai.yogasense.coaching

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.Recognition
import com.chinmai.yogasense.pose.FramingStatus

/** What the practice screen observes at one moment (built from real framing + recognition state). */
data class PracticeSnapshot(
    val timeMs: Long,
    val framing: FramingStatus,
    val recognition: Recognition,
)

/**
 * Converts the continuous stream of snapshots into discrete [CoachEvent]s:
 * - framing changes are debounced ([framingDebounceMs]) so a flicker never becomes speech;
 * - pose events are emitted only for temporally confirmed (isStable) recognition;
 * - hold milestones are measured from the moment the pose became stable.
 * Pure Kotlin; time is supplied by the caller.
 */
class CoachingEventSource(
    private val framingDebounceMs: Long = 1500,
    private val milestoneEverySeconds: Int = 10,
) {
    private var framingCandidate: FramingStatus? = null
    private var framingSince = 0L
    private var framingReported: FramingStatus? = null

    private var stablePose: Pair<Asana, BodySide>? = null
    private var holdStartMs = 0L
    private var lastMilestone = 0

    /** Longest confirmed hold per asana in this session (ms). */
    val longestHolds = linkedMapOf<Asana, Long>()

    fun reset() {
        framingCandidate = null; framingReported = null; framingSince = 0
        stablePose = null; holdStartMs = 0; lastMilestone = 0
        longestHolds.clear()
    }

    /** Call after a pause: the current hold restarts so paused time is never counted. */
    fun restartHold(nowMs: Long) {
        if (stablePose != null) { holdStartMs = nowMs; lastMilestone = 0 }
    }

    fun onSnapshot(s: PracticeSnapshot): List<CoachEvent> {
        val events = mutableListOf<CoachEvent>()

        // Framing (debounced).
        if (s.framing != framingCandidate) { framingCandidate = s.framing; framingSince = s.timeMs }
        if (s.timeMs - framingSince >= framingDebounceMs && framingCandidate != framingReported) {
            framingReported = framingCandidate
            events += if (s.framing == FramingStatus.FULL_BODY) CoachEvent.FramingOk else CoachEvent.FramingIssue(s.framing)
        }

        // Recognition (stable only).
        val r = s.recognition
        val current = if (r.isStable && r.asana != Asana.UNKNOWN) r.asana to r.side else null
        val previous = stablePose
        if (current != previous) {
            if (previous != null) {
                val held = s.timeMs - holdStartMs
                recordHold(previous.first, held)
                events += CoachEvent.PoseLost(previous.first, held)
            }
            if (current != null) {
                holdStartMs = s.timeMs; lastMilestone = 0
                events += CoachEvent.PoseRecognized(current.first, current.second, r.confidence)
                events += CoachEvent.StableHoldStarted(current.first)
            }
            stablePose = current
        } else if (current != null) {
            val heldS = ((s.timeMs - holdStartMs) / 1000).toInt()
            recordHold(current.first, s.timeMs - holdStartMs)
            val milestone = heldS / milestoneEverySeconds * milestoneEverySeconds
            if (milestone > 0 && milestone > lastMilestone) {
                lastMilestone = milestone
                events += CoachEvent.HoldMilestoneReached(current.first, milestone)
            }
        }
        return events
    }

    /** Current confirmed pose and how long it has been held, for the HUD. */
    fun currentHold(nowMs: Long): Pair<Asana, Long>? = stablePose?.let { it.first to (nowMs - holdStartMs) }

    private fun recordHold(asana: Asana, ms: Long) {
        if (ms > (longestHolds[asana] ?: 0L)) longestHolds[asana] = ms
    }
}
