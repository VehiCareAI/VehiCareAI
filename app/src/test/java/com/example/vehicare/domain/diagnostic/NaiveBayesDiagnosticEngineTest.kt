package com.example.vehicare.domain.diagnostic

import com.example.vehicare.domain.diagnostic.bayesian.DiagnosticEngineConfig
import com.example.vehicare.domain.diagnostic.bayesian.NaiveBayesDiagnosticEngine
import com.example.vehicare.domain.diagnostic.knowledgebase.Hypothesis
import com.example.vehicare.domain.diagnostic.knowledgebase.KnowledgeBase
import com.example.vehicare.domain.diagnostic.knowledgebase.VehiCareKnowledgeBase
import com.example.vehicare.domain.diagnostic.models.Evidence
import com.example.vehicare.domain.diagnostic.models.EvidenceGroup
import com.example.vehicare.domain.diagnostic.models.Severity
import com.example.vehicare.domain.diagnostic.models.SufficiencyStatus
import com.example.vehicare.domain.diagnostic.models.SymptomEvidence
import com.example.vehicare.domain.diagnostic.models.VehicleContext
import com.example.vehicare.domain.diagnostic.models.VehicleSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for the research core (Section 11). The hand-verified case in Section 6.5 of the
 * specification is the anchor test: priors 0.6 / 0.4 with likelihoods 0.2 / 0.9 must give
 * posteriors 0.25 / 0.75.
 */
class NaiveBayesDiagnosticEngineTest {

    private val fixedClock = { 1_700_000_000_000L }

    /** Config that disables the sufficiency guard so the pure maths can be asserted. */
    private val unrestrictedConfig = DiagnosticEngineConfig(
        minAnsweredEvidence = 1,
        minTopPosterior = 0.0,
        minEvidenceStrength = 0.0
    )

    private fun evidenceOne() = Evidence("e1", "Evidence one", VehicleSystem.ENGINE)

    private fun evidenceTwo() = Evidence("e2", "Evidence two", VehicleSystem.ENGINE)

    private fun hypothesis(
        id: String,
        prior: Double,
        severity: Severity = Severity.MEDIUM,
        likelihoods: Map<String, Double> = emptyMap(),
        groups: Map<String, Double> = emptyMap()
    ) = Hypothesis(
        id = id,
        name = id,
        system = VehicleSystem.ENGINE,
        description = "description of $id",
        priorProbability = prior,
        severity = severity,
        recommendedServiceCategory = "test",
        inspectionSteps = listOf("check $id"),
        evidenceLikelihoods = likelihoods,
        groupLikelihoods = groups
    )

    private fun residual(prior: Double = 0.0) = Hypothesis(
        id = KnowledgeBase.RESIDUAL_ID,
        name = "Other / undetermined",
        system = VehicleSystem.OTHER,
        description = "residual",
        priorProbability = prior,
        severity = Severity.LOW,
        recommendedServiceCategory = "general",
        inspectionSteps = emptyList(),
        evidenceLikelihoods = emptyMap(),
        groupLikelihoods = emptyMap(),
        isResidual = true
    )

    private fun knowledgeBase(
        hypotheses: List<Hypothesis>,
        evidence: List<Evidence> = listOf(evidenceOne()),
        groups: List<EvidenceGroup> = emptyList(),
        floor: Double = 0.05
    ) = KnowledgeBase(
        version = "test-1",
        likelihoodFloor = floor,
        hypotheses = hypotheses,
        evidence = evidence.associateBy { it.id },
        groups = groups
    )

    // ---------------------------------------------------------------------------------------
    // Section 6.5 hand-verified example
    // ---------------------------------------------------------------------------------------

