package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.pose.CameraOrientation
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L

/** How a variant picks the body side it measures. */
enum class SideMode {
    /** The side reported by recognition (Tree = lifted leg, Warrior II/Triangle = front leg). */
    RECOGNISED,
    /** Whichever side is fully reliable (LEFT tried first). Used for profile views and one-arm fallbacks. */
    EITHER,
}

/**
 * One way to measure a metric: the joints it needs (all must be RELIABLE), the camera
 * orientations in which the 2D geometry is meaningful, and the extraction. Variants are tried in
 * order; the first measurable one is used.
 */
class MetricVariant(
    val views: Set<CameraOrientation>,
    val joints: (BodySide) -> IntArray,
    val sideMode: SideMode = SideMode.RECOGNISED,
    /** Measures one side only because the other side is hidden (reported to the user). */
    val oneSideOnly: Boolean = false,
    val extract: (BodyFeatures, BodySide) -> Float?,
)

/**
 * One alignment heuristic.
 *
 * Score: 1 inside [idealMin, idealMax]; falls linearly to 0 at [tolerance] beyond the band.
 * A metric with no measurable variant is UNAVAILABLE (with a reason) — it never produces a
 * correction, is excluded from the score, and counts as NOT covered.
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
    /** Key metric for this asana: missing it lowers confidence and may prompt a camera adjustment. */
    val critical: Boolean = false,
    val variants: List<MetricVariant>,
) {
    fun score(v: Float): Float {
        val d = when { v < idealMin -> idealMin - v; v > idealMax -> v - idealMax; else -> return 1f }
        return (1f - d / tolerance).coerceIn(0f, 1f)
    }
}

class AssessmentProfile(
    val asana: Asana,
    val rules: List<AlignmentRule>,
    val notObservable: List<String> = emptyList(),
    /** Camera orientation the asana is designed to be assessed from (matches the catalogue guidance). */
    val preferredView: CameraOrientation = CameraOrientation.FRONT,
)

private const val DEG = "°"
private const val TORSO = "× torso"

/**
 * PROTOTYPE HEURISTICS — engineering estimates for a single 2D camera, chosen from common
 * beginner cues. They are NOT medically or scientifically validated and must be tuned with real
 * people. All values are joint angles (scale-free) or distances divided by torso length.
 *
 * Phase 8 visibility profiles: each metric lists the joints it needs and where its geometry is
 * meaningful. Examples: shoulder/hip LEVEL lines are meaningless in profile (the two points
 * overlap), so they are FRONT-only; Chair knee/hip angles are foreshortened when facing the
 * camera, so they are SIDE-only; in a profile view the trunk tilt can be measured from the
 * visible side's shoulder→hip line when the far side is hidden.
 *
 * Side convention (from recognition): Tree = lifted leg, Warrior II = bent front leg,
 * Triangle = front leg / lower hand.
 */
object AsanaAssessmentProfiles {
    private val ANY = setOf(CameraOrientation.FRONT, CameraOrientation.SIDE, CameraOrientation.ANGLED, CameraOrientation.UNKNOWN)
    private val FRONT = setOf(CameraOrientation.FRONT, CameraOrientation.ANGLED, CameraOrientation.UNKNOWN)
    private val SIDE = setOf(CameraOrientation.SIDE, CameraOrientation.ANGLED, CameraOrientation.UNKNOWN)

    private fun sh(s: BodySide) = if (s == BodySide.LEFT) L.LEFT_SHOULDER else L.RIGHT_SHOULDER
    private fun el(s: BodySide) = if (s == BodySide.LEFT) L.LEFT_ELBOW else L.RIGHT_ELBOW
    private fun wr(s: BodySide) = if (s == BodySide.LEFT) L.LEFT_WRIST else L.RIGHT_WRIST
    private fun hp(s: BodySide) = if (s == BodySide.LEFT) L.LEFT_HIP else L.RIGHT_HIP
    private fun kn(s: BodySide) = if (s == BodySide.LEFT) L.LEFT_KNEE else L.RIGHT_KNEE
    private fun an(s: BodySide) = if (s == BodySide.LEFT) L.LEFT_ANKLE else L.RIGHT_ANKLE

