package com.chinmai.yogasense.pose

import com.chinmai.yogasense.pose.PoseLandmarkIndex as L
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Per-landmark reliability (Phase 8). A coordinate existing in the 33-landmark array says nothing
 * about whether it was observed: MediaPipe also predicts hidden joints. We therefore classify
 * every landmark from its visibility, presence and position — and never use anything below
 * [LandmarkState.RELIABLE] as a measurement for scoring or corrections. Hidden joints are never
 * reconstructed or carried forward.
 *
 * Thresholds are prototype values to be tuned with the OPPO F15 test matrix.
 */
enum class LandmarkState {
    /** visibility ≥ [LandmarkReliability.RELIABLE] and inside the image: usable as a measurement. */
    RELIABLE,
    /** [LandmarkReliability.PARTIAL] ≤ visibility < RELIABLE: shown, but never scored or corrected. */
    PARTIAL,
    /** In the image (presence ok) but visibility < PARTIAL: hidden by the body or an object. */
    OCCLUDED,
    /** No body, presence too low, or predicted outside the image. */
    MISSING,
}

object LandmarkReliability {
    const val RELIABLE = 0.65f
    const val PARTIAL = 0.5f
    const val PRESENCE = 0.5f
    private const val EDGE = 0.0f

    fun classify(l: Landmark): LandmarkState = when {
        l.presence < PRESENCE -> LandmarkState.MISSING
        l.x < EDGE || l.x > 1f - EDGE || l.y < EDGE || l.y > 1f - EDGE -> LandmarkState.MISSING
        l.visibility >= RELIABLE -> LandmarkState.RELIABLE
        l.visibility >= PARTIAL -> LandmarkState.PARTIAL
        else -> LandmarkState.OCCLUDED
    }

    fun classify(frame: PoseFrame?): List<LandmarkState> =
        if (frame == null || !frame.hasPose) List(L.COUNT) { LandmarkState.MISSING } else frame.landmarks.map(::classify)
}

/** Reliability of each landmark in the current frame plus how long it has been unusable. */
data class ReliabilitySnapshot(
    val states: List<LandmarkState>,
    /** Landmarks unusable (not RELIABLE) continuously for at least the persistence time. */
    val persistentlyUnavailable: Set<Int>,
    /** Landmarks unusable now but only briefly (likely a tracking dropout). */
    val transientlyUnavailable: Set<Int>,
) {
    fun reliable(i: Int) = states[i] == LandmarkState.RELIABLE
    fun allReliable(idx: IntArray) = idx.all(::reliable)

    companion object {
        fun instant(states: List<LandmarkState>) = ReliabilitySnapshot(
            states, persistentlyUnavailable = emptySet(),
            transientlyUnavailable = states.indices.filter { states[it] != LandmarkState.RELIABLE }.toSet(),
        )
    }
}

/**
 * Temporal reliability: separates a brief tracking dropout (< [persistMs]) from a persistent
 * occlusion. Used to decide when to ask for a camera adjustment (persistent only) — it never
 * substitutes positions.
 */
class ReliabilityTracker(private val persistMs: Long = 2000) {
    private val unusableSince = LongArray(L.COUNT) { NEVER }

    fun reset() = unusableSince.fill(NEVER)

    fun update(frame: PoseFrame?, timeMs: Long): ReliabilitySnapshot {
        val states = LandmarkReliability.classify(frame)
        val persistent = mutableSetOf<Int>(); val transient = mutableSetOf<Int>()
        states.forEachIndexed { i, s ->
            if (s == LandmarkState.RELIABLE) unusableSince[i] = NEVER
            else {
                if (unusableSince[i] == NEVER) unusableSince[i] = timeMs
                if (timeMs - unusableSince[i] >= persistMs) persistent += i else transient += i
            }
        }
        return ReliabilitySnapshot(states, persistent, transient)
    }

    private companion object { const val NEVER = Long.MIN_VALUE }
}

/** How the body is oriented to the camera, from 2D geometry of reliable landmarks. */
enum class CameraOrientation { FRONT, SIDE, ANGLED, UNKNOWN }

object OrientationDetector {
    /** Wider of shoulder/hip span perpendicular to the trunk axis, ÷ trunk length. ~0.6–0.9 facing the camera, ~0–0.2 in profile. */
    const val FRONT_MIN = 0.45f
    const val SIDE_MAX = 0.2f