    @Test
    fun `hand verified case yields posteriors 0_25 and 0_75`() {
        val kb = knowledgeBase(
            hypotheses = listOf(
                hypothesis("h1", 0.6, likelihoods = mapOf("e1" to 0.2)),
                hypothesis("h2", 0.4, likelihoods = mapOf("e1" to 0.9)),
                residual()
            )
        )
        val engine = NaiveBayesDiagnosticEngine(kb, config = unrestrictedConfig, clock = fixedClock)

        val analysis = engine.analyzeSymptoms(
            evidence = SymptomEvidence(presentEvidenceIds = setOf("e1")),
            vehicle = VehicleContext()
        )

        assertEquals(2, analysis.rankedIssues.size)
        // P(E) = 0.6*0.2 + 0.4*0.9 = 0.48  ->  H1 = 0.12/0.48 = 0.25, H2 = 0.36/0.48 = 0.75
        assertEquals(0.75, analysis.rankedIssues[0].posteriorProbability, 1e-6)
        assertEquals("h2", analysis.rankedIssues[0].hypothesisId)
        assertEquals(0.25, analysis.rankedIssues[1].posteriorProbability, 1e-6)
        assertEquals("h1", analysis.rankedIssues[1].hypothesisId)
    }

    @Test
    fun `hand verified conflicting evidence case matches hand calculation`() {
        // Priors 0.6 / 0.4. h1 predicts e2 (0.9) and not e1 (0.2); h2 predicts e1 (0.9) and not e2 (0.2).
        // Report: e1 present, e2 explicitly denied.
        // h1: 0.6 * 0.2 * (1 - 0.9) = 0.012
        // h2: 0.4 * 0.9 * (1 - 0.2) = 0.288
        // P(E) = 0.300  ->  h1 = 0.04, h2 = 0.96
        val kb = knowledgeBase(
            hypotheses = listOf(
                hypothesis("h1", 0.6, likelihoods = mapOf("e1" to 0.2, "e2" to 0.9)),
                hypothesis("h2", 0.4, likelihoods = mapOf("e1" to 0.9, "e2" to 0.2)),
                residual()
            ),
            evidence = listOf(evidenceOne(), evidenceTwo())
        )
        val engine = NaiveBayesDiagnosticEngine(kb, config = unrestrictedConfig, clock = fixedClock)

        val analysis = engine.analyzeSymptoms(
            evidence = SymptomEvidence(presentEvidenceIds = setOf("e1"), absentEvidenceIds = setOf("e2")),
            vehicle = VehicleContext()
        )

        assertEquals(0.96, analysis.rankedIssues[0].posteriorProbability, 1e-6)
        assertEquals("h2", analysis.rankedIssues[0].hypothesisId)
        assertEquals(0.04, analysis.rankedIssues[1].posteriorProbability, 1e-6)
        // e2 was denied, so it contradicts h1 (which expects it) and must be surfaced to the user.
        assertTrue(analysis.rankedIssues[1].contradictingEvidence.any { it.id == "e2" })
    }

    @Test
    fun `purely negative evidence reports insufficient evidence instead of ranking by exclusion`() {
        // Documented rule: the engine only reports possibilities that are *positively* supported.
        // Denying symptoms ranks nothing affirmatively, so no issue is presented.
        val kb = knowledgeBase(
            hypotheses = listOf(
                hypothesis("h1", 0.6, likelihoods = mapOf("e1" to 0.2)),
                hypothesis("h2", 0.4, likelihoods = mapOf("e1" to 0.9)),
                residual()
            )
        )
        val engine = NaiveBayesDiagnosticEngine(kb, config = unrestrictedConfig, clock = fixedClock)

        val analysis = engine.analyzeSymptoms(
            evidence = SymptomEvidence(absentEvidenceIds = setOf("e1")),
            vehicle = VehicleContext()
        )

        assertEquals(SufficiencyStatus.INSUFFICIENT_EVIDENCE, analysis.sufficiency)
        assertTrue(analysis.rankedIssues.isEmpty())
    }

    // ---------------------------------------------------------------------------------------
    // Normalization and ranking
    // ---------------------------------------------------------------------------------------

    @Test
    fun `posteriors are normalized over the closed hypothesis set including the residual`() {
        val engine = NaiveBayesDiagnosticEngine(
            VehiCareKnowledgeBase.instance, config = unrestrictedConfig, clock = fixedClock
        )
        val analysis = engine.analyzeSymptoms(
            evidence = SymptomEvidence(
                presentEvidenceIds = setOf("slow_cranking", "dim_lights", "cranks_no_start"),
                selectedCategoryIds = setOf("starting", "electrical")
            ),
            vehicle = VehicleContext()
        )

        val total = analysis.rankedIssues.sumOf { it.posteriorProbability } + analysis.undeterminedProbability
        assertTrue("posteriors must sum to 1.0 but were $total", abs(total - 1.0) < 1e-9)
        assertTrue(analysis.undeterminedProbability > 0.0)
    }

