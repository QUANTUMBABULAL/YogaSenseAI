package com.chinmai.yogasense.ui.components

import com.chinmai.yogasense.data.db.SessionCompletion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Pure display formatting for recorded practice (JVM-testable). Never invents values. */
object PracticeFormat {
    /** "45 s", "3 min", "1 h 05 min". */
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min"
            else -> "${s / 3600} h %02d min".format(Locale.ROOT, (s % 3600) / 60)
        }
    }

    fun completion(c: SessionCompletion) = when (c) {
        SessionCompletion.COMPLETED -> "Completed"
        SessionCompletion.ENDED_EARLY -> "Ended early"
        SessionCompletion.ABANDONED -> "Not finished"
    }

    fun date(ms: Long, tz: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEE d MMM", locale).apply { timeZone = tz }.format(Date(ms))

    /** Score out of 100, or an explicit "not measured" (never 0 for missing data). */
    fun score(v: Int?): String = v?.let { "$it/100" } ?: "Not measured"
}
