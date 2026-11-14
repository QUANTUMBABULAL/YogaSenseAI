package com.chinmai.yogasense.data.db

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.data.db.Mappers.toDomain
import com.chinmai.yogasense.data.db.Mappers.toEntity
import com.chinmai.yogasense.data.db.Mappers.toRows
import com.chinmai.yogasense.profile.CompletedPracticeSession
import com.chinmai.yogasense.profile.CorrectionOutcome
import com.chinmai.yogasense.profile.PoseOutcome
import com.chinmai.yogasense.profile.ProfileValidation
import com.chinmai.yogasense.profile.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ConvertersTest {
    private val c = Converters()

    @Test fun enumsRoundTripByName() {
        Asana.entries.forEach { assertEquals(it, c.toAsana(c.fromAsana(it))) }
        VoiceMode.entries.forEach { assertEquals(it, c.toVoiceMode(c.fromVoiceMode(it))) }
        SessionCompletion.entries.forEach { assertEquals(it, c.toCompletion(c.fromCompletion(it))) }
        Instrument.entries.forEach { assertEquals(it, c.toInstrument(c.fromInstrument(it))) }
        ConsentType.entries.forEach { assertEquals(it, c.toConsentType(c.fromConsentType(it))) }
        ProfileStatus.entries.forEach { assertEquals(it, c.toProfileStatus(c.fromProfileStatus(it))) }
        ExperienceLevel.entries.forEach { assertEquals(it, c.toExperience(c.fromExperience(it))) }
        assertEquals("WARRIOR_II", c.fromAsana(Asana.WARRIOR_II)) // names, not ordinals
    }
}

class MappersTest {
    private fun session() = CompletedPracticeSession(
        sessionId = "s1", startedAt = 1_000, endedAt = 61_000, activeDurationMs = 55_000, completion = SessionCompletion.COMPLETED,
        poses = listOf(
            PoseOutcome(Asana.TREE, alignmentScore = 82, stabilityScore = 74, stableHoldTotalMs = 20_000, longestHoldMs = 15_000),
            PoseOutcome(Asana.CHAIR, alignmentScore = null, stabilityScore = null, stableHoldTotalMs = 0, longestHoldMs = 0),
        ),
        corrections = listOf(CorrectionOutcome(Asana.TREE, "TREE.torso_upright.high", 30_000, improvementObserved = true)),
    )

    @Test fun sessionRowsKeepOwnershipAndNullScores() {
        var n = 0
        val rows = session().toRows("u1") { "id${n++}" }
        assertEquals("u1", rows.session.userId)
        assertEquals(55_000L, rows.session.durationMs)
        assertEquals(listOf("s1", "s1"), rows.results.map { it.sessionId })
        val chair = rows.results.first { it.asana == Asana.CHAIR }
        assertNull(chair.alignmentScore); assertNull(chair.stabilityScore)   // unavailable stays null, never 0
        assertEquals(82, rows.results.first { it.asana == Asana.TREE }.alignmentScore)
        assertEquals("TREE.torso_upright.high", rows.corrections.single().correctionType)
        assertTrue(rows.corrections.single().improvementObserved)
    }

    @Test fun preferencesRoundTrip() {
        val p = UserPreferences(voiceMode = VoiceMode.MINIMAL, speechRate = 1.1f, preferredPracticeMinutes = 20, targetSkeletonEnabled = true)
        assertEquals(p, p.toEntity("u1", 5).toDomain())
        assertEquals(VoiceMode.GUIDED, UserPreferences().voiceMode) // mirrors existing CoachSettings defaults
        assertNull(UserPreferences().preferredPracticeMinutes)
    }

    @Test fun recentRecordsJoinOnlyKnownSessions() {
        val s = listOf(PracticeSessionEntity("s1", "u1", 1_000, 2_000, 1_000, SessionCompletion.COMPLETED))
        val r = listOf(
            PoseResultEntity("r1", "s1", Asana.TREE, null, null, 12_500, 12_500),
            PoseResultEntity("r2", "sX", Asana.CHAIR, null, null, 5_000, 5_000), // not in the list: ignored
        )
        val out = Mappers.recentRecords(s, r, 5)
        assertEquals(1, out.size); assertEquals(Asana.TREE, out[0].asana); assertEquals(12, out[0].durationSeconds)
    }

    @Test fun displayNameValidation() {
        assertEquals("Asha K", ProfileValidation.displayName("  Asha   K "))
        assertNull(ProfileValidation.displayName("   "))
        assertNull(ProfileValidation.displayName("x".repeat(41)))
    }
}

class PracticeStatsTest {
    private val tz = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 9) = Calendar.getInstance(tz).apply { clear(); set(y, m, d, h, 0) }.timeInMillis
    private fun s(start: Long, dur: Long = 600_000, c: SessionCompletion = SessionCompletion.COMPLETED) =
        PracticeSessionEntity("id$start", "u1", start, start + dur, dur, c)

    @Test fun noSessionsMeansNoSummary() = assertNull(PracticeStats.weekly(emptyList(), at(2026, Calendar.NOVEMBER, 14), tz))

    @Test fun weeklyTotalsStreakAndDays() {
        val now = at(2026, Calendar.NOVEMBER, 14, 20) // Saturday
        val sessions = listOf(
            s(at(2026, Calendar.NOVEMBER, 14)), s(at(2026, Calendar.NOVEMBER, 13)), s(at(2026, Calendar.NOVEMBER, 12), 1_200_000),
            s(at(2026, Calendar.NOVEMBER, 10)),                                   // Tuesday this week, breaks streak gap on 11th
            s(at(2026, Calendar.NOVEMBER, 8)),                                    // last week (Sunday)
            s(at(2026, Calendar.NOVEMBER, 11), c = SessionCompletion.ABANDONED),  // not counted
        )
        val w = PracticeStats.weekly(sessions, now, tz)!!
        assertEquals(4, w.sessions)
        assertEquals(50, w.totalMinutes)
        assertEquals(3, w.streakDays)
        assertEquals(setOf(1, 3, 4, 5), w.practicedDaysOfWeek) // Tue, Thu, Fri, Sat (Mon = 0)
    }
}
