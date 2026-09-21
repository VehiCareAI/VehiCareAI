package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.dao.AssessmentSummaryRow
import com.example.vehicare.domain.diagnostic.models.VehicleSystem
import com.example.vehicare.domain.model.AssessmentSummary
import com.example.vehicare.domain.model.AssessmentType
import com.example.vehicare.domain.model.Vehicle

/**
 * Builds history/report rows out of the single joined summary query.
 *
 * Name, system and symptom label resolution is passed in as lambdas because the knowledge base is
 * authoritative while it knows an id and the local catalogue tables cover ids that were renamed or
 * removed later. Keeping that call out of the query keeps this mapping pure and unit-testable.
 */
fun AssessmentSummaryRow.toSummary(
    hypothesisName: (String) -> String?,
    hypothesisSystem: (String) -> VehicleSystem?,
    evidenceLabel: (String) -> String?
): AssessmentSummary {
    val topId = topHypothesisId
    // The knowledge base is authoritative for ids it still knows; the persisted catalogue snapshot
    // only covers ids a later revision renamed or removed.
    val resolvedSystem = topId?.let { hypothesisSystem(it) }
        ?: topSystemId?.let { VehicleSystem.fromId(it) }
        ?: VehicleSystem.OTHER
    val matched = topSupportingEvidenceIds.size
    val considered = matched + topContradictingEvidenceIds.size

    return AssessmentSummary(
        assessmentId = assessmentId,
        vehicleId = vehicleId,
        vehicleName = Vehicle(
            nickname = vehicleNickname,
            make = vehicleMake,
            model = vehicleModel,
            year = vehicleYear
        ).displayName,
        typeLabel = AssessmentType.fromId(assessmentTypeId).label,
        completedAt = completedAt ?: 0L,
        mainSymptomLabel = mainSymptomLabel(evidenceLabel),
        topIssueName = topId?.let { hypothesisName(it) } ?: topIssueName,
        topProbability = topProbability,
        topMatchedEvidenceCount = matched,
        topConsideredEvidenceCount = considered,
        issueCount = issueCount,
        primarySystem = resolvedSystem.label,
        hasSafetyAlerts = safetyAlertCount > 0,
        resolved = resolved
    )
}

/**
 * Label of the first reported finding, i.e. of the symptom that opened the questionnaire.
 * Unknown ids fall back to the raw id: showing an id is honest, inventing wording is not, and an
 * analysis without reported findings gets an empty label.
 */
private fun AssessmentSummaryRow.mainSymptomLabel(evidenceLabel: (String) -> String?): String =
    presentEvidenceIds.firstOrNull()?.let { evidenceLabel(it) ?: it }.orEmpty()
