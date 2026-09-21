package com.example.vehicare.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One persisted ranked diagnostic result row (Section 8).
 *
 * The probability stored here is the engine's `posteriorProbability` (a relative estimate over the
 * considered hypothesis set) — never a calibrated confidence, and deliberately stored next to an
 * independent [severity] so the UI can keep probability and severity visually separate.
 */
@Entity(
    tableName = "assessment_results",
    foreignKeys = [
        ForeignKey(
            entity = AssessmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["assessmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["assessmentId"], name = "index_assessment_results_assessmentId"),
        Index(value = ["assessmentId", "rank"], name = "index_assessment_results_assessment_rank"),
        Index(value = ["hypothesisId"], name = "index_assessment_results_hypothesisId")
    ]
)
data class AssessmentResultEntity(
    @PrimaryKey val id: String,
    val assessmentId: String,
    val hypothesisId: String,
    val rank: Int,
    val posteriorProbability: Double,
    /** [com.example.vehicare.domain.diagnostic.models.Severity.name]. */
    val severity: String,
    val supportingEvidenceIds: List<String>,
    val missingEvidenceIds: List<String>,
    val contradictingEvidenceIds: List<String>,
    val generatedAt: Long
)
