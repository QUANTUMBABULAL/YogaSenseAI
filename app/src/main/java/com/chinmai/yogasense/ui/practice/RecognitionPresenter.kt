package com.chinmai.yogasense.ui.practice

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.Recognition

/** Pure text formatting for the recognition card. A pose name is only shown when confirmed. */
object RecognitionPresenter {

    sealed interface Headline {
        data class Confirmed(val name: String, val sideLabel: String?) : Headline
        data class Pending(val name: String, val progress: Float) : Headline
        data class NotRecognised(val reason: String) : Headline
    }

    fun headline(r: Recognition): Headline = when {
        r.isStable && r.asana != Asana.UNKNOWN -> Headline.Confirmed(r.asana.displayName, sideLabel(r.asana, r.side))
        r.pending != Asana.UNKNOWN -> Headline.Pending(r.pending.displayName, r.pendingProgress.coerceIn(0f, 1f))
        else -> Headline.NotRecognised(r.frame?.reason ?: "Waiting for body")
    }

    fun sideLabel(asana: Asana, side: BodySide): String? {
        if (!asana.sided || side == BodySide.NONE) return null
        val s = if (side == BodySide.LEFT) "Left" else "Right"
        return when (asana) {
            Asana.TREE -> "$s foot lifted"
            Asana.WARRIOR_II -> "$s knee bent"
            Asana.TRIANGLE -> "$s leg forward"
            else -> null
        }
    }

    /** Short guidance from low-confidence landmark names, e.g. "Keep your ankles and knees in view". */
    fun landmarkHint(lowConfidence: List<String>): String? {
        if (lowConfidence.isEmpty()) return null
        val parts = lowConfidence.map { it.substringAfter(' ') }.distinct().map { plural(it) }
        return "Keep your ${parts.joinToString(" and ")} in view"
    }

    private fun plural(part: String) = when (part) {
        "knee" -> "knees"; "ankle" -> "ankles"; "hip" -> "hips"; "wrist" -> "wrists"
        "elbow" -> "elbows"; "shoulder" -> "shoulders"; else -> part
    }

    fun percent(v: Float) = "${(v.coerceIn(0f, 1f) * 100).toInt()}%"
}
