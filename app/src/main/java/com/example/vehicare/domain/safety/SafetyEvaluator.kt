package com.example.vehicare.domain.safety

import com.example.vehicare.domain.diagnostic.models.SafetyAlert
import com.example.vehicare.domain.diagnostic.models.SafetyLevel
import com.example.vehicare.domain.diagnostic.models.SymptomEvidence
import com.example.vehicare.domain.diagnostic.models.VehicleContext

/**
 * Safety evaluation is deliberately INDEPENDENT of the Bayesian ranking (Section 7).
 *
 * A safety alert is produced from reported symptoms alone, so a rare (low-probability) but
 * dangerous condition still surfaces prominently, and a high-probability benign issue can never
 * suppress it.
 */
interface SafetyEvaluator {
    fun evaluate(evidence: SymptomEvidence, vehicle: VehicleContext): List<SafetyAlert>
}

/**
 * Rule-based implementation. Every rule maps one or more reported findings straight to a general
 * safety message. Communication rules: general guidance only, never a diagnosis, never
 * "keep driving" advice, and no implication of emergency services.
 */
class RuleBasedSafetyEvaluator : SafetyEvaluator {

    private data class Rule(
        val id: String,
        val triggers: Set<String>,
        val level: SafetyLevel,
        val title: String,
        val message: String,
        val minTriggers: Int = 1
    )

    private val rules: List<Rule> = listOf(
        Rule(
            id = "brake_performance",
            triggers = setOf("brake_reduced_performance", "brake_soft_pedal", "brake_pulling_side", "brake_burning_smell"),
            level = SafetyLevel.URGENT,
            title = "Possible braking system fault",
            message = "Reduced, uneven or overheated braking can make stopping distances unpredictable. Consider having the brakes inspected before further driving, and avoid heavy traffic or high-speed roads until then."
        ),
        Rule(
            id = "overheating_current",
            triggers = setOf("overheating_current", "steam_from_engine_bay", "temperature_warning_light"),
            level = SafetyLevel.URGENT,
            title = "Overheating reported",
            message = "Overheating can cause permanent engine damage. Consider stopping in a safe location, letting the engine cool, and seeking professional assistance. Do not open the coolant cap while the engine is hot."
        ),
        Rule(
            id = "overheating_frequent",
            triggers = setOf("overheating_frequent"),
            level = SafetyLevel.ADVISORY,
            title = "Repeated high temperature",
            message = "Repeated overheating episodes suggest a cooling fault that should be inspected soon, before it becomes a roadside breakdown."
        ),
        Rule(
            id = "coolant_loss",
            triggers = setOf("coolant_loss", "coolant_low", "sweet_smell"),
            level = SafetyLevel.ADVISORY,
            title = "Cooling system fluid loss",
            message = "Losing coolant, a low coolant level or a sweet coolant smell can indicate a leak. Have the cooling system checked soon to avoid overheating."
        ),
        Rule(
            id = "fire_risk",
            triggers = setOf("smoke_from_engine_bay", "electrical_burning_smell"),
            level = SafetyLevel.URGENT,
            title = "Possible fire risk",
            message = "Smoke or a burning smell from the engine bay may indicate an electrical or fluid fire risk. Consider stopping in a safe location, switching the engine off and seeking professional assistance."
        ),
        Rule(
            id = "oil_pressure",
            triggers = setOf("oil_pressure_warning"),
            level = SafetyLevel.URGENT,
            title = "Oil pressure warning",
            message = "Low oil pressure can destroy an engine within minutes. Consider stopping in a safe location as soon as it is safe to do so and checking the oil level before driving on."
        ),
        Rule(
            id = "fuel_leak",
            triggers = setOf("fuel_leak_visible", "fuel_smell"),
            level = SafetyLevel.URGENT,
            title = "Possible fuel leak",
            message = "Fuel leaks carry a serious fire risk. Avoid smoking or ignition sources near the vehicle and seek professional assistance promptly."
        ),
        Rule(
            id = "steering_fault",
            triggers = setOf("steering_loose", "steering_hard"),
            level = SafetyLevel.URGENT,
            title = "Possible steering fault",
            message = "Loose or heavy steering reduces your ability to control the vehicle safely. Consider seeking professional assistance before driving further."
        ),
        Rule(
            id = "sudden_power_loss",
            triggers = setOf("sudden_power_loss_driving", "intermittent_power_loss"),
            level = SafetyLevel.URGENT,
            title = "Sudden loss of power reported",
            message = "A sudden loss of engine power while driving can create a hazardous situation. Consider avoiding fast roads and having the vehicle inspected."
        ),
        Rule(
            id = "engine_knock",
            triggers = setOf("engine_knocking"),
            level = SafetyLevel.ADVISORY,
            title = "Abnormal engine noise",
            message = "Heavy knocking can indicate internal engine wear. Consider limiting driving until the source of the noise is identified."
        ),
        Rule(
            id = "stranded_start_risk",
            triggers = setOf("no_crank", "clicking_on_start", "cranks_no_start"),
            minTriggers = 2,
            level = SafetyLevel.ADVISORY,
            title = "Vehicle may not restart",
            message = "Repeated starting difficulty may leave the vehicle unable to restart. Consider avoiding stops in locations where a breakdown would be unsafe."
        ),
        Rule(
            id = "braking_abs",
            triggers = setOf("abs_light", "brake_grinding", "brake_vibration"),
            minTriggers = 2,
            level = SafetyLevel.ADVISORY,
            title = "Braking system warning",
            message = "A combination of brake noise or vibration with an ABS warning suggests the braking system needs prompt inspection."
        ),
        Rule(
            id = "transmission_fault",
            triggers = setOf("transmission_slipping", "delayed_engagement", "transmission_warning_light"),
            level = SafetyLevel.ADVISORY,
            title = "Possible transmission fault",
            message = "Slipping or delayed gear engagement can affect control in traffic. Consider having the transmission checked before relying on the vehicle."
        )
    )

    override fun evaluate(evidence: SymptomEvidence, vehicle: VehicleContext): List<SafetyAlert> {
        val reported = evidence.presentEvidenceIds
        return rules
            .filter { rule -> rule.triggers.count { it in reported } >= rule.minTriggers }
            .map { rule ->
                SafetyAlert(
                    id = rule.id,
                    title = rule.title,
                    message = rule.message,
                    level = rule.level,
                    relatedEvidenceIds = rule.triggers.intersect(reported)
                )
            }
            .sortedWith(compareByDescending<SafetyAlert> { it.level.ordinal }.thenBy { it.id })
    }
}
