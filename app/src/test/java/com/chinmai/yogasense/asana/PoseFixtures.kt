package com.chinmai.yogasense.asana

import com.chinmai.yogasense.pose.Landmark
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic, hand-constructed skeletons (NOT camera data) in a 480x640 portrait image.
 * Subject faces the camera unless noted, so the subject's LEFT side appears at larger x.
 * Torso length (mid-shoulder to mid-hip) is 120 px; thigh/shin 100 px; arm ~135 px.
 */
object PoseFixtures {
    const val W = 480
    const val H = 640

    /** Image-x direction of the subject's [side] when facing the camera. */
    fun dir(side: BodySide) = if (side == BodySide.LEFT) 1f else -1f

    class Skeleton(val joints: MutableMap<Int, Pt>) {
        val vis = FloatArray(L.COUNT) { 0.95f }

        fun occlude(vararg idx: Int, visibility: Float = 0.1f) = apply { idx.forEach { vis[it] = visibility } }
        /** Phase 8: per-landmark presence; NaN = same as visibility (the original fixture behaviour). */
        val presence = FloatArray(L.COUNT) { Float.NaN }
        /** Hidden but inside the image, as MediaPipe reports an occluded joint: low visibility, high presence. */
        fun hide(vararg idx: Int) = apply { idx.forEach { vis[it] = 0.1f; presence[it] = 0.95f } }
        fun allVisibility(v: Float) = apply { vis.fill(v) }

        /** Uniform scale about image centre + shift: simulates camera distance/position. */
        fun transform(scale: Float, dx: Float = 0f, dy: Float = 0f) = apply {
            joints.replaceAll { _, p -> Pt((p.x - W / 2f) * scale + W / 2f + dx, (p.y - H / 2f) * scale + H / 2f + dy) }
        }

        fun frame(ts: Long = 0L): PoseFrame {
            val pts = Array(L.COUNT) { Pt(0f, 0f) }
            joints.forEach { (i, p) -> pts[i] = p }
            val nose = joints.getValue(L.NOSE)
            // Face around the nose.
            listOf(L.LEFT_EYE_INNER to (4f to -6f), L.LEFT_EYE to (8f to -7f), L.LEFT_EYE_OUTER to (12f to -7f),
                L.RIGHT_EYE_INNER to (-4f to -6f), L.RIGHT_EYE to (-8f to -7f), L.RIGHT_EYE_OUTER to (-12f to -7f),
                L.LEFT_EAR to (18f to -2f), L.RIGHT_EAR to (-18f to -2f), L.MOUTH_LEFT to (5f to 8f), L.MOUTH_RIGHT to (-5f to 8f),
            ).forEach { (i, o) -> pts[i] = Pt(nose.x + o.first, nose.y + o.second) }
            // Hands continue the forearm direction.
            fun hand(elbow: Int, wrist: Int, vararg tips: Int) {
                val e = pts[elbow]; val w = pts[wrist]
                val dx = (w.x - e.x) * 0.15f; val dy = (w.y - e.y) * 0.15f
                tips.forEachIndexed { k, t -> pts[t] = Pt(w.x + dx + (k - 1) * 3f, w.y + dy + (k - 1) * 3f) }
            }
            hand(L.LEFT_ELBOW, L.LEFT_WRIST, L.LEFT_PINKY, L.LEFT_INDEX, L.LEFT_THUMB)
            hand(L.RIGHT_ELBOW, L.RIGHT_WRIST, L.RIGHT_PINKY, L.RIGHT_INDEX, L.RIGHT_THUMB)
            // Feet below the ankles.
            fun foot(ankle: Int, heel: Int, toe: Int) {
                val a = pts[ankle]; pts[heel] = Pt(a.x, a.y + 8f); pts[toe] = Pt(a.x, a.y + 14f)
            }
            foot(L.LEFT_ANKLE, L.LEFT_HEEL, L.LEFT_FOOT_INDEX)
            foot(L.RIGHT_ANKLE, L.RIGHT_HEEL, L.RIGHT_FOOT_INDEX)
            val lms = pts.mapIndexed { i, p -> Landmark(p.x / W, p.y / H, 0f, vis[i], if (presence[i].isNaN()) vis[i] else presence[i]) }
            return PoseFrame(lms, ts, W, H)
        }
    }

    private fun polar(from: Pt, len: Float, deg: Float): Pt {
        val r = Math.toRadians(deg.toDouble())
        return Pt(from.x + len * cos(r).toFloat(), from.y + len * sin(r).toFloat())
    }

