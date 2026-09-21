package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.dao.AssessmentSummaryRow
import com.example.vehicare.data.local.entities.DiagnosticHypothesisEntity
import com.example.vehicare.domain.diagnostic.models.VehicleSystem
import com.example.vehicare.domain.model.AssessmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryMapperTest {

    private fun row(
        vehicleNickname: String = "",
        presentEvidenceIds: Set<String> = linkedSetOf("slow_cranking", "dim_lights"),
        topHypothesisId: String? = "weak_battery",
        topIssueName: String? = "Weak or failing battery",
        topSystemId: String? = "electrical",
        topProbability: Double? = 0.41,
        topSupportingEvidenceIds: Set<String> = emptySet(),
        topContradictingEvidenceIds: Set<String> = emptySet(),
        issueCount: Int = 4,
        safetyAlertCount: Int = 1,
        completedAt: Long? = 1_756_000_000_000L,
        resolved: Boolean = false,
        assessmentTypeId: String = AssessmentType.SYMPTOM_BASED.id
    ) = AssessmentSummaryRow(
        assessmentId = "a1",
        vehicleId = "v1",
        vehicleNickname = vehicleNickname,
        vehicleMake = "Toyota",
        vehicleModel = "Vios",
        vehicleYear = 2020,
        assessmentTypeId = assessmentTypeId,
        completedAt = completedAt,
        presentEvidenceIds = presentEvidenceIds,
        resolved = resolved,
        topHypothesisId = topHypothesisId,
        topIssueName = topIssueName,
        topSystemId = topSystemId,
        topProbability = topProbability,
        topSupportingEvidenceIds = topSupportingEvidenceIds,
        topContradictingEvidenceIds = topContradictingEvidenceIds,
        issueCount = issueCount,
        safetyAlertCount = safetyAlertCount
    )

    /** Catalogue snapshot used when the knowledge base no longer knows an id. */
    private val catalogue: Map<String, DiagnosticHypothesisEntity> = mapOf(
        "weak_battery" to DiagnosticHypothesisEntity(
            id = "weak_battery",
            name = "Weak or failing battery (catalogue)",
            systemId = "electrical",
            severity = "HIGH",
            description = "",
            serviceCategory = "Battery service",
            updatedAt = 0L
        )
    )

    private fun map(
        row: AssessmentSummaryRow,
        knowledgeBaseNames: Map<String, String> = mapOf("weak_battery" to "Weak or failing battery"),
        knowledgeBaseSystems: Map<String, VehicleSystem> = mapOf("weak_battery" to VehicleSystem.ELECTRICAL),
        evidenceLabels: Map<String, String> = mapOf("slow_cranking" to "Engine cranks slowly"),
        useCatalogue: Boolean = true
    ) = row.toSummary(
        hypothesisName = { id -> knowledgeBaseNames[id] ?: if (useCatalogue) catalogue[id]?.name else null },
        hypothesisSystem = { id ->
            knowledgeBaseSystems[id] ?: if (useCatalogue) {
                catalogue[id]?.systemId?.let { VehicleSystem.fromId(it) }
            } else {
                null
            }
        },
        evidenceLabel = { id -> evidenceLabels[id] }
    )

    @Test
    fun `summary exposes rank-1 evidence strength counts`() {
        val summary = map(
            row(
                topSupportingEvidenceIds = linkedSetOf("slow_cranking", "dim_lights"),
                topContradictingEvidenceIds = linkedSetOf("clicking_on_start")
            )
        )

        assertEquals(2, summary.topMatchedEvidenceCount)
        assertEquals(3, summary.topConsideredEvidenceCount)
    }

    @Test
    fun `summary carries vehicle, type, counts and rank-1 result`() {
        val summary = map(row())

        assertEquals("a1", summary.assessmentId)
        assertEquals("v1", summary.vehicleId)
        assertEquals("2020 Toyota Vios", summary.vehicleName)
        assertEquals(AssessmentType.SYMPTOM_BASED.label, summary.typeLabel)
        assertEquals(1_756_000_000_000L, summary.completedAt)
        assertEquals("Engine cranks slowly", summary.mainSymptomLabel)
        assertEquals("Weak or failing battery", summary.topIssueName)
        assertEquals(0.41, summary.topProbability!!, 0.0)
        assertEquals(4, summary.issueCount)
        assertEquals("Electrical System", summary.primarySystem)
        assertTrue(summary.hasSafetyAlerts)
        assertFalse(summary.resolved)
    }

    @Test
    fun `vehicle nickname wins over year make model`() {
        assertEquals("My daily car", map(row(vehicleNickname = "My daily car")).vehicleName)
    }

    @Test
    fun `type label follows the stored assessment type`() {
        val summary = map(row(assessmentTypeId = AssessmentType.WARNING_LIGHTS.id))
        assertEquals(AssessmentType.WARNING_LIGHTS.label, summary.typeLabel)
    }

    @Test
    fun `missing completedAt falls back to zero and no alerts means no warning flag`() {
        val summary = map(row(completedAt = null, safetyAlertCount = 0))
        assertEquals(0L, summary.completedAt)
        assertFalse(summary.hasSafetyAlerts)
    }

    @Test
    fun `main symptom label falls back to the raw evidence id`() {
        val summary = map(row(presentEvidenceIds = linkedSetOf("legacy_evidence")), evidenceLabels = emptyMap())
        assertEquals("legacy_evidence", summary.mainSymptomLabel)
    }

    @Test
    fun `insufficient assessment without reported evidence has an empty symptom label`() {
        assertEquals("", map(row(presentEvidenceIds = emptySet())).mainSymptomLabel)
    }

    @Test
    fun `top issue name falls back to the catalogue snapshot`() {
        val summary = map(row(topIssueName = null), knowledgeBaseNames = emptyMap())
        assertEquals("Weak or failing battery (catalogue)", summary.topIssueName)
    }

    @Test
    fun `primary system falls back to the hypothesis system then to other`() {
        // Row has no system id, so it is resolved from the hypothesis lookup (knowledge base first).
        assertEquals("Electrical System", map(row(topSystemId = null)).primarySystem)

        // Catalogue snapshot answers when the knowledge base has forgotten the hypothesis.
        val onlyCatalogue = map(
            row(topSystemId = null),
            knowledgeBaseNames = emptyMap(),
            knowledgeBaseSystems = emptyMap()
        )
        assertEquals("Electrical System", onlyCatalogue.primarySystem)

        // Nothing known: the neutral "Other / Undetermined" label is used instead of inventing one.
        val unknown = map(
            row(topHypothesisId = null, topIssueName = null, topSystemId = null, topProbability = null, issueCount = 0),
            knowledgeBaseNames = emptyMap(),
            knowledgeBaseSystems = emptyMap(),
            useCatalogue = false
        )
        assertNull(unknown.topIssueName)
        assertNull(unknown.topProbability)
        assertEquals(0, unknown.issueCount)
        assertEquals(VehicleSystem.OTHER.label, unknown.primarySystem)
    }
}
