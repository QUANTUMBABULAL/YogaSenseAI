package com.chinmai.yogasense.asana

/** Catalogue asanas (the live engine recognises the first five). [UNKNOWN] means "not recognised" and is never forced into a real pose. */
enum class Asana(val displayName: String, val sided: Boolean) {
    TADASANA("Tadasana (Mountain)", sided = false),
    TREE("Vrksasana (Tree)", sided = true),
    WARRIOR_II("Virabhadrasana II (Warrior II)", sided = true),
    CHAIR("Utkatasana (Chair)", sided = false),
    TRIANGLE("Trikonasana (Triangle)", sided = true),
    /** Library/lesson only: not recognised or assessed by the live engine (see AsanaInfo.liveSupported). */
    BHUJANGASANA("Bhujangasana (Cobra)", sided = false),
    CHAKRASANA("Chakrasana (Wheel)", sided = false),
    UNKNOWN("Not recognised", sided = false),
}

/**
 * Anatomical side of the subject that defines an asymmetric pose:
 * Tree = lifted leg, Warrior II = bent front leg, Triangle = front leg / lower hand.
 */
enum class BodySide { LEFT, RIGHT, NONE;
    val opposite: BodySide get() = when (this) { LEFT -> RIGHT; RIGHT -> LEFT; NONE -> NONE }
}

/** One geometric check that contributed to a classification. */
data class FeatureEvidence(
    val name: String,
    val value: Float?,      // null = landmarks missing / low confidence
    val min: Float,
    val max: Float,
    val unit: String,
    val score: Float,       // 0..1 match to the reference range
    val required: Boolean,
)

/**
 * How well the current frame matches one asana template.
 *
 * [confidence] is CLASSIFICATION confidence (how sure we are which pose this is),
 * NOT a measure of physical correctness or alignment quality. Alignment scoring is Phase 4.
 */
data class AsanaCandidate(
    val asana: Asana,
    val side: BodySide,
    val confidence: Float,
    val evidence: List<FeatureEvidence>,
    val missingFeatures: List<String>,
)

/** Per-frame classification before temporal confirmation. */
data class FrameClassification(
    val timestampMs: Long,
    val asana: Asana,                 // UNKNOWN when below threshold or ambiguous
    val side: BodySide,
    val confidence: Float,
    val best: AsanaCandidate?,        // highest-scoring template, even if rejected
    val candidates: List<AsanaCandidate>,
    val lowConfidenceLandmarks: List<String>,
    val reason: String,
)

/**
 * Temporally confirmed recognition (shared contract; first three fields per ARCHITECTURE.md).
 *
 * - [asana] is only non-UNKNOWN when [isStable].
 * - [stability] = share of recent frames agreeing with the reported/pending pose (0..1).
 * - [pending]/[pendingProgress] expose a pose that is being confirmed, for UI hints only.
 */
data class Recognition(
    val asana: Asana,
    val confidence: Float,
    val isStable: Boolean,
    val side: BodySide = BodySide.NONE,
    val stability: Float = 0f,
    val pending: Asana = Asana.UNKNOWN,
    val pendingProgress: Float = 0f,
    val frame: FrameClassification? = null,
) {
    companion object {
        val NONE = Recognition(Asana.UNKNOWN, 0f, false)
    }
}
