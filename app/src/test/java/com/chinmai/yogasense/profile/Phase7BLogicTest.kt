package com.chinmai.yogasense.profile

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.coaching.CoachSettings
import com.chinmai.yogasense.coaching.LegacySettingsImport
import com.chinmai.yogasense.coaching.VisualSettings
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.coaching.assessment.PoseSummary
import com.chinmai.yogasense.coaching.assessment.SessionSummary
import com.chinmai.yogasense.data.LegacyCheckInRow
import com.chinmai.yogasense.data.LegacyQuestionnaireRow
import com.chinmai.yogasense.data.LegacyWellnessMapper
import com.chinmai.yogasense.data.LegacyWellnessSnapshot
import com.chinmai.yogasense.data.WellnessMappers
import com.chinmai.yogasense.data.WellnessMappers.toDomain
import com.chinmai.yogasense.data.WellnessMappers.toEntity
import com.chinmai.yogasense.data.WellnessMappers.toRows
import com.chinmai.yogasense.data.db.ConsentDecision
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.PoseResultEntity
import com.chinmai.yogasense.data.db.PracticeSessionEntity
import com.chinmai.yogasense.data.db.PracticeStats
import com.chinmai.yogasense.data.db.ProfileStatus
import com.chinmai.yogasense.data.db.SessionCompletion
import com.chinmai.yogasense.profile.PracticePreferences.coach
import com.chinmai.yogasense.profile.PracticePreferences.visual
import com.chinmai.yogasense.profile.PracticePreferences.with
import com.chinmai.yogasense.ui.components.PracticeFormat
import com.chinmai.yogasense.ui.practice.CorrectionLog
import com.chinmai.yogasense.ui.practice.SessionExit
import com.chinmai.yogasense.ui.practice.SessionRecordPolicy
import com.chinmai.yogasense.wellness.DailyCheckIn
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ProfileGateResolverTest {
    private fun p(id: String) = UserProfile(id, id, ExperienceLevel.BEGINNER, 0, 0, ProfileStatus.ACTIVE)

    @Test fun states() {
        assertEquals(ProfileGate.Loading, ProfileGateResolver.resolve(null, "a"))
        assertEquals(ProfileGate.NoProfiles, ProfileGateResolver.resolve(emptyList(), null))
        assertEquals(ProfileGate.ChooseProfile(listOf(p("a"))), ProfileGateResolver.resolve(listOf(p("a")), null))
        assertEquals(ProfileGate.Active(p("b"), listOf(p("a"), p("b"))), ProfileGateResolver.resolve(listOf(p("a"), p("b")), "b"))
    }

    @Test fun unknownStoredIdIsNeverActive() {
        assertEquals(ProfileGate.ChooseProfile(listOf(p("a"))), ProfileGateResolver.resolve(listOf(p("a")), "gone"))
        assertEquals(ProfileGate.NoProfiles, ProfileGateResolver.resolve(emptyList(), "gone"))
        assertEquals(ProfileGate.Loading, ProfileGateResolver.resolve(listOf(p("a")), "new", verifying = true))
    }
}

class SessionRecordPolicyTest {
    private fun summary(durationMs: Long, vararg poses: PoseSummary) = SessionSummary(durationMs, poses.map { it.asana }, poses.toList())
    private fun pose(a: Asana, hold: Long, align: Int? = null, stab: Int? = null) = PoseSummary(a, hold, hold, align, stab, emptyMap(), emptyList())

    @Test fun completionTable() {
        val held = summary(60_000, pose(Asana.TREE, 12_000))
        val notHeld = summary(60_000, pose(Asana.TREE, 0))
        assertEquals(SessionCompletion.COMPLETED, SessionRecordPolicy.completion(SessionExit.ENDED_BY_USER, held))
        assertEquals(SessionCompletion.ENDED_EARLY, SessionRecordPolicy.completion(SessionExit.ENDED_BY_USER, notHeld))
        assertEquals(SessionCompletion.ENDED_EARLY, SessionRecordPolicy.completion(SessionExit.LEFT, held))
        assertEquals(SessionCompletion.ABANDONED, SessionRecordPolicy.completion(SessionExit.LEFT, notHeld))
    }

    @Test fun trivialSessionsAreNotSaved() {
        assertNull(SessionRecordPolicy.build("s", 0, 0, 5_000, SessionExit.LEFT, summary(5_000), emptyList()))
        // Long enough without a pose: saved, but never as "completed".
        assertEquals(SessionCompletion.ENDED_EARLY, SessionRecordPolicy.build("s", 0, 0, 30_000, SessionExit.ENDED_BY_USER, summary(30_000), emptyList())!!.completion)
        assertEquals(SessionCompletion.ABANDONED, SessionRecordPolicy.build("s", 0, 0, 30_000, SessionExit.LEFT, summary(30_000), emptyList())!!.completion)
        // A recognised pose is saved even if short.
        assertEquals(SessionCompletion.ABANDONED, SessionRecordPolicy.build("s", 0, 0, 3_000, SessionExit.LEFT, summary(3_000, pose(Asana.TREE, 0)), emptyList())!!.completion)
    }