    private val MID = intArrayOf(L.LEFT_SHOULDER, L.RIGHT_SHOULDER, L.LEFT_HIP, L.RIGHT_HIP)
    private val SHOULDERS = intArrayOf(L.LEFT_SHOULDER, L.RIGHT_SHOULDER)
    private val HIPS = intArrayOf(L.LEFT_HIP, L.RIGHT_HIP)
    private val ANKLES = intArrayOf(L.LEFT_ANKLE, L.RIGHT_ANKLE)
    private val WRISTS = intArrayOf(L.LEFT_WRIST, L.RIGHT_WRIST)
    private fun leg(s: BodySide) = intArrayOf(hp(s), kn(s), an(s))
    private fun arm(s: BodySide) = intArrayOf(sh(s), el(s), wr(s))
    private fun trunkSide(s: BodySide) = intArrayOf(sh(s), hp(s))
    private val BOTH_LEGS = leg(BodySide.LEFT) + leg(BodySide.RIGHT)
    private val BOTH_ARMS = arm(BodySide.LEFT) + arm(BodySide.RIGHT)

    private fun max2(a: Float?, b: Float?) = if (a == null || b == null) null else maxOf(a, b)
    private fun min2(a: Float?, b: Float?) = if (a == null || b == null) null else minOf(a, b)
    private fun avg2(a: Float?, b: Float?) = if (a == null || b == null) null else (a + b) / 2f

    /** Trunk tilt: midline when both sides are reliable; in a profile view, the visible side's line. */
    private fun trunkTiltVariants(midlineViews: Set<CameraOrientation> = ANY, sideViews: Set<CameraOrientation> = SIDE) = listOf(
        MetricVariant(midlineViews, { MID }) { f, _ -> f.torsoTilt },
        MetricVariant(sideViews, ::trunkSide, SideMode.EITHER) { f, s -> f.torsoTiltSide(s) },
    )

    val TADASANA = AssessmentProfile(
        Asana.TADASANA,
        listOf(
            AlignmentRule("torso_upright", "Torso upright", 0f, 5f, 10f, DEG, 1.0f, null, "Stand tall and keep your torso upright",
                "Trunk tilt from vertical; Mountain Pose is an upright neutral stance.", critical = true, variants = trunkTiltVariants()),
            AlignmentRule("shoulders_level", "Shoulders level", 0f, 4f, 8f, DEG, 0.8f, null, "Level your shoulders",
                "Shoulder line from horizontal; uneven shoulders indicate leaning or hitching.",
                variants = listOf(MetricVariant(FRONT, { SHOULDERS }) { f, _ -> f.shoulderLineTilt })),
            AlignmentRule("hips_level", "Hips level", 0f, 4f, 8f, DEG, 0.8f, null, "Keep your hips level",
                "Hip line from horizontal; uneven hips indicate shifting weight to one leg.",
                variants = listOf(MetricVariant(FRONT, { HIPS }) { f, _ -> f.hipLineTilt })),
            AlignmentRule("legs_straight", "Both legs straight", 170f, 180f, 15f, DEG, 0.7f, "Straighten both legs without locking your knees", null,
                "Smaller of the two hip–knee–ankle angles; both legs are extended in Mountain Pose.", critical = true,
                variants = listOf(
                    MetricVariant(ANY, { BOTH_LEGS }) { f, _ -> min2(f.knee(BodySide.LEFT), f.knee(BodySide.RIGHT)) },
                    MetricVariant(ANY, ::leg, SideMode.EITHER, oneSideOnly = true) { f, s -> f.knee(s) },
                )),
            AlignmentRule("weight_centred", "Weight centred", 0f, 0.08f, 0.17f, TORSO, 0.8f, null, "Centre your weight between both feet",
                "Horizontal offset of mid-shoulder from mid-ankle; stacked = balanced weight (left/right symmetry).",
                variants = listOf(MetricVariant(FRONT, { MID + ANKLES }) { f, _ -> f.lateralLean })),
        ),
    )

