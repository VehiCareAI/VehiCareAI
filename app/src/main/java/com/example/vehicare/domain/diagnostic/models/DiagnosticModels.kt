package com.example.vehicare.domain.diagnostic.models

/**
 * Severity of a *possible issue* (not of the reported symptom, and never of a probability).
 * Severity is deliberately separate from probability: a low-probability issue may still be
 * safety-critical.
 */
enum class Severity(val label: String, val rank: Int) {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    CRITICAL("Critical", 4)
}

/** Vehicle system a hypothesis belongs to; also used for category filtering and trend charts. */
enum class VehicleSystem(val id: String, val label: String) {
    ENGINE("engine", "Engine"),
    STARTING_IGNITION("starting_ignition", "Starting & Ignition"),
    BRAKING("braking", "Braking System"),
    TRANSMISSION("transmission", "Transmission"),
    COOLING("cooling", "Cooling System"),
    ELECTRICAL("electrical", "Electrical System"),
    SUSPENSION_STEERING("suspension_steering", "Suspension & Steering"),
    EXHAUST("exhaust", "Exhaust System"),
    FUEL("fuel", "Fuel System"),
    WARNING_LIGHTS("warning_lights", "Dashboard Warning Lights"),
    NOISE("noise", "Unusual Sounds"),
    PERFORMANCE("performance", "Reduced Performance"),
    TIRES("tires", "Tires & Wheels"),
    OTHER("other", "Other / Undetermined");

    companion object {
        fun fromId(id: String): VehicleSystem = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/** Symptom categories a user can select before the questionnaire (Section 5.8). */
data class SymptomCategory(
    val id: String,
    val label: String,
    val description: String,
    val system: VehicleSystem
) {
    companion object {
        val all: List<SymptomCategory> = listOf(
            SymptomCategory("engine", "Engine Problems", "Rough running, unusual noise, stalling", VehicleSystem.ENGINE),
            SymptomCategory("starting", "Starting and Ignition", "Cranking, no-start, stalling after start", VehicleSystem.STARTING_IGNITION),
            SymptomCategory("braking", "Braking System", "Noise, vibration, pedal feel, stopping power", VehicleSystem.BRAKING),
            SymptomCategory("transmission", "Transmission", "Slipping, delayed engagement, fluid leaks", VehicleSystem.TRANSMISSION),
            SymptomCategory("cooling", "Cooling System", "Overheating, coolant loss, steam", VehicleSystem.COOLING),
            SymptomCategory("electrical", "Electrical System", "Dim lights, battery, warning lamps", VehicleSystem.ELECTRICAL),
            SymptomCategory("suspension", "Suspension and Steering", "Pulling, vibration, steering looseness", VehicleSystem.SUSPENSION_STEERING),
            SymptomCategory("exhaust", "Exhaust System", "Smoke colour, smell, noise", VehicleSystem.EXHAUST),
            SymptomCategory("fuel", "Fuel System", "Consumption, hesitation, fuel smell", VehicleSystem.FUEL),
            SymptomCategory("warning_lights", "Dashboard Warning Lights", "Which lamps are illuminated", VehicleSystem.WARNING_LIGHTS),
            SymptomCategory("sounds", "Unusual Sounds", "Clicking, knocking, grinding, whining", VehicleSystem.NOISE),
            SymptomCategory("performance", "Reduced Performance", "Power loss, hesitation, poor response", VehicleSystem.PERFORMANCE)
        )
    }
}