    @Test
    fun `ranking is sorted by descending posterior and rank numbers start at one`() {
        val engine = NaiveBayesDiagnosticEngine(
            VehiCareKnowledgeBase.instance, config = unrestrictedConfig, clock = fixedClock
        )
        val analysis = engine.analyzeSymptoms(
            evidence = SymptomEvidence(
                presentEvidenceIds = setOf("brake_squeaking", "brake_grinding", "brake_vibration")
            ),
            vehicle = VehicleContext()
        )

        assertTrue(analysis.rankedIssues.size >= 2)
        analysis.rankedIssues.zipWithNext().forEach { (higher, lower) ->
            assertTrue(higher.posteriorProbability >= lower.posteriorProbability)
        }
        assertEquals(1, analysis.rankedIssues.first().rank)
        assertEquals(
            analysis.rankedIssues.size,
            analysis.rankedIssues.last().rank
        )
    }

    @Test
    fun `equal posteriors break ties by severity then by hypothesis id`() {
        val kb = knowledgeBase(
            hypotheses = listOf(
                hypothesis("z_issue", 0.3, Severity.LOW, mapOf("e1" to 0.5)),
                hypothesis("a_issue", 0.3, Severity.LOW, mapOf("e1" to 0.5)),
                residual()
            )
        )
        val engine = NaiveBayesDiagnosticEngine(kb, config = unrestrictedConfig, clock = fixedClock)

        val first = engine.analyzeSymptoms(
            SymptomEvidence(presentEvidenceIds = setOf("e1")), VehicleContext()
        )
        assertEquals("a_issue", first.rankedIssues[0].hypothesisId)
        assertEquals("z_issue", first.rankedIssues[1].hypothesisId)

        val severityWins = knowledgeBase(
            hypotheses = listOf(
                hypothesis("a_issue", 0.3, Severity.LOW, mapOf("e1" to 0.5)),
                hypothesis("z_issue", 0.3, Severity.CRITICAL, mapOf("e1" to 0.5)),
                residual()
            )
        )
        val second = NaiveBayesDiagnosticEngine(severityWins, config = unrestrictedConfig, clock = fixedClock)
            .analyzeSymptoms(SymptomEvidence(presentEvidenceIds = setOf("e1")), VehicleContext())
        assertEquals("z_issue", second.rankedIssues[0].hypothesisId)
    }

    // ---------------------------------------------------------------------------------------
    // Grouped evidence vs naive multiplication (Section 6.2)
    // ---------------------------------------------------------------------------------------

    @Test
    fun `grouped evidence is not multiplied naively`() {
        val group = EvidenceGroup(
            id = "g_cluster",
            label = "cluster",
            system = VehicleSystem.ELECTRICAL,
            memberEvidenceIds = setOf("e1", "e2"),
            minMembers = 2
        )
        val members = listOf(
            Evidence("e1", "Evidence one", VehicleSystem.ELECTRICAL),
            Evidence("e2", "Evidence two", VehicleSystem.ELECTRICAL)
        )

        // Grouped model: one likelihood for the whole cluster.
        val grouped = knowledgeBase(
            hypotheses = listOf(
                hypothesis("h1", 0.5, likelihoods = mapOf("e1" to 0.8, "e2" to 0.7), groups = mapOf("g_cluster" to 0.85)),
                residual()
            ),
            evidence = members,
            groups = listOf(group)
        )

        // Naive alternative: the same cluster evaluated as the product of its members (0.8 * 0.7).
        val naive = knowledgeBase(
            hypotheses = listOf(
                hypothesis("h1", 0.5, likelihoods = mapOf("e1" to 0.8, "e2" to 0.7), groups = mapOf("g_cluster" to 0.8 * 0.7)),
                residual()
            ),
            evidence = members,
            groups = listOf(group)
        )

        val input = SymptomEvidence(presentEvidenceIds = setOf("e1", "e2"))
        val groupedResult = NaiveBayesDiagnosticEngine(grouped, config = unrestrictedConfig, clock = fixedClock)
            .analyzeSymptoms(input, VehicleContext())
        val naiveResult = NaiveBayesDiagnosticEngine(naive, config = unrestrictedConfig, clock = fixedClock)
            .analyzeSymptoms(input, VehicleContext())

        val groupedPosterior = groupedResult.rankedIssues.first().posteriorProbability
        val naivePosterior = naiveResult.rankedIssues.first().posteriorProbability

        assertTrue(
            "grouped posterior ($groupedPosterior) must exceed the naive product ($naivePosterior)",
            groupedPosterior > naivePosterior
        )
        // The grouped model keeps both member findings as supporting evidence.
        assertEquals(2, groupedResult.rankedIssues.first().supportingEvidence.size)
    }

