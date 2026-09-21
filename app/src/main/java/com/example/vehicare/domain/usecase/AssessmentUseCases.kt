package com.example.vehicare.domain.usecase

import com.example.vehicare.domain.diagnostic.bayesian.BayesianDiagnosticEngine
import com.example.vehicare.domain.diagnostic.bayesian.IssueExplanation
import com.example.vehicare.domain.diagnostic.knowledgebase.KnowledgeBase
import com.example.vehicare.domain.diagnostic.models.DiagnosticAnalysis
import com.example.vehicare.domain.diagnostic.models.RankedIssue
import com.example.vehicare.domain.diagnostic.models.SafetyAlert
import com.example.vehicare.domain.diagnostic.models.SafetyLevel
import com.example.vehicare.domain.diagnostic.models.SufficiencyStatus
import com.example.vehicare.domain.diagnostic.models.SymptomEvidence
import com.example.vehicare.domain.diagnostic.models.VehicleContext
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentResult
import com.example.vehicare.domain.model.AssessmentSafetyAlert
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.VehicleRepository

/**
 * Runs the Bayesian engine for the answers collected by the questionnaire. This is the only place
 * the UI is allowed to obtain an analysis, and it contains no UI or Android code (Section 6).
 */
class EvaluateSymptomsUseCase(private val engine: BayesianDiagnosticEngine) {

    operator fun invoke(assessment: Assessment, vehicle: Vehicle?): DiagnosticAnalysis =
        engine.analyzeSymptoms(evidence = assessment.toSymptomEvidence(), vehicle = vehicle.toContext())
}

/** A stored analysis with the assessment/vehicle it belongs to and whether the model version differs. */
data class LoadedAssessmentAnalysis(
    val assessment: Assessment,
    val vehicle: Vehicle?,
    val analysis: DiagnosticAnalysis,
    val modelVersionChanged: Boolean
)

/**
 * Loads a saved assessment's analysis.
 *
 * The persisted result and safety-alert rows are the authoritative snapshot, so viewing a report
 * DOES NOT silently recompute different numbers: the stored posterior probabilities, severity,
 * evidence lists and alert wording are used as-is, and the current knowledge base only supplies
 * descriptive text (names, causes, checks, explanations). The stored engine/knowledge-base versions
 * are carried through, and [LoadedAssessmentAnalysis.modelVersionChanged] tells the UI when they
 * differ from the running build.
 */
class LoadAssessmentAnalysisUseCase(
    private val assessmentRepository: AssessmentRepository,
    private val vehicleRepository: VehicleRepository,
    private val engine: BayesianDiagnosticEngine,
    private val knowledgeBase: KnowledgeBase
) {

    suspend operator fun invoke(assessmentId: String): LoadedAssessmentAnalysis? {
        val assessment = assessmentRepository.getAssessment(assessmentId) ?: return null
        val vehicle = vehicleRepository.getVehicle(assessment.vehicleId)
        val analysis = PersistedAnalysisReconstructor.reconstruct(
            assessment = assessment,
            vehicle = vehicle,
            evidence = assessment.toSymptomEvidence(),
            storedResults = assessmentRepository.getResults(assessment.id),
            storedAlerts = assessmentRepository.getSafetyAlerts(assessment.id),
            knowledgeBase = knowledgeBase,
            engine = engine
        )
        val modelVersionChanged = assessment.engineVersion.isNotBlank() &&
            (assessment.engineVersion != engine.engineVersion ||
                assessment.knowledgeBaseVersion != knowledgeBase.version)
        return LoadedAssessmentAnalysis(assessment, vehicle, analysis, modelVersionChanged)
    }
}

/**
 * Rebuilds a [DiagnosticAnalysis] from the stored result/alert snapshot.
 *
 * Stored posteriors, severity and evidence lists win over recomputation so a saved report is
 * reproducible; the current knowledge base is only consulted for descriptive text and to resolve
 * evidence labels. When a stored hypothesis id is no longer known (or the snapshot has no ranked
 * rows and the assessment was actually sufficient in the past), the engine re-evaluates instead.
 */
internal object PersistedAnalysisReconstructor {

