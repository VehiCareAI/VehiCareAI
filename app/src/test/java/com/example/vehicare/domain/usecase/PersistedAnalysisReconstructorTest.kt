package com.example.vehicare.domain.usecase

import com.example.vehicare.domain.diagnostic.bayesian.NaiveBayesDiagnosticEngine
import com.example.vehicare.domain.diagnostic.knowledgebase.VehiCareKnowledgeBase
import com.example.vehicare.domain.diagnostic.models.Severity
import com.example.vehicare.domain.diagnostic.models.SufficiencyStatus
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentResult
import com.example.vehicare.domain.model.AssessmentSafetyAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The persisted result/alert rows are the authoritative snapshot: a saved report must keep its
 * stored numbers even if the engine would now compute something else (Section 8).
 */
class PersistedAnalysisReconstructorTest {

    private val knowledgeBase = VehiCareKnowledgeBase.instance
    private val engine = NaiveBayesDiagnosticEngine(knowledgeBase)

    private val assessment = Assessment(
        id = "a1",
        vehicleId = "v1",
        presentEvidenceIds = linkedSetOf("slow_cranking", "dim_lights"),
        engineVersion = "vehicare-bayes-1.0.0",
        knowledgeBaseVersion = knowledgeBase.version,
        startedAt = 500L,
        completedAt = 1_000L,
        answers = mapOf("q_starting" to "slow_cranking")
    )

    private fun reconstruct(
        storedResults: List<AssessmentResult>,
        storedAlerts: List<AssessmentSafetyAlert> = emptyList()
    ) = PersistedAnalysisReconstructor.reconstruct(
        assessment = assessment,
        vehicle = null,
        evidence = assessment.toSymptomEvidence(),
        storedResults = storedResults,
        storedAlerts = storedAlerts,
        knowledgeBase = knowledgeBase,
        engine = engine
    )

    @Test
    fun `stored snapshot wins over recomputation`() {
        val analysis = reconstruct(
            storedResults = listOf(
                AssessmentResult(
                    assessmentId = "a1",
                    hypothesisId = "weak_battery",
                    rank = 1,
                    posteriorProbability = 0.4321,
                    severity = Severity.HIGH,
                    supportingEvidenceIds = listOf("slow_cranking"),
                    missingEvidenceIds = listOf("battery_older_than_3y"),
                    contradictingEvidenceIds = emptyList()
                )
            )
        )

        assertEquals(SufficiencyStatus.SUFFICIENT, analysis.sufficiency)
        val issue = analysis.rankedIssues.single()
        assertEquals("weak_battery", issue.hypothesisId)
        assertEquals(0.4321, issue.posteriorProbability, 1e-9)
        assertEquals(Severity.HIGH, issue.severity)
        assertEquals(1, issue.matchedEvidenceCount)
        assertEquals(1, issue.consideredEvidenceCount)
        assertEquals(0.4321 + analysis.undeterminedProbability, 1.0, 1e-9)
        assertEquals(2, analysis.reportedEvidence.size)
        assertEquals(1_000L, analysis.generatedAtEpochMillis)
        assertEquals("vehicare-bayes-1.0.0", analysis.engineVersion)
    }

    @Test
    fun `insufficient snapshot keeps reported evidence and stored alerts`() {
        val analysis = reconstruct(
            storedResults = emptyList(),
            storedAlerts = listOf(
                AssessmentSafetyAlert(
                    assessmentId = "a1",
                    alertId = "brake_performance",
                    title = "Possible braking system fault",
                    message = "Have the brakes inspected.",
                    level = "URGENT"
                )
            )
        )

        assertEquals(SufficiencyStatus.INSUFFICIENT_EVIDENCE, analysis.sufficiency)
        assertTrue(analysis.rankedIssues.isEmpty())
        assertEquals(2, analysis.reportedEvidence.size)
        assertEquals(1, analysis.safetyAlerts.size)
        assertTrue(analysis.hasSafetyAlerts)
        assertEquals(1, analysis.answeredQuestionCount)
    }

    @Test
    fun `unknown stored hypothesis falls back to a fresh evaluation`() {
        val analysis = reconstruct(
            storedResults = listOf(
                AssessmentResult(
                    assessmentId = "a1",
                    hypothesisId = "ghost_hypothesis",
                    rank = 1,
                    posteriorProbability = 0.9,
                    severity = Severity.LOW
                )
            )
        )

        assertFalse(analysis.rankedIssues.any { it.hypothesisId == "ghost_hypothesis" })
    }
}
