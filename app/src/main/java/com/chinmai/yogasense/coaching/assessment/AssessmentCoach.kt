package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.coaching.AlignmentCorrection
import com.chinmai.yogasense.coaching.CoachEvent

/**
 * Turns assessments into coaching events (pure Kotlin):
 * - One correction at a time: only the primary issue, and only after it persisted [persistMs]
 *   on reliable metrics with good framing. Re-emitted at most every [repeatMs] while it persists
 *   (the decision engine applies its own cooldowns on top).
 * - Improvement: for a correction that was emitted, the metric must be measured and score ≥
 *   [improvedScore] continuously for [improvePersistMs].
 * - StabilityDecreased: stability fell from ≥ [stableHigh] to < [stableLow] (once until it recovers).
 */
class AssessmentCoach(
    private val persistMs: Long = 2000,
    private val repeatMs: Long = 15_000,
    private val improvePersistMs: Long = 1500,
    private val improvedScore: Float = 0.85f,
    private val stableHigh: Int = 70,
    private val stableLow: Int = 45,
    /** Minimum time between two camera-adjustment requests (the hint itself is already persistence-gated). */
    private val cameraHintRepeatMs: Long = 20_000,
) {
    private var lastCameraHintAt = Long.MIN_VALUE / 2
    private var candidateId: String? = null
    private var candidateSince = 0L
    private val lastEmitted = HashMap<String, Long>()
    private val awaitingImprovement = HashMap<String, String>()   // correctionId -> metricId
    private val goodSince = HashMap<String, Long>()
    private var wasSteady = false
    private var decreaseAnnounced = false

    /** Measured improvements this session (labels), for the summary. */
    val improvements = mutableListOf<String>()

    fun resetPose() {
        candidateId = null; goodSince.clear(); awaitingImprovement.clear(); lastEmitted.clear()
        wasSteady = false; decreaseAnnounced = false
    }

    fun update(alignment: AlignmentResult?, stability: StabilityResult, framingOk: Boolean, timeMs: Long): List<CoachEvent> {
        val events = mutableListOf<CoachEvent>()
        // Phase 8: when key joints stay unobservable, ask for a camera/position change instead of
        // correcting a limb we can't see. Rate-limited; never repeated every frame.
        alignment?.cameraHint?.let { hint ->
            if (timeMs - lastCameraHintAt >= cameraHintRepeatMs) { lastCameraHintAt = timeMs; events += CoachEvent.CameraAdjustmentNeeded(hint) }
        }
        if (alignment == null || !alignment.available || !framingOk) {
            candidateId = null; goodSince.clear()
            return events
        }

        // Improvement on previously corrected metrics (measured, sustained). A metric that is not
        // evaluated (hidden, low confidence, wrong angle) has no score, so it can never "improve".
        val done = mutableListOf<String>()
        for ((cid, metricId) in awaitingImprovement) {
            val m = alignment.metric(metricId)
            val s = m?.score
            if (s != null && s >= improvedScore) {
                val since = goodSince.getOrPut(cid) { timeMs }
                if (timeMs - since >= improvePersistMs) {
                    events += CoachEvent.AlignmentImproved(cid)
                    improvements += m.label
                    done += cid
                }
            } else goodSince.remove(cid)
        }
        done.forEach { awaitingImprovement.remove(it); goodSince.remove(it) }

        // One correction at a time.
        val primary = alignment.primaryIssue
        if (primary == null) {
            candidateId = null
        } else {
            if (primary.correctionId != candidateId) { candidateId = primary.correctionId; candidateSince = timeMs }
            val last = lastEmitted[primary.correctionId]
            if (timeMs - candidateSince >= persistMs && (last == null || timeMs - last >= repeatMs)) {
                lastEmitted[primary.correctionId] = timeMs
                awaitingImprovement[primary.correctionId] = primary.metricId
                goodSince.remove(primary.correctionId)
                events += CoachEvent.AlignmentCorrectionRequired(AlignmentCorrection(primary.correctionId, primary.correction))
            }
        }

        // Stability decrease.
        val sc = stability.score
        if (sc != null) {
            if (sc >= stableHigh) { wasSteady = true; decreaseAnnounced = false }
            else if (sc < stableLow && wasSteady && !decreaseAnnounced) {
                decreaseAnnounced = true
                events += CoachEvent.StabilityDecreased(sc)
            }
        }
        return events
    }

    /** The single correction to show on screen right now (same rules, without persistence). */
    fun currentCorrection(alignment: AlignmentResult?, framingOk: Boolean): String? =
        if (alignment != null && alignment.available && framingOk) alignment.primaryIssue?.correction else null
}
