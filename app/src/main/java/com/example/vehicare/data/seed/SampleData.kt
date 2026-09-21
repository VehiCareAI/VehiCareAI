package com.example.vehicare.data.seed

import com.example.vehicare.domain.diagnostic.bayesian.NaiveBayesDiagnosticEngine
import com.example.vehicare.domain.diagnostic.knowledgebase.QuestionBank
import com.example.vehicare.domain.diagnostic.knowledgebase.VehiCareKnowledgeBase
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentStatus
import com.example.vehicare.domain.model.AssessmentType
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.usecase.QuestionnaireEngine

/**
 * The Section 9 demo content: one plausible vehicle and one already completed assessment so the app
 * can be explored immediately after install.
 *
 * The assessment never carries persisted diagnostic results — the seeded evidence was collected by
 * the documented questionnaire, and the engine output is computed on demand from it. Nothing here
 * hardcodes a probability: the answers below are ordinary user answers and the same questionnaire
 * engine that the live app uses turns them into evidence.
 */
object SampleData {

    /** Stable ids so seeding stays idempotent and reports can be reopened across launches. */
    const val SAMPLE_ASSESSMENT_ID = "assessment_sample_vios"

    private const val VEHICLE_AGE_MS = 3L * 24 * 60 * 60 * 1000
    private const val ASSESSMENT_STARTED_AGO_MS = 30L * 60 * 1000

    /** Category selection of the demo assessment (starting trouble, electrical, warning lights). */
    val SAMPLE_CATEGORY_IDS: List<String> = listOf("starting", "electrical", "warning_lights")

    /**
     * Demo questionnaire answers.
     *
     * Positive findings: slow cranking, dim lights, cranking without starting.
     * Negative findings: no clicking, no engine noise, no power loss, no warning lights, no braking
     * symptoms, no smoke. "Unsure" answers (battery age) deliberately contribute no evidence.
     */
    val SAMPLE_ANSWERS: Map<String, String> = linkedMapOf(
        "q_starting" to "slow_crank",
        "q_electrical" to "occasionally",
        "q_difficulty_starting" to "yes",
        "q_clicking_start" to "no",
        "q_engine_noise" to "none",
        "q_performance" to "no",
        "q_warning_lights" to "none",
        "q_braking" to "none",
        "q_smoke" to "none",
        "q_battery_age" to "unsure",
        QuestionBank.ONSET_QUESTION_ID to "within_week",
        QuestionBank.SEVERITY_QUESTION_ID to "3"
    )

    /** Toyota Vios 2020, gasoline, automatic; older than the seeded assessment for a sane timeline. */
    fun sampleVehicle(now: Long): Vehicle = Vehicle.sample(now = now - VEHICLE_AGE_MS)

    /**
     * The demo assessment with its questionnaire answers and the evidence they imply.
     *
     * It is returned as a completed assessment whose completion time, engine version and knowledge-base
     * version are stamped by `AssessmentRepository.complete()` when the seeder runs the engine over it,
     * so the demo ranking is computed exactly like a real session (never hardcoded).
     */
    fun sampleAssessment(
        vehicleId: String,
        now: Long,
        questionnaireEngine: QuestionnaireEngine = QuestionnaireEngine()
    ): Assessment {
        val startedAt = now - ASSESSMENT_STARTED_AGO_MS
        return Assessment(
            id = SAMPLE_ASSESSMENT_ID,
            vehicleId = vehicleId,
            assessmentTypeId = AssessmentType.SYMPTOM_BASED.id,
            selectedCategoryIds = SAMPLE_CATEGORY_IDS,
            answers = SAMPLE_ANSWERS,
            presentEvidenceIds = questionnaireEngine.presentEvidence(SAMPLE_ANSWERS),
            absentEvidenceIds = questionnaireEngine.absentEvidence(SAMPLE_ANSWERS),
            reportedSeverity = questionnaireEngine.reportedSeverity(SAMPLE_ANSWERS),
            onsetId = questionnaireEngine.onsetId(SAMPLE_ANSWERS),
            status = AssessmentStatus.COMPLETED,
            engineVersion = NaiveBayesDiagnosticEngine.ENGINE_VERSION,
            knowledgeBaseVersion = VehiCareKnowledgeBase.VERSION,
            disclaimerAccepted = true,
            resolved = false,
            startedAt = startedAt,
            completedAt = null
        )
    }
}
