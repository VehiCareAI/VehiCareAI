package com.example.vehicare.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * Snapshot of one safety alert raised for an assessment (Section 7 / Section 8).
 *
 * The alert text is copied verbatim so a saved report keeps the warnings it was generated with, even
 * if the knowledge base or safety rules change later. Safety alerts are never ranked and are stored
 * independently of [AssessmentResultEntity].
 *
 * The composite primary key is `(assessmentId, alertId)`, which also indexes `assessmentId` as its
 * leading column (Room creates an implicit index for the primary key), so the foreign-key lookup and
 * the per-assessment reads are covered without a redundant index.
 */
@Entity(
    tableName = "assessment_safety_alerts",
    primaryKeys = ["assessmentId", "alertId"],
    foreignKeys = [
        ForeignKey(
            entity = AssessmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["assessmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AssessmentSafetyAlertEntity(
    val assessmentId: String,
    val alertId: String,
    val title: String,
    val message: String,
    /** [com.example.vehicare.domain.diagnostic.models.SafetyLevel.name]. */
    val level: String
)
