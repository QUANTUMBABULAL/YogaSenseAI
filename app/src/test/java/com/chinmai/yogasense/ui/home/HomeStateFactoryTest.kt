package com.chinmai.yogasense.ui.home

import com.chinmai.yogasense.asana.Asana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class HomeStateFactoryTest {
    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun millis(y: Int, m: Int, d: Int, h: Int) =
        Calendar.getInstance(tz, Locale.US).apply { clear(); set(y, m, d, h, 0) }.timeInMillis

    @Test fun greetingByHour() {
        assertEquals("Good morning", HomeStateFactory.greetingFor(7))
        assertEquals("Good afternoon", HomeStateFactory.greetingFor(13))
        assertEquals("Good evening", HomeStateFactory.greetingFor(20))
        assertEquals("Good evening", HomeStateFactory.greetingFor(2))
    }

    @Test fun dateAndMondayFirstWeek() {
        // Thursday 12 November 2026
        val s = HomeStateFactory.create(millis(2026, Calendar.NOVEMBER, 12, 9), tz, Locale.US)
        assertEquals("Thursday, 12 November", s.dateLabel)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), s.weekDays.map { it.label })
        assertEquals(3, s.weekDays.indexOfFirst { it.isToday })
        assertEquals(1, s.weekDays.count { it.isToday })
    }

    @Test fun sundayIsLastDayOfWeek() {
        val s = HomeStateFactory.create(millis(2026, Calendar.NOVEMBER, 15, 9), tz, Locale.US)
        assertEquals(6, s.weekDays.indexOfFirst { it.isToday })
    }

    @Test fun noFabricatedHistory() {
        val s = HomeStateFactory.create(millis(2026, Calendar.NOVEMBER, 12, 9), tz, Locale.US)
        assertEquals(0, s.sessionsThisWeek)
        assertFalse(s.hasPracticeHistory)
        assertFalse(s.hasCheckIns)
        assertTrue(s.weekDays.none { it.practiced })
    }

    @Test fun recommendedFlowCoversSupportedAsanasOnly() {
        val asanas = HomeUiState.FOUNDATIONS_FLOW.map { it.asana }.toSet()
        assertEquals(Asana.entries.toSet() - Asana.UNKNOWN, asanas)
    }
}
