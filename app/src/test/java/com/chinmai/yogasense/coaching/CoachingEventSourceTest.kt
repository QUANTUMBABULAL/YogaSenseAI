package com.chinmai.yogasense.coaching

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.Recognition
import com.chinmai.yogasense.camera.CameraStatus
import com.chinmai.yogasense.pose.FramingStatus
import com.chinmai.yogasense.ui.practice.PracticeGuidance
import com.chinmai.yogasense.coaching.assessment.HoldStatus
import com.chinmai.yogasense.coaching.assessment.LiveAssessment
import com.chinmai.yogasense.coaching.assessment.StabilityResult
import com.chinmai.yogasense.ui.practice.SessionClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingEventSourceTest {
    private val none = Recognition.NONE
    private fun stable(a: Asana, side: BodySide = BodySide.NONE) = Recognition(a, 0.9f, true, side, 0.9f)
    private fun pending(a: Asana) = Recognition(Asana.UNKNOWN, 0f, false, pending = a, pendingProgress = 0.5f)
    private fun snap(t: Long, f: FramingStatus = FramingStatus.FULL_BODY, r: Recognition = none) = PracticeSnapshot(t, f, r)

    @Test fun framingIsDebounced() {
        val s = CoachingEventSource(framingDebounceMs = 1500)
        assertTrue(s.onSnapshot(snap(0, FramingStatus.SHOW_FEET)).isEmpty())
        assertTrue(s.onSnapshot(snap(1000, FramingStatus.SHOW_FEET)).isEmpty())
        assertEquals(listOf(CoachEvent.FramingIssue(FramingStatus.SHOW_FEET)), s.onSnapshot(snap(1600, FramingStatus.SHOW_FEET)))
        assertTrue(s.onSnapshot(snap(3000, FramingStatus.SHOW_FEET)).isEmpty()) // reported once
    }

    @Test fun framingFlickerNeverReported() {
        val s = CoachingEventSource(framingDebounceMs = 1500)
        s.onSnapshot(snap(0)); s.onSnapshot(snap(1600)) // FramingOk reported
        var t = 1700L
        repeat(10) { i -> assertTrue(s.onSnapshot(snap(t, if (i % 2 == 0) FramingStatus.TOO_FAR else FramingStatus.FULL_BODY)).isEmpty()); t += 400 }
    }

    @Test fun onlyStableRecognitionProducesPoseEvents() {
        val s = CoachingEventSource()
        assertTrue(s.onSnapshot(snap(0, r = pending(Asana.TREE))).none { it is CoachEvent.PoseRecognized })
        val ev = s.onSnapshot(snap(1000, r = stable(Asana.TREE, BodySide.LEFT)))
        assertTrue(ev.contains(CoachEvent.PoseRecognized(Asana.TREE, BodySide.LEFT, 0.9f)))
        assertTrue(ev.contains(CoachEvent.StableHoldStarted(Asana.TREE)))
    }

    @Test fun poseChangeEmitsLostThenRecognisedAndRecordsHold() {
        val s = CoachingEventSource()
        s.onSnapshot(snap(0, r = stable(Asana.TADASANA)))
        val ev = s.onSnapshot(snap(12_000, r = stable(Asana.TREE)))
        assertTrue(ev.contains(CoachEvent.PoseLost(Asana.TADASANA, 12_000)))
        assertTrue(ev.indexOf(CoachEvent.PoseLost(Asana.TADASANA, 12_000)) < ev.indexOfFirst { it is CoachEvent.PoseRecognized })
        assertTrue(ev.any { it is CoachEvent.PoseRecognized && it.asana == Asana.TREE })
        assertEquals(12_000L, s.longestHolds[Asana.TADASANA])
    }

    @Test fun milestonesEveryTenSecondsOnce() {
        val s = CoachingEventSource(milestoneEverySeconds = 10)
        s.onSnapshot(snap(0, r = stable(Asana.TREE)))
        val ms = (1..25).flatMap { s.onSnapshot(snap(it * 1000L, r = stable(Asana.TREE))) }
            .filterIsInstance<CoachEvent.HoldMilestoneReached>().map { it.seconds }
        assertEquals(listOf(10, 20), ms)
    }

    @Test fun restartHoldExcludesPausedTime() {
        val s = CoachingEventSource()
        s.onSnapshot(snap(0, r = stable(Asana.CHAIR)))
        s.restartHold(60_000)
        assertEquals(5_000L, s.currentHold(65_000)!!.second)
    }

    @Test fun resetClearsSession() {
        val s = CoachingEventSource()
        s.onSnapshot(snap(0, r = stable(Asana.CHAIR))); s.onSnapshot(snap(5000, r = stable(Asana.CHAIR)))
        s.reset()
        assertTrue(s.longestHolds.isEmpty())
        assertTrue(s.currentHold(6000) == null)
    }
}

