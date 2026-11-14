package com.chinmai.yogasense.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.data.NoPracticeHistory
import com.chinmai.yogasense.data.PracticeHistoryRepository
import com.chinmai.yogasense.data.PracticeOverview
import com.chinmai.yogasense.data.SessionRecord
import com.chinmai.yogasense.data.WeeklySummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Home screen state. [weekly], [recent] and [overview] come only from the active profile's real
 * recorded sessions; with no history the UI shows empty states rather than numbers.
 */
data class HomeUiState(
    val greeting: String,
    val dateLabel: String,
    val weekLabels: List<String>,
    val todayIndex: Int,
    val featured: AsanaInfo,
    val explore: List<AsanaInfo>,
    val weekly: WeeklySummary? = null,
    val recent: List<SessionRecord> = emptyList(),
    val overview: PracticeOverview = PracticeOverview.EMPTY,
)

/** Pure factory; no Android dependencies so it is unit-testable. */
object HomeStateFactory {
    fun create(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
        weekly: WeeklySummary? = null,
        recent: List<SessionRecord> = emptyList(),
    ): HomeUiState {
        val cal = Calendar.getInstance(timeZone, locale).apply { timeInMillis = nowMillis }
        return HomeUiState(
            greeting = greetingFor(cal.get(Calendar.HOUR_OF_DAY)),
            dateLabel = SimpleDateFormat("EEEE, d MMMM", locale).apply { this.timeZone = timeZone }.format(cal.time),
            weekLabels = weekLabels(locale, timeZone),
            todayIndex = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7,
            featured = featuredFor(cal.get(Calendar.DAY_OF_YEAR)),
            explore = AsanaCatalog.all,
            weekly = weekly,
            recent = recent,
        )
    }

    fun greetingFor(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    /** Rotates through the catalogue by day: a suggestion, not a record of user activity. */
    fun featuredFor(dayOfYear: Int): AsanaInfo = AsanaCatalog.live[Math.floorMod(dayOfYear, AsanaCatalog.live.size)]

    /** Monday-first single-letter labels. ("EEEEE" is not portable between JVM and Android.) */
    private fun weekLabels(locale: Locale, tz: TimeZone): List<String> {
        val fmt = SimpleDateFormat("EEE", locale).apply { timeZone = tz }
        val c = Calendar.getInstance(tz, locale).apply { set(2024, Calendar.JANUARY, 1) } // a Monday
        return (0 until 7).map { i ->
            val d = (c.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            fmt.format(d.time).take(1).uppercase(locale)
        }
    }
}

class HomeViewModel(
    repository: PracticeHistoryRepository = NoPracticeHistory,
    clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val initial = HomeStateFactory.create(clock())

    val state: StateFlow<HomeUiState> =
        // recentSessions() is not shown on Home any more (overview.recent replaces it), so it isn't subscribed.
        combine(repository.weeklySummary(), repository.overview()) { weekly, overview ->
            initial.copy(weekly = weekly, overview = overview)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
