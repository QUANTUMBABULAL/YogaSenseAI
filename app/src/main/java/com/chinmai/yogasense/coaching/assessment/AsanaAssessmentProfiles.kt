package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide

/**
 * One alignment heuristic.
 *
 * Score: 1 inside [idealMin, idealMax]; falls linearly to 0 at [tolerance] beyond the band.
 * A metric whose joints are not reliable returns null from [extract] and is UNAVAILABLE —
 * it never produces a correction and is excluded from the score.
 */
class AlignmentRule(
    val id: String,
    val label: String,
    val idealMin: Float,
    val idealMax: Float,
    val tolerance: Float,
    val unit: String,
    val weight: Float,
    val lowCorrection: String?,     // shown/spoken when value < idealMin
    val highCorrection: String?,    // shown/spoken when value > idealMax
    val rationale: String,
    val extract: (BodyFeatures, BodySide) -> Float?,
) {
    fun score(v: Float): Float {
        val d = when { v < idealMin -> idealMin - v; v > idealMax -> v - idealMax; else -> return 1f }
        return (1f - d / tolerance).coerceIn(0f, 1f)
    }
}

class AssessmentProfile(val asana: Asana, val rules: List<AlignmentRule>, val notObservable: List<String> = emptyList())

private const val DEG = "°"
private const val TORSO = "× torso"

/**
 * PROTOTYPE HEURISTICS — engineering estimates for a single 2D camera, chosen from common
 * beginner cues. They are NOT medically or scientifically validated and must be tuned with real
 * people. All values are joint angles (scale-free) or distances divided by torso length, so they
 * do not depend on camera distance or body size. Line tilts use absolute angles, so mirroring the
 * image does not change them; sided metrics use the side reported by recognition.
 *
 * Side convention (from recognition): Tree = lifted leg, Warrior II = bent front leg,
 * Triangle = front leg / lower hand.
 */
object AsanaAssessmentProfiles {
    private fun max2(a: Float?, b: Float?) = if (a == null || b == null) null else maxOf(a, b)
    private fun min2(a: Float?, b: Float?) = if (a == null || b == null) null else minOf(a, b)
    private fun avg2(a: Float?, b: Float?) = if (a == null || b == null) null else (a + b) / 2f

    val TADASANA = AssessmentProfile(
        Asana.TADASANA,
        listOf(
            AlignmentRule("torso_upright", "Torso upright", 0f, 5f, 10f, DEG, 1.0f, null, "Stand tall and keep your torso upright",
                "Trunk tilt from vertical; Mountain Pose is an upright neutral stance.") { f, _ -> f.torsoTilt },
            AlignmentRule("shoulders_level", "Shoulders level", 0f, 4f, 8f, DEG, 0.8f, null, "Level your shoulders",
                "Shoulder line from horizontal; uneven shoulders indicate leaning or hitching.") { f, _ -> f.shoulderLineTilt },
            AlignmentRule("hips_level", "Hips level", 0f, 4f, 8f, DEG, 0.8f, null, "Keep your hips level",
                "Hip line from horizontal; uneven hips indicate shifting weight to one leg.") { f, _ -> f.hipLineTilt },
            AlignmentRule("legs_straight", "Both legs straight", 170f, 180f, 15f, DEG, 0.7f, "Straighten both legs without locking your knees", null,
                "Smaller of the two hip–knee–ankle angles; both legs are extended in Mountain Pose.") { f, _ -> min2(f.knee(BodySide.LEFT), f.knee(BodySide.RIGHT)) },
            AlignmentRule("weight_centred", "Weight centred", 0f, 0.08f, 0.17f, TORSO, 0.8f, null, "Centre your weight between both feet",
                "Horizontal offset of mid-shoulder from mid-ankle; stacked = balanced weight (left/right symmetry).") { f, _ -> f.lateralLean },
        ),
    )

    val TREE = AssessmentProfile(
        Asana.TREE,
        listOf(
            AlignmentRule("standing_leg", "Standing leg straight", 168f, 180f, 15f, DEG, 1.0f, "Keep your standing leg strong and straight", null,
                "Hip–knee–ankle angle of the supporting leg.") { f, s -> f.knee(s.opposite) },
            AlignmentRule("knee_open", "Bent knee opened out", 35f, 90f, 25f, DEG, 0.8f, "Let your bent knee open out to the side", null,
                "Lifted thigh angle from vertical (2D proxy for hip external rotation / knee pointing outward).") { f, s -> f.thighFromVertical(s) },
            AlignmentRule("foot_height", "Lifted foot height", 0.5f, 2.0f, 0.3f, TORSO, 0.6f, "Place your foot higher on the standing leg — calf or thigh, not the knee", null,
                "How far the lifted ankle is above the standing ankle.") { f, s -> f.footRaise(s) },
            AlignmentRule("torso_upright", "Torso upright", 0f, 8f, 12f, DEG, 1.0f, null, "Lift your chest and keep your torso upright",
                "Trunk tilt from vertical; leaning is a common balance compensation.") { f, _ -> f.torsoTilt },
            AlignmentRule("hips_level", "Hips level", 0f, 6f, 10f, DEG, 0.7f, null, "Keep your hips level",
                "Hip line from horizontal; the standing hip tends to push out.") { f, _ -> f.hipLineTilt },
        ),
    )

