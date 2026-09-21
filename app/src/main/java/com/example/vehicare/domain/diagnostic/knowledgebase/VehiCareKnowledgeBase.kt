package com.example.vehicare.domain.diagnostic.knowledgebase

import com.example.vehicare.domain.diagnostic.models.Evidence
import com.example.vehicare.domain.diagnostic.models.EvidenceGroup
import com.example.vehicare.domain.diagnostic.models.Severity
import com.example.vehicare.domain.diagnostic.models.VehicleSystem

/**
 * The VehiCare AI diagnostic knowledge base.
 *
 * ============================ IMPORTANT ============================
 * Every prior, likelihood and threshold below is an **illustrative prototype value** authored for
 * this thesis prototype. They are informed by commonly documented symptom/fault relationships but
 * are NOT validated automotive statistics, and they have not been calibrated against a vehicle
 * fleet or workshop dataset. All values are documented in docs/DIAGNOSTIC_MODEL.md.
 * ==================================================================
 *
 * Structure: 21 hypotheses (20 ranked issues + the `other_undetermined` residual), 65 atomic
 * evidence items and 14 evidence groups. Evidence groups carry a SINGLE likelihood per hypothesis
 * because their members are physically correlated (Section 6.2: grouped evidence, never naive
 * multiplication of correlated likelihoods).
 */
object VehiCareKnowledgeBase {

    const val VERSION = "1.0.0"

    /** Documented likelihood floor for unlisted (hypothesis, evidence) pairs. Never 0.0. */
    const val LIKELIHOOD_FLOOR = 0.05

