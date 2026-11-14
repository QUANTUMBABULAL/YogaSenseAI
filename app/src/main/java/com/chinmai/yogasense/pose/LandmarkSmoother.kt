package com.chinmai.yogasense.pose

import kotlin.math.exp

/**
 * Time-aware exponential smoothing of landmarks. Pure Kotlin.
 *
 * alpha = 1 - exp(-dt / tau), so smoothing strength stays consistent when inference FPS varies
 * (5–9 FPS measured on OPPO F15). A short [tauMs] keeps lag low at low frame rates.
 * State resets when the pose is lost or frames are more than [resetGapMs] apart.
 */
class LandmarkSmoother(
    private val tauMs: Float = 60f,
    private val resetGapMs: Long = 500L,
    /**
     * Phase 8 (assessment only): when a joint was unreliable in the previous frame, MediaPipe's
     * position for it was a prediction. Don't blend the newly observed position with that guess —
     * take the observation as-is. Recognition keeps the default (false), so its behaviour is unchanged.
     */
    private val resetOnReliabilityChange: Boolean = false,
) {
    private var last: PoseFrame? = null

    fun reset() { last = null }

    fun smooth(frame: PoseFrame): PoseFrame {
        val prev = last
        if (!frame.hasPose) { last = null; return frame }
        if (prev == null || !prev.hasPose || frame.timestampMs - prev.timestampMs > resetGapMs ||
            frame.timestampMs <= prev.timestampMs
        ) {
            last = frame
            return frame
        }
        val dt = (frame.timestampMs - prev.timestampMs).toFloat()
        val a = 1f - exp(-dt / tauMs)
        val out = frame.landmarks.mapIndexed { i, cur ->
            val p = prev.landmarks[i]
            if (resetOnReliabilityChange && (!p.isReliable() || !cur.isReliable())) return@mapIndexed cur
            Landmark(
                x = p.x + a * (cur.x - p.x),
                y = p.y + a * (cur.y - p.y),
                z = p.z + a * (cur.z - p.z),
                // Confidence is not smoothed: a joint that just became occluded must be gated immediately.
                visibility = cur.visibility,
                presence = cur.presence,
            )
        }
        return frame.copy(landmarks = out).also { last = it }
    }
}
