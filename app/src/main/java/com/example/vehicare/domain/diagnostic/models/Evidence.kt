package com.example.vehicare.domain.diagnostic.models

/**
 * Atomic symptom finding (a *single* observation the knowledge base can reason about).
 * Evidence ids are stable strings because they are persisted with draft assessments.
 */
data class Evidence(
    val id: String,
    val label: String,
    val system: VehicleSystem,
    /** Safety-relevant findings are evaluated by [com.example.vehicare.domain.safety.SafetyEvaluator]. */
    val safetyRelevant: Boolean = false
)

/**
 * A cluster of correlated findings that co-occur for physical reasons (for example slow cranking,
 * dim lights and clicking on start all point at the same electrical/starting fault).
 *
 * Naive Bayes would multiply the likelihoods of these findings and over-count the same physical
 * cause, so when a group is *triggered* its members are removed from individual evaluation and the
 * group contributes exactly ONE likelihood per hypothesis.
 *
 * Trigger rule (documented in docs/DIAGNOSTIC_MODEL.md, rule G2): a group is triggered when at
 * least [minMembers] of its members are explicitly present and none of its answered members is
 * explicitly absent. Missing members contribute nothing.
 */
data class EvidenceGroup(
    val id: String,
    val label: String,
    val system: VehicleSystem,
    val memberEvidenceIds: Set<String>,
    val minMembers: Int = 2
)

/**
 * Everything the engine knows about the user's observations.
 *
 * Interpretation rules (Section 5.9 of the specification):
 *  - [presentEvidenceIds] is explicit positive evidence.
 *  - [absentEvidenceIds] is explicit *negative* evidence ("No"), which contributes P(not s | H).
 *  - anything not in either set is MISSING evidence and contributes NOTHING at all
 *    (a skipped or "unsure" answer is not evidence of absence).
 */
data class SymptomEvidence(
    val presentEvidenceIds: Set<String> = emptySet(),
    val absentEvidenceIds: Set<String> = emptySet(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val assessmentTypeId: String = "symptom_based",
    /** User-reported severity 1 (minor) .. 5 (severe). Distinct from issue [Severity]. */
    val reportedSeverity: Int? = null,
    /** Id of the "when did it begin" option, used for explanation text only. */
    val onsetId: String? = null,
    /** Question ids the user actually answered (used for sufficiency and progress). */
    val answeredQuestionIds: Set<String> = emptySet()
) {
    val answeredEvidenceCount: Int get() = presentEvidenceIds.size + absentEvidenceIds.size
}

/** Vehicle context passed to the engine; never a Room entity. */
data class VehicleContext(
    val vehicleId: String? = null,
    val displayName: String = "",
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val fuelType: String = "",
    val transmission: String = "",
    val mileageKm: Int? = null
)
