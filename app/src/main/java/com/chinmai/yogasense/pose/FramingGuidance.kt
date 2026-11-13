package com.chinmai.yogasense.pose

import com.chinmai.yogasense.pose.PoseLandmarkIndex as L

enum class FramingStatus(val message: String) {
    NO_BODY("Move into frame"),
    SHOW_HEAD("Step back so your head is visible"),
    SHOW_FEET("Step back so your feet are visible"),
    TOO_CLOSE("Step back a little"),
    TOO_FAR("Move a little closer"),
    OFF_CENTER("Move to the centre of the frame"),
    FULL_BODY("Body detected — full body in frame"),
}

/** Pure framing check over a [PoseFrame]; no Android dependencies so it is unit-testable. */
object FramingGuidance {
    private const val EDGE = 0.02f

    fun reliableCount(frame: PoseFrame?, threshold: Float = Landmark.DEFAULT_CONFIDENCE): Int =
        frame?.landmarks?.count { it.isReliable(threshold) && it.inFrame() } ?: 0

    fun evaluate(frame: PoseFrame?): FramingStatus {
        if (frame == null || !frame.hasPose) return FramingStatus.NO_BODY
        val lm = frame.landmarks
        fun ok(i: Int) = lm[i].isReliable() && lm[i].inFrame()

        val headOk = ok(L.NOSE) && ok(L.LEFT_SHOULDER) && ok(L.RIGHT_SHOULDER)
        val feetOk = ok(L.LEFT_ANKLE) && ok(L.RIGHT_ANKLE)
        val hipsOk = ok(L.LEFT_HIP) && ok(L.RIGHT_HIP)
        if (!headOk && !feetOk && !hipsOk) return FramingStatus.NO_BODY
        if (!headOk) return FramingStatus.SHOW_HEAD
        if (!feetOk || !ok(L.LEFT_KNEE) || !ok(L.RIGHT_KNEE)) return FramingStatus.SHOW_FEET

        val key = L.FULL_BODY_KEY.map { lm[it] }
        val top = key.minOf { it.y }
        val bottom = key.maxOf { it.y }
        val height = bottom - top
        if (height > 0.92f) return FramingStatus.TOO_CLOSE
        if (height < 0.35f) return FramingStatus.TOO_FAR

        val centreX = (lm[L.LEFT_HIP].x + lm[L.RIGHT_HIP].x) / 2f
        if (centreX < 0.2f || centreX > 0.8f) return FramingStatus.OFF_CENTER
        return FramingStatus.FULL_BODY
    }

    private fun Landmark.inFrame() = x in EDGE..(1 - EDGE) && y in EDGE..(1 - EDGE)
}
