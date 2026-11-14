package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide

/**
 * Explainable alignment assessment for a confirmed asana.
 *
 * Score = round(100 × Σ(wᵢ·sᵢ) / Σwᵢ) over AVAILABLE metrics only.
 * The score is null (unavailable) when the available metrics carry less than
 * [minWeightFraction] of the profile's total weight or fewer than [minMetrics] metrics are available.
 * An issue is raised for an available metric whose score < [issueBelow]; issues are sorted by
 * severity × weight so the single most important correction comes first.
 */
class AsanaAlignmentAssessor(
    private val profiles: Map<Asana, AssessmentProfile> = AsanaAssessmentProfiles.ALL,
    private val issueBelow: Float = 0.75f,
    private val minWeightFraction: Float = 0.6f,
    private val minMetrics: Int = 2,
) {
    fun assess(f: BodyFeatures, asana: Asana, side: BodySide, timestampMs: Long): AlignmentResult {
        val visibility = 1f - f.lowConfidenceLandmarks.size / BodyFeatures.KEY_LANDMARKS.size.toFloat()
        val profile = profiles[asana]
            ?: return AlignmentResult(asana, side, timestampMs, Availability.NO_PROFILE, null, emptyList(), emptyList(),
                if (f.hasPose) visibility else 0f, f.lowConfidenceLandmarks)

        val metrics = profile.rules.map { r ->
            val v = if (f.hasPose) r.extract(f, side) else null
            val s = v?.let { r.score(it) }
            MetricResult(
                r.id, r.label, v, r.idealMin, r.idealMax, r.unit, r.weight, s,
                when { s == null -> MetricStatus.UNAVAILABLE; s < issueBelow -> MetricStatus.NEEDS_WORK; else -> MetricStatus.GOOD },
            )
        }
        val available = metrics.filter { it.score != null }
        val totalW = profile.rules.sumOf { it.weight.toDouble() }.toFloat()
        val availW = available.sumOf { it.weight.toDouble() }.toFloat()
        val enough = f.hasPose && available.size >= minMetrics && availW / totalW >= minWeightFraction

        val issues = if (!enough) emptyList() else profile.rules.zip(metrics).mapNotNull { (r, m) ->
            val v = m.value ?: return@mapNotNull null
            val s = m.score ?: return@mapNotNull null
            if (s >= issueBelow) return@mapNotNull null
            val dir = if (v < r.idealMin) Direction.LOW else Direction.HIGH
            val text = (if (dir == Direction.LOW) r.lowCorrection else r.highCorrection) ?: return@mapNotNull null
            AlignmentIssue(asana, r.id, dir, text, 1f - s, r.weight)
        }.sortedByDescending { it.priority }

        val score = if (enough) Math.round(100f * available.sumOf { (it.weight * it.score!!).toDouble() }.toFloat() / availW) else null
        return AlignmentResult(
            asana, side, timestampMs,
            if (enough) Availability.AVAILABLE else Availability.INSUFFICIENT_LANDMARKS,
            score, metrics, issues, if (f.hasPose) visibility else 0f, f.lowConfidenceLandmarks,
        )
    }
}
