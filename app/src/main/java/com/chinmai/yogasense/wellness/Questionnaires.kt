package com.chinmai.yogasense.wellness

/**
 * PHQ-9 and GAD-7, reproduced VERBATIM from the official English forms published at
 * phqscreeners.com ("Developed by Drs. Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke and
 * colleagues, with an educational grant from Pfizer Inc. No permission required to reproduce,
 * translate, display or distribute."). Verified 2026-11-14 against PHQ-9_English.pdf (2010) and
 * GAD-7_English.pdf (v2, 2010). Do not edit wording, response labels or scoring.
 *
 * Severity bands are the standard published cut-offs (PHQ-9: Kroenke et al. 2001; GAD-7: Spitzer
 * et al. 2006). They describe symptom severity on a screening instrument and are NOT a diagnosis.
 */
enum class QuestionnaireType { PHQ9, GAD7 }

data class ResponseOption(val label: String, val points: Int)

data class SeverityBand(val min: Int, val max: Int, val label: String, val explanation: String)

data class Questionnaire(
    val type: QuestionnaireType,
    val title: String,
    val stem: String,
    val items: List<String>,
    val options: List<ResponseOption>,
    val difficultyQuestion: String?,
    val difficultyOptions: List<String>,
    val bands: List<SeverityBand>,
    val source: String,
) {
    val maxScore: Int get() = items.size * options.maxOf { it.points }

    /** Total score; null unless every item has a valid answer (no prorating). */
    fun score(answers: List<Int?>): Int? {
        if (answers.size != items.size) return null
        if (answers.any { it == null || it !in options.map { o -> o.points } }) return null
        return answers.sumOf { it!! }
    }

    fun band(total: Int): SeverityBand = bands.first { total in it.min..it.max }
}

object Questionnaires {
    /** Shared standard response scale (0–3). */
    val FREQUENCY = listOf(
        ResponseOption("Not at all", 0),
        ResponseOption("Several days", 1),
        ResponseOption("More than half the days", 2),
        ResponseOption("Nearly every day", 3),
    )

    private const val SOURCE =
        "Developed by Drs. Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke and colleagues, " +
            "with an educational grant from Pfizer Inc. No permission required to reproduce, translate, display or distribute."

    private const val NOT_DIAGNOSIS = "This is a screening score, not a diagnosis."

    val PHQ9 = Questionnaire(
        type = QuestionnaireType.PHQ9,
        title = "PHQ-9",
        stem = "Over the last 2 weeks, how often have you been bothered by any of the following problems?",
        items = listOf(
            "Little interest or pleasure in doing things",
            "Feeling down, depressed, or hopeless",
            "Trouble falling or staying asleep, or sleeping too much",
            "Feeling tired or having little energy",
            "Poor appetite or overeating",
            "Feeling bad about yourself — or that you are a failure or have let yourself or your family down",
            "Trouble concentrating on things, such as reading the newspaper or watching television",
            "Moving or speaking so slowly that other people could have noticed? Or the opposite — being so fidgety or restless that you have been moving around a lot more than usual",
            "Thoughts that you would be better off dead or of hurting yourself in some way",
        ),
        options = FREQUENCY,
        difficultyQuestion = "If you checked off any problems, how difficult have these problems made it for you to do your work, take care of things at home, or get along with other people?",
        difficultyOptions = listOf("Not difficult at all", "Somewhat difficult", "Very difficult", "Extremely difficult"),
        bands = listOf(
            SeverityBand(0, 4, "Minimal", "Your answers suggest few or no depression symptoms over the past two weeks. $NOT_DIAGNOSIS"),
            SeverityBand(5, 9, "Mild", "Your answers fall in the range often described as mild symptoms. $NOT_DIAGNOSIS"),
            SeverityBand(10, 14, "Moderate", "Your answers fall in the range often described as moderate symptoms. Talking with a qualified health professional can help you understand what this means for you. $NOT_DIAGNOSIS"),
            SeverityBand(15, 19, "Moderately severe", "Your answers fall in the range often described as moderately severe symptoms. Consider talking with a qualified health professional about how you have been feeling. $NOT_DIAGNOSIS"),
            SeverityBand(20, 27, "Severe", "Your answers fall in the range often described as severe symptoms. Please consider reaching out to a qualified health professional or someone you trust. $NOT_DIAGNOSIS"),
        ),
        source = SOURCE,
    )

    val GAD7 = Questionnaire(
        type = QuestionnaireType.GAD7,
        title = "GAD-7",
        stem = "Over the last 2 weeks, how often have you been bothered by the following problems?",
        items = listOf(
            "Feeling nervous, anxious or on edge",
            "Not being able to stop or control worrying",
            "Worrying too much about different things",
            "Trouble relaxing",
            "Being so restless that it is hard to sit still",
            "Becoming easily annoyed or irritable",
            "Feeling afraid as if something awful might happen",
        ),
        options = FREQUENCY,
        difficultyQuestion = null, // not part of the official GAD-7 form used here
        difficultyOptions = emptyList(),
        bands = listOf(
            SeverityBand(0, 4, "Minimal", "Your answers suggest few or no anxiety symptoms over the past two weeks. $NOT_DIAGNOSIS"),
            SeverityBand(5, 9, "Mild", "Your answers fall in the range often described as mild anxiety symptoms. $NOT_DIAGNOSIS"),
            SeverityBand(10, 14, "Moderate", "Your answers fall in the range often described as moderate anxiety symptoms. Talking with a qualified health professional can help you understand what this means for you. $NOT_DIAGNOSIS"),
            SeverityBand(15, 21, "Severe", "Your answers fall in the range often described as severe anxiety symptoms. Please consider reaching out to a qualified health professional or someone you trust. $NOT_DIAGNOSIS"),
        ),
        source = SOURCE,
    )

    fun of(type: QuestionnaireType) = when (type) { QuestionnaireType.PHQ9 -> PHQ9; QuestionnaireType.GAD7 -> GAD7 }

    /** PHQ-9 item 9 (index 8). Any answer other than "Not at all" triggers the support screen. */
    const val PHQ9_ITEM9_INDEX = 8

    fun isItem9Positive(type: QuestionnaireType, itemIndex: Int, points: Int): Boolean =
        type == QuestionnaireType.PHQ9 && itemIndex == PHQ9_ITEM9_INDEX && points > 0
}
