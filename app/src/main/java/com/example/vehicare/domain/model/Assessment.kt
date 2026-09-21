package com.example.vehicare.domain.model

import com.example.vehicare.domain.diagnostic.models.Severity

/** All symptom-category ids, kept top level so enum entries can reference them during init. */
object SymptomCategoryIds {
    val All = listOf(
        "engine", "starting", "braking", "transmission", "cooling", "electrical",
        "suspension", "exhaust", "fuel", "warning_lights", "sounds", "performance"
    )
}

/** Assessment types (Section 5.7). All types share one Bayesian engine. */
enum class AssessmentType(
    val id: String,
    val label: String,
    val description: String,
    /** Symptom categories pre-selected for this assessment type. */
    val presetCategoryIds: List<String>
) {
    SYMPTOM_BASED(
        id = "symptom_based",
        label = "Symptom-Based Diagnosis",
        description = "General assessment driven by the symptoms you report.",
        presetCategoryIds = emptyList()
    ),
    WARNING_LIGHTS(
        id = "warning_lights",
        label = "Warning Light Assessment",
        description = "Focuses on illuminated dashboard warning lights.",
        presetCategoryIds = listOf("warning_lights", "electrical")
    ),
    PERFORMANCE_ISSUE(
        id = "performance_issue",
        label = "Performance Issue Assessment",
        description = "Focuses on power loss, hesitation and fuel consumption.",
        presetCategoryIds = listOf("performance", "engine", "fuel")
    ),
    GENERAL_HEALTH_CHECK(
        id = "general_health_check",
        label = "General Vehicle Health Check",
        description = "Broad coverage across all major systems.",
        presetCategoryIds = SymptomCategoryIds.All
    );

    companion object {
        fun fromId(id: String): AssessmentType = entries.firstOrNull { it.id == id } ?: SYMPTOM_BASED
    }
}

enum class AssessmentStatus(val id: String, val label: String) {
    DRAFT("draft", "Draft"),
    COMPLETED("completed", "Completed")
}

/**
 * An assessment. Drafts are persisted in Room so questionnaire progress survives back navigation and
 * process death (Section 4).
 */
data class Assessment(
    val id: String = "",
    val vehicleId: String,
    val assessmentTypeId: String = AssessmentType.SYMPTOM_BASED.id,
    val selectedCategoryIds: List<String> = emptyList(),
    /** Raw answers: questionId -> optionId (or slider value as string). */
    val answers: Map<String, String> = emptyMap(),
    val presentEvidenceIds: Set<String> = emptySet(),
    val absentEvidenceIds: Set<String> = emptySet(),
    val reportedSeverity: Int? = null,
    val onsetId: String? = null,
    val status: AssessmentStatus = AssessmentStatus.DRAFT,
    val engineVersion: String = "",
    val knowledgeBaseVersion: String = "",
    val disclaimerAccepted: Boolean = false,
    val resolved: Boolean = false,
    val startedAt: Long = 0L,
    val completedAt: Long? = null
) {
    val type: AssessmentType get() = AssessmentType.fromId(assessmentTypeId)
    val isDraft: Boolean get() = status == AssessmentStatus.DRAFT
}

/** One persisted ranked result row (Section 8). */
data class AssessmentResult(
    val id: String = "",
    val assessmentId: String,
    val hypothesisId: String,
    val rank: Int,
    val posteriorProbability: Double,
    val severity: Severity,
    val supportingEvidenceIds: List<String> = emptyList(),
    val missingEvidenceIds: List<String> = emptyList(),
    val contradictingEvidenceIds: List<String> = emptyList(),
    val generatedAt: Long = 0L
)

/** Lightweight row used by history lists, the dashboard and trends. */
data class AssessmentSummary(
    val assessmentId: String,
    val vehicleId: String,
    val vehicleName: String,
    val typeLabel: String,
    val completedAt: Long,
    val mainSymptomLabel: String,
    val topIssueName: String?,
    val topProbability: Double?,
    /** Evidence strength of the rank-1 result: considered findings that supported vs. contradicted it. */
    val topMatchedEvidenceCount: Int = 0,
    val topConsideredEvidenceCount: Int = 0,
    val issueCount: Int,
    val primarySystem: String,
    val hasSafetyAlerts: Boolean,
    val resolved: Boolean
)

/** Persisted safety alert snapshot so reports keep their original warnings. */
data class AssessmentSafetyAlert(
    val assessmentId: String,
    val alertId: String,
    val title: String,
    val message: String,
    val level: String
)
