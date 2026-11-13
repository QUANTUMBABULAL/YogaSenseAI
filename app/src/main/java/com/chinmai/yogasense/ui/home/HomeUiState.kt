package com.chinmai.yogasense.ui.home

import com.chinmai.yogasense.asana.Asana
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class WeekDay(val label: String, val isToday: Boolean, val practiced: Boolean)

data class RecommendedPose(val asana: Asana, val focus: String, val note: String? = null)

/**
 * Home screen state. Session and check-in persistence does not exist yet (Phase 6), so history
 * flags are false and the UI shows empty states — nothing is fabricated.
 */
data class HomeUiState(
    val greeting: String,
    val dateLabel: String,
    val weekDays: List<WeekDay>,
    val sessionsThisWeek: Int = 0,
    val hasPracticeHistory: Boolean = false,
    val hasCheckIns: Boolean = false,
    val recommended: List<RecommendedPose> = FOUNDATIONS_FLOW,
) {
    companion object {
        /** Static beginner sequence of the supported asanas (guidance, not user data). */
        val FOUNDATIONS_FLOW = listOf(
            RecommendedPose(Asana.TADASANA, "Posture and grounding"),
            RecommendedPose(Asana.TREE, "Balance and focus"),
            RecommendedPose(Asana.WARRIOR_II, "Leg strength and stamina"),
            RecommendedPose(Asana.TRIANGLE, "Side-body stretch"),
            RecommendedPose(Asana.CHAIR, "Leg strength", note = "Turn side-on to the camera"),
        )
    }
}

/** Pure factory; no Android dependencies so it is unit-testable. */
object HomeStateFactory {
    fun create(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
    ): HomeUiState {
        val cal = Calendar.getInstance(timeZone, locale).apply { timeInMillis = nowMillis }
        return HomeUiState(
            greeting = greetingFor(cal.get(Calendar.HOUR_OF_DAY)),
            dateLabel = SimpleDateFormat("EEEE, d MMMM", locale).apply { this.timeZone = timeZone }.format(cal.time),
            weekDays = weekOf(cal, locale),
        )
    }

    fun greetingFor(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    /** Monday-first week containing [today]. */
    private fun weekOf(today: Calendar, locale: Locale): List<WeekDay> {
        val todayDow = today.get(Calendar.DAY_OF_WEEK)
        // "EEEEE" (narrow) is not portable between JVM and Android; take the first letter of "EEE".
        val fmt = SimpleDateFormat("EEE", locale).apply { timeZone = today.timeZone }
        val start = (today.clone() as Calendar).apply {
            val offset = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_MONTH, -offset)
        }
        return (0 until 7).map { i ->
            val d = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            WeekDay(fmt.format(d.time).take(1).uppercase(locale), d.get(Calendar.DAY_OF_WEEK) == todayDow, practiced = false)
        }
    }
}
