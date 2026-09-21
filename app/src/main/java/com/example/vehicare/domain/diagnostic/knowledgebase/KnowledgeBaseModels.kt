package com.example.vehicare.domain.diagnostic.knowledgebase

import com.example.vehicare.domain.diagnostic.models.Evidence
import com.example.vehicare.domain.diagnostic.models.EvidenceGroup
import com.example.vehicare.domain.diagnostic.models.Severity
import com.example.vehicare.domain.diagnostic.models.VehicleSystem

/**
 * One diagnosable hypothesis (a possible issue).
 *
 * ALL numeric values in the knowledge base are **illustrative prototype values** authored for this
 * thesis prototype. They are not validated automotive statistics (see docs/DIAGNOSTIC_MODEL.md).
 */
data class Hypothesis(
    val id: String,
    val name: String,
    val system: VehicleSystem,
    val description: String,
    /** P(H) before any evidence is considered. Validated to lie in [0, 1]. */
    val priorProbability: Double,
    val severity: Severity,
    val recommendedServiceCategory: String,
    val inspectionSteps: List<String>,
    val safetyWarnings: List<String> = emptyList(),
    val possibleCauses: List<String> = emptyList(),
    /** Non-group evidence likelihoods: P(evidence present | H). */
    val evidenceLikelihoods: Map<String, Double> = emptyMap(),
    /** Evidence-group likelihoods: P(group triggered | H), one entry per group max. */
    val groupLikelihoods: Map<String, Double> = emptyMap(),
    /** Hypotheses explaining the "Other / undetermined" residual are never ranked. */
    val isResidual: Boolean = false
)

/**
 * Immutable, validated diagnostic knowledge base.
 *
 * Validation happens in [init] and fails loudly on invalid data (Section 6.2), so a malformed
 * knowledge base can never silently produce nonsense probabilities.
 */
class KnowledgeBase(
    val version: String,
    val likelihoodFloor: Double,
    val hypotheses: List<Hypothesis>,
    val evidence: Map<String, Evidence>,
    val groups: List<EvidenceGroup>
) {
    private val hypothesesById: Map<String, Hypothesis> = hypotheses.associateBy { it.id }

    /** Groups indexed by member evidence id so lookup during scoring is O(1). */
    private val groupsByMember: Map<String, List<EvidenceGroup>> =
        groups.flatMap { group -> group.memberEvidenceIds.map { it to group } }
            .groupBy({ it.first }, { it.second })

    val residualHypothesis: Hypothesis =
        hypothesesById[RESIDUAL_ID] ?: error("Knowledge base is missing the '$RESIDUAL_ID' hypothesis")

    val rankedHypotheses: List<Hypothesis> = hypotheses.filterNot { it.isResidual }

    init {
        require(version.isNotBlank()) { "Knowledge base version must not be blank" }
        require(likelihoodFloor > 0.0 && likelihoodFloor <= 1.0) {
            "Likelihood floor must be in (0, 1]; was $likelihoodFloor"
        }
        require(hypotheses.size == hypothesesById.size) { "Duplicate hypothesis ids in knowledge base" }
        // A Map cannot contain duplicate keys, so duplicate evidence ids can only be detected at the
        // point the list is collapsed into a map (see VehiCareKnowledgeBase.evidence).
        require(groups.map { it.id }.toSet().size == groups.size) { "Duplicate evidence group ids" }

        hypotheses.forEach { hypothesis ->
            requireProbability("priorProbability", hypothesis.priorProbability, hypothesis.id)
            hypothesis.evidenceLikelihoods.forEach { (evidenceId, likelihood) ->
                require(evidence.containsKey(evidenceId)) {
                    "Hypothesis '${hypothesis.id}' references unknown evidence '$evidenceId'"
                }
                requireProbability("P($evidenceId | ${hypothesis.id})", likelihood, hypothesis.id)
            }
            hypothesis.groupLikelihoods.forEach { (groupId, likelihood) ->
                require(groups.any { it.id == groupId }) {
                    "Hypothesis '${hypothesis.id}' references unknown evidence group '$groupId'"
                }
                requireProbability("P($groupId | ${hypothesis.id})", likelihood, hypothesis.id)
            }
        }

        groups.forEach { group ->
            require(group.memberEvidenceIds.size >= group.minMembers) {
                "Group '${group.id}' needs at least ${group.minMembers} members"
            }
            group.memberEvidenceIds.forEach { evidenceId ->
                require(evidence.containsKey(evidenceId)) {
                    "Group '${group.id}' references unknown evidence '$evidenceId'"
                }
            }
        }
    }

    private fun requireProbability(what: String, value: Double, hypothesisId: String) {
        require(!value.isNaN() && value >= 0.0 && value <= 1.0) {
            "Invalid probability for '$what' on hypothesis '$hypothesisId': $value (must be in [0, 1])"
        }
    }

    fun hypothesis(id: String): Hypothesis? = hypothesesById[id]

    fun evidenceOrNull(id: String): Evidence? = evidence[id]

    /** P(evidence present | hypothesis), falling back to the documented likelihood floor. */
    fun likelihood(hypothesis: Hypothesis, evidenceId: String): Double =
        (hypothesis.evidenceLikelihoods[evidenceId] ?: likelihoodFloor).coerceIn(0.0, 1.0)

    fun groupsFor(evidenceId: String): List<EvidenceGroup> = groupsByMember[evidenceId].orEmpty()

    fun groupProbability(hypothesis: Hypothesis, groupId: String): Double =
        (hypothesis.groupLikelihoods[groupId] ?: likelihoodFloor).coerceIn(0.0, 1.0)

    companion object {
        const val RESIDUAL_ID = "other_undetermined"
    }
}
