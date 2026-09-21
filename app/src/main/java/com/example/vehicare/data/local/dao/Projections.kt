package com.example.vehicare.data.local.dao

/**
 * Query projections.
 *
 * These are plain data classes (no Room annotations) so that the mapping from a query result to a
 * domain model stays testable on the JVM: a projection carries exactly the columns the mapper needs
 * and nothing else.
 */

/**
 * One row of the history/report list: the assessment, its vehicle, the rank-1 result and cheap
 * aggregate counts, all fetched in a single indexed query (no N+1 reads).
 */
data class AssessmentSummaryRow(
    val assessmentId: String,
    val vehicleId: String,
    val vehicleNickname: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val vehicleYear: Int,
    val assessmentTypeId: String,
    val completedAt: Long?,
    val presentEvidenceIds: Set<String>,
    val resolved: Boolean,
    /** Rank-1 hypothesis id; null when the analysis was insufficient and produced no ranked rows. */
    val topHypothesisId: String?,
    val topIssueName: String?,
    val topSystemId: String?,
    val topProbability: Double?,
    /** Rank-1 supporting/contradicting findings, used to show evidence strength next to the estimate. */
    val topSupportingEvidenceIds: Set<String> = emptySet(),
    val topContradictingEvidenceIds: Set<String> = emptySet(),
    val issueCount: Int,
    val safetyAlertCount: Int
)

/** Minimal per-assessment data needed by the trends aggregation. */
data class AssessmentTrendRow(
    val assessmentId: String,
    val completedAt: Long,
    val presentEvidenceIds: Set<String>,
    val resolved: Boolean
)

/** Rank-1 hypothesis of one completed assessment, joined with the assessment timestamp. */
data class ProbabilityPointRow(
    val assessmentId: String,
    val completedAt: Long,
    val hypothesisId: String,
    val posteriorProbability: Double
)
