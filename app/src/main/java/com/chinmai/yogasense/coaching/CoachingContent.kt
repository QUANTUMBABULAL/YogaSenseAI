package com.chinmai.yogasense.coaching

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.pose.FramingStatus

/**
 * Spoken content. Pose instructions are general guidance spoken while a pose is held — they are
 * NOT corrections and are never triggered by (non-existent) alignment measurements.
 * Breathing cues come from the catalogue so text is not duplicated.
 */
object CoachingContent {
    data class PoseScript(val intro: String, val instructions: List<String>)

    private val scripts = mapOf(
        Asana.TADASANA to PoseScript(
            "Tadasana.",
            listOf("Stand tall and relax your shoulders.", "Keep your weight balanced across both feet.", "Lengthen up through the crown of your head."),
        ),
        Asana.TREE to PoseScript(
            "Vrikshasana.",
            listOf("Focus on a steady point in front of you.", "Try to maintain your balance.", "Keep your standing leg strong and your hips level."),
        ),
        Asana.WARRIOR_II to PoseScript(
            "Virabhadrasana Two.",
            listOf("Keep your front knee aligned with your foot.", "Extend both arms comfortably.", "Keep your torso upright between your legs."),
        ),
        Asana.CHAIR to PoseScript(
            "Utkatasana.",
            listOf("Move your hips backwards.", "Keep your knees comfortable and aligned.", "Keep your weight in your heels."),
        ),
        Asana.TRIANGLE to PoseScript(
            "Trikonasana.",
            listOf("Lengthen your spine.", "Move into the side bend gently.", "Keep both legs long without locking the knees."),
        ),
    )

    fun script(asana: Asana): PoseScript? = scripts[asana]

    fun breathing(asana: Asana): List<String> = AsanaCatalog.of(asana)?.breathing.orEmpty()

    /** Recommended minimum hold, used for the "you reached the hold" encouragement. */
    fun recommendedHoldSeconds(asana: Asana): Int? = AsanaCatalog.of(asana)?.holdSeconds?.first

    const val SESSION_START = "Let's begin. Step back so your whole body is in view."
    const val SAFETY_REMINDER = "Move gently, and stop if you feel pain or dizziness."
    const val POSE_LOST = "Take your time, and come back into the pose when you're ready."
    const val GENERIC_BREATH = "Breathe slowly and steadily."
    const val STEADY = "Steady yourself. Find a fixed point and breathe."

    fun sessionComplete(withPraise: Boolean) = if (withPraise) "Session complete. Well done." else "Session complete."

    fun holdReached(asana: Asana, seconds: Int): String {
        val name = script(asana)?.intro?.removeSuffix(".") ?: "the pose"
        return "Great, you've held $name for $seconds seconds."
    }

    fun countdown(seconds: Int) = "$seconds seconds."

    fun improved(): String = "That's better. Keep it there."

    /** Spoken framing guidance uses the same text as the on-screen guidance (single source). */
    fun framing(status: FramingStatus): String? =
        if (status == FramingStatus.FULL_BODY) null else status.message + "."

    fun different(target: Asana, recognised: Asana): String =
        "That looks like ${script(recognised)?.intro?.removeSuffix(".")}. Today's target is ${script(target)?.intro?.removeSuffix(".")}."
}