    fun detect(frame: PoseFrame?, states: List<LandmarkState>): CameraOrientation {
        if (frame == null || !frame.hasPose) return CameraOrientation.UNKNOWN
        val w = frame.imageWidth.coerceAtLeast(1).toFloat(); val h = frame.imageHeight.coerceAtLeast(1).toFloat()
        fun ok(i: Int) = states[i] == LandmarkState.RELIABLE || states[i] == LandmarkState.PARTIAL
        fun p(i: Int) = frame.landmarks[i].let { it.x * w to it.y * h }
        val sh = ok(L.LEFT_SHOULDER) && ok(L.RIGHT_SHOULDER)
        val hp = ok(L.LEFT_HIP) && ok(L.RIGHT_HIP)
        if (sh && hp) {
            val (lsx, lsy) = p(L.LEFT_SHOULDER); val (rsx, rsy) = p(L.RIGHT_SHOULDER)
            val (lhx, lhy) = p(L.LEFT_HIP); val (rhx, rhy) = p(L.RIGHT_HIP)
            val ax = (lsx + rsx) / 2 - (lhx + rhx) / 2; val ay = (lsy + rsy) / 2 - (lhy + rhy) / 2
            val len = hypot(ax, ay).takeIf { it > 1f } ?: return CameraOrientation.UNKNOWN
            val ux = ax / len; val uy = ay / len
            fun perp(dx: Float, dy: Float) = abs(dx * uy - dy * ux) // |cross(span, trunk unit)|
            // Either span being wide means the body faces the camera (hips are naturally narrower than shoulders).
            val ratio = maxOf(perp(lsx - rsx, lsy - rsy), perp(lhx - rhx, lhy - rhy)) / len
            return when {
                ratio >= FRONT_MIN -> CameraOrientation.FRONT
                ratio <= SIDE_MAX -> CameraOrientation.SIDE
                else -> CameraOrientation.ANGLED
            }
        }
        // One whole side visible and the other side hidden (in frame) is the typical profile view.
        fun sideVisible(s: Int, hip: Int) = states[s] == LandmarkState.RELIABLE && states[hip] == LandmarkState.RELIABLE
        fun sideHidden(s: Int, hip: Int) = states[s] == LandmarkState.OCCLUDED && states[hip] == LandmarkState.OCCLUDED
        if ((sideVisible(L.LEFT_SHOULDER, L.LEFT_HIP) && sideHidden(L.RIGHT_SHOULDER, L.RIGHT_HIP)) ||
            (sideVisible(L.RIGHT_SHOULDER, L.RIGHT_HIP) && sideHidden(L.LEFT_SHOULDER, L.LEFT_HIP))
        ) return CameraOrientation.SIDE
        return CameraOrientation.UNKNOWN
    }
}

/**
 * Phase 8: FramingGuidance asks for BOTH ankles/knees/shoulders. In a profile view the far side is
 * normally hidden behind the body, which is not a framing problem. This accepts SHOW_FEET /
 * SHOW_HEAD only in a SIDE orientation, only when every failing landmark is in the image (occluded,
 * not cut off) and the near side of each pair is reliable. Front views keep the original guidance.
 */
object OcclusionAwareFraming {
    private val pairs = listOf(
        L.LEFT_SHOULDER to L.RIGHT_SHOULDER, L.LEFT_HIP to L.RIGHT_HIP,
        L.LEFT_KNEE to L.RIGHT_KNEE, L.LEFT_ANKLE to L.RIGHT_ANKLE,
    )

    fun accepted(framing: FramingStatus, orientation: CameraOrientation, states: List<LandmarkState>): Boolean {
        if (framing == FramingStatus.FULL_BODY) return true
        if (orientation != CameraOrientation.SIDE) return false
        if (framing != FramingStatus.SHOW_FEET && framing != FramingStatus.SHOW_HEAD) return false
        if (states[L.NOSE] == LandmarkState.MISSING && states[L.LEFT_EAR] == LandmarkState.MISSING && states[L.RIGHT_EAR] == LandmarkState.MISSING) return false
        return pairs.all { (a, b) ->
            val sa = states[a]; val sb = states[b]
            if (sa == LandmarkState.MISSING || sb == LandmarkState.MISSING) false // cut off by the frame
            else sa == LandmarkState.RELIABLE || sb == LandmarkState.RELIABLE
        }
    }
}
