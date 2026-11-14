package com.chinmai.yogasense.wellness

import com.chinmai.yogasense.wellness.QuestionnaireFlow.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireContentTest {
    private val phq = Questionnaires.PHQ9
    private val gad = Questionnaires.GAD7

    @Test fun standardStructure() {
        assertEquals(9, phq.items.size); assertEquals(27, phq.maxScore)
        assertEquals(7, gad.items.size); assertEquals(21, gad.maxScore)
        assertTrue(phq.stem.startsWith("Over the last 2 weeks"))
        assertTrue(gad.stem.startsWith("Over the last 2 weeks"))
    }

    @Test fun standardResponseMapping() {
        val expected = listOf("Not at all" to 0, "Several days" to 1, "More than half the days" to 2, "Nearly every day" to 3)
        assertEquals(expected, phq.options.map { it.label to it.points })
        assertEquals(expected, gad.options.map { it.label to it.points })
    }

    @Test fun verbatimKeyItems() {
        assertEquals("Little interest or pleasure in doing things", phq.items[0])
        assertEquals("Thoughts that you would be better off dead or of hurting yourself in some way", phq.items[8])
        assertEquals("Feeling nervous, anxious or on edge", gad.items[0])
        assertEquals("Feeling afraid as if something awful might happen", gad.items[6])
        assertTrue(phq.difficultyQuestion!!.startsWith("If you checked off any problems"))
        assertEquals(4, phq.difficultyOptions.size)
        assertNull(gad.difficultyQuestion) // not on the official GAD-7 form used
        assertTrue(phq.source.contains("No permission required"))
    }

    @Test fun phq9Scoring() {
        assertEquals(0, phq.score(List(9) { 0 }))
        assertEquals(27, phq.score(List(9) { 3 }))
        assertEquals(13, phq.score(listOf(1, 2, 3, 0, 1, 2, 3, 1, 0)))
        assertNull(phq.score(List(8) { 1 } + listOf(null)))   // incomplete: no prorating
        assertNull(phq.score(List(9) { 4 }))                     // invalid value
        assertNull(phq.score(List(8) { 1 }))                     // wrong length
    }

    @Test fun phq9Bands() {
        mapOf(0 to "Minimal", 4 to "Minimal", 5 to "Mild", 9 to "Mild", 10 to "Moderate", 14 to "Moderate",
            15 to "Moderately severe", 19 to "Moderately severe", 20 to "Severe", 27 to "Severe")
            .forEach { (score, band) -> assertEquals("$score", band, phq.band(score).label) }
    }

    @Test fun gad7ScoringAndBands() {
        assertEquals(21, gad.score(List(7) { 3 }))
        assertEquals(7, gad.score(listOf(1, 1, 1, 1, 1, 1, 1)))
        mapOf(0 to "Minimal", 4 to "Minimal", 5 to "Mild", 9 to "Mild", 10 to "Moderate", 14 to "Moderate", 15 to "Severe", 21 to "Severe")
            .forEach { (score, band) -> assertEquals("$score", band, gad.band(score).label) }
    }

    @Test fun bandExplanationsAreNotDiagnoses() {
        (phq.bands + gad.bands).forEach {
            assertTrue(it.explanation.contains("not a diagnosis"))
            // No diagnostic labels such as "you have depression" / "you have an anxiety disorder".
            assertFalse(Regex("you (have|suffer from) (depression|anxiety|a |an )", RegexOption.IGNORE_CASE).containsMatchIn(it.explanation))
            assertFalse(it.explanation.contains("disorder", ignoreCase = true))
            assertFalse(it.explanation.contains("treatment", ignoreCase = true))
        }
    }

    @Test fun item9Trigger() {
        assertFalse(Questionnaires.isItem9Positive(QuestionnaireType.PHQ9, 8, 0))
        (1..3).forEach { assertTrue(Questionnaires.isItem9Positive(QuestionnaireType.PHQ9, 8, it)) }
        assertFalse(Questionnaires.isItem9Positive(QuestionnaireType.PHQ9, 7, 3))
        assertFalse(Questionnaires.isItem9Positive(QuestionnaireType.GAD7, 6, 3))
    }
}

class QuestionnaireFlowTest {
    private fun phqFlow() = QuestionnaireFlow(Questionnaires.PHQ9).also { it.start() }

    @Test fun introDeclineExits() {
        val f = QuestionnaireFlow(Questionnaires.PHQ9)
        assertEquals(State.Intro, f.state)
        assertEquals(State.Exited, f.decline())
    }

    @Test fun positiveItem9ShowsSupportImmediatelyBeforeCompletion() {
        val f = phqFlow()
        repeat(8) { f.answer(it, 0) }
        assertEquals(State.Support(8), f.answer(8, 1))
        assertTrue(f.item9EverPositive)
        assertTrue(f.supportShown)
        assertNull(f.toRecord(0))                         // not finished; support did not require completion
        assertEquals(State.Difficulty, f.continueAfterSupport())
    }

    @Test fun item9NotAtAllDoesNotTriggerSupport() {
        val f = phqFlow()
        repeat(8) { f.answer(it, 2) }
        assertEquals(State.Difficulty, f.answer(8, 0))
        assertFalse(f.supportShown)
    }

    @Test fun item9TriggersEvenWhenOtherAnswersAreLow() {
        val f = phqFlow()
        repeat(8) { f.answer(it, 0) }
        assertTrue(f.answer(8, 3) is State.Support)
    }

    @Test fun stopFromSupportDiscardsAnswers() {
        val f = phqFlow()
        repeat(8) { f.answer(it, 1) }
        f.answer(8, 2)
        assertEquals(State.Exited, f.exit())
        assertTrue(f.answers.all { it == null })
        assertNull(f.toRecord(0))
    }

    @Test fun exitMidwayDiscards() {
        val f = phqFlow()
        f.answer(0, 3); f.answer(1, 3)
        f.exit()
        assertEquals(State.Exited, f.state)
        assertTrue(f.answers.all { it == null })
    }

    @Test fun fullPhq9FlowProducesRecord() {
        val f = phqFlow()
        val answers = listOf(1, 2, 3, 0, 1, 2, 3, 1, 1)
        answers.forEachIndexed { i, a -> f.answer(i, a); if (f.state is State.Support) f.continueAfterSupport() }
        assertEquals(State.Difficulty, f.state)
        assertEquals(State.Review, f.chooseDifficulty(2))
        val r = f.submit() as State.Result
        assertEquals(14, r.total)
        assertEquals("Moderate", r.band.label)
        val rec = f.toRecord(1_000)!!
        assertEquals(answers, rec.answers)
        assertEquals(2, rec.difficulty)
        assertTrue(rec.item9Positive)
    }

    @Test fun gad7HasNoDifficultyStep() {
        val f = QuestionnaireFlow(Questionnaires.GAD7).also { it.start() }
        repeat(6) { f.answer(it, 1) }
        assertEquals(State.Review, f.answer(6, 1))
        assertEquals(7, (f.submit() as State.Result).total)
        assertFalse(f.toRecord(0)!!.item9Positive)
    }

    @Test fun backNavigation() {
        val f = phqFlow()
        f.answer(0, 1)
        assertEquals(State.Question(0), f.back())
        assertEquals(State.Intro, f.back())
    }

    @Test fun cannotSubmitIncomplete() {
        val f = phqFlow()
        f.answer(0, 1)
        f.submit()
        assertEquals(State.Question(1), f.state)
    }
}
