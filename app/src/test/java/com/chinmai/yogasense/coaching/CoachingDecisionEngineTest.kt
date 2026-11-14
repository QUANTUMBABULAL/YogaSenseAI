package com.chinmai.yogasense.coaching

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.pose.FramingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingDecisionEngineTest {
    private fun engine(mode: VoiceMode = VoiceMode.GUIDED, target: Asana? = null) =
        CoachingDecisionEngine(CoachSettings(mode = mode)).also { it.onEvent(CoachEvent.SessionStarted(target), 0) }

    private fun recognised(a: Asana = Asana.TREE, conf: Float = 0.9f) = CoachEvent.PoseRecognized(a, BodySide.LEFT, conf)

    @Test fun sessionStartSpeaksSafetyFirst() {
        val e = CoachingDecisionEngine()
        val cues = e.onEvent(CoachEvent.SessionStarted(null), 0)
        assertEquals(CuePriority.SAFETY, cues.first().priority)
        assertTrue(cues.any { it.key == "start" })
    }

    @Test fun offModeIsSilentForEverything() {
        val e = CoachingDecisionEngine(CoachSettings(mode = VoiceMode.OFF))
        assertTrue(e.onEvent(CoachEvent.SessionStarted(null), 0).isEmpty())
        assertTrue(e.onEvent(CoachEvent.FramingIssue(FramingStatus.SHOW_FEET), 10_000).isEmpty())
        assertTrue(e.onEvent(recognised(), 20_000).isEmpty())
        assertTrue(e.onEvent(CoachEvent.AlignmentCorrectionRequired(AlignmentCorrection("k", "Bend knee")), 30_000).isEmpty())
        assertTrue(e.onEvent(CoachEvent.SessionCompleted(60_000), 40_000).isEmpty())
    }

    @Test fun guidedAnnouncesConfirmedPoseWithInstruction() {
        val cues = engine().onEvent(recognised(), 10_000)
        assertEquals(1, cues.size)
        assertTrue(cues[0].text.startsWith("Vrikshasana."))
        assertEquals(CuePriority.INSTRUCTION, cues[0].priority)
    }

    @Test fun uncertainRecognitionIsNeverAnnounced() {
        assertTrue(engine().onEvent(recognised(conf = 0.4f), 10_000).isEmpty())
    }

    @Test fun minimalSkipsInstructionsAndCountdownButKeepsFramingAndCorrections() {
        val e = engine(VoiceMode.MINIMAL)
        assertTrue(e.onEvent(recognised(), 10_000).isEmpty())
        assertTrue(e.onEvent(CoachEvent.HoldMilestoneReached(Asana.TREE, 10), 20_000).isEmpty())
        assertTrue(e.onTick(60_000).isEmpty())
        assertEquals(CuePriority.CORRECTION,
            e.onEvent(CoachEvent.AlignmentCorrectionRequired(AlignmentCorrection("hips", "Level your hips")), 70_000).single().priority)
        assertEquals(CuePriority.FRAMING, e.onEvent(CoachEvent.FramingIssue(FramingStatus.SHOW_FEET), 80_000).single().priority)
    }

    @Test fun minimalStillGivesOccasionalEncouragementAtHoldGoal() {
        val e = engine(VoiceMode.MINIMAL)
        e.onEvent(recognised(), 10_000)
        val cues = e.onEvent(CoachEvent.HoldMilestoneReached(Asana.TREE, 20), 30_000) // Tree goal = 20 s
        assertEquals(CuePriority.ENCOURAGEMENT, cues.single().priority)
    }

    @Test fun framingCooldownSuppressesDuplicates() {
        val e = engine()
        val issue = CoachEvent.FramingIssue(FramingStatus.SHOW_FEET)
        assertEquals(1, e.onEvent(issue, 10_000).size)
        assertTrue(e.onEvent(issue, 12_000).isEmpty())       // within 8 s cooldown
        assertEquals(1, e.onEvent(issue, 18_500).size)       // after cooldown
    }

    @Test fun correctionCooldownAndDuplicateSuppression() {
        val e = engine()
        val c = CoachEvent.AlignmentCorrectionRequired(AlignmentCorrection("knee", "Align your front knee"))
        assertEquals(1, e.onEvent(c, 10_000).size)
        assertTrue(e.onEvent(c, 15_000).isEmpty())
        assertEquals(1, e.onEvent(c, 22_500).size)
    }

    @Test fun badFramingSuppressesEncouragementCountdownAndPoseChatter() {
        val e = engine()
        e.onEvent(CoachEvent.FramingIssue(FramingStatus.SHOW_FEET), 10_000)
        assertTrue(e.onEvent(recognised(), 20_000).isEmpty())
        assertTrue(e.onEvent(CoachEvent.HoldMilestoneReached(Asana.TREE, 20), 30_000).isEmpty())
        assertTrue(e.onTick(60_000).isEmpty())
        e.onEvent(CoachEvent.FramingOk, 61_000)
        assertEquals(1, e.onEvent(CoachEvent.HoldMilestoneReached(Asana.TREE, 30), 70_000).size)
    }

    @Test fun safetyCorrectionOutranksOthersInOneBatch() {
        val e = engine()
        val cues = e.onEvent(CoachEvent.AlignmentCorrectionRequired(AlignmentCorrection("s", "Ease off the knee", safety = true)), 10_000)
        assertEquals(CuePriority.SAFETY, cues.single().priority)
    }

    @Test fun improvementOnlyAfterAMatchingRecentCorrection() {
        val e = engine()
        assertTrue(e.onEvent(CoachEvent.AlignmentImproved("knee"), 10_000).isEmpty())          // never corrected
        e.onEvent(CoachEvent.AlignmentCorrectionRequired(AlignmentCorrection("knee", "Align your knee")), 20_000)
        assertEquals("That's better. Keep it there.", e.onEvent(CoachEvent.AlignmentImproved("knee"), 30_000).single().text)
        assertTrue(e.onEvent(CoachEvent.AlignmentImproved("knee"), 40_000).isEmpty())          // consumed
        e.onEvent(CoachEvent.AlignmentCorrectionRequired(AlignmentCorrection("hip", "Level hips")), 50_000)
        assertTrue(e.onEvent(CoachEvent.AlignmentImproved("hip"), 200_000).isEmpty())          // too late
    }

    @Test fun settingsTogglesAreRespected() {
        val e = CoachingDecisionEngine(CoachSettings(encouragement = false, countdown = false, corrections = false))
        e.onEvent(CoachEvent.SessionStarted(null), 0)
        e.onEvent(recognised(), 10_000)
        assertTrue(e.onEvent(CoachEvent.HoldMilestoneReached(Asana.TREE, 10), 20_000).isEmpty())   // countdown off
        assertTrue(e.onEvent(CoachEvent.HoldMilestoneReached(Asana.TREE, 20), 30_000).isEmpty())   // encouragement off
        assertTrue(e.onEvent(CoachEvent.AlignmentCorrectionRequired(AlignmentCorrection("k", "x")), 40_000).isEmpty())
        assertEquals("Session complete.", e.onEvent(CoachEvent.SessionCompleted(1), 50_000).single().text)
    }

    @Test fun globalGapPreventsChatter() {
        val e = engine()
        e.onEvent(recognised(), 10_000)
        assertTrue(e.onEvent(CoachEvent.HoldMilestoneReached(Asana.TREE, 10), 11_000).isEmpty()) // 1 s after last speech
    }

    @Test fun pauseSilencesUntilResume() {
        val e = engine()
        e.onEvent(recognised(), 10_000)
        e.onEvent(CoachEvent.SessionPaused, 11_000)
        assertTrue(e.onEvent(CoachEvent.FramingIssue(FramingStatus.SHOW_FEET), 20_000).isEmpty())
        assertTrue(e.onTick(60_000).isEmpty())
        e.onEvent(CoachEvent.SessionResumed, 61_000)
        assertEquals(1, e.onEvent(CoachEvent.FramingIssue(FramingStatus.TOO_FAR), 70_000).size)
    }

    @Test fun guidedTipsAreSpacedAndRotate() {
        val e = engine()
        e.onEvent(recognised(), 10_000)
        assertTrue(e.onTick(15_000).isEmpty())            // before the 20 s interval
        val first = e.onTick(30_000).single()
        val second = e.onTick(50_000).single()
        assertTrue(first.key != second.key)
        assertTrue(first.priority == CuePriority.BREATHING || first.priority == CuePriority.INSTRUCTION)
    }

    @Test fun differentPoseThanTargetIsNamedNotPraised() {
        val cues = engine(target = Asana.WARRIOR_II).onEvent(recognised(Asana.TRIANGLE), 10_000)
        assertTrue(cues.single().text.contains("Trikonasana") && cues.single().text.contains("Virabhadrasana"))
    }

    @Test fun sessionResetClearsCooldowns() {
        val e = engine()
        val issue = CoachEvent.FramingIssue(FramingStatus.SHOW_FEET)
        assertEquals(1, e.onEvent(issue, 10_000).size)
        e.onEvent(CoachEvent.SessionStarted(null), 11_000)
        assertEquals(1, e.onEvent(issue, 12_000).size)
    }
}
