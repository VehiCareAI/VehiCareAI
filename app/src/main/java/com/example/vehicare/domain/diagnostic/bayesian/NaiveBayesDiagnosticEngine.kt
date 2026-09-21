package com.example.vehicare.domain.diagnostic.bayesian

import com.example.vehicare.domain.diagnostic.knowledgebase.KnowledgeBase
import com.example.vehicare.domain.diagnostic.knowledgebase.VehiCareKnowledgeBase
import com.example.vehicare.domain.diagnostic.models.DiagnosticAnalysis
import com.example.vehicare.domain.diagnostic.models.Evidence
import com.example.vehicare.domain.diagnostic.models.EvidenceGroup
import com.example.vehicare.domain.diagnostic.models.RankedIssue
import com.example.vehicare.domain.diagnostic.models.SymptomEvidence
import com.example.vehicare.domain.diagnostic.models.VehicleContext
import com.example.vehicare.domain.safety.RuleBasedSafetyEvaluator
import com.example.vehicare.domain.safety.SafetyEvaluator
import kotlin.math.exp
import kotlin.math.ln

/**
 * Grouped-evidence Naive Bayes diagnostic engine (Section 6).
 *
 * ### Model
 *
 * `P(H | E) = P(E | H) * P(H) / P(E)`, with `P(E) = SUM_k P(E | H_k) * P(H_k)` over the candidate
 * hypothesis set (including the `other_undetermined` residual hypothesis).
 *
 * Scoring is performed in **log space**:
 *
 * ```
 * logScore(H) = ln P(H)
 *             + SUM over triggered groups g   of ln P(g | H)
 *             + SUM over present non-group evidence e of ln P(e | H)
 *             + SUM over absent  non-group evidence e of ln P(not e | H)
 * ```
 *
 * then normalized with the log-sum-exp trick so long evidence lists cannot underflow.
 *
 * ### Evidence handling
 *  - Missing / skipped / "unsure" answers contribute **nothing** (log term 0.0).
 *  - Explicit "No" answers contribute `ln(1 - P(e | H))` (explicit negative evidence).
 *  - Correlated findings in a triggered [EvidenceGroup] contribute exactly **one** group
 *    likelihood and are removed from individual scoring, so correlated symptoms are never
 *    multiplied (see [EvidenceGroup] and rule G2 in docs/DIAGNOSTIC_MODEL.md).
 *  - Unlisted (hypothesis, evidence) pairs use the knowledge-base likelihood floor, never 0.
 *
 * ### Sufficiency (never fabricate results)
 * A ranking is only returned when the answer count reaches `minAnsweredEvidence`, the best-supported
 * hypothesis clears `minTopPosterior`, matches a share `minEvidenceStrength` of the findings it was
 * scored against, and at least one reported finding supported it positively. Purely negative
 * answers therefore produce an explicit **insufficient evidence** result rather than a ranking
 * built by exclusion, and safety alerts are still returned in that case.
 *
 * ### Determinism
 * Ties are broken by higher severity, then by hypothesis id. Timestamps come from an injectable
 * clock so tests are reproducible.
 */
