package com.chinmai.yogasense.wellness

/**
 * Pure state machine for taking a questionnaire. UI renders [state]; actions return the next state.
 *
 * Intro → (decline → Exited) → Question(i) … → [Difficulty (PHQ-9 only, optional)] → Review →
 * Result. Selecting a positive answer to PHQ-9 item 9 IMMEDIATELY moves to [State.Support]
 * (before the questionnaire is finished); from there the user can continue or exit.
 * Exiting at any point discards answers; nothing is saved without consent.
 */
class QuestionnaireFlow(val questionnaire: Questionnaire) {
    sealed interface State {
        data object Intro : State
        data class Question(val index: Int) : State
        data class Support(val returnToIndex: Int) : State
        data object Difficulty : State
        data object Review : State
        data class Result(val total: Int, val band: SeverityBand) : State
        data object Exited : State
    }

    var state: State = State.Intro
        private set
    val answers: MutableList<Int?> = MutableList(questionnaire.items.size) { null }
    var difficulty: Int? = null
        private set
    /** True once a positive item-9 answer was given (stays true even if later changed). */
    var item9EverPositive = false
        private set
    var supportShown = false
        private set

    val progress: Float get() = answers.count { it != null } / answers.size.toFloat()

    fun start(): State = set(State.Question(0))
    fun decline(): State = set(State.Exited)
    fun exit(): State { answers.fill(null); difficulty = null; return set(State.Exited) }

    fun answer(index: Int, points: Int): State {
        require(state is State.Question && (state as State.Question).index == index)
        require(points in questionnaire.options.map { it.points })
        answers[index] = points
        if (Questionnaires.isItem9Positive(questionnaire.type, index, points)) {
            item9EverPositive = true
            supportShown = true
            return set(State.Support(index))
        }
        return next(index)
    }

    /** From the support screen, continue the questionnaire after item 9. */
    fun continueAfterSupport(): State {
        val s = state as? State.Support ?: return state
        return next(s.returnToIndex)
    }

    fun back(): State = when (val s = state) {
        is State.Question -> if (s.index == 0) set(State.Intro) else set(State.Question(s.index - 1))
        State.Difficulty -> set(State.Question(questionnaire.items.lastIndex))
        State.Review -> set(if (questionnaire.difficultyQuestion != null) State.Difficulty else State.Question(questionnaire.items.lastIndex))
        else -> s
    }

    fun chooseDifficulty(option: Int?): State {
        difficulty = option
        return set(State.Review)
    }

    fun submit(): State {
        val total = questionnaire.score(answers) ?: return state
        return set(State.Result(total, questionnaire.band(total)))
    }

    fun toRecord(nowMillis: Long): QuestionnaireRecord? {
        val r = state as? State.Result ?: return null
        val item9 = questionnaire.type == QuestionnaireType.PHQ9 && (answers[Questionnaires.PHQ9_ITEM9_INDEX] ?: 0) > 0
        return QuestionnaireRecord(0, questionnaire.type, nowMillis, answers.map { it!! }, r.total, r.band.label, difficulty, item9)
    }

    private fun next(index: Int): State = when {
        index < questionnaire.items.lastIndex -> set(State.Question(index + 1))
        questionnaire.difficultyQuestion != null -> set(State.Difficulty)
        else -> set(State.Review)
    }

    private fun set(s: State): State { state = s; return s }
}
