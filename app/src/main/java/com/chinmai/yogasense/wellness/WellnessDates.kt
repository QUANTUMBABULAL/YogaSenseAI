package com.chinmai.yogasense.wellness

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Local-calendar-day helpers (no java.time: minSdk 24 without desugaring). */
object WellnessDates {
    private const val DAY_MS = 86_400_000L

    fun epochDay(millis: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        Math.floorDiv(millis + tz.getOffset(millis), DAY_MS)

    fun startOfDayMillis(epochDay: Long, tz: TimeZone = TimeZone.getDefault()): Long =
        epochDay * DAY_MS - tz.getOffset(epochDay * DAY_MS)

    fun dayLabel(epochDay: Long, locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("EEE d MMM", locale).apply { timeZone = tz }.format(startOfDayMillis(epochDay, tz) + 12 * 3_600_000L)

    fun dateLabel(millis: Long, locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("d MMM yyyy", locale).apply { timeZone = tz }.format(millis)

    fun shortDay(epochDay: Long, locale: Locale = Locale.getDefault(), tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("d MMM", locale).apply { timeZone = tz }.format(startOfDayMillis(epochDay, tz) + 12 * 3_600_000L)
}
