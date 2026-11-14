package com.chinmai.yogasense.asana

import com.chinmai.yogasense.pose.Landmark
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/** 2D point in image pixel space (aspect-ratio corrected). */
data class Pt(val x: Float, val y: Float)

object Geometry {
    /** Interior angle ABC in degrees, 0..180. Null if a segment has zero length. */
    fun angleDeg(a: Pt, b: Pt, c: Pt): Float? {
        val v1x = a.x - b.x; val v1y = a.y - b.y
        val v2x = c.x - b.x; val v2y = c.y - b.y
        val n = hypot(v1x, v1y) * hypot(v2x, v2y)
        if (n < 1e-6f) return null
        val cos = ((v1x * v2x + v1y * v2y) / n).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    /** Angle of vector from→to relative to straight up (image -y), 0..180. */
    fun tiltFromVerticalDeg(from: Pt, to: Pt): Float {
        val dx = to.x - from.x; val dy = to.y - from.y
        return Math.toDegrees(atan2(abs(dx).toDouble(), (-dy).toDouble())).toFloat()
    }

    /** Absolute angle of a line from horizontal, 0..90. */
    fun fromHorizontalDeg(a: Pt, b: Pt): Float {
        val d = Math.toDegrees(atan2(abs(b.y - a.y).toDouble(), abs(b.x - a.x).toDouble())).toFloat()
        return d
    }

    fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
    fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}

/**
 * Body features computed from one [PoseFrame]. Pure Kotlin.
 *
 * - Coordinates are converted to pixel space (x * imageWidth, y * imageHeight) so angles are not
 *   distorted by the non-square image.
 * - Distances are divided by torso length (mid-shoulder to mid-hip) to cancel camera distance
 *   and body size.
 * - Any feature whose landmarks are below [threshold] visibility/presence returns null.
 * - LEFT/RIGHT always mean the subject's anatomical side (MediaPipe convention).
 */
class BodyFeatures(frame: PoseFrame, private val threshold: Float = Landmark.DEFAULT_CONFIDENCE) {
    private val w = if (frame.imageWidth > 0) frame.imageWidth.toFloat() else 1f
    private val h = if (frame.imageHeight > 0) frame.imageHeight.toFloat() else 1f
    private val pts: List<Pt?> = if (frame.hasPose) {
        frame.landmarks.map { if (it.isReliable(threshold)) Pt(it.x * w, it.y * h) else null }
    } else List(L.COUNT) { null }

    fun point(i: Int): Pt? = pts[i]

    val hasPose = frame.hasPose

    val lowConfidenceLandmarks: List<String> =
        if (!frame.hasPose) emptyList()
        else KEY_LANDMARKS.filter { pts[it] == null }.map { LANDMARK_NAMES[it] }

    private fun idx(side: BodySide, left: Int, right: Int) = if (side == BodySide.LEFT) left else right
    private fun shoulder(s: BodySide) = pts[idx(s, L.LEFT_SHOULDER, L.RIGHT_SHOULDER)]
    private fun elbow(s: BodySide) = pts[idx(s, L.LEFT_ELBOW, L.RIGHT_ELBOW)]
    private fun wrist(s: BodySide) = pts[idx(s, L.LEFT_WRIST, L.RIGHT_WRIST)]
    private fun hipPt(s: BodySide) = pts[idx(s, L.LEFT_HIP, L.RIGHT_HIP)]
    private fun kneePt(s: BodySide) = pts[idx(s, L.LEFT_KNEE, L.RIGHT_KNEE)]
    private fun ankle(s: BodySide) = pts[idx(s, L.LEFT_ANKLE, L.RIGHT_ANKLE)]

    private val midShoulder: Pt? = both(shoulder(BodySide.LEFT), shoulder(BodySide.RIGHT))?.let { Geometry.mid(it.first, it.second) }
    private val midHip: Pt? = both(hipPt(BodySide.LEFT), hipPt(BodySide.RIGHT))?.let { Geometry.mid(it.first, it.second) }

    /** Torso length in pixels; the normalisation scale for all distance features. */
    val torsoLength: Float? = both(midShoulder, midHip)
        ?.let { Geometry.dist(it.first, it.second) }
        ?.takeIf { it > 1f }

    /** Hip–knee–ankle angle; 180 = straight leg. */
    fun knee(s: BodySide): Float? = angle(hipPt(s), kneePt(s), ankle(s))

    /** Shoulder–hip–knee angle; 180 = hip extended, ~90 = seated/flexed. */
    fun hip(s: BodySide): Float? = angle(shoulder(s), hipPt(s), kneePt(s))

    /** Shoulder–elbow–wrist angle; 180 = straight arm. */
    fun elbowAngle(s: BodySide): Float? = angle(shoulder(s), elbow(s), wrist(s))

    /** Hip–shoulder–elbow angle; ~0 = arm by side, ~90 = arm out horizontally, ~180 = overhead. */
    fun shoulderAbduction(s: BodySide): Float? = angle(hipPt(s), shoulder(s), elbow(s))

    /** Shoulder→wrist line angle from horizontal (0 = arm horizontal). */
    fun armElevation(s: BodySide): Float? {
        val a = shoulder(s) ?: return null; val b = wrist(s) ?: return null
        if (Geometry.dist(a, b) < 1f) return null
        return Geometry.fromHorizontalDeg(a, b)
    }

