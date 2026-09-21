package com.example.vehicare.domain.diagnostic.models

/** Whether the engine found enough support to report possibilities at all. */
enum class SufficiencyStatus {
    /** Enough evidence and at least one meaningfully supported hypothesis. */
    SUFFICIENT,

    /** Too little evidence, or no hypothesis is meaningfully supported. */
    INSUFFICIENT_EVIDENCE
}

/** Severity of a safety alert. Alert presence never depends on hypothesis ranking. */
enum class SafetyLevel(val label: String) {
    ADVISORY("Advisory"),
    URGENT("Urgent")
}

/**
 * A safety alert derived directly from reported symptoms (Section 7).
 * Deliberately independent of probability: it is produced by the SafetyEvaluator, not by ranking.
 */
data class SafetyAlert(
    val id: String,
    val title: String,
    val message: String,
    val level: SafetyLevel,
    val relatedEvidenceIds: Set<String> = emptySet()
)

/** One ranked possible issue, i.e. one hypothesis with its posterior estimate. */
data class RankedIssue(
    val hypothesisId: String,
    val name: String,
    val system: VehicleSystem,
    val rank: Int,
    /** Relative estimate over the considered hypothesis set; NOT a calibrated confidence. */
    val posteriorProbability: Double,
    val priorProbability: Double,
    val severity: Severity,
    val summary: String,
    /** Plain-language explanation tied to the reported symptoms. */
    val explanation: String,
    val supportingEvidence: List<Evidence>,
    val missingEvidence: List<Evidence>,
    val contradictingEvidence: List<Evidence>,
    val possibleCauses: List<String>,
    val recommendedChecks: List<String>,
    val safetyWarnings: List<String>,
    val recommendedServiceCategory: String,
    /** How many of the reported findings actually matched this hypothesis (0..1). */
    val evidenceStrength: Double,
    val matchedEvidenceCount: Int,
    val consideredEvidenceCount: Int
)

/**
 * Complete, immutable engine output. Contains no Android or UI types so it can be unit tested and
 * (if ever needed) reproduced server-side.
 */
data class DiagnosticAnalysis(
    val rankedIssues: List<RankedIssue>,
    val safetyAlerts: List<SafetyAlert>,
    val sufficiency: SufficiencyStatus,
    /** Posterior mass left to the "other / undetermined" hypothesis. */
    val undeterminedProbability: Double,
    val reportedEvidence: List<Evidence>,
    val absentEvidence: List<Evidence>,
    val answeredQuestionCount: Int,
    val generatedAtEpochMillis: Long,
    val engineVersion: String,
    val knowledgeBaseVersion: String
) {
    val isSufficient: Boolean get() = sufficiency == SufficiencyStatus.SUFFICIENT
    val hasSafetyAlerts: Boolean get() = safetyAlerts.isNotEmpty()
    val primaryIssue: RankedIssue? get() = rankedIssues.firstOrNull()

    companion object {
        /**
         * Returned when the engine cannot responsibly infer anything. The engine never fabricates a
         * result, so callers must render this as an explicit "insufficient evidence" state.
         */
        fun insufficient(
            evidence: SymptomEvidence,
            generatedAtEpochMillis: Long,
            engineVersion: String,
            knowledgeBaseVersion: String,
            safetyAlerts: List<SafetyAlert> = emptyList(),
            reportedEvidence: List<Evidence> = emptyList(),
            absentEvidence: List<Evidence> = emptyList()
        ) = DiagnosticAnalysis(
            rankedIssues = emptyList(),
            safetyAlerts = safetyAlerts,
            sufficiency = SufficiencyStatus.INSUFFICIENT_EVIDENCE,
            undeterminedProbability = 1.0,
            reportedEvidence = reportedEvidence,
            absentEvidence = absentEvidence,
            answeredQuestionCount = evidence.answeredQuestionIds.size,
            generatedAtEpochMillis = generatedAtEpochMillis,
            engineVersion = engineVersion,
            knowledgeBaseVersion = knowledgeBaseVersion
        )
    }
}