    @Test fun recordKeepsNullScoresAndConvertsTimes() {
        val log = CorrectionLog().apply {
            corrected(Asana.TREE, "TREE.torso_upright.high", atUptimeMs = 15_000)
            corrected(Asana.TREE, "TREE.torso_upright.high", atUptimeMs = 40_000)
            improved("TREE.torso_upright.high") // marks the latest one
        }
        val r = SessionRecordPolicy.build(
            "s1", startedAtWallMs = 1_700_000_000_000, startUptimeMs = 10_000, endUptimeMs = 70_000, SessionExit.ENDED_BY_USER,
            summary(55_000, pose(Asana.TREE, 20_000, align = 81, stab = null), pose(Asana.CHAIR, 0)), log.snapshot(),
        )!!
        assertEquals(1_700_000_060_000, r.endedAt)
        assertEquals(55_000L, r.activeDurationMs)
        assertEquals(SessionCompletion.COMPLETED, r.completion)
        val tree = r.poses.first { it.asana == Asana.TREE }
        assertEquals(81, tree.alignmentScore); assertNull(tree.stabilityScore)
        assertNull(r.poses.first { it.asana == Asana.CHAIR }.alignmentScore)
        assertEquals(listOf(1_700_000_005_000, 1_700_000_030_000), r.corrections.map { it.timestamp })
        assertEquals(listOf(false, true), r.corrections.map { it.improvementObserved })
    }
}

class PreferencesMappingTest {
    @Test fun legacyImportOnlyWhenTheUserSavedSettings() {
        assertNull(LegacySettingsImport.fromMap(emptyMap<String, Any>()))
        assertNull(LegacySettingsImport.fromMap(mapOf("unrelated" to 1)))
        val (c, v) = LegacySettingsImport.fromMap(mapOf("voice_mode" to "OFF", "voice_rate" to 1.1f, "vis_reference" to false, "vis_diagnostics" to true))!!
        assertEquals(VoiceMode.OFF, c.mode); assertEquals(1.1f, c.speechRate)
        assertFalse(v.referenceFigure); assertTrue(v.diagnostics)
        assertTrue(c.encouragement) // unspecified keys keep defaults
    }

    @Test fun roundTripKeepsFieldsThePracticeScreenDoesNotEdit() {
        val base = UserPreferences(targetSkeletonEnabled = true, preferredPracticeMinutes = 20)
        val c = CoachSettings(mode = VoiceMode.MINIMAL, encouragement = false, countdown = false, corrections = true, speechRate = 0.8f)
        val v = VisualSettings(liveSkeleton = false, correctionHighlights = false, referenceFigure = true, diagnostics = true)
        val p = base.with(c, v)
        assertEquals(c, p.coach())
        assertEquals(v, p.visual(diagnostics = true))
        assertTrue(p.targetSkeletonEnabled); assertEquals(20, p.preferredPracticeMinutes)
        assertEquals(CoachSettings(), UserPreferences().coach())            // defaults unchanged for all voice modes
        assertEquals(VisualSettings(), UserPreferences().visual())
        VoiceMode.entries.forEach { m -> assertEquals(m, UserPreferences(voiceMode = m).coach().mode) }
    }
}

class PracticeHistoryStatsTest {
    private val tz = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(d: Int, h: Int = 9) = Calendar.getInstance(tz).apply { clear(); set(2026, Calendar.NOVEMBER, d, h, 0) }.timeInMillis
    private fun s(id: String, start: Long, c: SessionCompletion = SessionCompletion.COMPLETED) = PracticeSessionEntity(id, "u", start, start + 60_000, 60_000, c)

    @Test fun streakSurvivesUntilAFullDayIsMissed() {
        val sessions = listOf(s("a", at(12)), s("b", at(13)))
        assertEquals(2, PracticeStats.weekly(sessions, at(14, 8), tz)!!.streakDays) // nothing today yet: still alive
        assertEquals(0, PracticeStats.weekly(sessions, at(15, 8), tz)!!.streakDays) // the 14th was missed
    }

