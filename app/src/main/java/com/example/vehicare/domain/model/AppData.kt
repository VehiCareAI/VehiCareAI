package com.example.vehicare.domain.model

/** Measurement units. Metric is the default; imperial converts display only. */
enum class MeasurementUnit(val id: String, val label: String) {
    METRIC("metric", "Kilometres (km)"),
    IMPERIAL("imperial", "Miles (mi)");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: METRIC
    }
}

/**
 * Local, non-identifying user preferences. There is deliberately no email, password or account
 * field anywhere in the app (Section 1.1).
 */
data class AppPreferences(
    val displayName: String = "",
    val onboardingCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val measurementUnit: MeasurementUnit = MeasurementUnit.METRIC,
    val disclaimerAcknowledged: Boolean = false,
    /** Vehicle the dashboard and assessment flow follow; null means "first vehicle". */
    val selectedVehicleId: String? = null
)

/** Aggregated counters for Home and Profile. */
data class UsageCounts(
    val vehicleCount: Int = 0,
    val assessmentCount: Int = 0,
    val draftCount: Int = 0,
    val openConcernCount: Int = 0,
    val resolvedCount: Int = 0
)

/** Dashboard payload (Section 5.3). */
data class DashboardData(
    val selectedVehicle: Vehicle? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val counts: UsageCounts = UsageCounts(),
    val recentAssessments: List<AssessmentSummary> = emptyList(),
    val latestAssessment: AssessmentSummary? = null,
    val draftToResume: Assessment? = null
)

/** Information items shown by the Home notification icon (Section 5.3). */
data class InfoNotice(
    val id: String,
    val title: String,
    val message: String,
    val actionRoute: String? = null
)

/** One point on the assessments-over-time chart. */
data class TrendPoint(val label: String, val value: Int)

/** Symptoms grouped by system, for the category chart. */
data class SystemFrequency(val systemLabel: String, val count: Int)

/** A symptom reported more than once across assessments. */
data class RepeatedSymptom(val label: String, val occurrences: Int)

/** One point of the probability-across-repeat-assessments chart. */
data class ProbabilityTrendPoint(val label: String, val probability: Double, val hypothesisName: String)

/** Aggregated health trends for the Reports tab (Section 5.16). */
data class HealthTrends(
    val assessmentsOverTime: List<TrendPoint> = emptyList(),
    val symptomsBySystem: List<SystemFrequency> = emptyList(),
    val mostReportedSystem: String? = null,
    val repeatedSymptoms: List<RepeatedSymptom> = emptyList(),
    val probabilityTrend: List<ProbabilityTrendPoint> = emptyList(),
    val resolvedCount: Int = 0,
    val unresolvedCount: Int = 0,
    val totalAssessments: Int = 0,
    val hasData: Boolean = false
)