    fun reconstruct(
        assessment: Assessment,
        vehicle: Vehicle?,
        evidence: SymptomEvidence,
        storedResults: List<AssessmentResult>,
        storedAlerts: List<AssessmentSafetyAlert>,
        knowledgeBase: KnowledgeBase,
        engine: BayesianDiagnosticEngine
    ): DiagnosticAnalysis {
        val vehicleContext = vehicle.toContext()
        val safetyAlerts = storedAlerts.map { stored ->
            SafetyAlert(
                id = stored.alertId,
                title = stored.title,
                message = stored.message,
                level = SafetyLevel.entries.firstOrNull { it.name == stored.level } ?: SafetyLevel.URGENT
            )
        }
        val reported = assessment.presentEvidenceIds.mapNotNull { knowledgeBase.evidenceOrNull(it) }
        val absent = assessment.absentEvidenceIds.mapNotNull { knowledgeBase.evidenceOrNull(it) }
        val generatedAt = assessment.completedAt ?: assessment.startedAt
        val storedEngineVersion = assessment.engineVersion.ifBlank { engine.engineVersion }
        val storedKbVersion = assessment.knowledgeBaseVersion.ifBlank { knowledgeBase.version }

        if (storedResults.isEmpty()) {
            return DiagnosticAnalysis.insufficient(
                evidence = evidence,
                generatedAtEpochMillis = generatedAt,
                engineVersion = storedEngineVersion,
                knowledgeBaseVersion = storedKbVersion,
                safetyAlerts = safetyAlerts,
                reportedEvidence = reported,
                absentEvidence = absent
            )
        }

        if (storedResults.any { knowledgeBase.hypothesis(it.hypothesisId) == null }) {
            return engine.analyzeSymptoms(evidence, vehicleContext)
        }

        val ranked = storedResults.sortedBy { it.rank }.map { result ->
            val hypothesis = knowledgeBase.hypothesis(result.hypothesisId)!!
            val supporting = result.supportingEvidenceIds.mapNotNull { knowledgeBase.evidenceOrNull(it) }
            val contradicting = result.contradictingEvidenceIds.mapNotNull { knowledgeBase.evidenceOrNull(it) }
            val missing = result.missingEvidenceIds.mapNotNull { knowledgeBase.evidenceOrNull(it) }
            val considered = supporting.size + contradicting.size
            val matched = supporting.size
            val strength = if (considered == 0) 0.0 else matched.toDouble() / considered.toDouble()
            RankedIssue(
                hypothesisId = hypothesis.id,
                name = hypothesis.name,
                system = hypothesis.system,
                rank = result.rank,
                posteriorProbability = result.posteriorProbability,
                priorProbability = hypothesis.priorProbability,
                severity = result.severity,
                summary = hypothesis.description,
                explanation = IssueExplanation.build(supporting, strength, vehicleContext),
                supportingEvidence = supporting,
                missingEvidence = missing,
                contradictingEvidence = contradicting,
                possibleCauses = hypothesis.possibleCauses,
                recommendedChecks = hypothesis.inspectionSteps,
                safetyWarnings = hypothesis.safetyWarnings,
                recommendedServiceCategory = hypothesis.recommendedServiceCategory,
                evidenceStrength = strength,
                matchedEvidenceCount = matched,
                consideredEvidenceCount = considered
            )
        }
        val undetermined = (1.0 - ranked.sumOf { it.posteriorProbability }).coerceIn(0.0, 1.0)
        return DiagnosticAnalysis(
            rankedIssues = ranked,
            safetyAlerts = safetyAlerts,
            sufficiency = SufficiencyStatus.SUFFICIENT,
            undeterminedProbability = undetermined,
            reportedEvidence = reported,
            absentEvidence = absent,
            answeredQuestionCount = assessment.answers.size,
            generatedAtEpochMillis = generatedAt,
            engineVersion = storedEngineVersion,
            knowledgeBaseVersion = storedKbVersion
        )
    }
}

/** Maps a persisted/collected assessment into the engine's evidence input exactly once. */
fun Assessment.toSymptomEvidence(): SymptomEvidence = SymptomEvidence(
    presentEvidenceIds = presentEvidenceIds,
    absentEvidenceIds = absentEvidenceIds,
    selectedCategoryIds = selectedCategoryIds.toSet(),
    assessmentTypeId = assessmentTypeId,
    reportedSeverity = reportedSeverity,
    onsetId = onsetId,
    answeredQuestionIds = answers.keys.toSet()
)

/** Maps a domain vehicle into the engine's vehicle context. */
fun Vehicle?.toContext(): VehicleContext = if (this == null) {
    VehicleContext()
} else {
    VehicleContext(
        vehicleId = id,
        displayName = displayName,
        make = make,
        model = model,
        year = year,
        fuelType = fuelType,
        transmission = transmission,
        mileageKm = mileageKm
    )
}
