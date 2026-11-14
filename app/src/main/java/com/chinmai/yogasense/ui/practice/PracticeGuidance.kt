package com.chinmai.yogasense.ui.practice

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.Recognition
import com.chinmai.yogasense.camera.CameraStatus
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.coaching.assessment.LiveAssessment
import com.chinmai.yogasense.pose.FramingStatus

/** The single most important on-screen instruction right now. Pure Kotlin. */
object PracticeGuidance {
    enum class Tone { Info, Warn, Good }
    data class Guidance(val text: String, val tone: Tone)

    fun of(
        camera: CameraStatus,
        hasPose: Boolean,
        framing: FramingStatus,
        recognition: Recognition,
        live: LiveAssessment?,
        paused: Boolean,
    ): Guidance = when {
        paused -> Guidance("Paused — tap resume when you're ready", Tone.Info)
        camera is CameraStatus.Error -> Guidance(camera.message, Tone.Warn)
        camera is CameraStatus.Starting -> Guidance("Starting camera…", Tone.Info)
        !hasPose -> Guidance(FramingStatus.NO_BODY.message, Tone.Warn)
        // A recognised pose in profile with only the far side hidden is not asked to "show your feet".
        framing != FramingStatus.FULL_BODY && !(recognition.isStable && live?.framingAccepted == true) -> Guidance(framing.message, Tone.Warn)
        recognition.isStable && live?.alignment?.score == null && live?.alignment?.cameraHint != null -> Guidance(live.alignment.cameraHint, Tone.Warn)
        recognition.isStable && live?.correction != null -> Guidance(live.correction, Tone.Warn)
        recognition.isStable && live != null && live.hold.counting ->
            Guidance("Holding ${name(recognition.asana)} · ${live.hold.currentHoldMs / 1000} s steady", Tone.Good)
        recognition.isStable && live?.hold?.reason != null -> Guidance(live.hold.reason, Tone.Info)
        recognition.isStable -> Guidance("${name(recognition.asana)} recognised", Tone.Good)
        recognition.pending != Asana.UNKNOWN -> Guidance("Hold still while the camera detects your pose", Tone.Info)
        else -> Guidance("Full body in view — move into the pose", Tone.Info)
    }

    /** Caption for the last spoken cue, hidden when it only repeats the on-screen instruction. */
    fun caption(spoken: String?, onScreen: String): String? {
        if (spoken == null) return null
        fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
        return spoken.takeIf { norm(it) != norm(onScreen) }
    }

    fun name(a: Asana): String = AsanaCatalog.of(a)?.name ?: a.displayName

    /** Short HUD label for recognition state (never names an unconfirmed pose as recognised). */
    fun recognitionLabel(r: Recognition): String = when {
        r.isStable && r.asana != Asana.UNKNOWN -> name(r.asana)
        r.pending != Asana.UNKNOWN -> "Detecting…"
        else -> "No pose yet"
    }

    fun formatClock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
}
