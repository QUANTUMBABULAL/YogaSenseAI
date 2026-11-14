package com.chinmai.yogasense.ui.home

import com.chinmai.yogasense.catalog.AsanaCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val s = HomeStateFactory.create(millis(2026, Calendar.NOVEMBER, 12, 9), tz, Locale.US) // Thursday
        assertEquals("Thursday, 12 November", s.dateLabel)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), s.weekLabels)
        assertEquals(3, s.todayIndex)
    }

    @Test fun sundayIsLastDayOfWeek() {
        val s = HomeStateFactory.create(millis(2026, Calendar.NOVEMBER, 15, 9), tz, Locale.US)
        assertEquals(6, s.todayIndex)
    }

    @Test fun noFabricatedHistory() {
        val s = HomeStateFactory.create(millis(2026, Calendar.NOVEMBER, 12, 9), tz, Locale.US)
        assertNull(s.weekly)
        assertTrue(s.recent.isEmpty())
    }

    @Test fun featuredRotatesThroughCatalogueDeterministically() {
        val picks = (1..AsanaCatalog.all.size).map { HomeStateFactory.featuredFor(it) }.toSet()
        assertEquals(AsanaCatalog.all.toSet(), picks)
        assertEquals(HomeStateFactory.featuredFor(40), HomeStateFactory.featuredFor(40))
        assertEquals(AsanaCatalog.all, HomeStateFactory.create(0L, tz, Locale.US).explore)
    }
}