    val evidence: Map<String, Evidence> = listOf(
        // --- Starting / electrical ---
        Evidence("slow_cranking", "Engine cranks slowly", VehicleSystem.STARTING_IGNITION),
        Evidence("cranks_no_start", "Cranks but does not start", VehicleSystem.STARTING_IGNITION),
        Evidence("no_crank", "Does not crank at all", VehicleSystem.STARTING_IGNITION),
        Evidence("starts_then_stalls", "Starts then stalls", VehicleSystem.STARTING_IGNITION),
        Evidence("clicking_on_start", "Clicking sound when starting", VehicleSystem.STARTING_IGNITION, safetyRelevant = true),
        Evidence("dim_lights", "Dashboard or headlights dim / flickering", VehicleSystem.ELECTRICAL),
        Evidence("battery_older_than_3y", "Battery is three or more years old", VehicleSystem.ELECTRICAL),
        Evidence("warning_battery_light", "Battery warning light illuminated", VehicleSystem.WARNING_LIGHTS),

        // --- Engine ---
        Evidence("engine_clicking_ticking", "Clicking or ticking from the engine", VehicleSystem.NOISE),
        Evidence("engine_knocking", "Knocking from the engine", VehicleSystem.NOISE, safetyRelevant = true),
        Evidence("engine_grinding", "Grinding from the engine", VehicleSystem.NOISE),
        Evidence("engine_whining", "Whining noise", VehicleSystem.NOISE),
        Evidence("rough_idle", "Rough or unstable idle", VehicleSystem.ENGINE),
        Evidence("misfire_hesitation", "Hesitation or misfire under acceleration", VehicleSystem.ENGINE),
        Evidence("loss_of_power_mild", "Mild reduction in engine power", VehicleSystem.PERFORMANCE),
        Evidence("loss_of_power_significant", "Significant loss of engine power", VehicleSystem.PERFORMANCE),
        Evidence("intermittent_power_loss", "Intermittent loss of power", VehicleSystem.PERFORMANCE, safetyRelevant = true),
        Evidence("poor_fuel_economy", "Higher than usual fuel consumption", VehicleSystem.FUEL),
        Evidence("oil_pressure_warning", "Oil pressure warning light illuminated", VehicleSystem.WARNING_LIGHTS, safetyRelevant = true),
        Evidence("oil_level_low", "Engine oil level is low", VehicleSystem.ENGINE),
        Evidence("oil_leak_visible", "Visible oil leak or oil spots", VehicleSystem.ENGINE),
        Evidence("sudden_power_loss_driving", "Sudden loss of power while driving", VehicleSystem.PERFORMANCE, safetyRelevant = true),
        Evidence("check_engine_light", "Check engine light illuminated", VehicleSystem.WARNING_LIGHTS),
        Evidence("belt_squeal", "Squealing from the belt area", VehicleSystem.NOISE),

        // --- Cooling ---
        Evidence("overheating_occasional", "Temperature gauge high occasionally", VehicleSystem.COOLING),
        Evidence("overheating_frequent", "Temperature gauge high frequently", VehicleSystem.COOLING, safetyRelevant = true),
        Evidence("overheating_current", "Currently overheating", VehicleSystem.COOLING, safetyRelevant = true),
        Evidence("coolant_loss", "Coolant level drops repeatedly", VehicleSystem.COOLING),
        Evidence("coolant_low", "Coolant level is low", VehicleSystem.COOLING),
        Evidence("steam_from_engine_bay", "Steam from the engine bay", VehicleSystem.COOLING, safetyRelevant = true),
        Evidence("fan_not_running", "Radiator fan does not run", VehicleSystem.COOLING),
        Evidence("gauge_fluctuating", "Temperature gauge fluctuates", VehicleSystem.COOLING),
        Evidence("sweet_smell", "Sweet smell (coolant odour)", VehicleSystem.COOLING),
        Evidence("temperature_warning_light", "Temperature warning light illuminated", VehicleSystem.WARNING_LIGHTS, safetyRelevant = true),

        // --- Fuel ---
        Evidence("fuel_smell", "Fuel smell around the vehicle", VehicleSystem.FUEL, safetyRelevant = true),
        Evidence("fuel_leak_visible", "Visible fuel leak", VehicleSystem.FUEL, safetyRelevant = true),
        Evidence("hesitation_under_load", "Hesitation when accelerating uphill", VehicleSystem.FUEL),

        // --- Exhaust ---
        Evidence("smoke_white", "White exhaust smoke", VehicleSystem.EXHAUST),
        Evidence("smoke_blue", "Blue exhaust smoke", VehicleSystem.EXHAUST),
        Evidence("smoke_black", "Black exhaust smoke", VehicleSystem.EXHAUST),
        Evidence("exhaust_noise_loud", "Unusually loud exhaust", VehicleSystem.EXHAUST),
        Evidence("rotten_egg_smell", "Rotten-egg smell from the exhaust", VehicleSystem.EXHAUST),
        Evidence("smoke_from_engine_bay", "Smoke from the engine bay", VehicleSystem.ENGINE, safetyRelevant = true),
        Evidence("electrical_burning_smell", "Electrical burning smell", VehicleSystem.ELECTRICAL, safetyRelevant = true),

        // --- Braking ---
        Evidence("brake_squeaking", "Brakes squeak", VehicleSystem.BRAKING),
        Evidence("brake_grinding", "Brakes grind", VehicleSystem.BRAKING),
        Evidence("brake_vibration", "Vibration when braking", VehicleSystem.BRAKING),
        Evidence("brake_soft_pedal", "Brake pedal feels soft or spongy", VehicleSystem.BRAKING, safetyRelevant = true),
        Evidence("brake_reduced_performance", "Reduced braking performance", VehicleSystem.BRAKING, safetyRelevant = true),
        Evidence("brake_pulling_side", "Vehicle pulls to one side when braking", VehicleSystem.BRAKING, safetyRelevant = true),
        Evidence("brake_burning_smell", "Burning smell while braking", VehicleSystem.BRAKING, safetyRelevant = true),
        Evidence("abs_light", "ABS warning light illuminated", VehicleSystem.WARNING_LIGHTS),
        Evidence("airbag_light", "Airbag warning light illuminated", VehicleSystem.WARNING_LIGHTS),

        // --- Transmission ---
        Evidence("transmission_slipping", "Transmission slips", VehicleSystem.TRANSMISSION),
        Evidence("delayed_engagement", "Delay engaging drive or reverse", VehicleSystem.TRANSMISSION),
        Evidence("hard_shifting", "Harsh or jerky gear changes", VehicleSystem.TRANSMISSION),
        Evidence("transmission_fluid_leak", "Reddish transmission fluid leak", VehicleSystem.TRANSMISSION),
        Evidence("transmission_warning_light", "Transmission warning light illuminated", VehicleSystem.WARNING_LIGHTS),

        // --- Suspension / steering / tires ---
        Evidence("steering_vibration", "Steering wheel vibrates", VehicleSystem.SUSPENSION_STEERING),
        Evidence("steering_pull", "Vehicle pulls while driving straight", VehicleSystem.SUSPENSION_STEERING),
        Evidence("steering_loose", "Loose or vague steering", VehicleSystem.SUSPENSION_STEERING, safetyRelevant = true),
        Evidence("steering_hard", "Steering is hard to turn", VehicleSystem.SUSPENSION_STEERING, safetyRelevant = true),
        Evidence("clunk_over_bumps", "Clunking over bumps", VehicleSystem.SUSPENSION_STEERING),
        Evidence("tire_vibration_high_speed", "Vibration at higher speeds", VehicleSystem.TIRES),
        Evidence("uneven_tire_wear", "Uneven tire wear", VehicleSystem.TIRES)
    ).also { list ->
        // Fail loudly on duplicate ids before they are silently collapsed by associateBy.
        require(list.size == list.distinctBy { it.id }.size) {
            "Duplicate evidence ids in the knowledge base"
        }
    }.associateBy { it.id }