class SessionClockAndGuidanceTest {
    @Test fun clockExcludesPausedTime() {
        val c = SessionClock(0)
        c.pause(10_000)
        assertEquals(10_000L, c.elapsedMs(50_000))
        c.resume(50_000)
        assertEquals(15_000L, c.elapsedMs(55_000))
    }

    @Test fun guidancePriority() {
        val ok = FramingStatus.FULL_BODY
        fun g(cam: CameraStatus = CameraStatus.Active, pose: Boolean = true, f: FramingStatus = ok, r: Recognition = Recognition.NONE,
              hold: Pair<Asana, Long>? = null, paused: Boolean = false) = PracticeGuidance.of(
            cam, pose, f, r,
            hold?.let { LiveAssessment(null, StabilityResult.NONE, HoldStatus(it.first, it.second, true, null), null) },
            paused,
        )

        assertTrue(g(paused = true, f = FramingStatus.SHOW_FEET).text.startsWith("Paused"))
        assertEquals("Camera busy", g(cam = CameraStatus.Error("Camera busy")).text)
        assertEquals(FramingStatus.NO_BODY.message, g(pose = false).text)
        assertEquals(FramingStatus.SHOW_FEET.message, g(f = FramingStatus.SHOW_FEET, r = Recognition(Asana.TREE, .9f, true)).text)
        assertEquals("Holding Vrikshasana · 12 s steady", g(r = Recognition(Asana.TREE, .9f, true), hold = Asana.TREE to 12_400).text)
        assertTrue(g(r = Recognition(Asana.UNKNOWN, 0f, false, pending = Asana.TREE)).text.contains("Hold still"))
    }

    @Test fun captionHiddenWhenItRepeatsOnScreenText() {
        assertEquals(null, PracticeGuidance.caption("Step into the camera view.", "Step into the camera view"))
        assertEquals("Tree Pose. Focus on a steady point in front of you.",
            PracticeGuidance.caption("Tree Pose. Focus on a steady point in front of you.", "Holding Vrikshasana · 3 s"))
        assertEquals(null, PracticeGuidance.caption(null, "x"))
    }

    @Test fun spokenFramingMatchesOnScreenFraming() {
        FramingStatus.entries.filter { it != FramingStatus.FULL_BODY }.forEach {
            assertEquals(null, PracticeGuidance.caption(CoachingContent.framing(it), it.message))
        }
    }

    @Test fun recognitionLabelNeverNamesUnconfirmedPose() {
        assertEquals("Detecting…", PracticeGuidance.recognitionLabel(Recognition(Asana.UNKNOWN, 0f, false, pending = Asana.TREE)))
        assertEquals("No pose yet", PracticeGuidance.recognitionLabel(Recognition.NONE))
        assertEquals("Utkatasana", PracticeGuidance.recognitionLabel(Recognition(Asana.CHAIR, .8f, true)))
        assertEquals("1:05", PracticeGuidance.formatClock(65_900))
    }
}

class SettingsCodecTest {
    @Test fun roundTrip() {
        val c = CoachSettings(VoiceMode.MINIMAL, encouragement = false, countdown = false, corrections = true, speechRate = 1.1f)
        val v = VisualSettings(liveSkeleton = false, correctionHighlights = false, referenceFigure = false, diagnostics = true)
        val m = SettingsCodec.encode(c, v)
        assertEquals(c, SettingsCodec.decodeCoach(m))
        assertEquals(v, SettingsCodec.decodeVisual(m))
    }

    @Test fun defaultsAndBadValues() {
        assertEquals(CoachSettings(), SettingsCodec.decodeCoach(emptyMap<String, Any>()))
        assertEquals(VoiceMode.GUIDED, SettingsCodec.decodeCoach(mapOf("voice_mode" to "LOUD")).mode)
        assertEquals(1.2f, SettingsCodec.decodeCoach(mapOf("voice_rate" to 9f)).speechRate)
        assertEquals(VisualSettings(), SettingsCodec.decodeVisual(emptyMap<String, Any>()))
        assertTrue(!VisualSettings.TARGET_SKELETON_AVAILABLE)
    }
}
