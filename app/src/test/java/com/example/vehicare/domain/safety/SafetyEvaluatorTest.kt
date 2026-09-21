package com.example.vehicare.domain.safety

import com.example.vehicare.domain.diagnostic.bayesian.NaiveBayesDiagnosticEngine
import com.example.vehicare.domain.diagnostic.knowledgebase.VehiCareKnowledgeBase
import com.example.vehicare.domain.diagnostic.models.SafetyLevel
import com.example.vehicare.domain.diagnostic.models.SufficiencyStatus
import com.example.vehicare.domain.diagnostic.models.SymptomEvidence
import com.example.vehicare.domain.diagnostic.models.VehicleContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Safety tests (Sections 7 and 11). Safety is evaluated from symptoms directly and must never be
 * suppressed by a low probability or by the sufficiency rule.
 */
class SafetyEvaluatorTest {

    private val evaluator = RuleBasedSafetyEvaluator()
    private val vehicle = VehicleContext(make = "Toyota", model = "Vios")

    @Test
    fun `reduced braking performance raises an urgent alert`() {
        val alerts = evaluator.evaluate(
            SymptomEvidence(presentEvidenceIds = setOf("brake_reduced_performance")), vehicle
        )
        assertEquals(1, alerts.size)
        assertEquals(SafetyLevel.URGENT, alerts.first().level)
        assertEquals("brake_performance", alerts.first().id)
        assertTrue(alerts.first().message.contains("inspected"))
        assertFalse(alerts.first().message.contains("keep driving", ignoreCase = true))
    }

    @Test
    fun `currently overheating is urgent and steam escalates the same rule once`() {
        val alerts = evaluator.evaluate(
            SymptomEvidence(presentEvidenceIds = setOf("overheating_current", "steam_from_engine_bay")), vehicle
        )
        assertEquals(1, alerts.size)
        assertEquals("overheating_current", alerts.first().id)
        assertEquals(2, alerts.first().relatedEvidenceIds.size)
    }

    @Test
    fun `electrical burning smell and engine bay smoke map to fire risk`() {
        val alerts = evaluator.evaluate(
            SymptomEvidence(presentEvidenceIds = setOf("electrical_burning_smell")), vehicle
        )
        assertTrue(alerts.any { it.id == "fire_risk" && it.level == SafetyLevel.URGENT })
    }

    @Test
    fun `oil pressure warning is urgent`() {
        val alerts = evaluator.evaluate(SymptomEvidence(presentEvidenceIds = setOf("oil_pressure_warning")), vehicle)
        assertTrue(alerts.any { it.id == "oil_pressure" && it.level == SafetyLevel.URGENT })
    }

    @Test
    fun `fuel leak is urgent`() {
        assertTrue(
            evaluator.evaluate(SymptomEvidence(presentEvidenceIds = setOf("fuel_leak_visible")), vehicle)
                .any { it.id == "fuel_leak" && it.level == SafetyLevel.URGENT }
        )
    }

    @Test
    fun `steering fault is urgent`() {
        assertTrue(
            evaluator.evaluate(SymptomEvidence(presentEvidenceIds = setOf("steering_loose")), vehicle)
                .any { it.id == "steering_fault" && it.level == SafetyLevel.URGENT }
        )
    }

    @Test
    fun `sudden power loss while driving is urgent`() {
        assertTrue(
            evaluator.evaluate(SymptomEvidence(presentEvidenceIds = setOf("sudden_power_loss_driving")), vehicle)
                .any { it.id == "sudden_power_loss" && it.level == SafetyLevel.URGENT }
        )
    }

    @Test
    fun `multi trigger rules need both findings`() {
        assertTrue(
            evaluator.evaluate(SymptomEvidence(presentEvidenceIds = setOf("brake_grinding")), vehicle).isEmpty()
        )
        assertTrue(
            evaluator.evaluate(
                SymptomEvidence(presentEvidenceIds = setOf("brake_grinding", "abs_light")), vehicle
            ).any { it.id == "braking_abs" }
        )
    }

    @Test
    fun `benign findings produce no safety alerts`() {
        val alerts = evaluator.evaluate(
            SymptomEvidence(presentEvidenceIds = setOf("poor_fuel_economy", "uneven_tire_wear")), vehicle
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `alerts are ordered urgent first and are deterministic`() {
        val evidence = SymptomEvidence(
            presentEvidenceIds = setOf("engine_knocking", "brake_reduced_performance", "oil_pressure_warning")
        )
        val first = evaluator.evaluate(evidence, vehicle)
        val second = evaluator.evaluate(evidence, vehicle)
        assertEquals(first, second)
        assertEquals(SafetyLevel.URGENT, first.first().level)
        assertEquals(3, first.size)
    }

    @Test
    fun `low probability high severity case still surfaces the safety alert`() {
        val engine = NaiveBayesDiagnosticEngine(VehiCareKnowledgeBase.instance)
        // One answer only: the engine must refuse to rank, but the dangerous symptom must still warn.
        val analysis = engine.analyzeSymptoms(
            evidence = SymptomEvidence(presentEvidenceIds = setOf("brake_soft_pedal")),
            vehicle = vehicle
        )

        assertEquals(SufficiencyStatus.INSUFFICIENT_EVIDENCE, analysis.sufficiency)
        assertTrue(analysis.rankedIssues.isEmpty())
        assertTrue(analysis.hasSafetyAlerts)
        assertEquals("brake_performance", analysis.safetyAlerts.first().id)
    }
}