    val TREE = AssessmentProfile(
        Asana.TREE,
        listOf(
            AlignmentRule("standing_leg", "Standing leg straight", 168f, 180f, 15f, DEG, 1.0f, "Keep your standing leg strong and straight", null,
                "Hip–knee–ankle angle of the supporting leg.", critical = true,
                variants = listOf(MetricVariant(ANY, { s -> leg(s.opposite) }) { f, s -> f.knee(s.opposite) })),
            AlignmentRule("knee_open", "Bent knee opened out", 35f, 90f, 25f, DEG, 0.8f, "Let your bent knee open out to the side", null,
                "Lifted thigh angle from vertical (2D proxy for hip external rotation / knee pointing outward). Front view only.",
                variants = listOf(MetricVariant(FRONT, { s -> intArrayOf(hp(s), kn(s)) }) { f, s -> f.thighFromVertical(s) })),
            AlignmentRule("foot_height", "Lifted foot height", 0.5f, 2.0f, 0.3f, TORSO, 0.6f, "Place your foot higher on the standing leg — calf or thigh, not the knee", null,
                "How far the lifted ankle is above the standing ankle.",
                variants = listOf(
                    MetricVariant(ANY, { MID + ANKLES }) { f, s -> f.footRaise(s) },
                    MetricVariant(ANY, { s -> ANKLES + trunkSide(s.opposite) }) { f, s -> f.footRaiseSideNorm(s, s.opposite) },
                )),
            AlignmentRule("torso_upright", "Torso upright", 0f, 8f, 12f, DEG, 1.0f, null, "Lift your chest and keep your torso upright",
                "Trunk tilt from vertical; leaning is a common balance compensation.", critical = true, variants = trunkTiltVariants()),
            AlignmentRule("hips_level", "Hips level", 0f, 6f, 10f, DEG, 0.7f, null, "Keep your hips level",
                "Hip line from horizontal; the standing hip tends to push out.",
                variants = listOf(MetricVariant(FRONT, { HIPS }) { f, _ -> f.hipLineTilt })),
        ),
    )

    val WARRIOR_II = AssessmentProfile(
        Asana.WARRIOR_II,
        listOf(
            AlignmentRule("front_knee", "Front knee bend", 85f, 110f, 25f, DEG,
                1.0f, "Ease your front knee back so it stays over your ankle", "Bend your front knee a little more",
                "Front hip–knee–ankle angle; Warrior II aims toward a right angle. Foreshortened in profile, so front view only.", critical = true,
                variants = listOf(MetricVariant(FRONT, ::leg) { f, s -> f.knee(s) })),
            AlignmentRule("back_leg", "Back leg straight", 165f, 180f, 15f, DEG, 0.8f, "Straighten your back leg", null,
                "Hip–knee–ankle angle of the back leg.",
                variants = listOf(MetricVariant(FRONT, { s -> leg(s.opposite) }) { f, s -> f.knee(s.opposite) })),
            AlignmentRule("arms_level", "Arms at shoulder height", 0f, 10f, 20f, DEG, 0.8f, null, "Lift your arms to shoulder height",
                "Larger of the two shoulder→wrist angles from horizontal; one arm if the other is hidden.",
                variants = listOf(
                    MetricVariant(FRONT, { SHOULDERS + WRISTS }) { f, _ -> max2(f.armElevation(BodySide.LEFT), f.armElevation(BodySide.RIGHT)) },
                    MetricVariant(FRONT, { s -> intArrayOf(sh(s), wr(s)) }, SideMode.EITHER, oneSideOnly = true) { f, s -> f.armElevation(s) },
                )),
            AlignmentRule("arms_extended", "Arms extended", 160f, 180f, 25f, DEG, 0.6f, "Extend both arms fully", null,
                "Smaller of the two elbow angles; one arm if the other is hidden.",
                variants = listOf(
                    MetricVariant(FRONT, { BOTH_ARMS }) { f, _ -> min2(f.elbowAngle(BodySide.LEFT), f.elbowAngle(BodySide.RIGHT)) },
                    MetricVariant(FRONT, ::arm, SideMode.EITHER, oneSideOnly = true) { f, s -> f.elbowAngle(s) },
                )),
            AlignmentRule("torso_upright", "Torso upright", 0f, 8f, 12f, DEG, 1.0f, null, "Keep your torso upright between your legs",
                "Trunk tilt; leaning over the front leg is the most common error.", critical = true, variants = trunkTiltVariants()),
            AlignmentRule("shoulders_level", "Shoulders level", 0f, 6f, 10f, DEG, 0.5f, null, "Relax and level your shoulders",
                "Shoulder line from horizontal.",
                variants = listOf(MetricVariant(FRONT, { SHOULDERS }) { f, _ -> f.shoulderLineTilt })),
        ),
        notObservable = listOf("Hip and torso orientation toward the side (needs depth)"),
    )