    val groups: List<EvidenceGroup> = listOf(
        EvidenceGroup("g_battery_start_cluster", "Electrical starting cluster", VehicleSystem.ELECTRICAL, setOf("slow_cranking", "dim_lights", "clicking_on_start")),
        EvidenceGroup("g_dead_crank_cluster", "No-crank cluster", VehicleSystem.STARTING_IGNITION, setOf("no_crank", "engine_clicking_ticking")),
        EvidenceGroup("g_overheat_cluster", "Severe overheating cluster", VehicleSystem.COOLING, setOf("overheating_current", "steam_from_engine_bay", "coolant_loss")),
        EvidenceGroup("g_brake_noise_cluster", "Brake noise cluster", VehicleSystem.BRAKING, setOf("brake_squeaking", "brake_grinding")),
        EvidenceGroup("g_brake_safety_cluster", "Braking performance cluster", VehicleSystem.BRAKING, setOf("brake_reduced_performance", "brake_soft_pedal", "brake_pulling_side")),
        EvidenceGroup("g_transmission_slip_cluster", "Transmission slip cluster", VehicleSystem.TRANSMISSION, setOf("transmission_slipping", "delayed_engagement", "hard_shifting")),
        EvidenceGroup("g_misfire_cluster", "Misfire cluster", VehicleSystem.ENGINE, setOf("misfire_hesitation", "rough_idle")),
        EvidenceGroup("g_power_loss_cluster", "Power loss cluster", VehicleSystem.PERFORMANCE, setOf("loss_of_power_significant", "intermittent_power_loss")),
        EvidenceGroup("g_oil_pressure_cluster", "Oil pressure cluster", VehicleSystem.ENGINE, setOf("oil_pressure_warning", "engine_knocking")),
        EvidenceGroup("g_wheel_imbalance_cluster", "Wheel vibration cluster", VehicleSystem.TIRES, setOf("tire_vibration_high_speed", "steering_vibration")),
        EvidenceGroup("g_fuel_odour_cluster", "Fuel odour cluster", VehicleSystem.FUEL, setOf("fuel_smell", "fuel_leak_visible")),
        EvidenceGroup("g_steering_fault_cluster", "Steering fault cluster", VehicleSystem.SUSPENSION_STEERING, setOf("steering_loose", "steering_hard", "steering_pull")),
        EvidenceGroup("g_coolant_level_cluster", "Coolant level cluster", VehicleSystem.COOLING, setOf("coolant_low", "sweet_smell")),
        EvidenceGroup("g_exhaust_smoke_cluster", "Exhaust smoke cluster", VehicleSystem.EXHAUST, setOf("smoke_blue", "smoke_white"))
    )

    private fun h(
        id: String,
        name: String,
        system: VehicleSystem,
        description: String,
        prior: Double,
        severity: Severity,
        serviceCategory: String,
        inspection: List<String>,
        evidence: Map<String, Double>,
        groups: Map<String, Double> = emptyMap(),
        causes: List<String> = emptyList(),
        safety: List<String> = emptyList(),
        residual: Boolean = false
    ) = Hypothesis(
        id = id,
        name = name,
        system = system,
        description = description,
        priorProbability = prior,
        severity = severity,
        recommendedServiceCategory = serviceCategory,
        inspectionSteps = inspection,
        safetyWarnings = safety,
        possibleCauses = causes,
        evidenceLikelihoods = evidence,
        groupLikelihoods = groups,
        isResidual = residual
    )