    @Test
    fun `single group member falls back to its individual likelihood`() {
        val group = EvidenceGroup(
            id = "g_cluster",
            label = "cluster",
            system = VehicleSystem.ELECTRICAL,
            memberEvidenceIds = setOf("e1", "e2"),
            minMembers = 2
        )
        val kb = knowledgeBase(
            hypotheses = listOf(
                hypothesis("h1", 0.5, likelihoods = mapOf("e1" to 0.8, "e2" to 0.7), groups = mapOf("g_cluster" to 0.85)),
                residual()
            ),
            evidence = listOf(evidenceOne(), Evidence("e2", "Evidence two", VehicleSystem.ELECTRICAL)),
            groups = listOf(group)
        )
        val engine = NaiveBayesDiagnosticEngine(kb, config = unrestrictedConfig, clock = fixedClock)

        val oneMember = engine.analyzeSymptoms(SymptomEvidence(presentEvidenceIds = setOf("e1")), VehicleContext())
        val bothMembers = engine.analyzeSymptoms(SymptomEvidence(presentEvidenceIds = setOf("e1", "e2")), VehicleContext())

        // One member -> individual likelihood 0.8; both -> group likelihood 0.85 (higher, and not 0.56).
        assertTrue(bothMembers.rankedIssues.first().posteriorProbability > oneMember.rankedIssues.first().posteriorProbability)
        assertEquals(1, oneMember.rankedIssues.first().supportingEvidence.size)
    }

    @Test
    fun `a denied group member prevents the group from triggering`() {
        val group = EvidenceGroup(
            id = "g_cluster",
            label = "cluster",
            system = VehicleSystem.ELECTRICAL,
            memberEvidenceIds = setOf("e1", "e2"),
            minMembers = 2
        )
        val kb = knowledgeBase(
            hypotheses = listOf(
                hypothesis("h1", 0.5, likelihoods = mapOf("e1" to 0.8, "e2" to 0.7), groups = mapOf("g_cluster" to 0.85)),
                residual(prior = 0.5)
            ),
            evidence = listOf(evidenceOne(), evidenceTwo()),
            groups = listOf(group)
        )
        val engine = NaiveBayesDiagnosticEngine(kb, config = unrestrictedConfig, clock = fixedClock)

        val triggered = engine.analyzeSymptoms(
            SymptomEvidence(presentEvidenceIds = setOf("e1", "e2")), VehicleContext()
        ).rankedIssues.first()

        // e1 reported present, e2 explicitly denied: the cluster cannot trigger (only one member
        // present) and the denied member becomes contradicting evidence.
        val blocked = engine.analyzeSymptoms(
            SymptomEvidence(presentEvidenceIds = setOf("e1"), absentEvidenceIds = setOf("e2")),
            VehicleContext()
        ).rankedIssues.first()

        assertTrue(
            "denying a member must weaken support: ${blocked.posteriorProbability} vs ${triggered.posteriorProbability}",
            blocked.posteriorProbability < triggered.posteriorProbability
        )
        assertEquals(2, triggered.supportingEvidence.size)
        assertEquals(1, blocked.supportingEvidence.size)
        assertTrue(blocked.contradictingEvidence.any { it.id == "e2" })
    }

    // ---------------------------------------------------------------------------------------
    // Missing, unknown, conflicting evidence and empty submissions
    // ---------------------------------------------------------------------------------------