    /** Chair is assessed from the SIDE view (see catalogue camera guidance). */
    val CHAIR = AssessmentProfile(
        Asana.CHAIR,
        listOf(
            AlignmentRule("knee_bend", "Knee bend", 90f, 130f, 25f, DEG,
                1.0f, "Come up slightly and ease the knee bend", "Bend your knees a little more",
                "Average hip–knee–ankle angle (side view); the visible leg when the far leg is hidden.", critical = true,
                variants = listOf(
                    MetricVariant(SIDE, { BOTH_LEGS }) { f, _ -> avg2(f.knee(BodySide.LEFT), f.knee(BodySide.RIGHT)) },
                    MetricVariant(SIDE, ::leg, SideMode.EITHER, oneSideOnly = true) { f, s -> f.knee(s) },
                )),
            AlignmentRule("hips_back", "Hips sitting back", 75f, 120f, 25f, DEG,
                0.9f, "Lift your chest a little", "Sit your hips further back",
                "Average shoulder–hip–knee angle; smaller = deeper hip hinge.", critical = true,
                variants = listOf(
                    MetricVariant(SIDE, { MID + intArrayOf(L.LEFT_KNEE, L.RIGHT_KNEE) }) { f, _ -> avg2(f.hip(BodySide.LEFT), f.hip(BodySide.RIGHT)) },
                    MetricVariant(SIDE, { s -> intArrayOf(sh(s), hp(s), kn(s)) }, SideMode.EITHER, oneSideOnly = true) { f, s -> f.hip(s) },
                )),
            AlignmentRule("torso_lean", "Torso lean", 15f, 40f, 15f, DEG,
                0.9f, "Lean your chest slightly forward", "Lift your chest — don't fold too far forward",
                "Trunk tilt from vertical; a moderate forward lean balances the hips sitting back.",
                variants = trunkTiltVariants(midlineViews = SIDE)),
            AlignmentRule("arms_up", "Arms raised", 145f, 180f, 40f, DEG, 0.6f, "Reach your arms up alongside your ears", null,
                "Smaller hip–shoulder–elbow angle; ~180° = arms overhead.",
                variants = listOf(
                    MetricVariant(ANY, { MID + intArrayOf(L.LEFT_ELBOW, L.RIGHT_ELBOW) }) { f, _ -> min2(f.shoulderAbduction(BodySide.LEFT), f.shoulderAbduction(BodySide.RIGHT)) },
                    MetricVariant(ANY, { s -> intArrayOf(hp(s), sh(s), el(s)) }, SideMode.EITHER, oneSideOnly = true) { f, s -> f.shoulderAbduction(s) },
                )),
        ),
        notObservable = listOf("Knee position relative to toes (feet length not tracked reliably)"),
        preferredView = CameraOrientation.SIDE,
    )

    val TRIANGLE = AssessmentProfile(
        Asana.TRIANGLE,
        listOf(
            AlignmentRule("side_bend", "Side bend", 50f, 95f, 25f, DEG, 1.0f, "Lengthen further into the side bend", "Come up slightly — keep length in your side body",
                "Trunk tilt toward the front leg.", critical = true,
                variants = listOf(
                    MetricVariant(FRONT, { MID }) { f, _ -> f.torsoTilt },
                    MetricVariant(FRONT, ::trunkSide, SideMode.EITHER, oneSideOnly = true) { f, s -> f.torsoTiltSide(s) },
                )),
            AlignmentRule("arm_line", "Arms in one line", 0f, 12f, 20f, DEG, 0.9f, null, "Stack your top arm directly over your shoulder",
                "Wrist-to-wrist line from vertical.",
                variants = listOf(MetricVariant(FRONT, { WRISTS }) { f, _ -> f.armLineFromVertical })),
            AlignmentRule("legs_straight", "Legs straight", 165f, 180f, 15f, DEG, 0.8f, "Keep both legs long and straight", null,
                "Smaller of the two knee angles; one leg if the other is hidden.", critical = true,
                variants = listOf(
                    MetricVariant(FRONT, { BOTH_LEGS }) { f, _ -> min2(f.knee(BodySide.LEFT), f.knee(BodySide.RIGHT)) },
                    MetricVariant(FRONT, ::leg, SideMode.EITHER, oneSideOnly = true) { f, s -> f.knee(s) },
                )),
            AlignmentRule("arms_extended", "Arms extended", 160f, 180f, 25f, DEG, 0.6f, "Extend both arms fully", null,
                "Smaller of the two elbow angles; one arm if the other is hidden.",
                variants = listOf(
                    MetricVariant(FRONT, { BOTH_ARMS }) { f, _ -> min2(f.elbowAngle(BodySide.LEFT), f.elbowAngle(BodySide.RIGHT)) },
                    MetricVariant(FRONT, ::arm, SideMode.EITHER, oneSideOnly = true) { f, s -> f.elbowAngle(s) },
                )),
        ),
        notObservable = listOf("Hip and chest opening toward the camera (needs depth)"),
    )

    val ALL: Map<Asana, AssessmentProfile> = listOf(TADASANA, TREE, WARRIOR_II, CHAIR, TRIANGLE).associateBy { it.asana }
}
