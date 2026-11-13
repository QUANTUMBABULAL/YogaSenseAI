package com.chinmai.yogasense.asana

/**
 * One reference range for a geometric feature.
 *
 * Score is 1 inside [min, max], falling linearly to 0 at [tolerance] beyond the range.
 * A required rule scoring 0 (or with missing landmarks) rejects the template outright.
 * For sided poses, [extract] receives the defining side (see [BodySide]).
 */
class FeatureRule(
    val name: String,
    val min: Float,
    val max: Float,
    val tolerance: Float,
    val unit: String,
    val weight: Float = 1f,
    val required: Boolean = true,
    val extract: (BodyFeatures, BodySide) -> Float?,
) {
    fun score(v: Float): Float {
        val d = when {
            v < min -> min - v
            v > max -> v - max
            else -> return 1f
        }
        return (1f - d / tolerance).coerceAtLeast(0f)
    }
}

class AsanaReference(val asana: Asana, val viewNote: String, val rules: List<FeatureRule>)

private const val DEG = "°"
private const val TORSO = "× torso"

/**
 * Recognition templates.
 *
 * Ranges describe what each pose LOOKS LIKE in a 2D camera image, wide enough to recognise
 * beginners. They are recognition gates, not alignment targets, and make no clinical claim.
 * Shapes follow common descriptions of the poses (e.g. Yoga Journal pose library; B.K.S. Iyengar,
 * "Light on Yoga"). Numbers are engineering estimates for 2D projection, to be tuned on the device.
 *
 * Distances are in torso lengths (mid-shoulder to mid-hip). Approximate adult proportions:
 * hip-to-ankle ≈ 1.6–1.8 torso lengths.
 */
object AsanaReferences {

    private fun knee(s: (BodySide) -> BodySide) = { f: BodyFeatures, side: BodySide -> f.knee(s(side)) }
    private val same: (BodySide) -> BodySide = { it }
    private val other: (BodySide) -> BodySide = { it.opposite }

    /** Mountain: standing tall, legs straight, feet together, arms by the sides. Frontal view. */
    val TADASANA = AsanaReference(
        Asana.TADASANA, "Face the camera",
        listOf(
            FeatureRule("left knee angle", 165f, 180f, 10f, DEG) { f, _ -> f.knee(BodySide.LEFT) },
            FeatureRule("right knee angle", 165f, 180f, 10f, DEG) { f, _ -> f.knee(BodySide.RIGHT) },
            FeatureRule("left hip angle", 165f, 180f, 10f, DEG) { f, _ -> f.hip(BodySide.LEFT) },
            FeatureRule("right hip angle", 165f, 180f, 10f, DEG) { f, _ -> f.hip(BodySide.RIGHT) },
            FeatureRule("torso tilt from vertical", 0f, 10f, 10f, DEG) { f, _ -> f.torsoTilt },
            FeatureRule("stance width", 0f, 0.5f, 0.3f, TORSO) { f, _ -> f.stanceWidth },
            FeatureRule("foot height difference", 0f, 0.12f, 0.12f, TORSO) { f, _ -> f.footLevelDiff },
            FeatureRule("left arm abduction", 0f, 35f, 20f, DEG, weight = 0.7f) { f, _ -> f.shoulderAbduction(BodySide.LEFT) },
            FeatureRule("right arm abduction", 0f, 35f, 20f, DEG, weight = 0.7f) { f, _ -> f.shoulderAbduction(BodySide.RIGHT) },
        ),
    )

    /** Tree: one straight standing leg, other foot lifted onto the standing leg, trunk upright. Side = lifted leg. */
    val TREE = AsanaReference(
        Asana.TREE, "Face the camera",
        listOf(
            FeatureRule("standing knee angle", 165f, 180f, 12f, DEG, extract = knee(other)),
            FeatureRule("lifted knee angle", 20f, 110f, 20f, DEG, extract = knee(same)),
            FeatureRule("lifted foot height", 0.35f, 2.0f, 0.15f, TORSO) { f, s -> f.footRaise(s) },
            FeatureRule("torso tilt from vertical", 0f, 15f, 10f, DEG) { f, _ -> f.torsoTilt },
            FeatureRule("standing hip angle", 160f, 180f, 12f, DEG, weight = 0.5f) { f, s -> f.hip(s.opposite) },
        ),
    )

