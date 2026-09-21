package com.example.vehicare.domain.diagnostic.bayesian

import com.example.vehicare.domain.diagnostic.models.DiagnosticAnalysis
import com.example.vehicare.domain.diagnostic.models.SymptomEvidence
import com.example.vehicare.domain.diagnostic.models.VehicleContext

/**
 * The diagnostic contract. Implemented locally by [NaiveBayesDiagnosticEngine]; a remote or
 * alternative implementation could be substituted without touching the UI (Section 2: the engine
 * sits behind an interface so a backend could be added later).
 */
interface BayesianDiagnosticEngine {

    /** Model version, stored with every persisted assessment for reproducibility. */
    val engineVersion: String

    /** Knowledge-base version used for this analysis, stored with every persisted assessment. */
    val knowledgeBaseVersion: String

    fun analyzeSymptoms(evidence: SymptomEvidence, vehicle: VehicleContext): DiagnosticAnalysis
}

/**
 * Thresholds for the sufficiency rule (Section 6.2).
 *
 * The engine returns an explicit *insufficient evidence* result instead of presenting a ranking
 * that the data cannot support. All values are illustrative prototype settings and are documented
 * in docs/DIAGNOSTIC_MODEL.md.
 */
data class DiagnosticEngineConfig(
    /** Answer count below which no ranking is attempted at all. */
    val minAnsweredEvidence: Int = 3,

    /** The best-supported hypothesis must clear this posterior for a ranking to be reported. */
    val minTopPosterior: Double = 0.35,

    /** The best-supported hypothesis must also match a reasonable share of reported findings. */
    val minEvidenceStrength: Double = 0.40,

    /** Numerical safety net: never take log(1 - P) exactly at zero. */
    val minimumComplement: Double = 1e-6
)