    @Test fun overviewCountsCompletedSeparatelyAndIgnoresAbandoned() {
        val sessions = listOf(s("a", at(10)), s("b", at(11), SessionCompletion.ENDED_EARLY), s("c", at(12), SessionCompletion.ABANDONED))
        val results = listOf(
            PoseResultEntity("1", "a", Asana.TREE, 80, 60, 10_000, 8_000),
            PoseResultEntity("2", "b", Asana.TREE, null, 70, 4_000, 4_000),
            PoseResultEntity("3", "c", Asana.CHAIR, 50, 50, 1_000, 1_000), // abandoned: not counted
        )
        val o = PracticeStats.overview(sessions, results)
        assertEquals(1, o.completedSessions); assertEquals(2, o.recordedSessions); assertEquals(2, o.totalActiveMinutes)
        assertEquals("b", o.lastSession!!.sessionId)
        assertEquals(listOf(Asana.TREE), o.perAsana.map { it.asana })
        val t = o.perAsana.single()
        assertEquals(80, t.averageAlignment); assertEquals(1, t.measuredAlignmentSessions) // null not averaged in as 0
        assertEquals(65, t.averageStability); assertEquals(14_000L, t.totalStableHoldMs); assertEquals(8_000L, t.longestHoldMs)
    }

    @Test fun unavailableIsNotZero() {
        assertEquals("Not measured", PracticeFormat.score(null))
        assertEquals("0/100", PracticeFormat.score(0))
        assertEquals("45 s", PracticeFormat.duration(45_000)); assertEquals("3 min", PracticeFormat.duration(200_000))
    }
}

class WellnessMappingTest {
    @Test fun checkInRoundTripIncludingReflection() {
        val c = DailyCheckIn(100, 3, null, 7.5f, 4, "a quiet day", 9)
        assertEquals(c, c.toEntity("u", "id").toDomain())
        assertNull(c.toEntity("u", "id").reflectionCiphertext) // ciphertext column untouched until Phase 7D
    }

    @Test fun questionnaireRoundTripAndSafetyFlag() {
        val phq = QuestionnaireRecord("", QuestionnaireType.PHQ9, 5, listOf(0, 1, 2, 3, 0, 1, 2, 3, 0), 12, "Moderate", 2, true)
        val (a, rs) = phq.toRows("u", "A") { "r$it" }
        assertEquals((1..9).toList(), rs.map { it.questionNumber })
        assertEquals(phq.copy(id = "A"), WellnessMappers.toDomain(a, rs.reversed()))
        assertEquals(true, a.safetyFlag)
        val gad = QuestionnaireRecord("", QuestionnaireType.GAD7, 5, List(7) { 0 }, 0, "Minimal", null, false)
        assertNull(gad.toRows("u", "G") { "g$it" }.first.safetyFlag) // GAD-7 has no item 9
    }
}

class LegacyMapperTest {
    private val snap = LegacyWellnessSnapshot(
        listOf(LegacyCheckInRow(1, 2, 3, 4f, 5, "r", 6)),
        listOf(LegacyQuestionnaireRow(7, "GAD7", 8, "1,1,1,1,1,1,1", 7, "Mild", null, 0)),
        "DECLINED",
    )

    @Test fun mapsWithDeterministicIdsAndDeclinedConsent() {
        val m = (LegacyWellnessMapper.map(snap, "u", 99) as LegacyWellnessMapper.Result.Ok).mapped
        assertEquals("legacy7:u:checkin:1", m.checkIns.single().checkInId)
        assertEquals("legacy7:u:q:7", m.assessments.single().first.assessmentId)
        assertEquals(ConsentDecision.DECLINED, m.consent!!.decision)
        assertEquals(m, (LegacyWellnessMapper.map(snap, "u", 99) as LegacyWellnessMapper.Result.Ok).mapped) // same input → same rows
    }

    @Test fun unmappableRecordsAreReportedWithoutValues() {
        val bad = snap.copy(questionnaires = snap.questionnaires + LegacyQuestionnaireRow(9, "PHQ9", 1, "1,x,2", 3, "Mild", null, 0), consent = "MAYBE")
        val r = LegacyWellnessMapper.map(bad, "u", 1) as LegacyWellnessMapper.Result.Unmappable
        assertEquals(2, r.problems.size)
        assertTrue(r.problems.none { it.contains("1,x,2") })
    }

    @Test fun verificationDetectsAnyDifference() {
        val m = (LegacyWellnessMapper.map(snap, "u", 99) as LegacyWellnessMapper.Result.Ok).mapped
        val good = LegacyWellnessMapper.ReadBack(m.checkIns, m.assessments.map { it.first }, m.assessments.flatMap { it.second }, m.consent)
        assertTrue(LegacyWellnessMapper.verify(snap, m, good, "u").isEmpty())
        val tampered = good.copy(checkIns = listOf(m.checkIns.single().copy(mood = 5)))
        assertFalse(LegacyWellnessMapper.verify(snap, m, tampered, "u").isEmpty())
        val missing = good.copy(responses = good.responses.drop(1))
        assertFalse(LegacyWellnessMapper.verify(snap, m, missing, "u").isEmpty())
        assertFalse(LegacyWellnessMapper.verify(snap, m, good.copy(consent = null), "u").isEmpty())
    }
}
