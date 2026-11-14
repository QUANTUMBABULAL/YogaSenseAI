package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana

/** What a teacher looks at in a held posture. Weights sum to 1. */
enum class ScoreComponent(val label: String, val weight: Float) {
    /** Is the shape of the pose right (joint angles, lines, level shoulders and hips). */
    ALIGNMENT("Alignment", 0.50f),
    /** How still the body was while holding. */
    STABILITY("Stability", 0.25f),
    /** Longest steady hold against the pose's target hold. */
    HOLD("Hold", 0.25f),
}

enum class ScoreBand(val min: Int, val label: String, val headline: String) {
    EXCELLENT(90, "Excellent", "Excellent session. Your form was precise and steady."),
    STRONG(75, "Strong", "Strong session. Your alignment and balance are well established."),
    GOOD(60, "Good", "Good work. The foundations are there; now refine the details."),
    DEVELOPING(40, "Developing", "A solid start. Slow down and work on one correction at a time."),
    STARTING(0, "Getting started", "Early days. Use the lessons and build each pose step by step.");

    companion object { fun of(score: Int): ScoreBand = entries.first { score >= it.min } }
}

/**
 * Score for one pose. Every field is null when it could not be measured: an unmeasured component
 * is left out of [total] (the other weights are rescaled) and is never treated as 0.
 */
data class PoseScore(
    val asana: Asana,
    val total: Int?,
    val alignment: Int?,
    val stability: Int?,
    val hold: Int?,
    val longestHoldMs: Long,
    val holdGoalMs: Long,
    /** Best and weakest measured component (null when fewer than two were measured). */
    val strongest: ScoreComponent?,
    val focus: ScoreComponent?,
    /** One instructor-style note about [focus] (or praise when everything is strong). */
    val note: String?,
) {
    val band: ScoreBand? get() = total?.let(ScoreBand::of)
    fun component(c: ScoreComponent): Int? = when (c) {
        ScoreComponent.ALIGNMENT -> alignment
        ScoreComponent.STABILITY -> stability
        ScoreComponent.HOLD -> hold
    }
}

data class SessionScore(
    /** Null when no pose could be scored. */
    val total: Int?,
    val poses: List<PoseScore>,
    val headline: String,
) {
    val band: ScoreBand? get() = total?.let(ScoreBand::of)
    val scoredPoses: Int get() = poses.count { it.total != null }
    fun of(asana: Asana): PoseScore? = poses.firstOrNull { it.asana == asana }
}

/**
 * Posture-based scoring for a finished session, built only from what [SessionSummary] measured
 * (mean alignment and stability while the hold was counting, and steady-hold time).
 *
 * Pose score = weighted mean of the measured components (alignment 50 %, stability 25 %, hold 25 %).
 * - Hold = longest steady hold ÷ the pose's hold goal, capped at 100.
 * - A pose is scored only if its posture was actually measured (alignment or stability); hold time
 *   alone says nothing about posture, so such a pose is "not scored".
 * Session score = mean of the scored poses, weighted by steady-hold time (min 5 s each, so a brief
 * pose still counts but a long, well-held pose counts more).
 *
 * Like the inputs, these are prototype single-camera estimates, not validated measurements.
 */
object SessionScoring {
    const val MIN_POSE_WEIGHT_MS = 5_000L
    /** A component at or above this needs no correction note. */
    const val STRONG = 85

    fun score(summary: SessionSummary, holdGoalMs: (Asana) -> Long): SessionScore {
        val poses = summary.poses.map { pose(it, holdGoalMs(it.asana).coerceAtLeast(1_000)) }
        val scored = poses.filter { it.total != null }
        val total = if (scored.isEmpty()) null else {
            val weights = scored.associateWith { p ->
                summary.poses.first { it.asana == p.asana }.stableHoldTotalMs.coerceAtLeast(MIN_POSE_WEIGHT_MS).toDouble()
            }
            Math.round(scored.sumOf { it.total!! * weights.getValue(it) } / weights.values.sum()).toInt()
        }
        val headline = when {
            total != null -> ScoreBand.of(total).headline
            summary.poses.isEmpty() -> "No pose was confirmed, so this session has no posture score."
            else -> "Not enough clear measurements to score this session. Hold each pose still, with your whole body in view."
        }
        return SessionScore(total, poses, headline)
    }

    fun pose(p: PoseSummary, holdGoalMs: Long): PoseScore {
        val hold = Math.round(100.0 * p.longestHoldMs / holdGoalMs).toInt().coerceIn(0, 100)
        val postureMeasured = p.meanAlignment != null || p.meanStability != null
        val parts = buildMap {
            p.meanAlignment?.let { put(ScoreComponent.ALIGNMENT, it.coerceIn(0, 100)) }
            p.meanStability?.let { put(ScoreComponent.STABILITY, it.coerceIn(0, 100)) }
            if (postureMeasured) put(ScoreComponent.HOLD, hold)
        }
        val total = if (!postureMeasured) null
        else Math.round(parts.entries.sumOf { (c, s) -> (c.weight * s).toDouble() } / parts.keys.sumOf { it.weight.toDouble() }).toInt()
        val strongest = parts.takeIf { it.size >= 2 }?.maxByOrNull { it.value }?.key
        val focus = parts.takeIf { it.size >= 2 }?.minByOrNull { it.value }?.key
        return PoseScore(
            asana = p.asana, total = total,
            alignment = parts[ScoreComponent.ALIGNMENT], stability = parts[ScoreComponent.STABILITY], hold = parts[ScoreComponent.HOLD],
            longestHoldMs = p.longestHoldMs, holdGoalMs = holdGoalMs,
            strongest = strongest, focus = focus,
            note = note(p, parts, focus, holdGoalMs),
        )
    }

    private fun note(p: PoseSummary, parts: Map<ScoreComponent, Int>, focus: ScoreComponent?, holdGoalMs: Long): String? {
        if (parts.isEmpty()) return "Recognised, but not held still long enough to measure. Settle into the pose, then breathe."
        val weakest = focus ?: parts.keys.first()
        if (parts.getValue(weakest) >= STRONG) return "Precise and steady. Keep the same quality as you lengthen the hold."
        return when (weakest) {
            ScoreComponent.ALIGNMENT -> p.corrections.maxByOrNull { it.value }?.key?.let { "Form first: ${it.trimEnd('.')}." }
                ?: "Form first: move into the pose slowly and set each joint before you start to hold."
            ScoreComponent.STABILITY -> "Steadiness: fix your gaze on one still point, spread your weight evenly and let the breath slow down."
            ScoreComponent.HOLD -> "Endurance: your longest steady hold was ${p.longestHoldMs / 1000} s of ${holdGoalMs / 1000} s. " +
                "Add a few seconds each session and come out before your form slips."
        }
    }
}
