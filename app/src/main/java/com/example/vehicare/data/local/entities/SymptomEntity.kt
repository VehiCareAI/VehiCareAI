package com.example.vehicare.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local snapshot of one knowledge-base symptom (evidence item).
 *
 * The knowledge base is the authoritative source of symptom wording while it still contains an
 * evidence id. This table keeps the *label and system a report was written with* available after a
 * knowledge-base update renames or removes an item, so historical assessments and trends never show
 * a raw id like `rough_idle` when a readable label was recorded at the time.
 */
@Entity(
    tableName = "symptoms",
    indices = [
        Index(value = ["systemId"], name = "index_symptoms_systemId"),
        Index(value = ["categoryId"], name = "index_symptoms_categoryId")
    ]
)
data class SymptomEntity(
    @PrimaryKey val id: String,
    val label: String,
    /** [com.example.vehicare.domain.diagnostic.models.VehicleSystem.id]. */
    val systemId: String,
    /** Symptom category id used for filtering, when the id maps onto a category. */
    val categoryId: String?,
    val safetyRelevant: Boolean,
    val updatedAt: Long
)
