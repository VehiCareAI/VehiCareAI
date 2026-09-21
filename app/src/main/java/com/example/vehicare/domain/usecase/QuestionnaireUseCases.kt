package com.example.vehicare.domain.usecase

import com.example.vehicare.domain.diagnostic.knowledgebase.Question
import com.example.vehicare.domain.diagnostic.knowledgebase.QuestionBank
import com.example.vehicare.domain.diagnostic.knowledgebase.QuestionInput
import com.example.vehicare.domain.diagnostic.models.SymptomEvidence

/**
 * Turns raw questionnaire answers into engine evidence and decides which questions are relevant.
 *
 * Rules (Section 5.9):
 *  - explicit positive options produce present evidence,
 *  - explicit "No" options produce absent (negative) evidence,
 *  - "Unsure" and skipped questions produce NO evidence at all.
 */
class QuestionnaireEngine(private val questionBank: QuestionBank = QuestionBank) {

    /** Multi-select answers are stored as a comma-separated list of option ids. */
    fun selectedOptionIds(question: Question, rawAnswer: String?): List<String> = when {
        rawAnswer.isNullOrBlank() -> emptyList()
        question.input == QuestionInput.MULTI -> rawAnswer.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        else -> listOf(rawAnswer)
    }

    fun presentEvidence(answers: Map<String, String>): Set<String> =
        evidence(answers).first

    fun absentEvidence(answers: Map<String, String>): Set<String> =
        evidence(answers).second

    private fun evidence(answers: Map<String, String>): Pair<Set<String>, Set<String>> {
        val present = linkedSetOf<String>()
        val absent = linkedSetOf<String>()
        answers.forEach { (questionId, rawAnswer) ->
            val question = questionBank.question(questionId) ?: return@forEach
            selectedOptionIds(question, rawAnswer).forEach { optionId ->
                val option = question.options.firstOrNull { it.id == optionId } ?: return@forEach
                present += option.presentEvidenceIds
                absent += option.absentEvidenceIds
            }
        }
        // An option can never be both present and absent for the same session.
        return present - absent to absent - present
    }

    fun reportedSeverity(answers: Map<String, String>): Int? =
        answers[QuestionBank.SEVERITY_QUESTION_ID]?.toIntOrNull()?.coerceIn(1, 5)

    fun onsetId(answers: Map<String, String>): String? =
        answers[QuestionBank.ONSET_QUESTION_ID]?.takeIf { it.isNotBlank() }

    fun toSymptomEvidence(
        answers: Map<String, String>,
        selectedCategoryIds: Set<String>,
        assessmentTypeId: String
    ): SymptomEvidence {
        val (present, absent) = evidence(answers)
        return SymptomEvidence(
            presentEvidenceIds = present,
            absentEvidenceIds = absent,
            selectedCategoryIds = selectedCategoryIds,
            assessmentTypeId = assessmentTypeId,
            reportedSeverity = reportedSeverity(answers),
            onsetId = onsetId(answers),
            answeredQuestionIds = answers.keys.toSet()
        )
    }

    /**
     * Questions relevant to this session, in a stable order: base questions, then the selected
     * categories' questions, then rule-based follow-ups whose trigger finding was reported.
     * Evaluated to a fixpoint because follow-ups can unlock further follow-ups.
     */
    fun visibleQuestions(
        answers: Map<String, String>,
        selectedCategoryIds: Set<String>
    ): List<Question> {
        var previousIds: List<String> = emptyList()
        var visible: List<Question> = emptyList()
        var guard = 0
        while (guard++ < MAX_FIXPOINT_ITERATIONS) {
            val present = presentEvidence(answers)
            val severity = reportedSeverity(answers)
            visible = questionBank.allQuestions.filter { question ->
                val categoryMatch = question.categoryIds.isEmpty() ||
                    question.categoryIds.any { it in selectedCategoryIds }
                val conditionMatch = question.showIf?.isSatisfiedBy(present, severity) ?: true
                categoryMatch && conditionMatch
            }
            val ids = visible.map { it.id }
            if (ids == previousIds) break
            previousIds = ids
        }
        return visible
    }

    /** Questions still unanswered, in order. */
    fun pendingQuestions(
        answers: Map<String, String>,
        selectedCategoryIds: Set<String>
    ): List<Question> = visibleQuestions(answers, selectedCategoryIds)
        .filterNot { answers.containsKey(it.id) }

    /** Progress across the visible set. */
    fun progress(answers: Map<String, String>, selectedCategoryIds: Set<String>): Pair<Int, Int> {
        val visible = visibleQuestions(answers, selectedCategoryIds)
        val answered = visible.count { answers.containsKey(it.id) }
        return answered to visible.size
    }

    companion object {
        private const val MAX_FIXPOINT_ITERATIONS = 10
    }
}
