package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.entities.AssessmentEntity
import com.example.vehicare.data.local.entities.AssessmentResultEntity
import com.example.vehicare.data.local.entities.AssessmentSafetyAlertEntity
import com.example.vehicare.domain.diagnostic.models.RankedIssue
import com.example.vehicare.domain.diagnostic.models.SafetyAlert
import com.example.vehicare.domain.diagnostic.models.SafetyLevel
import com.example.vehicare.domain.diagnostic.models.Severity
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentResult
import com.example.vehicare.domain.model.AssessmentSafetyAlert
import com.example.vehicare.domain.model.AssessmentStatus

/**
 * Entity <-> domain conversions for assessments, their ranked results and their safety alerts.
 */

fun AssessmentEntity.toDomain(): Assessment = Assessment(
    id = id,
    vehicleId = vehicleId,
    assessmentTypeId = assessmentTypeId,
    selectedCategoryIds = selectedCategoryIds,
    answers = answers,
    presentEvidenceIds = LinkedHashSet(presentEvidenceIds),
    absentEvidenceIds = LinkedHashSet(absentEvidenceIds),
    reportedSeverity = reportedSeverity,
    onsetId = onsetId,
    status = assessmentStatusFromId(status),
    engineVersion = engineVersion,
    knowledgeBaseVersion = knowledgeBaseVersion,
    disclaimerAccepted = disclaimerAccepted,
    resolved = resolved,
    startedAt = startedAt,
    completedAt = completedAt
)

fun Assessment.toEntity(): AssessmentEntity = AssessmentEntity(
    id = id,
    vehicleId = vehicleId,
    assessmentTypeId = assessmentTypeId,
    selectedCategoryIds = selectedCategoryIds,
    answers = answers,
    presentEvidenceIds = LinkedHashSet(presentEvidenceIds),
    absentEvidenceIds = LinkedHashSet(absentEvidenceIds),
    reportedSeverity = reportedSeverity,
    onsetId = onsetId,
    status = status.id,
    engineVersion = engineVersion,
    knowledgeBaseVersion = knowledgeBaseVersion,
    disclaimerAccepted = disclaimerAccepted,
    resolved = resolved,
    startedAt = startedAt,
    completedAt = completedAt
)

fun AssessmentResultEntity.toDomain(): AssessmentResult = AssessmentResult(
    id = id,
    assessmentId = assessmentId,
    hypothesisId = hypothesisId,
    rank = rank,
    posteriorProbability = posteriorProbability,
    severity = severityFromName(severity),
    supportingEvidenceIds = supportingEvidenceIds,
    missingEvidenceIds = missingEvidenceIds,
    contradictingEvidenceIds = contradictingEvidenceIds,
    generatedAt = generatedAt
)

/**
 * One persisted ranked result row. Ids are deterministic (`assessmentId#rank`) so re-completing the
 * same assessment overwrites its rows instead of duplicating them.
 */
fun RankedIssue.toResultEntity(assessmentId: String, generatedAt: Long): AssessmentResultEntity =
    AssessmentResultEntity(
        id = "$assessmentId#$rank",
        assessmentId = assessmentId,
        hypothesisId = hypothesisId,
        rank = rank,
        posteriorProbability = posteriorProbability,
        severity = severity.name,
        supportingEvidenceIds = supportingEvidence.map { it.id },
        missingEvidenceIds = missingEvidence.map { it.id },
        contradictingEvidenceIds = contradictingEvidence.map { it.id },
        generatedAt = generatedAt
    )

/** Alert wording is snapshotted so a saved report keeps the warnings it was generated with. */
fun SafetyAlert.toAlertEntity(assessmentId: String): AssessmentSafetyAlertEntity =
    AssessmentSafetyAlertEntity(
        assessmentId = assessmentId,
        alertId = id,
        title = title,
        message = message,
        level = level.name
    )

fun AssessmentSafetyAlertEntity.toDomain(): AssessmentSafetyAlert = AssessmentSafetyAlert(
    assessmentId = assessmentId,
    alertId = alertId,
    title = title,
    message = message,
    level = level
)

/**
 * Unknown severity text falls back to [Severity.MEDIUM]: the app must still show the issue (it was
 * ranked for a reason) and severity must never be silently inflated or dropped.
 */
fun severityFromName(name: String): Severity =
    Severity.entries.firstOrNull { it.name == name } ?: Severity.MEDIUM

/** Unknown level text falls back to [SafetyLevel.URGENT] so a warning is never downplayed. */
fun safetyLevelFromName(name: String): SafetyLevel =
    SafetyLevel.entries.firstOrNull { it.name == name } ?: SafetyLevel.URGENT

/** Matches on the stable id, then on the enum name; anything unknown is treated as an open draft. */
fun assessmentStatusFromId(value: String): AssessmentStatus =
    AssessmentStatus.entries.firstOrNull { it.id == value }
        ?: AssessmentStatus.entries.firstOrNull { it.name == value }
        ?: AssessmentStatus.DRAFT