    @Test
    fun `missing evidence contributes nothing and unknown ids are ignored`() {
        val engine = NaiveBayesDiagnosticEngine(
            VehiCareKnowledgeBase.instance, config = unrestrictedConfig, clock = fixedClock
        )
        val baseline = engine.analyzeSymptoms(
            SymptomEvidence(presentEvidenceIds = setOf("slow_cranking"), selectedCategoryIds = setOf("starting")),
            VehicleContext()
        )
        val withUnknown = engine.analyzeSymptoms(
            SymptomEvidence(
                presentEvidenceIds = setOf("slow_cranking", "not_in_knowledge_base"),
                selectedCategoryIds = setOf("starting")
            ),
            VehicleContext()
        )

        assertEquals(
            baseline.rankedIssues.first().posteriorProbability,
            withUnknown.rankedIssues.first().posteriorProbability,
            1e-12
        )
        assertTrue(withUnknown.reportedEvidence.none { it.id == "not_in_knowledge_base" })
    }

    @Test
    fun `conflicting evidence for the same finding is ignored instead of double counted`() {
        val engine = NaiveBayesDiagnosticEngine(
            VehiCareKnowledgeBase.instance, config = unrestrictedConfig, clock = fixedClock
        )
        val clean = engine.analyzeSymptoms(
            SymptomEvidence(
                presentEvidenceIds = setOf("slow_cranking", "dim_lights", "cranks_no_start"),
                selectedCategoryIds = setOf("starting", "electrical")
            ),
            VehicleContext()
        )
        // dim_lights reported as both present and absent -> unusable, so it must not be scored at all
        // and the result must match the case where it was never reported.
        val conflicting = engine.analyzeSymptoms(
            SymptomEvidence(
                presentEvidenceIds = setOf("slow_cranking", "cranks_no_start", "dim_lights"),
                absentEvidenceIds = setOf("dim_lights"),
                selectedCategoryIds = setOf("starting", "electrical")
            ),
            VehicleContext()
        )
        val withoutDimLights = engine.analyzeSymptoms(
            SymptomEvidence(
                presentEvidenceIds = setOf("slow_cranking", "cranks_no_start"),
                selectedCategoryIds = setOf("starting", "electrical")
            ),
            VehicleContext()
        )

        assertTrue(clean.rankedIssues.first().posteriorProbability > 0.0)
        assertEquals(
            withoutDimLights.rankedIssues.first().posteriorProbability,
            conflicting.rankedIssues.first().posteriorProbability,
            1e-12
        )
    }

    @Test
    fun `empty submission returns insufficient evidence with no ranked issues`() {
        val engine = NaiveBayesDiagnosticEngine(VehiCareKnowledgeBase.instance, clock = fixedClock)
        val analysis = engine.analyzeSymptoms(SymptomEvidence(), VehicleContext())

        assertEquals(SufficiencyStatus.INSUFFICIENT_EVIDENCE, analysis.sufficiency)
        assertTrue(analysis.rankedIssues.isEmpty())
        assertEquals(0, analysis.answeredQuestionCount)
        assertEquals(1.0, analysis.undeterminedProbability, 1e-9)
    }

    @Test
    fun `thin evidence returns insufficient instead of fabricating a ranking`() {
        val engine = NaiveBayesDiagnosticEngine(VehiCareKnowledgeBase.instance, clock = fixedClock)
        // Only two answers: below the documented minimum answer count.
        val analysis = engine.analyzeSymptoms(
            SymptomEvidence(presentEvidenceIds = setOf("slow_cranking", "dim_lights")),
            VehicleContext()
        )
        assertEquals(SufficiencyStatus.INSUFFICIENT_EVIDENCE, analysis.sufficiency)
        assertTrue(analysis.rankedIssues.isEmpty())
    }

