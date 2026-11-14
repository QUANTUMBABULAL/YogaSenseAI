package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodySide

/** Why an assessment value is or is not available. Unavailable is never encoded as 0. */
enum class Availability { AVAILABLE, INSUFFICIENT_LANDMARKS, NO_PROFILE }

enum class MetricStatus { GOOD, NEEDS_WORK, UNAVAILABLE }

/** Why a metric was not evaluated (Phase 8). Never shown or scored as a measurement. */
enum class UnavailableReason(val label: String) {
    OCCLUDED("hidden"),                 // a required joint is occluded or out of frame
    LOW_CONFIDENCE("low confidence"),   // joints seen, but not confidently enough to measure
    NOT_FROM_THIS_VIEW("not visible from this angle"), // geometry not meaningful for the current camera orientation
}

/** How much the overall assessment can be trusted — reported separately from the posture score. */
enum class ConfidenceLevel(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low"), INSUFFICIENT("Not enough data") }

data class AssessmentConfidence(val level: ConfidenceLevel, val value: Float) {
    companion object { val NONE = AssessmentConfidence(ConfidenceLevel.INSUFFICIENT, 0f) }
}

enum class Direction { LOW, HIGH }

/** One measured alignment metric. [value]/[score] are null when the joints were not reliably observed. */
data class MetricResult(
    val id: String,
    val label: String,
    val value: Float?,
    val idealMin: Float,
    val idealMax: Float,
    val unit: String,
    val weight: Float,
    val score: Float?,           // 0..1 match to the ideal band, null when unavailable
    val status: MetricStatus,
    /** Set when [status] is UNAVAILABLE. */
    val reason: UnavailableReason? = null,
    /** True when measured on one side only because the other side was hidden (e.g. one arm). */
    val oneSideOnly: Boolean = false,
    /** A key metric for this asana (missing it lowers confidence and may trigger a camera hint). */
    val critical: Boolean = false,
)

/** A correctable deviation on a reliably measured metric. */
data class AlignmentIssue(
    val asana: Asana,
    val metricId: String,
    val direction: Direction,
    val correction: String,      // short on-screen and spoken instruction
    val severity: Float,         // 1 - metric score
    val weight: Float,
) {
    /** Stable id used for cooldowns, de-duplication and improvement tracking. */
    val correctionId: String get() = "${asana.name}.$metricId.${direction.name.lowercase()}"
    val priority: Float get() = severity * weight
}

/**
 * Alignment for one frame. [score] (0–100) is only present when enough weighted metrics were
 * observable; it describes geometric similarity to prototype heuristics, not clinical correctness.
 */
data class AlignmentResult(
    val asana: Asana,
    val side: BodySide,
    val timestampMs: Long,
    val availability: Availability,
    val score: Int?,
    val metrics: List<MetricResult>,
    val issues: List<AlignmentIssue>,          // sorted, highest priority first
    val visibility: Float,                     // fraction of the 12 key joints reliably observed
    val lowConfidenceLandmarks: List<String>,
    // ---- Phase 8: occlusion-aware coverage and confidence (separate from the posture score) ----
    val orientation: com.chinmai.yogasense.pose.CameraOrientation = com.chinmai.yogasense.pose.CameraOrientation.UNKNOWN,
    /** Evaluated weight ÷ the profile's TOTAL weight (unavailable metrics are never counted as covered). */
    val coverage: Float = 0f,
    val confidence: AssessmentConfidence = AssessmentConfidence.NONE,
    /** Short camera instruction when key joints are persistently not observable (null otherwise). */
    val cameraHint: String? = null,
) {
    val available: Boolean get() = availability == Availability.AVAILABLE
    val evaluated: List<MetricResult> get() = metrics.filter { it.score != null }
    val unavailable: List<MetricResult> get() = metrics.filter { it.score == null }
    val missingCritical: List<MetricResult> get() = metrics.filter { it.critical && it.score == null }
    val primaryIssue: AlignmentIssue? get() = issues.firstOrNull()
    fun metric(id: String): MetricResult? = metrics.firstOrNull { it.id == id }
}

/** Temporal stability. [score] is null until enough reliable observations exist. */
data class StabilityResult(
    val score: Int?,
    val sway: Float?,          // RMS movement of body centre, in torso lengths
    val samples: Int,
) {
    val label: String get() = when {
        score == null -> "Measuring"
        score >= 70 -> "Steady"
        score >= 40 -> "Some sway"
        else -> "Unsteady"
    }

    companion object { val NONE = StabilityResult(null, null, 0) }
}

/** Live stable-hold state (time counted only while the hold qualifies). */
data class HoldStatus(
    val asana: Asana?,
    val currentHoldMs: Long,
    val counting: Boolean,
    val reason: String?,       // why the hold is not counting, for the UI
) {
    companion object { val NONE = HoldStatus(null, 0, false, null) }
}

/** Everything the live UI shows about the current pose. */
data class LiveAssessment(
    val alignment: AlignmentResult?,
    val stability: StabilityResult,
    val hold: HoldStatus,
    val correction: String?,   // one current correction, already filtered for reliability/framing
    /** Framing is acceptable for assessment (incl. a profile view whose far side is hidden). */
    val framingAccepted: Boolean = false,
)
