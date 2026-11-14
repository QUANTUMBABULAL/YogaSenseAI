package com.chinmai.yogasense.ui.practice

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.coaching.assessment.SessionSummary

/**
 * Next-practice suggestions derived only from what the session actually measured.
 * Thresholds mirror the live labels (stability ≥ 70 "Steady", ≥ 40 "Some sway"). Pure Kotlin.
 */
object SessionSuggestions {
    const val MAX = 3

    fun of(summary: SessionSummary, target: Asana?): List<String> {
        val out = mutableListOf<String>()
        if (summary.poses.isEmpty()) {
            out += "No pose was confirmed. Place the phone 2–3 m away at hip height so your whole body, head to feet, stays in frame."
            target?.let { AsanaCatalog.of(it) }?.let { out += "Watch the ${it.name} lesson first, then hold the final position still for a few seconds so it can be recognised." }
            return out.take(MAX)
        }
        if (target != null && target != Asana.UNKNOWN && summary.poses.none { it.asana == target }) {
            val name = AsanaCatalog.of(target)?.name ?: target.displayName
            out += "$name wasn't recognised this time. Check the camera view in the lesson (${AsanaCatalog.of(target)?.cameraView?.label?.lowercase() ?: "full body in frame"})."
        }
        for (p in summary.poses.sortedByDescending { it.stableHoldTotalMs }) {
            val info = AsanaCatalog.of(p.asana)
            val name = info?.name ?: p.asana.displayName
            val top = p.corrections.maxByOrNull { it.value }?.key
            if (top != null) out += "$name: focus on “${top.trimEnd('.')}”."
            p.meanStability?.let { if (it < 40) out += "$name: balance was unsteady. Fix your gaze on one point, or practise next to a wall." }
            if (info != null) {
                val goalMs = info.holdSeconds.first * 1000L
                if (p.longestHoldMs in 1 until goalMs) out += "$name: your longest steady hold was ${p.longestHoldMs / 1000} s. Build towards ${info.holdSeconds.first} s."
                else if (p.longestHoldMs >= goalMs && (p.meanAlignment ?: 0) >= 80) out += "$name: steady and well aligned. Try holding for ${info.holdSeconds.last} s next time."
            }
            if (p.longestHoldMs == 0L) out += "$name was recognised but never held steadily. Settle into the pose and breathe before the hold timer starts."
        }
        if (out.isEmpty()) out += "Good session. Practise again tomorrow to build consistency."
        return out.distinct().take(MAX)
    }
}
