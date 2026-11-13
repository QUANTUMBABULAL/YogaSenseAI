package com.chinmai.yogasense.asana

import com.chinmai.yogasense.pose.PoseFrame

/**
 * Single-frame, rule-based classifier. Pure Kotlin, deterministic, explainable.
 *
 * Each template (and each side, for sided poses) is scored independently. A template is rejected
 * if a required feature is missing or completely outside tolerance. The winner must exceed
 * [minConfidence] and beat the best different asana by [minMargin]; otherwise UNKNOWN.
 */
class AsanaClassifier(
    private val references: List<AsanaReference> = AsanaReferences.ALL,
    private val minConfidence: Float = 0.65f,
    private val minMargin: Float = 0.10f,
) {
    fun classify(frame: PoseFrame): FrameClassification = classify(BodyFeatures(frame), frame.timestampMs)

    fun classify(f: BodyFeatures, timestampMs: Long): FrameClassification {
        if (!f.hasPose) return unknown(timestampMs, emptyList(), null, f, "No body detected")
        if (f.torsoLength == null) return unknown(timestampMs, emptyList(), null, f, "Shoulders and hips must be visible")

        val candidates = references.flatMap { ref ->
            val sides = if (ref.asana.sided) listOf(BodySide.LEFT, BodySide.RIGHT) else listOf(BodySide.NONE)
            sides.map { evaluate(ref, f, it) }
        }.sortedByDescending { it.confidence }

        val best = candidates.first()
        val runnerUp = candidates.firstOrNull { it.asana != best.asana }?.confidence ?: 0f

        return when {
            best.confidence <= 0f -> {
                val missing = candidates.flatMap { it.missingFeatures }.distinct()
                val reason = if (f.lowConfidenceLandmarks.isNotEmpty() && missing.isNotEmpty())
                    "Not recognised — low confidence: ${f.lowConfidenceLandmarks.joinToString()}"
                else "Not recognised as a supported pose"
                unknown(timestampMs, candidates, best, f, reason)
            }
            best.confidence < minConfidence ->
                unknown(timestampMs, candidates, best, f, "Closest match ${best.asana.displayName} below confidence threshold")
            best.confidence - runnerUp < minMargin ->
                unknown(timestampMs, candidates, best, f, "Ambiguous between ${best.asana.displayName} and ${candidates.first { it.asana != best.asana }.asana.displayName}")
            else -> FrameClassification(
                timestampMs, best.asana, best.side, best.confidence, best, candidates,
                f.lowConfidenceLandmarks, "Matches ${best.asana.displayName}",
            )
        }
    }

    private fun evaluate(ref: AsanaReference, f: BodyFeatures, side: BodySide): AsanaCandidate {
        val evidence = ref.rules.map { r ->
            val v = r.extract(f, side)
            FeatureEvidence(r.name, v, r.min, r.max, r.unit, if (v == null) 0f else r.score(v), r.required)
        }
        val missing = evidence.filter { it.value == null && it.required }.map { it.name }
        val rejected = missing.isNotEmpty() || evidence.any { it.required && it.score <= 0f }

        val confidence = if (rejected) 0f else {
            var sum = 0f; var wsum = 0f; var minReq = 1f
            ref.rules.forEachIndexed { i, r ->
                val e = evidence[i]
                if (e.value == null) return@forEachIndexed // optional & missing: ignored
                sum += r.weight * e.score; wsum += r.weight
                if (r.required) minReq = minOf(minReq, e.score)
            }
            // Weighted mean, pulled down by the weakest required feature.
            0.7f * (sum / wsum) + 0.3f * minReq
        }
        return AsanaCandidate(ref.asana, side, confidence, evidence, missing)
    }

    private fun unknown(
        ts: Long, candidates: List<AsanaCandidate>, best: AsanaCandidate?, f: BodyFeatures, reason: String,
    ) = FrameClassification(ts, Asana.UNKNOWN, BodySide.NONE, 0f, best, candidates, f.lowConfidenceLandmarks, reason)
}