    val hypotheses: List<Hypothesis> = listOf(
        h(
            id = "weak_battery",
            name = "Weak or failing battery",
            system = VehicleSystem.ELECTRICAL,
            description = "The battery no longer holds enough charge to crank the engine reliably, especially when cold or after short trips.",
            prior = 0.070,
            severity = Severity.HIGH,
            serviceCategory = "Battery inspection and replacement",
            inspection = listOf(
                "Measure battery voltage at rest and while cranking",
                "Inspect battery terminals and earth straps for corrosion",
                "Test the alternator charging output",
                "Check the battery manufacture date"
            ),
            causes = listOf("Battery at end of service life", "Loose or corroded terminals", "Parasitic drain while parked"),
            safety = listOf("A failing battery can leave the vehicle unable to restart in traffic. Avoid shutting the engine down in unsafe locations."),
            evidence = mapOf(
                "slow_cranking" to 0.80, "cranks_no_start" to 0.50, "no_crank" to 0.35,
                "starts_then_stalls" to 0.30, "clicking_on_start" to 0.60, "dim_lights" to 0.70,
                "battery_older_than_3y" to 0.70, "warning_battery_light" to 0.65
            ),
            groups = mapOf("g_battery_start_cluster" to 0.85, "g_dead_crank_cluster" to 0.55)
        ),
        h(
            id = "faulty_alternator",
            name = "Faulty alternator",
            system = VehicleSystem.ELECTRICAL,
            description = "The alternator is not recharging the battery properly, so electrical systems run down while driving.",
            prior = 0.050,
            severity = Severity.HIGH,
            serviceCategory = "Charging system repair",
            inspection = listOf(
                "Measure charging voltage at idle and under electrical load",
                "Inspect the drive belt and tensioner",
                "Check for a parasitic drain",
                "Inspect charging circuit wiring and grounds"
            ),
            causes = listOf("Worn alternator brushes or bearings", "Failed voltage regulator", "Slipping or worn drive belt"),
            safety = listOf("If charging fails completely the vehicle may stall or lose power steering and lighting while moving."),
            evidence = mapOf(
                "slow_cranking" to 0.65, "cranks_no_start" to 0.45, "dim_lights" to 0.75,
                "clicking_on_start" to 0.35, "warning_battery_light" to 0.80, "battery_older_than_3y" to 0.20,
                "starts_then_stalls" to 0.35, "engine_whining" to 0.45, "electrical_burning_smell" to 0.25
            ),
            groups = mapOf("g_battery_start_cluster" to 0.60)
        ),
        h(
            id = "starter_motor_failure",
            name = "Starter motor failure",
            system = VehicleSystem.STARTING_IGNITION,
            description = "The starter motor or its solenoid fails to turn the engine, producing a click but no cranking.",
            prior = 0.045,
            severity = Severity.HIGH,
            serviceCategory = "Starter motor service",
            inspection = listOf(
                "Confirm battery voltage is adequate before testing the starter",
                "Listen for a single loud click from the starter solenoid",
                "Check the starter relay and related fuses",
                "Measure starter current draw"
            ),
            causes = listOf("Worn starter solenoid contacts", "Worn starter brushes", "Faulty starter relay or ignition switch"),
            evidence = mapOf(
                "no_crank" to 0.90, "clicking_on_start" to 0.85, "engine_clicking_ticking" to 0.80,
                "slow_cranking" to 0.45, "cranks_no_start" to 0.35, "dim_lights" to 0.30,
                "starts_then_stalls" to 0.25
            ),
            groups = mapOf("g_dead_crank_cluster" to 0.85, "g_battery_start_cluster" to 0.55)
        ),
        h(
            id = "worn_spark_plugs",
            name = "Worn or fouled spark plugs",
            system = VehicleSystem.ENGINE,
            description = "Spark plugs past their service interval cause misfires, rough running and hard starting.",
            prior = 0.060,
            severity = Severity.MEDIUM,
            serviceCategory = "Ignition service",
            inspection = listOf(
                "Inspect spark plugs for wear, gap and fouling",
                "Read stored misfire codes",
                "Check ignition leads and coil boots"
            ),
            causes = listOf("Plugs beyond the service interval", "Incorrect plug gap or heat range", "Oil or fuel fouling"),
            evidence = mapOf(
                "cranks_no_start" to 0.60, "rough_idle" to 0.75, "misfire_hesitation" to 0.70,
                "starts_then_stalls" to 0.40, "poor_fuel_economy" to 0.60, "loss_of_power_mild" to 0.55,
                "check_engine_light" to 0.70
            ),
            groups = mapOf("g_misfire_cluster" to 0.65, "g_battery_start_cluster" to 0.15)
        ),
        h(
            id = "failing_ignition_coil",
            name = "Failing ignition coil",
            system = VehicleSystem.ENGINE,
            description = "A weak ignition coil produces intermittent misfires and a noticeable drop in power.",
            prior = 0.045,
            severity = Severity.MEDIUM,
            serviceCategory = "Ignition service",
            inspection = listOf(
                "Read misfire codes and identify the affected cylinder",
                "Swap-test the suspected coil",
                "Measure coil primary and secondary resistance"
            ),
            causes = listOf("Coil winding breakdown from heat", "Cracked coil housing", "Worn coil boot"),
            evidence = mapOf(
                "misfire_hesitation" to 0.75, "rough_idle" to 0.65, "loss_of_power_mild" to 0.50,
                "check_engine_light" to 0.80, "cranks_no_start" to 0.30
            ),
            groups = mapOf("g_misfire_cluster" to 0.70)
        ),
        h(
            id = "clogged_air_filter",
            name = "Clogged air filter",
            system = VehicleSystem.ENGINE,
            description = "A restricted intake reduces airflow, which costs power and fuel economy.",
            prior = 0.050,
            severity = Severity.LOW,
            serviceCategory = "Routine maintenance",
            inspection = listOf(
                "Inspect the engine air filter element",
                "Check intake ducting for blockages"
            ),
            causes = listOf("Filter beyond its service interval", "Dusty driving conditions"),
            evidence = mapOf(
                "poor_fuel_economy" to 0.60, "loss_of_power_mild" to 0.55, "smoke_black" to 0.30,
                "rough_idle" to 0.35, "hesitation_under_load" to 0.45
            )
        ),
        h(
            id = "low_engine_oil",
            name = "Low engine oil or oil pressure",
            system = VehicleSystem.ENGINE,
            description = "Insufficient oil or low oil pressure accelerates wear and can damage the engine quickly.",
            prior = 0.040,
            severity = Severity.CRITICAL,
            serviceCategory = "Urgent engine inspection",
            inspection = listOf(
                "Check the oil level and top up with the specified grade",
                "Inspect for oil leaks and seepage",
                "Verify oil pressure with a mechanical gauge",
                "Inspect the PCV system"
            ),
            causes = listOf("Oil level below minimum", "Worn oil pump", "Restricted oil filter", "Oil leak from seals or gaskets"),
            safety = listOf("Low oil pressure can cause rapid engine failure and sudden loss of power while driving."),
            evidence = mapOf(
                "oil_level_low" to 0.85, "oil_leak_visible" to 0.60, "oil_pressure_warning" to 0.70,
                "engine_knocking" to 0.65, "engine_clicking_ticking" to 0.40
            ),
            groups = mapOf("g_oil_pressure_cluster" to 0.75)
        ),
        h(
            id = "serpentine_belt_issue",
            name = "Serpentine belt or tensioner issue",
            system = VehicleSystem.ENGINE,
            description = "A worn or slipping accessory belt reduces charging and cooling, and can fail completely.",
            prior = 0.035,
            severity = Severity.HIGH,
            serviceCategory = "Belt and tensioner service",
            inspection = listOf(
                "Inspect the serpentine belt for cracks, glazing and missing ribs",
                "Check the automatic tensioner travel",
                "Check pulley alignment",
                "Verify all driven accessories rotate freely"
            ),
            causes = listOf("Belt at end of life", "Weak or seized tensioner", "Misaligned pulley"),
            safety = listOf("A broken belt can stop the alternator, water pump and power steering simultaneously."),
            evidence = mapOf(
                "belt_squeal" to 0.85, "engine_whining" to 0.40, "warning_battery_light" to 0.35,
                "dim_lights" to 0.30, "fan_not_running" to 0.30, "steam_from_engine_bay" to 0.20
            )
        )
    )

