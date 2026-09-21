package com.example.vehicare.domain.diagnostic.bayesian

import com.example.vehicare.domain.diagnostic.models.Evidence
import com.example.vehicare.domain.diagnostic.models.VehicleContext

/**
 * Plain-language explanation (Section 5.13), shared by the live engine and the persisted-snapshot
 * reader so a freshly computed issue and a reconstructed saved issue read the same way. It is built
 * only from reported findings, so non-technical users can see *why* a possibility was raised.
 */
internal object IssueExplanation {

    private const val STRONG_EXPLANATION_STRENGTH = 0.66

    fun build(supporting: List<Evidence>, strength: Double, vehicle: VehicleContext): String {
        val vehicleLabel = vehicle.displayName.ifBlank { "this vehicle" }
        val reported = supporting.joinToString("; ") { it.label.lowercase() }
        return when {
            supporting.isEmpty() ->
                "No reported finding directly supports this possibility, so it is included mainly because it cannot be excluded from the available information about $vehicleLabel."

            strength >= STRONG_EXPLANATION_STRENGTH ->
                "The reported findings — $reported — are consistent with this possibility, and it is one of the " +
                    "better-supported explanations among the possibilities considered for $vehicleLabel."

            else ->
                "The reported finding(s) — $reported — are consistent with this possibility, which is why it " +
                    "appears in the ranking for $vehicleLabel."
        }
    }
}