    /** Mid-hip→mid-shoulder angle from vertical; 0 = upright trunk. */
    val torsoTilt: Float? = both(midHip, midShoulder)?.let { Geometry.tiltFromVerticalDeg(it.first, it.second) }

    /** Horizontal ankle separation / torso length. */
    val stanceWidth: Float? = norm(both(ankle(BodySide.LEFT), ankle(BodySide.RIGHT))?.let { abs(it.first.x - it.second.x) })

    /** |left ankle y − right ankle y| / torso length (0 = both feet on same level). */
    val footLevelDiff: Float? = norm(both(ankle(BodySide.LEFT), ankle(BodySide.RIGHT))?.let { abs(it.first.y - it.second.y) })

    /** How far side [s]'s ankle is ABOVE the other ankle, / torso length (positive = raised). */
    fun footRaise(s: BodySide): Float? =
        norm(both(ankle(s), ankle(s.opposite))?.let { it.second.y - it.first.y }, allowNegative = true)

    /** How far side [s]'s wrist is BELOW the other wrist, / torso length. */
    fun wristDrop(s: BodySide): Float? =
        norm(both(wrist(s), wrist(s.opposite))?.let { it.first.y - it.second.y }, allowNegative = true)

    /** Wrist-to-wrist line angle from vertical (0 = arms form a vertical line). */
    val armLineFromVertical: Float? = both(wrist(BodySide.LEFT), wrist(BodySide.RIGHT))
        ?.takeIf { Geometry.dist(it.first, it.second) > 1f }
        ?.let { 90f - Geometry.fromHorizontalDeg(it.first, it.second) }

    // ---- Assessment helpers (Phase 6; additive, not used by recognition) ----------------------

    /** Mid-shoulder / mid-hip points in pixel space (null if either side is unreliable). */
    val midShoulderPoint: Pt? get() = midShoulder
    val midHipPoint: Pt? get() = midHip

    /** Shoulder line angle from horizontal, 0..90 (0 = level). Mirror-invariant. */
    val shoulderLineTilt: Float? = both(shoulder(BodySide.LEFT), shoulder(BodySide.RIGHT))
        ?.takeIf { Geometry.dist(it.first, it.second) > 1f }?.let { Geometry.fromHorizontalDeg(it.first, it.second) }

    /** Hip line angle from horizontal, 0..90 (0 = level). Mirror-invariant. */
    val hipLineTilt: Float? = both(hipPt(BodySide.LEFT), hipPt(BodySide.RIGHT))
        ?.takeIf { Geometry.dist(it.first, it.second) > 1f }?.let { Geometry.fromHorizontalDeg(it.first, it.second) }

    /** Horizontal offset of mid-shoulder from mid-ankle / torso length (0 = shoulders stacked over feet). */
    val lateralLean: Float? = both(midShoulder, both(ankle(BodySide.LEFT), ankle(BodySide.RIGHT))?.let { Geometry.mid(it.first, it.second) })
        ?.let { norm(abs(it.first.x - it.second.x)) }

    /** Hip→knee line angle from straight down, 0..180 (0 = thigh vertical, 90 = thigh horizontal). */
    fun thighFromVertical(s: BodySide): Float? {
        val hp = hipPt(s) ?: return null; val kn = kneePt(s) ?: return null
        if (Geometry.dist(hp, kn) < 1f) return null
        return Math.toDegrees(atan2(abs(kn.x - hp.x).toDouble(), (kn.y - hp.y).toDouble())).toFloat()
    }

    private fun angle(a: Pt?, b: Pt?, c: Pt?): Float? =
        if (a == null || b == null || c == null) null else Geometry.angleDeg(a, b, c)

    private fun norm(v: Float?, allowNegative: Boolean = false): Float? {
        val t = torsoLength ?: return null
        v ?: return null
        return if (allowNegative) v / t else abs(v) / t
    }

    private fun <A : Any> both(a: A?, b: A?): Pair<A, A>? = if (a != null && b != null) a to b else null

    companion object {
        val KEY_LANDMARKS = intArrayOf(
            L.LEFT_SHOULDER, L.RIGHT_SHOULDER, L.LEFT_ELBOW, L.RIGHT_ELBOW, L.LEFT_WRIST, L.RIGHT_WRIST,
            L.LEFT_HIP, L.RIGHT_HIP, L.LEFT_KNEE, L.RIGHT_KNEE, L.LEFT_ANKLE, L.RIGHT_ANKLE,
        )
        val LANDMARK_NAMES = arrayOf(
            "nose", "left eye inner", "left eye", "left eye outer", "right eye inner", "right eye",
            "right eye outer", "left ear", "right ear", "mouth left", "mouth right",
            "left shoulder", "right shoulder", "left elbow", "right elbow", "left wrist", "right wrist",
            "left pinky", "right pinky", "left index", "right index", "left thumb", "right thumb",
            "left hip", "right hip", "left knee", "right knee", "left ankle", "right ankle",
            "left heel", "right heel", "left foot index", "right foot index",
        )
    }
}