    // ---------------------------------------------------------------------------------------
    // Validation and reproducibility
    // ---------------------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `likelihood above one is rejected on load`() {
        knowledgeBase(hypotheses = listOf(hypothesis("h1", 0.5, likelihoods = mapOf("e1" to 1.5)), residual()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative prior is rejected on load`() {
        knowledgeBase(hypotheses = listOf(hypothesis("h1", -0.1, likelihoods = mapOf("e1" to 0.5)), residual()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `likelihood referencing unknown evidence is rejected on load`() {
        knowledgeBase(hypotheses = listOf(hypothesis("h1", 0.5, likelihoods = mapOf("ghost" to 0.5)), residual()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid likelihood floor is rejected on load`() {
        knowledgeBase(hypotheses = listOf(hypothesis("h1", 0.5), residual()), floor = 0.0)
    }

    @Test
    fun `analysis is reproducible for identical input`() {
        val engine = NaiveBayesDiagnosticEngine(
            VehiCareKnowledgeBase.instance, config = unrestrictedConfig, clock = fixedClock
        )
        val input = SymptomEvidence(
            presentEvidenceIds = setOf("slow_cranking", "dim_lights", "cranks_no_start"),
            selectedCategoryIds = setOf("starting", "electrical")
        )
        val first = engine.analyzeSymptoms(input, VehicleContext(make = "Toyota", model = "Vios"))
        val second = engine.analyzeSymptoms(input, VehicleContext(make = "Toyota", model = "Vios"))

        assertEquals(first, second)
        assertEquals(NaiveBayesDiagnosticEngine.ENGINE_VERSION, first.engineVersion)
        assertEquals(VehiCareKnowledgeBase.VERSION, first.knowledgeBaseVersion)
        assertEquals(1_700_000_000_000L, first.generatedAtEpochMillis)
    }

    // ---------------------------------------------------------------------------------------
    // Demonstration data (Section 9) - values are computed, never hardcoded
    // ---------------------------------------------------------------------------------------

    @Test
    fun `demo Vios symptoms rank battery then alternator then starter`() {
        val engine = NaiveBayesDiagnosticEngine(VehiCareKnowledgeBase.instance, clock = fixedClock)
        val analysis = engine.analyzeSymptoms(
            evidence = SymptomEvidence(
                presentEvidenceIds = setOf("slow_cranking", "dim_lights", "cranks_no_start"),
                selectedCategoryIds = setOf("starting", "electrical", "warning_lights"),
                reportedSeverity = 3,
                onsetId = "within_week",
                answeredQuestionIds = setOf("q_starting", "q_lights", "q_onset")
            ),
            vehicle = VehicleContext(make = "Toyota", model = "Vios", year = 2020, fuelType = "Gasoline")
        )

        assertEquals(SufficiencyStatus.SUFFICIENT, analysis.sufficiency)
        val topIds = analysis.rankedIssues.take(3).map { it.hypothesisId }
        assertEquals(listOf("weak_battery", "faulty_alternator", "starter_motor_failure"), topIds)

        val top = analysis.primaryIssue!!
        assertTrue("top posterior should be a meaningful estimate, was ${top.posteriorProbability}", top.posteriorProbability in 0.2..0.95)
        assertTrue(top.posteriorProbability < 1.0)
        assertTrue(top.supportingEvidence.isNotEmpty())
        assertTrue(top.missingEvidence.isNotEmpty())
        assertTrue(top.recommendedChecks.isNotEmpty())
        // The demo symptoms are not safety-critical, so no urgent alert may be raised.
        assertTrue(
            "demo symptoms must not raise an urgent alert",
            analysis.safetyAlerts.none { it.level == com.example.vehicare.domain.diagnostic.models.SafetyLevel.URGENT }
        )
    }

    @Test
    fun `probability and severity are independent`() {
        val engine = NaiveBayesDiagnosticEngine(
            VehiCareKnowledgeBase.instance, config = unrestrictedConfig, clock = fixedClock
        )
        // Oil level reported, nothing else: low_engine_oil is CRITICAL but must not out-rank a
        // better-supported hypothesis, and severity must never be derived from probability.
        val analysis = engine.analyzeSymptoms(
            SymptomEvidence(presentEvidenceIds = setOf("oil_level_low"), selectedCategoryIds = setOf("engine")),
            VehicleContext()
        )
        val oil = analysis.rankedIssues.first { it.hypothesisId == "low_engine_oil" }
        assertEquals(Severity.CRITICAL, oil.severity)
        assertTrue(oil.posteriorProbability <= 1.0)
        assertNotNull(analysis.primaryIssue)
    }
}
