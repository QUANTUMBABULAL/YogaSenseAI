package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.pose.CameraOrientation
import com.chinmai.yogasense.pose.LandmarkReliability
import com.chinmai.yogasense.pose.LandmarkState
import com.chinmai.yogasense.pose.OrientationDetector
import com.chinmai.yogasense.pose.ReliabilitySnapshot

/**
 * Occlusion-aware, explainable alignment assessment for a confirmed asana (Phase 8).
 *
 * Per metric: the first variant that is meaningful for the detected camera orientation AND whose
 * joints are all RELIABLE is measured. Otherwise the metric is UNAVAILABLE with a reason
 * (hidden / low confidence / not visible from this angle) — never 0, never a correction.
 *
 * Score = round(100 × Σ(wᵢ·sᵢ) / Σwᵢ) over EVALUATED metrics only (transparent re-weighting).
 * It is withheld (null) unless evaluated metrics carry ≥ [minCoverage] of the profile's TOTAL
 * weight and ≥ [minMetrics] metrics are evaluated, so a small subset can never produce a high
 * score. Metrics that can't be measured from the current view count as not covered.
 *
 * Confidence is reported separately from the score:
 *   coverage × joint quality × view factor × critical factor  (see [confidence]).
 */
class AsanaAlignmentAssessor(
    private val profiles: Map<Asana, AssessmentProfile> = AsanaAssessmentProfiles.ALL,
    private val issueBelow: Float = 0.75f,
    private val minCoverage: Float = 0.6f,
    private val minMetrics: Int = 2,
) {
    fun assess(f: BodyFeatures, asana: Asana, side: BodySide, timestampMs: Long, temporal: ReliabilitySnapshot? = null): AlignmentResult {
        val visibility = 1f - f.lowConfidenceLandmarks.size / BodyFeatures.KEY_LANDMARKS.size.toFloat()
        val profile = profiles[asana]
            ?: return AlignmentResult(asana, side, timestampMs, Availability.NO_PROFILE, null, emptyList(), emptyList(),
                if (f.hasPose) visibility else 0f, f.lowConfidenceLandmarks)

        val states = temporal?.states ?: LandmarkReliability.classify(f.frame)
        val snapshot = temporal ?: ReliabilitySnapshot.instant(states)
        val orientation = OrientationDetector.detect(f.frame, states)
        // Measurements use RELIABLE joints only.
        val strict = BodyFeatures(f.frame, LandmarkReliability.RELIABLE)

        val evaluations = profile.rules.map { r -> evaluate(r, strict, side, orientation, states) }
        val metrics = profile.rules.zip(evaluations).map { (r, e) ->
            val s = e.value?.let { r.score(it) }
            MetricResult(
                r.id, r.label, e.value, r.idealMin, r.idealMax, r.unit, r.weight, s,
                when { s == null -> MetricStatus.UNAVAILABLE; s < issueBelow -> MetricStatus.NEEDS_WORK; else -> MetricStatus.GOOD },
                reason = if (s == null) e.reason else null, oneSideOnly = e.oneSideOnly, critical = r.critical,
            )
        }
        val evaluated = metrics.filter { it.score != null }
        val totalW = profile.rules.sumOf { it.weight.toDouble() }.toFloat()
        val evalW = evaluated.sumOf { it.weight.toDouble() }.toFloat()
        val coverage = if (totalW > 0f) evalW / totalW else 0f
        val enough = f.hasPose && evaluated.size >= minMetrics && coverage >= minCoverage

        val issues = if (!enough) emptyList() else profile.rules.zip(metrics).mapNotNull { (r, m) ->
            val v = m.value ?: return@mapNotNull null
            val s = m.score ?: return@mapNotNull null
            if (s >= issueBelow) return@mapNotNull null
            val dir = if (v < r.idealMin) Direction.LOW else Direction.HIGH
            val text = (if (dir == Direction.LOW) r.lowCorrection else r.highCorrection) ?: return@mapNotNull null
            AlignmentIssue(asana, r.id, dir, text, 1f - s, r.weight)
        }.sortedByDescending { it.priority }

        val score = if (enough) Math.round(100f * evaluated.sumOf { (it.weight * it.score!!).toDouble() }.toFloat() / evalW) else null
        val usedJoints = profile.rules.zip(evaluations).flatMap { (_, e) -> e.joints.toList() }.distinct()
        return AlignmentResult(
            asana, side, timestampMs,
            if (enough) Availability.AVAILABLE else Availability.INSUFFICIENT_LANDMARKS,
            score, metrics, issues, if (f.hasPose) visibility else 0f, f.lowConfidenceLandmarks,
            orientation = orientation,
            coverage = coverage,
            confidence = if (!enough) AssessmentConfidence.NONE else confidence(coverage, usedJoints.map { f.frame.landmarks[it].visibility }, orientation, profile, metrics),
            cameraHint = cameraHint(profile, metrics, evaluations, f, orientation, snapshot, scoreAvailable = enough),
        )
    }

    private class Evaluation(val value: Float?, val reason: UnavailableReason?, val oneSideOnly: Boolean, val joints: IntArray, val neededJoints: IntArray)

    private fun evaluate(r: AlignmentRule, strict: BodyFeatures, side: BodySide, o: CameraOrientation, states: List<LandmarkState>): Evaluation {
        if (!strict.hasPose) return Evaluation(null, UnavailableReason.OCCLUDED, false, IntArray(0), IntArray(0))
        val applicable = r.variants.filter { o in it.views }
        if (applicable.isEmpty()) return Evaluation(null, UnavailableReason.NOT_FROM_THIS_VIEW, false, IntArray(0), IntArray(0))
        var best: UnavailableReason = UnavailableReason.OCCLUDED
        var needed = IntArray(0)
        for (v in applicable) {
            val sides = when (v.sideMode) {
                SideMode.RECOGNISED -> listOf(side)
                SideMode.EITHER -> listOf(BodySide.LEFT, BodySide.RIGHT)
            }
            for (s in sides) {
                val joints = v.joints(s)
                if (needed.isEmpty()) needed = joints
                val js = joints.map { states[it] }
                when {
                    js.all { it == LandmarkState.RELIABLE } -> {
                        val value = v.extract(strict, s)
                        if (value != null) return Evaluation(value, null, v.oneSideOnly, joints, joints)
                    }
                    js.none { it == LandmarkState.OCCLUDED || it == LandmarkState.MISSING } -> best = UnavailableReason.LOW_CONFIDENCE
                }
            }
        }
        return Evaluation(null, best, false, IntArray(0), needed)
    }

    /**
     * Explainable confidence (0..1), independent of the posture score:
     * - coverage of the profile's weight,
     * - joint quality: mean visibility of the joints actually measured, mapped from the RELIABLE
     *   threshold (→0.6) to 1.0 (→1.0),
     * - view factor: 1.0 in the asana's intended orientation, 0.85 when unknown/angled/other,
     * - critical factor: 0.75 if any key metric could not be evaluated.
     */
    private fun confidence(coverage: Float, vis: List<Float>, o: CameraOrientation, p: AssessmentProfile, metrics: List<MetricResult>): AssessmentConfidence {
        val q = if (vis.isEmpty()) 0.6f else 0.6f + 0.4f * ((vis.average().toFloat() - LandmarkReliability.RELIABLE) / (1f - LandmarkReliability.RELIABLE)).coerceIn(0f, 1f)
        val view = if (o == p.preferredView) 1f else 0.85f
        val critical = if (metrics.any { it.critical && it.score == null }) 0.75f else 1f
        val v = (coverage * q * view * critical).coerceIn(0f, 1f)
        val level = when { v >= 0.7f -> ConfidenceLevel.HIGH; v >= 0.5f -> ConfidenceLevel.MEDIUM; else -> ConfidenceLevel.LOW }
        return AssessmentConfidence(level, v)
    }

    /**
     * A camera instruction only when it would help and only for PERSISTENT problems:
     * - the asana's key metrics can't be measured from this orientation → turn to the intended view;
     * - key joints are out of frame for a while → step back;
     * - key joints are hidden for a while → turn so that side is visible.
     * Brief tracking dropouts never produce a hint.
     */
    private fun cameraHint(
        p: AssessmentProfile, metrics: List<MetricResult>, evals: List<Evaluation>, f: BodyFeatures,
        @Suppress("UNUSED_PARAMETER") o: CameraOrientation, snap: ReliabilitySnapshot, scoreAvailable: Boolean,
    ): String? {
        // Score withheld because this camera angle can't show enough of the pose → change the view.
        if (!scoreAvailable && metrics.any { it.reason == UnavailableReason.NOT_FROM_THIS_VIEW }) {
            return if (p.preferredView == CameraOrientation.SIDE) "Turn sideways to the camera for this pose" else "Face the camera so your whole front is visible"
        }
        val missingCritical = metrics.indices.filter { metrics[it].critical && metrics[it].score == null }
        if (missingCritical.isEmpty()) return null
        val joints = missingCritical.flatMap { evals[it].neededJoints.toList() }.distinct()
        val persistentMissing = joints.filter { it in snap.persistentlyUnavailable }
        if (persistentMissing.isEmpty()) return null // a brief dropout: wait, don't nag
        fun offFrame(i: Int) = f.frame.hasPose && f.frame.landmarks[i].let { it.x < 0f || it.x > 1f || it.y < 0f || it.y > 1f }
        if (persistentMissing.any(::offFrame)) return "Step back so your whole body is in view"
        val name = BodyFeatures.LANDMARK_NAMES[persistentMissing.first()]
        return "Turn slightly so the camera can see your $name"
    }
}
