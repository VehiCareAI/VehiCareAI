package com.example.vehicare.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted assessment, including **drafts**: [answers], [selectedCategoryIds], [presentEvidenceIds]
 * and [absentEvidenceIds] are stored with type converters so questionnaire progress survives back
 * navigation and process death (Section 4 / Section 8).
 *
 * Deleting the owning vehicle deliberately deletes its assessments ([ForeignKey.CASCADE], Section 8),
 * and the cascade continues to [AssessmentResultEntity] and [AssessmentSafetyAlertEntity].
 *
 * Evidence sets keep the write order (see converters), so "the first reported symptom" stays stable
 * between the questionnaire and the saved report.
 */
@Entity(
    tableName = "assessments",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vehicleId"], name = "index_assessments_vehicleId"),
        Index(value = ["status", "completedAt"], name = "index_assessments_status_completedAt"),
        Index(value = ["startedAt"], name = "index_assessments_startedAt")
    ]
)
data class AssessmentEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val assessmentTypeId: String,
    val selectedCategoryIds: List<String>,
    /** Raw questionnaire answers: questionId -> optionId (or slider value as text). */
    val answers: Map<String, String>,
    val presentEvidenceIds: Set<String>,
    val absentEvidenceIds: Set<String>,
    val reportedSeverity: Int?,
    val onsetId: String?,
    /** [com.example.vehicare.domain.model.AssessmentStatus.id]; stored as text for forward compatibility. */
    val status: String,
    val engineVersion: String,
    val knowledgeBaseVersion: String,
    val disclaimerAccepted: Boolean,
    val resolved: Boolean,
    val startedAt: Long,
    val completedAt: Long?
)