class NaiveBayesDiagnosticEngine(
    private val knowledgeBase: KnowledgeBase = VehiCareKnowledgeBase.instance,
    private val safetyEvaluator: SafetyEvaluator = RuleBasedSafetyEvaluator(),
    private val config: DiagnosticEngineConfig = DiagnosticEngineConfig(),
    override val engineVersion: String = ENGINE_VERSION,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : BayesianDiagnosticEngine {

    override val knowledgeBaseVersion: String get() = knowledgeBase.version

    private data class Scored(
        val hypothesis: com.example.vehicare.domain.diagnostic.knowledgebase.Hypothesis,
        val logScore: Double
    )

    override fun analyzeSymptoms(evidence: SymptomEvidence, vehicle: VehicleContext): DiagnosticAnalysis {
        val now = clock()
        val alerts = safetyEvaluator.evaluate(evidence, vehicle)

        val present = evidence.presentEvidenceIds.filter { knowledgeBase.evidence.containsKey(it) }.toSet()
        val absent = evidence.absentEvidenceIds.filter { knowledgeBase.evidence.containsKey(it) }.toSet()

        // Conflicting input (the same finding reported as both present and absent) is rejected as
        // unusable: it is removed from BOTH sets rather than double-counted as positive and negative
        // evidence. The questionnaire cannot produce this, but imported or edited drafts might.
        val conflicting = present intersect absent
        val usablePresent = present - conflicting
        val usableAbsent = absent - conflicting
        val answeredCount = usablePresent.size + usableAbsent.size
        // Reported findings resolved to knowledge-base entries, reused for every result path so an
        // insufficient analysis still carries what the user actually reported.
        val reportedEvidence = usablePresent.mapNotNull { knowledgeBase.evidenceOrNull(it) }
        val absentEvidence = usableAbsent.mapNotNull { knowledgeBase.evidenceOrNull(it) }

        // --- 1. Triggered evidence groups: one likelihood instead of multiplying correlated members.
        val triggeredGroups = knowledgeBase.groups.filter { group ->
            val presentMembers = group.memberEvidenceIds.count { it in usablePresent }
            val anyMemberDenied = group.memberEvidenceIds.any { it in usableAbsent }
            presentMembers >= group.minMembers && !anyMemberDenied
        }
        val groupedMembers = triggeredGroups.flatMapTo(mutableSetOf()) { it.memberEvidenceIds }
        val presentAtomic = usablePresent - groupedMembers
        val absentAtomic = usableAbsent - groupedMembers

        // --- 2. Candidate hypothesis set (selected categories + cross-category matches + residual)
        val selectedSystems = evidence.selectedCategoryIds
            .mapNotNull { categoryId ->
                com.example.vehicare.domain.diagnostic.models.SymptomCategory.all
                    .firstOrNull { it.id == categoryId }?.system
            }.toSet()

        val candidates = knowledgeBase.hypotheses.filter { hypothesis ->
            if (hypothesis.isResidual) {
                true
            } else if (selectedSystems.isEmpty()) {
                true
            } else if (hypothesis.system in selectedSystems) {
                true
            } else {
                // Cross-category match: the hypothesis explains a reported finding directly. All
                // reported findings count, including members of a triggered evidence group.
                usablePresent.any { knowledgeBase.likelihood(hypothesis, it) > knowledgeBase.likelihoodFloor }
            }
        }

        if (answeredCount < config.minAnsweredEvidence) {
            return DiagnosticAnalysis.insufficient(
                evidence = evidence,
                generatedAtEpochMillis = now,
                engineVersion = engineVersion,
                knowledgeBaseVersion = knowledgeBaseVersion,
                safetyAlerts = alerts,
                reportedEvidence = reportedEvidence,
                absentEvidence = absentEvidence
            )
        }

        // --- 3. Log-space scores -------------------------------------------------------------
        val scored = candidates.map { hypothesis ->
            var logScore = ln(hypothesis.priorProbability.coerceAtLeast(MIN_PRIOR))

            triggeredGroups.forEach { group ->
                val p = knowledgeBase.groupProbability(hypothesis, group.id)
                logScore += ln(p.coerceIn(MIN_PRIOR, 1.0))
            }
            presentAtomic.forEach { evidenceId ->
                val p = knowledgeBase.likelihood(hypothesis, evidenceId)
                logScore += ln(p.coerceIn(MIN_PRIOR, 1.0))
            }
            absentAtomic.forEach { evidenceId ->
                val p = knowledgeBase.likelihood(hypothesis, evidenceId)
                logScore += ln((1.0 - p).coerceAtLeast(config.minimumComplement))
            }
            Scored(hypothesis, logScore)
        }

        if (scored.isEmpty()) {
            return DiagnosticAnalysis.insufficient(
                evidence, now, engineVersion, knowledgeBaseVersion, alerts,
                reportedEvidence = reportedEvidence,
                absentEvidence = absentEvidence
            )
        }

        // --- 4. Normalize with log-sum-exp ----------------------------------------------------
        val maxLog = scored.maxOf { it.logScore }
        val denominator = scored.sumOf { exp(it.logScore - maxLog) }
        val posteriors = scored.associate { scoredItem ->
            scoredItem.hypothesis.id to exp(scoredItem.logScore - maxLog) / denominator
        }

        val residualPosterior = posteriors[KnowledgeBase.RESIDUAL_ID] ?: 0.0

        // --- 5. Rank (posterior desc, then severity desc, then id) ---------------------------
        val ranked = scored
            .filterNot { it.hypothesis.isResidual }
            .sortedWith(
                compareByDescending<Scored> { posteriors[it.hypothesis.id] ?: 0.0 }
                    .thenByDescending { it.hypothesis.severity.rank }
                    .thenBy { it.hypothesis.id }
            )
            .mapIndexed { index, scoredItem ->
                buildRankedIssue(
                    hypothesis = scoredItem.hypothesis,
                    rank = index + 1,
                    posterior = posteriors[scoredItem.hypothesis.id] ?: 0.0,
                    presentAtomic = presentAtomic,
                    reportedPresent = usablePresent,
                    absentAtomic = absentAtomic,
                    triggeredGroups = triggeredGroups,
                    vehicle = vehicle
                )
            }

        // --- 6. Sufficiency rule -------------------------------------------------------------
        val top = ranked.firstOrNull()
        val sufficient = top != null &&
            top.posteriorProbability >= config.minTopPosterior &&
            top.evidenceStrength >= config.minEvidenceStrength &&
            top.matchedEvidenceCount > 0

        if (!sufficient) {
            return DiagnosticAnalysis.insufficient(
                evidence = evidence,
                generatedAtEpochMillis = now,
                engineVersion = engineVersion,
                knowledgeBaseVersion = knowledgeBaseVersion,
                safetyAlerts = alerts,
                reportedEvidence = reportedEvidence,
                absentEvidence = absentEvidence
            )
        }

        return DiagnosticAnalysis(
            rankedIssues = ranked,
            safetyAlerts = alerts,
            sufficiency = com.example.vehicare.domain.diagnostic.models.SufficiencyStatus.SUFFICIENT,
            undeterminedProbability = residualPosterior,
            reportedEvidence = reportedEvidence,
            absentEvidence = absentEvidence,
            answeredQuestionCount = evidence.answeredQuestionIds.size,
            generatedAtEpochMillis = now,
            engineVersion = engineVersion,
            knowledgeBaseVersion = knowledgeBaseVersion
        )
    }

    private fun buildRankedIssue(
        hypothesis: com.example.vehicare.domain.diagnostic.knowledgebase.Hypothesis,
        rank: Int,
        posterior: Double,
        presentAtomic: Set<String>,
        reportedPresent: Set<String>,
        absentAtomic: Set<String>,
        triggeredGroups: List<EvidenceGroup>,
        vehicle: VehicleContext
    ): RankedIssue {
        val floor = knowledgeBase.likelihoodFloor

        val supporting = mutableListOf<Evidence>()
        presentAtomic.forEach { evidenceId ->
            if (knowledgeBase.likelihood(hypothesis, evidenceId) > floor) {
                knowledgeBase.evidenceOrNull(evidenceId)?.let { supporting += it }
            }
        }
        triggeredGroups.forEach { group ->
            val presentInGroup = group.memberEvidenceIds.filter { memberId ->
                memberId in reportedPresent && knowledgeBase.likelihood(hypothesis, memberId) > floor
            }
            if (knowledgeBase.groupProbability(hypothesis, group.id) > floor) {
                presentInGroup.forEach { memberId ->
                    knowledgeBase.evidenceOrNull(memberId)?.let { if (it !in supporting) supporting += it }
                }
            }
        }

        // Contradicting evidence: the user explicitly denied a finding this hypothesis predicts.
        val contradicting = absentAtomic
            .filter { knowledgeBase.likelihood(hypothesis, it) >= CONTRADICTION_THRESHOLD }
            .mapNotNull { knowledgeBase.evidenceOrNull(it) }

        // Missing evidence: findings this hypothesis would like to know about but the user did not report.
        val missing = hypothesis.evidenceLikelihoods
            .filter { (evidenceId, likelihood) ->
                likelihood >= MISSING_EVIDENCE_THRESHOLD &&
                    evidenceId !in reportedPresent &&
                    evidenceId !in absentAtomic
            }
            .entries
            .sortedByDescending { it.value }
            .mapNotNull { knowledgeBase.evidenceOrNull(it.key) }
            .take(MAX_MISSING_EVIDENCE)

        val consideredCount = supporting.size + contradicting.size
        val matchedCount = supporting.count { knowledgeBase.evidenceOrNull(it.id) != null }
        val strength = if (consideredCount == 0) 0.0 else matchedCount.toDouble() / consideredCount.toDouble()

        return RankedIssue(
            hypothesisId = hypothesis.id,
            name = hypothesis.name,
            system = hypothesis.system,
            rank = rank,
            posteriorProbability = posterior,
            priorProbability = hypothesis.priorProbability,
            severity = hypothesis.severity,
            summary = hypothesis.description,
            explanation = IssueExplanation.build(supporting, strength, vehicle),
            supportingEvidence = supporting,
            missingEvidence = missing,
            contradictingEvidence = contradicting,
            possibleCauses = hypothesis.possibleCauses,
            recommendedChecks = hypothesis.inspectionSteps,
            safetyWarnings = hypothesis.safetyWarnings,
            recommendedServiceCategory = hypothesis.recommendedServiceCategory,
            evidenceStrength = strength,
            matchedEvidenceCount = matchedCount,
            consideredEvidenceCount = consideredCount
        )
    }

    companion object {
        /** Engine version stored with every assessment (bump when the maths changes). */
        const val ENGINE_VERSION = "vehicare-bayes-1.0.0"

        private const val MIN_PRIOR = 1e-9

        /** An explicit "No" is only treated as contradicting evidence above this likelihood. */
        private const val CONTRADICTION_THRESHOLD = 0.35

        /** Findings this hypothesis would have liked to know about. */
        private const val MISSING_EVIDENCE_THRESHOLD = 0.45

        private const val MAX_MISSING_EVIDENCE = 5
    }
}