    /** Warrior II: wide stance, front knee bent, back leg straight, trunk upright, arms horizontal. Side = bent leg. */
    val WARRIOR_II = AsanaReference(
        Asana.WARRIOR_II, "Face the camera, stance across the frame",
        listOf(
            FeatureRule("front knee angle", 80f, 130f, 15f, DEG, extract = knee(same)),
            FeatureRule("back knee angle", 160f, 180f, 12f, DEG, extract = knee(other)),
            FeatureRule("stance width", 1.2f, 3.5f, 0.3f, TORSO) { f, _ -> f.stanceWidth },
            FeatureRule("torso tilt from vertical", 0f, 20f, 10f, DEG) { f, _ -> f.torsoTilt },
            FeatureRule("left arm from horizontal", 0f, 20f, 15f, DEG, weight = 0.7f) { f, _ -> f.armElevation(BodySide.LEFT) },
            FeatureRule("right arm from horizontal", 0f, 20f, 15f, DEG, weight = 0.7f) { f, _ -> f.armElevation(BodySide.RIGHT) },
            FeatureRule("left elbow angle", 150f, 180f, 15f, DEG, weight = 0.5f) { f, _ -> f.elbowAngle(BodySide.LEFT) },
            FeatureRule("right elbow angle", 150f, 180f, 15f, DEG, weight = 0.5f) { f, _ -> f.elbowAngle(BodySide.RIGHT) },
        ),
    )

    /**
     * Chair: knees and hips flexed, trunk inclined forward, feet together.
     * Knee flexion is mostly toward the camera in a frontal view and is foreshortened in 2D, so
     * a SIDE view is required for reliable recognition. Arms are not required.
     */
    val CHAIR = AsanaReference(
        Asana.CHAIR, "Turn side-on to the camera",
        listOf(
            FeatureRule("left knee angle", 60f, 150f, 15f, DEG) { f, _ -> f.knee(BodySide.LEFT) },
            FeatureRule("right knee angle", 60f, 150f, 15f, DEG) { f, _ -> f.knee(BodySide.RIGHT) },
            FeatureRule("left hip angle", 50f, 140f, 15f, DEG) { f, _ -> f.hip(BodySide.LEFT) },
            FeatureRule("right hip angle", 50f, 140f, 15f, DEG) { f, _ -> f.hip(BodySide.RIGHT) },
            FeatureRule("torso tilt from vertical", 10f, 50f, 10f, DEG) { f, _ -> f.torsoTilt },
            FeatureRule("stance width", 0f, 0.6f, 0.3f, TORSO, weight = 0.5f) { f, _ -> f.stanceWidth },
            FeatureRule("foot height difference", 0f, 0.15f, 0.15f, TORSO, weight = 0.5f) { f, _ -> f.footLevelDiff },
        ),
    )

    /**
     * Triangle: wide stance, both legs straight, trunk tilted sideways over the front leg,
     * arms in a vertical line (lower hand toward the front leg). Side = front leg / lower hand.
     */
    val TRIANGLE = AsanaReference(
        Asana.TRIANGLE, "Face the camera, stance across the frame",
        listOf(
            FeatureRule("front knee angle", 155f, 180f, 12f, DEG, extract = knee(same)),
            FeatureRule("back knee angle", 155f, 180f, 12f, DEG, extract = knee(other)),
            FeatureRule("stance width", 1.0f, 3.5f, 0.3f, TORSO) { f, _ -> f.stanceWidth },
            FeatureRule("torso tilt from vertical", 30f, 100f, 15f, DEG) { f, _ -> f.torsoTilt },
            FeatureRule("arm line from vertical", 0f, 25f, 15f, DEG, weight = 0.8f) { f, _ -> f.armLineFromVertical },
            FeatureRule("lower hand drop", 1.0f, 4.0f, 0.4f, TORSO) { f, s -> f.wristDrop(s) },
        ),
    )

    val ALL = listOf(TADASANA, TREE, WARRIOR_II, CHAIR, TRIANGLE)
}
