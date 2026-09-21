package com.example.vehicare.domain.diagnostic

import com.example.vehicare.domain.diagnostic.knowledgebase.KnowledgeBase
import com.example.vehicare.domain.diagnostic.knowledgebase.VehiCareKnowledgeBase
import com.example.vehicare.domain.diagnostic.models.Severity
import com.example.vehicare.domain.diagnostic.models.SymptomEvidence
import com.example.vehicare.domain.diagnostic.models.VehicleContext
import com.example.vehicare.domain.diagnostic.models.SymptomCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integrity of the illustrative knowledge base (Section 6.4). */
class KnowledgeBaseIntegrityTest {

    private val kb = VehiCareKnowledgeBase.instance

    @Test
    fun `knowledge base contains twenty ranked hypotheses plus the residual`() {
        assertEquals(21, kb.hypotheses.size)
        assertEquals(20, kb.rankedHypotheses.size)
        assertEquals(KnowledgeBase.RESIDUAL_ID, kb.residualHypothesis.id)
    }

    @Test
    fun `every hypothesis carries the fields the UI and report need`() {
        kb.rankedHypotheses.forEach { hypothesis ->
            assertTrue("blank name for ${hypothesis.id}", hypothesis.name.isNotBlank())
            assertTrue("blank description for ${hypothesis.id}", hypothesis.description.isNotBlank())
            assertTrue("no inspection steps for ${hypothesis.id}", hypothesis.inspectionSteps.isNotEmpty())
            assertTrue("no possible causes for ${hypothesis.id}", hypothesis.possibleCauses.isNotEmpty())
            assertTrue("no service category for ${hypothesis.id}", hypothesis.recommendedServiceCategory.isNotBlank())
            assertTrue(
                "no evidence modelled for ${hypothesis.id}",
                hypothesis.evidenceLikelihoods.isNotEmpty() || hypothesis.groupLikelihoods.isNotEmpty()
            )
        }
    }

    @Test
    fun `all priors and likelihoods are probabilities`() {
        kb.hypotheses.forEach { hypothesis ->
            assertTrue(hypothesis.priorProbability in 0.0..1.0)
            hypothesis.evidenceLikelihoods.values.forEach { assertTrue(it in 0.0..1.0) }
            hypothesis.groupLikelihoods.values.forEach { assertTrue(it in 0.0..1.0) }
        }
    }

    @Test
    fun `safety relevant hypotheses carry warnings and safety evidence exists`() {
        val critical = kb.rankedHypotheses.filter { it.severity == Severity.CRITICAL }
        assertTrue(critical.isNotEmpty())
        assertTrue(critical.all { it.safetyWarnings.isNotEmpty() })
        assertTrue(kb.evidence.values.any { it.safetyRelevant })
    }

    @Test
    fun `evidence groups only reference real evidence and are disjoint`() {
        val memberships = mutableListOf<String>()
        kb.groups.forEach { group ->
            group.memberEvidenceIds.forEach { id ->
                assertNotNull("group ${group.id} references unknown evidence $id", kb.evidenceOrNull(id))
                memberships += id
            }
        }
        assertEquals("an evidence item must belong to at most one group", memberships.size, memberships.toSet().size)
    }

    @Test
    fun `every symptom category maps to a system the knowledge base or evidence covers`() {
        val hypothesisSystems = kb.hypotheses.map { it.system }.toSet()
        val evidenceSystems = kb.evidence.values.map { it.system }.toSet()
        SymptomCategory.all.forEach { category ->
            assertTrue(
                "category ${category.id} maps to a system that is neither modelled nor observed: ${category.system}",
                category.system in hypothesisSystems || category.system in evidenceSystems
            )
        }
    }

    /**
     * Documented limitation (see docs/DIAGNOSTIC_MODEL.md): the 20-hypothesis knowledge base has no
     * dedicated suspension/steering hypothesis, so suspension observations currently act as
     * cross-category signals and safety triggers. If a user selects only that category, the engine
     * honestly reports insufficient evidence rather than inventing an issue.
     */
    @Test
    fun `suspension only categories yield insufficient evidence rather than a fabricated issue`() {
        val engine = com.example.vehicare.domain.diagnostic.bayesian.NaiveBayesDiagnosticEngine(
            kb, clock = { 0L }
        )
        val analysis = engine.analyzeSymptoms(
            SymptomEvidence(
                presentEvidenceIds = setOf("clunk_over_bumps", "steering_hard", "steering_loose"),
                selectedCategoryIds = setOf("suspension")
            ),
            VehicleContext()
        )
        assertTrue(analysis.safetyAlerts.any { it.id == "steering_fault" })
        org.junit.Assert.assertEquals(
            com.example.vehicare.domain.diagnostic.models.SufficiencyStatus.INSUFFICIENT_EVIDENCE,
            analysis.sufficiency
        )
    }

    @Test
    fun `residual hypothesis has no positive likelihoods so it absorbs uncertainty only`() {
        assertTrue(kb.residualHypothesis.evidenceLikelihoods.isEmpty())
        assertTrue(kb.residualHypothesis.groupLikelihoods.isEmpty())
        assertTrue(kb.residualHypothesis.isResidual)
        assertFalse(kb.rankedHypotheses.any { it.isResidual })
    }
}
