package com.example.vehicare.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local snapshot of one knowledge-base hypothesis (possible issue).
 *
 * Ranked results store only the stable [AssessmentResultEntity.hypothesisId]; this catalogue resolves
 * the human-readable name, system and severity for history, reports and trends, and keeps them
 * available after a knowledge-base update. The in-memory knowledge base stays authoritative while it
 * knows the id; this table is the durable fallback (and the source for previously seen wording).
 */
@Entity(
    tableName = "hypotheses",
    indices = [
        Index(value = ["systemId"], name = "index_hypotheses_systemId"),
        Index(value = ["severity"], name = "index_hypotheses_severity")
    ]
)
data class DiagnosticHypothesisEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** [com.example.vehicare.domain.diagnostic.models.VehicleSystem.id]. */
    val systemId: String,
    /** [com.example.vehicare.domain.diagnostic.models.Severity.name]. */
    val severity: String,
    val description: String,
    val serviceCategory: String,
    val updatedAt: Long
)
