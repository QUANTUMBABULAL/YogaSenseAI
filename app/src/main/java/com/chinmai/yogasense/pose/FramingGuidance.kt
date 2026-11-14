package com.chinmai.yogasense.pose

import com.chinmai.yogasense.pose.PoseLandmarkIndex as L

enum class FramingStatus(val message: String) {
    NO_BODY("Step into the camera view"),
    SHOW_HEAD("Move back so your head is visible"),
    SHOW_FEET("Keep your feet visible — move back a little"),
    TOO_CLOSE("Move back to fit your entire body"),
    TOO_FAR("Move a little closer"),
    /** Directions are in the user's own frame (facing the camera), valid for both lenses. */
    STEP_LEFT("Step slightly to your left"),
    STEP_RIGHT("Step slightly to your right"),
    FULL_BODY("Full body in view"),
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
        // Landmarks are un-mirrored: a user facing the camera appears with their LEFT side at larger x.
        // Hips near image-left => the user must move toward image-right, i.e. to their own left.
        if (centreX < 0.2f) return FramingStatus.STEP_LEFT
        if (centreX > 0.8f) return FramingStatus.STEP_RIGHT
        return FramingStatus.FULL_BODY
    }

    private fun Landmark.inFrame() = x in EDGE..(1 - EDGE) && y in EDGE..(1 - EDGE)
}