    val WARRIOR_II = AssessmentProfile(
        Asana.WARRIOR_II,
        listOf(
            AlignmentRule("front_knee", "Front knee bend", 85f, 110f, 25f, DEG,
                1.0f, "Ease your front knee back so it stays over your ankle", "Bend your front knee a little more",
                "Front hip–knee–ankle angle; Warrior II aims toward a right angle.") { f, s -> f.knee(s) },
            AlignmentRule("back_leg", "Back leg straight", 165f, 180f, 15f, DEG, 0.8f, "Straighten your back leg", null,
                "Hip–knee–ankle angle of the back leg.") { f, s -> f.knee(s.opposite) },
            AlignmentRule("arms_level", "Arms at shoulder height", 0f, 10f, 20f, DEG, 0.8f, null, "Lift your arms to shoulder height",
                "Larger of the two shoulder→wrist angles from horizontal.") { f, _ -> max2(f.armElevation(BodySide.LEFT), f.armElevation(BodySide.RIGHT)) },
            AlignmentRule("arms_extended", "Arms extended", 160f, 180f, 25f, DEG, 0.6f, "Extend both arms fully", null,
                "Smaller of the two elbow angles.") { f, _ -> min2(f.elbowAngle(BodySide.LEFT), f.elbowAngle(BodySide.RIGHT)) },
            AlignmentRule("torso_upright", "Torso upright", 0f, 8f, 12f, DEG, 1.0f, null, "Keep your torso upright between your legs",
                "Trunk tilt; leaning over the front leg is the most common error.") { f, _ -> f.torsoTilt },
            AlignmentRule("shoulders_level", "Shoulders level", 0f, 6f, 10f, DEG, 0.5f, null, "Relax and level your shoulders",
                "Shoulder line from horizontal.") { f, _ -> f.shoulderLineTilt },
        ),
        notObservable = listOf("Hip and torso orientation toward the side (needs depth)"),
    )

    /** Chair is assessed from the SIDE view (see catalogue camera guidance). */
    val CHAIR = AssessmentProfile(
        Asana.CHAIR,
        listOf(
            AlignmentRule("knee_bend", "Knee bend", 90f, 130f, 25f, DEG,
                1.0f, "Come up slightly and ease the knee bend", "Bend your knees a little more",
                "Average hip–knee–ankle angle (side view).") { f, _ -> avg2(f.knee(BodySide.LEFT), f.knee(BodySide.RIGHT)) },
            AlignmentRule("hips_back", "Hips sitting back", 75f, 120f, 25f, DEG,
                0.9f, "Lift your chest a little", "Sit your hips further back",
                "Average shoulder–hip–knee angle; smaller = deeper hip hinge.") { f, _ -> avg2(f.hip(BodySide.LEFT), f.hip(BodySide.RIGHT)) },
            AlignmentRule("torso_lean", "Torso lean", 15f, 40f, 15f, DEG,
                0.9f, "Lean your chest slightly forward", "Lift your chest — don't fold too far forward",
                "Trunk tilt from vertical; a moderate forward lean balances the hips sitting back.") { f, _ -> f.torsoTilt },
            AlignmentRule("arms_up", "Arms raised", 145f, 180f, 40f, DEG, 0.6f, "Reach your arms up alongside your ears", null,
                "Smaller hip–shoulder–elbow angle; ~180° = arms overhead.") { f, _ -> min2(f.shoulderAbduction(BodySide.LEFT), f.shoulderAbduction(BodySide.RIGHT)) },
        ),
        notObservable = listOf("Knee position relative to toes (feet length not tracked reliably)"),
    )

    val TRIANGLE = AssessmentProfile(
        Asana.TRIANGLE,
        listOf(
            AlignmentRule("side_bend", "Side bend", 50f, 95f, 25f, DEG, 1.0f, "Lengthen further into the side bend", "Come up slightly — keep length in your side body",
                "Trunk tilt toward the front leg.") { f, _ -> f.torsoTilt },
            AlignmentRule("arm_line", "Arms in one line", 0f, 12f, 20f, DEG, 0.9f, null, "Stack your top arm directly over your shoulder",
                "Wrist-to-wrist line from vertical.") { f, _ -> f.armLineFromVertical },
            AlignmentRule("legs_straight", "Legs straight", 165f, 180f, 15f, DEG, 0.8f, "Keep both legs long and straight", null,
                "Smaller of the two knee angles.") { f, _ -> min2(f.knee(BodySide.LEFT), f.knee(BodySide.RIGHT)) },
            AlignmentRule("arms_extended", "Arms extended", 160f, 180f, 25f, DEG, 0.6f, "Extend both arms fully", null,
                "Smaller of the two elbow angles.") { f, _ -> min2(f.elbowAngle(BodySide.LEFT), f.elbowAngle(BodySide.RIGHT)) },
        ),
        notObservable = listOf("Hip and chest opening toward the camera (needs depth)"),
    )

    val ALL: Map<Asana, AssessmentProfile> = listOf(TADASANA, TREE, WARRIOR_II, CHAIR, TRIANGLE).associateBy { it.asana }
}
