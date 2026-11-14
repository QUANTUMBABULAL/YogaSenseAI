package com.chinmai.yogasense.wellness

import kotlin.math.roundToInt

/** One day in a trend series; [value] is null when nothing was recorded (a gap, never interpolated). */
data class TrendPoint(val epochDay: Long, val value: Float?)

data class TrendSeries(
    val title: String,
    val points: List<TrendPoint>,
    val average: Float?,            // over recorded days only; null when fewer than [minEntries]
    val recordedDays: Int,
    val periodLabel: String,
)

data class QuestionnaireComparison(val previousTotal: Int, val previousAtMillis: Long, val difference: Int) {
    /** Neutral wording: a difference is not described as improvement or worsening. */
    fun describe(dateLabel: String): String = when {
        difference == 0 -> "Same total as your previous result on $dateLabel."
        difference < 0 -> "${-difference} point${if (difference == -1) "" else "s"} lower than your previous result on $dateLabel."
        else -> "$difference point${if (difference == 1) "" else "s"} higher than your previous result on $dateLabel."
    }
}

data class WeeklyReflection(
    val startDay: Long,
    val endDay: Long,
    val checkIns: Int,
    val averageSleepHours: Float?,
    val averageMood: Float?,
    val averageStress: Float?,
    val practiceSessions: Int?,     // null = practice history is not recorded
    val prompt: String,
) {
    val hasEnoughForPatterns: Boolean get() = checkIns >= WellnessInsights.MIN_ENTRIES
    val isEmpty: Boolean get() = checkIns == 0
}

/** Pure calculations over stored entries. Nothing here invents data. */
object WellnessInsights {
    const val MIN_ENTRIES = 3

    private val PROMPTS = listOf(
        "What helped you feel most like yourself this week?",
        "When did you feel most at ease this week?",
        "What is one small thing you'd like more of next week?",
        "Which moments this week would you like to remember?",
    )

    fun series(
        title: String,
        entries: List<DailyCheckIn>,
        endDay: Long,
        days: Int,
        value: (DailyCheckIn) -> Float?,
    ): TrendSeries {
        val byDay = entries.associateBy { it.epochDay }
        val points = (0 until days).map { i ->
            val d = endDay - (days - 1) + i
            TrendPoint(d, byDay[d]?.let(value))
        }
        val values = points.mapNotNull { it.value }
        return TrendSeries(title, points, if (values.size >= MIN_ENTRIES) values.average().toFloat() else null, values.size, "Last $days days")
    }

    fun comparison(current: QuestionnaireRecord, history: List<QuestionnaireRecord>): QuestionnaireComparison? {
        val prev = history.filter { it.type == current.type && it.id != current.id && it.completedAtMillis < current.completedAtMillis }
            .maxByOrNull { it.completedAtMillis } ?: return null
        return QuestionnaireComparison(prev.total, prev.completedAtMillis, current.total - prev.total)
    }

    /** Questionnaires are not daily check-ins: suggest one only after ~2 weeks (their reference period). */
    fun isQuestionnaireDue(type: QuestionnaireType, history: List<QuestionnaireRecord>, nowMillis: Long): Boolean {
        val last = history.filter { it.type == type }.maxOfOrNull { it.completedAtMillis } ?: return true
        return nowMillis - last >= 14L * 24 * 60 * 60 * 1000
    }

    fun weekly(entries: List<DailyCheckIn>, todayEpochDay: Long, practiceSessions: Int?): WeeklyReflection {
        val start = todayEpochDay - 6
        val week = entries.filter { it.epochDay in start..todayEpochDay && !it.isEmpty }
        fun avg(f: (DailyCheckIn) -> Float?): Float? =
            week.mapNotNull(f).takeIf { it.size >= MIN_ENTRIES }?.average()?.toFloat()
        return WeeklyReflection(
            startDay = start, endDay = todayEpochDay, checkIns = week.size,
            averageSleepHours = avg { it.sleepHours },
            averageMood = avg { it.mood?.toFloat() },
            averageStress = avg { it.stress?.toFloat() },
            practiceSessions = practiceSessions,
            prompt = PROMPTS[Math.floorMod(todayEpochDay / 7, PROMPTS.size.toLong()).toInt()],
        )
    }

    fun describeAverage(scale: RatingScale, avg: Float): String = scale.label(avg.roundToInt().coerceIn(scale.range))
}
