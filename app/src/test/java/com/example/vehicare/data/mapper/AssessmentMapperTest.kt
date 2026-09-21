package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.entities.AssessmentResultEntity
import com.example.vehicare.domain.diagnostic.models.Evidence
import com.example.vehicare.domain.diagnostic.models.RankedIssue
import com.example.vehicare.domain.diagnostic.models.SafetyAlert
import com.example.vehicare.domain.diagnostic.models.SafetyLevel
import com.example.vehicare.domain.diagnostic.models.Severity
import com.example.vehicare.domain.diagnostic.models.VehicleSystem
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentStatus
import com.example.vehicare.domain.model.AssessmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssessmentMapperTest {

    private val assessment = Assessment(
        id = "a1",
        vehicleId = "v1",
        assessmentTypeId = AssessmentType.WARNING_LIGHTS.id,
        selectedCategoryIds = listOf("warning_lights", "electrical"),
        answers = linkedMapOf("q_warning_lights" to "battery", "q_severity" to "4"),
        presentEvidenceIds = linkedSetOf("warning_battery_light", "dim_lights"),
        absentEvidenceIds = linkedSetOf("oil_pressure_warning"),
        reportedSeverity = 4,
        onsetId = "within_week",
        status = AssessmentStatus.DRAFT,
        engineVersion = "",
        knowledgeBaseVersion = "",
        disclaimerAccepted = true,
        resolved = false,
        startedAt = 1_000L,
        completedAt = null
    )

    @Test
    fun `draft round trips with answers, evidence and timeline intact`() {
        val entity = assessment.toEntity()
        assertEquals(AssessmentStatus.DRAFT.id, entity.status)
        assertNull(entity.completedAt)
        val restored = entity.toDomain()
        assertEquals(assessment, restored)
        assertEquals(listOf("warning_battery_light", "dim_lights"), restored.presentEvidenceIds.toList())
    }

    @Test
    fun `completed assessment round trips`() {
        val completed = assessment.copy(
            status = AssessmentStatus.COMPLETED,
            engineVersion = "engine-1",
            knowledgeBaseVersion = "kb-1",
            completedAt = 2_000L,
            resolved = true
        )
        val restored: Assessment = completed.toEntity().toDomain()
        assertEquals(AssessmentStatus.COMPLETED, restored.status)
        assertEquals(2_000L, restored.completedAt)
        assertEquals("engine-1", restored.engineVersion)
        assertEquals("kb-1", restored.knowledgeBaseVersion)
        assertTrue(restored.resolved)
        assertEquals(AssessmentType.WARNING_LIGHTS, restored.type)
    }

    @Test
    fun `status parsing accepts the stable id, the enum name and rejects anything else`() {
        assertEquals(AssessmentStatus.COMPLETED, assessmentStatusFromId("completed"))
        assertEquals(AssessmentStatus.COMPLETED, assessmentStatusFromId("COMPLETED"))
        assertEquals(AssessmentStatus.DRAFT, assessmentStatusFromId("draft"))
        assertEquals(AssessmentStatus.DRAFT, assessmentStatusFromId("unexpected"))
    }

    @Test
    fun `severity parsing falls back to medium for unknown text`() {
        assertEquals(Severity.CRITICAL, severityFromName("CRITICAL"))
        assertEquals(Severity.LOW, severityFromName("LOW"))
        assertEquals(Severity.MEDIUM, severityFromName("legacy-value"))
    }

    @Test
    fun `safety level parsing never downplays an unknown level`() {
        assertEquals(SafetyLevel.URGENT, safetyLevelFromName("URGENT"))
        assertEquals(SafetyLevel.ADVISORY, safetyLevelFromName("ADVISORY"))
        assertEquals(SafetyLevel.URGENT, safetyLevelFromName("legacy-value"))
    }

    @Test
    fun `ranked issue maps to a deterministic result row`() {
        val issue = RankedIssue(
            hypothesisId = "weak_battery",
            name = "Weak or failing battery",
            system = VehicleSystem.ELECTRICAL,
            rank = 1,
            posteriorProbability = 0.42,
            priorProbability = 0.09,
            severity = Severity.HIGH,
            summary = "summary",
            explanation = "explanation",
            supportingEvidence = listOf(
                Evidence("slow_cranking", "Engine cranks slowly", VehicleSystem.STARTING_IGNITION)
            ),
            missingEvidence = listOf(
                Evidence("battery_older_than_3y", "Battery is three or more years old", VehicleSystem.ELECTRICAL)
            ),
            contradictingEvidence = emptyList(),
            possibleCauses = listOf("aged battery"),
            recommendedChecks = listOf("load test"),
            safetyWarnings = emptyList(),
            recommendedServiceCategory = "Battery service",
            evidenceStrength = 0.5,
            matchedEvidenceCount = 2,
            consideredEvidenceCount = 4
        )

        val entity = issue.toResultEntity(assessmentId = "a1", generatedAt = 5_000L)

        assertEquals("a1#1", entity.id)
        assertEquals("a1", entity.assessmentId)
        assertEquals("weak_battery", entity.hypothesisId)
        assertEquals(1, entity.rank)
        assertEquals(0.42, entity.posteriorProbability, 0.0)
        assertEquals("HIGH", entity.severity)
        assertEquals(listOf("slow_cranking"), entity.supportingEvidenceIds)
        assertEquals(listOf("battery_older_than_3y"), entity.missingEvidenceIds)
        assertEquals(emptyList<String>(), entity.contradictingEvidenceIds)
        assertEquals(5_000L, entity.generatedAt)
    }

    @Test
    fun `result row round trips back to the domain model`() {
        val entity = AssessmentResultEntity(
            id = "a1#2",
            assessmentId = "a1",
            hypothesisId = "faulty_alternator",
            rank = 2,
            posteriorProbability = 0.25,
            severity = "MEDIUM",
            supportingEvidenceIds = listOf("dim_lights"),
            missingEvidenceIds = listOf("warning_battery_light"),
            contradictingEvidenceIds = listOf("clicking_on_start"),
            generatedAt = 7_000L
        )
        val domain = entity.toDomain()
        assertEquals("faulty_alternator", domain.hypothesisId)
        assertEquals(2, domain.rank)
        assertEquals(Severity.MEDIUM, domain.severity)
        assertEquals(listOf("dim_lights"), domain.supportingEvidenceIds)
        assertEquals(listOf("clicking_on_start"), domain.contradictingEvidenceIds)
    }

    @Test
    fun `safety alert snapshot keeps the wording it was generated with`() {
        val alert = SafetyAlert(
            id = "brake_performance",
            title = "Possible braking system fault",
            message = "Consider having the brakes inspected.",
            level = SafetyLevel.URGENT,
            relatedEvidenceIds = setOf("brake_soft_pedal")
        )
        val entity = alert.toAlertEntity("a1")
        assertEquals("a1", entity.assessmentId)
        assertEquals("brake_performance", entity.alertId)
        assertEquals("URGENT", entity.level)
        val domain = entity.toDomain()
        assertEquals(alert.title, domain.title)
        assertEquals(alert.message, domain.message)
        assertEquals("URGENT", domain.level)
    }
}
