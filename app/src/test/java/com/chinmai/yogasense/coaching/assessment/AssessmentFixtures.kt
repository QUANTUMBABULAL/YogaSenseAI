package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.PoseFixtures
import com.chinmai.yogasense.asana.Pt
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L

/**
 * Synthetic fixtures for assessment (hand-built 2D skeletons in a 480x640 image).
 * They exercise code paths only; they say nothing about real-person accuracy.
 */
object AssessmentFixtures {
    fun features(s: PoseFixtures.Skeleton, ts: Long = 0) = BodyFeatures(s.frame(ts))

    /** Warrior II with the front knee at ~95° (inside the heuristic ideal band). */
    fun warriorIdeal(side: BodySide = BodySide.LEFT): PoseFixtures.Skeleton = PoseFixtures.warrior2(side).also { s ->
        val d = PoseFixtures.dir(side)
        val (hipI, kneeI, ankleI) = if (side == BodySide.LEFT) Triple(L.LEFT_HIP, L.LEFT_KNEE, L.LEFT_ANKLE) else Triple(L.RIGHT_HIP, L.RIGHT_KNEE, L.RIGHT_ANKLE)
        val hip = s.joints.getValue(hipI)
        val knee = Pt(hip.x + d * 100f, hip.y + 9f)
        s.joints[kneeI] = knee
        s.joints[ankleI] = Pt(knee.x, knee.y + 100f)
    }

    /** Warrior II with the front knee nearly straight (~152°). */
    fun warriorShallow(side: BodySide = BodySide.LEFT): PoseFixtures.Skeleton = PoseFixtures.warrior2(side).also { s ->
        val d = PoseFixtures.dir(side)
        val (hipI, kneeI, ankleI) = if (side == BodySide.LEFT) Triple(L.LEFT_HIP, L.LEFT_KNEE, L.LEFT_ANKLE) else Triple(L.RIGHT_HIP, L.RIGHT_KNEE, L.RIGHT_ANKLE)
        val hip = s.joints.getValue(hipI)
        val knee = Pt(hip.x + d * 45f, hip.y + 85f)
        s.joints[kneeI] = knee
        s.joints[ankleI] = Pt(knee.x, knee.y + 100f)
    }

    /** Tadasana with the left shoulder hitched ~16°. */
    fun tadasanaShoulderHitch() = PoseFixtures.tadasana().also { it.joints[L.LEFT_SHOULDER] = Pt(275f, 195f) }

    /** Tree with the upper body leaning ~18° sideways. */
    fun treeLeaning(side: BodySide = BodySide.LEFT) = PoseFixtures.tree(side).also { s ->
        listOf(L.NOSE, L.LEFT_SHOULDER, L.RIGHT_SHOULDER, L.LEFT_ELBOW, L.RIGHT_ELBOW, L.LEFT_WRIST, L.RIGHT_WRIST)
            .forEach { i -> s.joints[i] = s.joints.getValue(i).let { Pt(it.x + 40f, it.y) } }
    }

    /** Horizontally mirrored image (labels unchanged, as the analysis frame is never mirrored). */
    fun mirrored(f: PoseFrame): PoseFrame = f.copy(landmarks = f.landmarks.map { it.copy(x = 1f - it.x) })

    /** Same pose with legs 30 % longer (different body proportions). Vertical legs keep their angles. */
    fun longLegs(s: PoseFixtures.Skeleton) = s.also { sk ->
        val hipY = sk.joints.getValue(L.LEFT_HIP).y
        listOf(L.LEFT_KNEE, L.RIGHT_KNEE, L.LEFT_ANKLE, L.RIGHT_ANKLE).forEach { i ->
            val p = sk.joints.getValue(i); sk.joints[i] = Pt(p.x, hipY + (p.y - hipY) * 1.3f)
        }
    }
}
