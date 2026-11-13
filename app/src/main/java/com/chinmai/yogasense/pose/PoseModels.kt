package com.chinmai.yogasense.pose

/**
 * One body landmark from MediaPipe Pose Landmarker (BlazePose topology).
 *
 * Coordinate convention:
 * - [x], [y] are normalized to [0, 1] relative to the **upright** analysed image
 *   (camera sensor rotation already applied). Origin is top-left, x grows right, y grows down.
 *   Values can fall slightly outside [0, 1] when a joint is predicted off-frame.
 * - [z] is depth relative to the hip midpoint, roughly in the same scale as x; smaller = closer to camera.
 * - [visibility] and [presence] are model confidences in [0, 1] (0 when the API omits them).
 * - Images are NOT mirrored. LEFT_* indices refer to the subject's anatomical left side.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float,
) {
    fun isReliable(threshold: Float = DEFAULT_CONFIDENCE): Boolean =
        visibility >= threshold && presence >= threshold

    companion object {
        const val DEFAULT_CONFIDENCE = 0.5f
    }
}

/**
 * A single processed frame. [landmarks] is either empty (no body) or exactly 33 entries
 * ordered by [PoseLandmarkIndex].
 */
data class PoseFrame(
    val landmarks: List<Landmark>,
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    val hasPose: Boolean get() = landmarks.size == PoseLandmarkIndex.COUNT
}

/** BlazePose 33-landmark ordering used by MediaPipe Pose Landmarker. */
object PoseLandmarkIndex {
    const val NOSE = 0
    const val LEFT_EYE_INNER = 1
    const val LEFT_EYE = 2
    const val LEFT_EYE_OUTER = 3
    const val RIGHT_EYE_INNER = 4
    const val RIGHT_EYE = 5
    const val RIGHT_EYE_OUTER = 6
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val MOUTH_LEFT = 9
    const val MOUTH_RIGHT = 10
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_PINKY = 17
    const val RIGHT_PINKY = 18
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_THUMB = 21
    const val RIGHT_THUMB = 22
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32
    const val COUNT = 33

    /** Landmarks that must be reliable for "full body in frame". */
    val FULL_BODY_KEY = intArrayOf(
        NOSE, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE,
    )

    /** Skeleton edges (same topology as MediaPipe's POSE_LANDMARKS connections). */
    val CONNECTIONS: List<Pair<Int, Int>> = listOf(
        // Face
        0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
        // Torso
        11 to 12, 11 to 23, 12 to 24, 23 to 24,
        // Left arm + hand
        11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
        // Right arm + hand
        12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
        // Left leg + foot
        23 to 25, 25 to 27, 27 to 29, 29 to 31, 27 to 31,
        // Right leg + foot
        24 to 26, 26 to 28, 28 to 30, 30 to 32, 28 to 32,
    )
}