    private val remainingHypotheses: List<Hypothesis> = listOf(
        h(
            id = "clogged_fuel_filter",
            name = "Clogged fuel filter",
            system = VehicleSystem.FUEL,
            description = "A restricted fuel filter starves the engine under load, causing hesitation and power loss.",
            prior = 0.040,
            severity = Severity.MEDIUM,
            serviceCategory = "Fuel system service",
            inspection = listOf(
                "Replace the fuel filter if past its interval",
                "Measure fuel pressure and flow",
                "Inspect the fuel tank for contamination"
            ),
            causes = listOf("Filter clogged with debris", "Contaminated fuel"),
            evidence = mapOf(
                "hesitation_under_load" to 0.70, "loss_of_power_mild" to 0.60, "loss_of_power_significant" to 0.45,
                "misfire_hesitation" to 0.45, "starts_then_stalls" to 0.30, "poor_fuel_economy" to 0.35
            ),
            groups = mapOf("g_power_loss_cluster" to 0.45)
        ),
        h(
            id = "faulty_fuel_pump",
            name = "Faulty fuel pump",
            system = VehicleSystem.FUEL,
            description = "A weak fuel pump cannot maintain pressure, which can cause no-start conditions or stalling.",
            prior = 0.035,
            severity = Severity.HIGH,
            serviceCategory = "Fuel system repair",
            inspection = listOf(
                "Measure fuel rail pressure with the key on and while cranking",
                "Listen for fuel pump priming noise",
                "Check the fuel pump relay and fuse",
                "Inspect the wiring harness at the tank"
            ),
            causes = listOf("Worn pump motor", "Failed pump check valve", "Faulty relay or wiring"),
            evidence = mapOf(
                "cranks_no_start" to 0.55, "starts_then_stalls" to 0.60, "loss_of_power_significant" to 0.45,
                "hesitation_under_load" to 0.55, "intermittent_power_loss" to 0.50
            ),
            groups = mapOf("g_power_loss_cluster" to 0.50)
        ),
        h(
            id = "cooling_system_overheating",
            name = "Cooling system overheating",
            system = VehicleSystem.COOLING,
            description = "The cooling system cannot remove enough heat, so engine temperature rises beyond the safe range.",
            prior = 0.045,
            severity = Severity.CRITICAL,
            serviceCategory = "Urgent cooling system inspection",
            inspection = listOf(
                "Check coolant level and condition when the engine is cold",
                "Pressure-test the cooling system for leaks",
                "Verify radiator fan operation and thermostat opening",
                "Inspect the water pump and hoses"
            ),
            causes = listOf("Low coolant", "Failed thermostat", "Inoperative radiator fan", "Failing water pump"),
            safety = listOf(
                "Continuing to drive while overheating can warp the cylinder head and destroy the engine.",
                "Do not open the coolant cap while the engine is hot."
            ),
            evidence = mapOf(
                "overheating_occasional" to 0.70, "overheating_frequent" to 0.85, "overheating_current" to 0.90,
                "temperature_warning_light" to 0.75, "gauge_fluctuating" to 0.60, "coolant_loss" to 0.55,
                "steam_from_engine_bay" to 0.70, "fan_not_running" to 0.45, "sweet_smell" to 0.40,
                "coolant_low" to 0.55
            ),
            groups = mapOf("g_overheat_cluster" to 0.90, "g_coolant_level_cluster" to 0.55)
        ),
        h(
            id = "low_coolant",
            name = "Low coolant level or coolant leak",
            system = VehicleSystem.COOLING,
            description = "Coolant loss from a hose, radiator or gasket lowers the system's ability to absorb heat.",
            prior = 0.055,
            severity = Severity.HIGH,
            serviceCategory = "Cooling system service",
            inspection = listOf(
                "Top up coolant and look for external leaks",
                "Inspect hoses, radiator and heater core for seepage",
                "Check for combustion gases in the coolant (head gasket test)"
            ),
            causes = listOf("Split or loose hose", "Radiator seepage", "Head gasket failure", "Faulty pressure cap"),
            safety = listOf("Coolant loss can escalate to overheating within minutes."),
            evidence = mapOf(
                "coolant_low" to 0.85, "coolant_loss" to 0.80, "sweet_smell" to 0.60,
                "overheating_occasional" to 0.55, "overheating_frequent" to 0.60, "steam_from_engine_bay" to 0.45,
                "smoke_white" to 0.35, "gauge_fluctuating" to 0.40
            ),
            groups = mapOf("g_coolant_level_cluster" to 0.85, "g_overheat_cluster" to 0.60, "g_exhaust_smoke_cluster" to 0.35)
        ),
        h(
            id = "thermostat_malfunction",
            name = "Thermostat malfunction",
            system = VehicleSystem.COOLING,
            description = "A thermostat stuck closed or slow to open causes temperature swings and overheating.",
            prior = 0.035,
            severity = Severity.HIGH,
            serviceCategory = "Cooling system service",
            inspection = listOf(
                "Monitor warm-up time and gauge behaviour",
                "Test the thermostat in hot water or replace it",
                "Check coolant flow through the radiator"
            ),
            causes = listOf("Thermostat stuck closed", "Thermostat stuck open", "Wax element failure"),
            evidence = mapOf(
                "gauge_fluctuating" to 0.70, "overheating_occasional" to 0.60, "overheating_frequent" to 0.55,
                "coolant_loss" to 0.20, "fan_not_running" to 0.20, "temperature_warning_light" to 0.45
            ),
            groups = mapOf("g_overheat_cluster" to 0.55)
        ),
        h(
            id = "radiator_fan_failure",
            name = "Radiator fan failure",
            system = VehicleSystem.COOLING,
            description = "Without a working radiator fan, the engine overheats in traffic or when stationary.",
            prior = 0.030,
            severity = Severity.HIGH,
            serviceCategory = "Cooling system repair",
            inspection = listOf(
                "Confirm the fan runs when the engine reaches operating temperature",
                "Check the fan relay, fuse and coolant temperature sensor",
                "Test the fan motor directly"
            ),
            causes = listOf("Failed fan motor", "Blown fuse or relay", "Faulty temperature sensor"),
            evidence = mapOf(
                "fan_not_running" to 0.90, "overheating_occasional" to 0.55, "overheating_frequent" to 0.60,
                "gauge_fluctuating" to 0.35, "temperature_warning_light" to 0.45
            ),
            groups = mapOf("g_overheat_cluster" to 0.50)
        ),
        h(
            id = "brake_pad_wear",
            name = "Worn brake pads",
            system = VehicleSystem.BRAKING,
            description = "Brake pads worn to their limit cause squealing and lengthen stopping distances.",
            prior = 0.065,
            severity = Severity.HIGH,
            serviceCategory = "Brake service",
            inspection = listOf(
                "Measure remaining pad thickness at each wheel",
                "Inspect wear indicators and caliper slide pins",
                "Check brake fluid level and condition"
            ),
            causes = listOf("Pads beyond minimum thickness", "Seized caliper slide pins", "Frequent heavy braking"),
            safety = listOf("Worn pads reduce stopping power and can damage the rotors."),
            evidence = mapOf(
                "brake_squeaking" to 0.80, "brake_grinding" to 0.60, "brake_reduced_performance" to 0.55,
                "brake_soft_pedal" to 0.30, "brake_vibration" to 0.25, "abs_light" to 0.20
            ),
            groups = mapOf("g_brake_noise_cluster" to 0.80, "g_brake_safety_cluster" to 0.50)
        ),
        h(
            id = "brake_rotor_issues",
            name = "Brake rotor or disc problem",
            system = VehicleSystem.BRAKING,
            description = "Warped, scored or worn rotors cause vibration and inconsistent braking.",
            prior = 0.045,
            severity = Severity.HIGH,
            serviceCategory = "Brake service",
            inspection = listOf(
                "Measure rotor thickness and runout",
                "Inspect rotor surfaces for scoring and heat cracks",
                "Road-test for pedal pulsation"
            ),
            causes = listOf("Warped rotor", "Deep scoring from metal-on-metal contact", "Rotor below minimum thickness"),
            safety = listOf("Braking performance may be inconsistent in an emergency stop."),
            evidence = mapOf(
                "brake_vibration" to 0.80, "brake_grinding" to 0.65, "brake_squeaking" to 0.35,
                "brake_pulling_side" to 0.40, "brake_reduced_performance" to 0.45, "brake_burning_smell" to 0.30
            ),
            groups = mapOf("g_brake_noise_cluster" to 0.65, "g_brake_safety_cluster" to 0.55)
        ),
        h(
            id = "low_transmission_fluid",
            name = "Low or degraded transmission fluid",
            system = VehicleSystem.TRANSMISSION,
            description = "Insufficient or burnt transmission fluid causes slipping and delayed engagement.",
            prior = 0.040,
            severity = Severity.MEDIUM,
            serviceCategory = "Transmission service",
            inspection = listOf(
                "Check fluid level and colour when warm",
                "Look for leaks at the pan, seals and cooler lines",
                "Check the fluid for a burnt smell"
            ),
            causes = listOf("Fluid level low", "Fluid overdue for replacement", "External leak at seals or lines"),
            evidence = mapOf(
                "transmission_slipping" to 0.70, "delayed_engagement" to 0.65, "hard_shifting" to 0.60,
                "transmission_fluid_leak" to 0.75, "transmission_warning_light" to 0.45
            ),
            groups = mapOf("g_transmission_slip_cluster" to 0.70)
        ),
        h(
            id = "transmission_malfunction",
            name = "Internal transmission malfunction",
            system = VehicleSystem.TRANSMISSION,
            description = "Worn clutches, bands or valve body faults cause persistent slipping and harsh shifts.",
            prior = 0.035,
            severity = Severity.CRITICAL,
            serviceCategory = "Transmission repair",
            inspection = listOf(
                "Read transmission fault codes",
                "Road-test to record shift behaviour and slip points",
                "Inspect the pan for debris",
                "Perform a transmission pressure test"
            ),
            causes = listOf("Worn clutch packs", "Valve body fault", "Failed torque converter"),
            safety = listOf("A transmission that fails suddenly can leave the vehicle unable to move in traffic."),
            evidence = mapOf(
                "transmission_slipping" to 0.85, "hard_shifting" to 0.80, "delayed_engagement" to 0.70,
                "transmission_warning_light" to 0.70, "loss_of_power_significant" to 0.35, "loss_of_power_mild" to 0.30
            ),
            groups = mapOf("g_transmission_slip_cluster" to 0.85)
        ),
        h(
            id = "faulty_oxygen_sensor",
            name = "Faulty oxygen sensor",
            system = VehicleSystem.EXHAUST,
            description = "A degraded oxygen sensor makes the engine run rich, raising consumption and emissions.",
            prior = 0.045,
            severity = Severity.MEDIUM,
            serviceCategory = "Emission system service",
            inspection = listOf(
                "Read fuel-trim codes and oxygen sensor data",
                "Check the sensor heater circuit",
                "Inspect for exhaust leaks upstream of the sensor"
            ),
            causes = listOf("Sensor contaminated or aged", "Heater circuit failure", "Exhaust leak before the sensor"),
            evidence = mapOf(
                "check_engine_light" to 0.75, "poor_fuel_economy" to 0.60, "smoke_black" to 0.35,
                "rotten_egg_smell" to 0.35, "rough_idle" to 0.30
            )
        ),
        h(
            id = "tire_wheel_imbalance",
            name = "Tire or wheel imbalance",
            system = VehicleSystem.TIRES,
            description = "Out-of-balance or unevenly worn tires produce vibration that worsens with speed.",
            prior = 0.040,
            severity = Severity.MEDIUM,
            serviceCategory = "Wheel alignment and balancing",
            inspection = listOf(
                "Check tire pressures and tread wear pattern",
                "Balance all four wheels",
                "Check for a buckled rim or separated tire belt"
            ),
            causes = listOf("Lost wheel weights", "Bent rim", "Uneven tire wear", "Tire belt separation"),
            evidence = mapOf(
                "tire_vibration_high_speed" to 0.85, "steering_vibration" to 0.70, "uneven_tire_wear" to 0.65,
                "brake_vibration" to 0.20
            ),
            groups = mapOf("g_wheel_imbalance_cluster" to 0.85)
        ),
        h(
            id = "other_undetermined",
            name = "Other / undetermined",
            system = VehicleSystem.OTHER,
            description = "The reported combination of symptoms does not clearly favour one of the modelled issues, or a condition outside the knowledge base is involved.",
            prior = 0.075,
            severity = Severity.LOW,
            serviceCategory = "General inspection",
            inspection = listOf(
                "Describe the symptoms to a qualified mechanic in person",
                "Consider a full diagnostic scan and road test"
            ),
            causes = listOf("Condition outside the modelled knowledge base", "Insufficient distinguishing evidence", "Multiple simultaneous faults"),
            evidence = emptyMap(),
            groups = emptyMap(),
            residual = true
        )
    )

    val allHypotheses: List<Hypothesis> = hypotheses + remainingHypotheses

    /** Validated singleton used by the app and by tests. */
    val instance: KnowledgeBase = KnowledgeBase(
        version = VERSION,
        likelihoodFloor = LIKELIHOOD_FLOOR,
        hypotheses = allHypotheses,
        evidence = evidence,
        groups = groups
    )
}