    private fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)

    /** Standing, feet together. [armsHorizontal] = T-pose (not Tadasana). */
    fun tadasana(armsHorizontal: Boolean = false): Skeleton {
        val j = mutableMapOf(
            L.NOSE to Pt(240f, 170f),
            L.LEFT_SHOULDER to Pt(275f, 215f), L.RIGHT_SHOULDER to Pt(205f, 215f),
            L.LEFT_HIP to Pt(260f, 335f), L.RIGHT_HIP to Pt(220f, 335f),
            L.LEFT_KNEE to Pt(260f, 435f), L.RIGHT_KNEE to Pt(220f, 435f),
            L.LEFT_ANKLE to Pt(260f, 535f), L.RIGHT_ANKLE to Pt(220f, 535f),
        )
        if (armsHorizontal) {
            j[L.LEFT_ELBOW] = Pt(345f, 215f); j[L.LEFT_WRIST] = Pt(410f, 215f)
            j[L.RIGHT_ELBOW] = Pt(135f, 215f); j[L.RIGHT_WRIST] = Pt(70f, 215f)
        } else {
            j[L.LEFT_ELBOW] = Pt(282f, 285f); j[L.LEFT_WRIST] = Pt(286f, 350f)
            j[L.RIGHT_ELBOW] = Pt(198f, 285f); j[L.RIGHT_WRIST] = Pt(194f, 350f)
        }
        return Skeleton(j)
    }

    /**
     * Tree with [side] = lifted leg. Foot rests on the inner standing leg, hands at the chest.
     * [lowFoot] = foot barely off the floor with a slight knee bend (should NOT be Tree).
     */
    fun tree(side: BodySide, lowFoot: Boolean = false): Skeleton {
        val s = tadasana()
        val d = dir(side)
        val hipIdx = if (side == BodySide.LEFT) L.LEFT_HIP else L.RIGHT_HIP
        val kneeIdx = if (side == BodySide.LEFT) L.LEFT_KNEE else L.RIGHT_KNEE
        val ankleIdx = if (side == BodySide.LEFT) L.LEFT_ANKLE else L.RIGHT_ANKLE
        val standingKnee = s.joints.getValue(if (side == BodySide.LEFT) L.RIGHT_KNEE else L.LEFT_KNEE)
        val hip = s.joints.getValue(hipIdx)
        if (lowFoot) {
            s.joints[kneeIdx] = Pt(hip.x + d * 20f, hip.y + 95f)
            s.joints[ankleIdx] = Pt(hip.x + d * 10f, 523f)
        } else {
            s.joints[kneeIdx] = Pt(hip.x + d * 87f, hip.y + 50f)
            s.joints[ankleIdx] = Pt(standingKnee.x + d * 10f, standingKnee.y - 30f)
        }
        // Hands together at chest.
        s.joints[L.LEFT_ELBOW] = Pt(290f, 280f); s.joints[L.LEFT_WRIST] = Pt(245f, 270f)
        s.joints[L.RIGHT_ELBOW] = Pt(190f, 280f); s.joints[L.RIGHT_WRIST] = Pt(235f, 270f)
        return s
    }

    /** Warrior II with [side] = bent front leg. [frontKneeStraight] = wide-legged stand (NOT Warrior II). */
    fun warrior2(side: BodySide, frontKneeStraight: Boolean = false): Skeleton {
        val d = dir(side)
        val hipF = if (side == BodySide.LEFT) Pt(260f, 330f) else Pt(220f, 330f)
        val hipB = if (side == BodySide.LEFT) Pt(220f, 330f) else Pt(260f, 330f)
        val ankleF: Pt; val kneeF: Pt
        if (frontKneeStraight) {
            ankleF = Pt(hipF.x + d * 100f, hipF.y + 173f); kneeF = mid(hipF, ankleF)
        } else {
            kneeF = Pt(hipF.x + d * 90f, hipF.y + 45f); ankleF = Pt(kneeF.x, kneeF.y + 100f)
        }
        val ankleB = Pt(hipB.x - d * 100f, hipB.y + 173f)
        val kneeB = mid(hipB, ankleB)
        val front = side; val back = side.opposite
        val j = mutableMapOf(
            L.NOSE to Pt(240f, 165f),
            L.LEFT_SHOULDER to Pt(275f, 210f), L.RIGHT_SHOULDER to Pt(205f, 210f),
            L.LEFT_ELBOW to Pt(345f, 210f), L.LEFT_WRIST to Pt(410f, 212f),
            L.RIGHT_ELBOW to Pt(135f, 210f), L.RIGHT_WRIST to Pt(70f, 212f),
        )
        fun put(s: BodySide, hip: Pt, knee: Pt, ankle: Pt) {
            if (s == BodySide.LEFT) { j[L.LEFT_HIP] = hip; j[L.LEFT_KNEE] = knee; j[L.LEFT_ANKLE] = ankle }
            else { j[L.RIGHT_HIP] = hip; j[L.RIGHT_KNEE] = knee; j[L.RIGHT_ANKLE] = ankle }
        }
        put(front, hipF, kneeF, ankleF); put(back, hipB, kneeB, ankleB)
        return Skeleton(j)
    }

    /** Triangle with [side] = front leg / lower hand. [tiltDeg] = trunk tilt toward the front leg. */
    fun triangle(side: BodySide, tiltDeg: Float = 60f): Skeleton {
        val d = dir(side)
        val midHip = Pt(240f, 380f)
        val r = Math.toRadians(tiltDeg.toDouble())
        val ux = d * sin(r).toFloat(); val uy = -cos(r).toFloat()   // hip→shoulder unit vector
        val midSh = Pt(midHip.x + 120f * ux, midHip.y + 120f * uy)
        // Perpendicular pointing downward: front-side shoulder is the lower one.
        val qx = -uy * d; val qy = ux * d
        val shF = Pt(midSh.x + 35f * qx, midSh.y + 35f * qy)
        val shB = Pt(midSh.x - 35f * qx, midSh.y - 35f * qy)
        val hipF = Pt(240f + d * 20f, 380f); val hipB = Pt(240f - d * 20f, 380f)
        val ankleF = Pt(hipF.x + d * 100f, hipF.y + 173f); val ankleB = Pt(hipB.x - d * 100f, hipB.y + 173f)
        val j = mutableMapOf(L.NOSE to Pt(midSh.x + 35f * ux, midSh.y + 35f * uy))
        fun put(s: BodySide, sh: Pt, elbow: Pt, wrist: Pt, hip: Pt, ankle: Pt) {
            val k = mid(hip, ankle)
            if (s == BodySide.LEFT) {
                j[L.LEFT_SHOULDER] = sh; j[L.LEFT_ELBOW] = elbow; j[L.LEFT_WRIST] = wrist
                j[L.LEFT_HIP] = hip; j[L.LEFT_KNEE] = k; j[L.LEFT_ANKLE] = ankle
            } else {
                j[L.RIGHT_SHOULDER] = sh; j[L.RIGHT_ELBOW] = elbow; j[L.RIGHT_WRIST] = wrist
                j[L.RIGHT_HIP] = hip; j[L.RIGHT_KNEE] = k; j[L.RIGHT_ANKLE] = ankle
            }
        }
        put(side, shF, Pt(shF.x, shF.y + 70f), Pt(shF.x, shF.y + 135f), hipF, ankleF)
        put(side.opposite, shB, Pt(shB.x, shB.y - 70f), Pt(shB.x, shB.y - 135f), hipB, ankleB)
        return Skeleton(j)
    }

    /** Chair, SIDE view facing +x. [upright] = squat with vertical trunk (NOT Chair by reference). */
    fun chair(upright: Boolean = false): Skeleton {
        val hip = Pt(240f, 360f)
        val knee = polar(hip, 100f, 20f)
        val ankle = polar(knee, 100f, 105f)
        val tilt = if (upright) 0f else 30f
        val r = Math.toRadians(tilt.toDouble())
        val sh = Pt(hip.x + 120f * sin(r).toFloat(), hip.y - 120f * cos(r).toFloat())
        val elbow = polar(sh, 70f, -70f); val wrist = polar(sh, 135f, -70f)
        val o = 4f // far side slightly offset
        return Skeleton(mutableMapOf(
            L.NOSE to Pt(sh.x + 18f, sh.y - 31f),
            L.LEFT_SHOULDER to sh, L.RIGHT_SHOULDER to Pt(sh.x - o, sh.y),
            L.LEFT_ELBOW to elbow, L.RIGHT_ELBOW to Pt(elbow.x - o, elbow.y),
            L.LEFT_WRIST to wrist, L.RIGHT_WRIST to Pt(wrist.x - o, wrist.y),
            L.LEFT_HIP to hip, L.RIGHT_HIP to Pt(hip.x - o, hip.y),
            L.LEFT_KNEE to knee, L.RIGHT_KNEE to Pt(knee.x - o, knee.y),
            L.LEFT_ANKLE to ankle, L.RIGHT_ANKLE to Pt(ankle.x - o, ankle.y),
        ))
    }
}
