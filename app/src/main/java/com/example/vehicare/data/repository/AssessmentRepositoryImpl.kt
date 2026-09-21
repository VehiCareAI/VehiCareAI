package com.example.vehicare.data.repository

import androidx.room.withTransaction
import com.example.vehicare.data.di.IoDispatcher
import com.example.vehicare.data.local.dao.AssessmentDao
import com.example.vehicare.data.local.dao.AssessmentResultDao
import com.example.vehicare.data.local.dao.AssessmentSafetyAlertDao
import com.example.vehicare.data.local.dao.AssessmentSummaryRow
import com.example.vehicare.data.local.dao.DiagnosticHypothesisDao
import com.example.vehicare.data.local.dao.SymptomDao
import com.example.vehicare.data.local.dao.VehicleDao
import com.example.vehicare.data.local.database.VehiCareDatabase
import com.example.vehicare.data.mapper.TrendMapper
import com.example.vehicare.data.mapper.toAlertEntity
import com.example.vehicare.data.mapper.toDomain
import com.example.vehicare.data.mapper.toEntity
import com.example.vehicare.data.mapper.toHypothesisEntity
import com.example.vehicare.data.mapper.toResultEntity
import com.example.vehicare.data.mapper.toSummary
import com.example.vehicare.data.mapper.toSymptomEntity
import com.example.vehicare.domain.diagnostic.knowledgebase.VehiCareKnowledgeBase
import com.example.vehicare.domain.diagnostic.models.DiagnosticAnalysis
import com.example.vehicare.domain.diagnostic.models.VehicleSystem
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentResult
import com.example.vehicare.domain.model.AssessmentSafetyAlert
import com.example.vehicare.domain.model.AssessmentStatus
import com.example.vehicare.domain.model.AssessmentSummary
import com.example.vehicare.domain.model.HealthTrends
import com.example.vehicare.domain.model.UsageCounts
import com.example.vehicare.domain.repository.AssessmentRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Local-first [AssessmentRepository]: drafts, completed assessments, their ranked results and their
 * safety alerts, plus the aggregated history/trend views used by Home and Reports.
 *
 * Name and system resolution prefers the in-memory knowledge base (authoritative for ids it still
 * knows) and falls back to the persisted catalogue snapshots for ids that a later knowledge-base
 * revision renamed or removed — an old report must still read the way it was written.
 */
class AssessmentRepositoryImpl @Inject constructor(
    private val database: VehiCareDatabase,
    private val assessmentDao: AssessmentDao,
    private val resultDao: AssessmentResultDao,
    private val alertDao: AssessmentSafetyAlertDao,
    private val vehicleDao: VehicleDao,
    private val symptomDao: SymptomDao,
    private val hypothesisDao: DiagnosticHypothesisDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AssessmentRepository {

    private val knowledgeBase = VehiCareKnowledgeBase.instance

    override fun observeSummaries(): Flow<List<AssessmentSummary>> =
        summaries(assessmentDao.observeSummaryRows(COMPLETED))

    override fun observeSummariesForVehicle(vehicleId: String): Flow<List<AssessmentSummary>> =
        summaries(assessmentDao.observeSummaryRowsForVehicle(COMPLETED, vehicleId))

    override fun observeAssessment(assessmentId: String): Flow<Assessment?> =
        assessmentDao.observeById(assessmentId)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeDraft(): Flow<Assessment?> =
        assessmentDao.observeLatestDraft(DRAFT)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun getAssessment(assessmentId: String): Assessment? = withContext(ioDispatcher) {
        assessmentDao.getById(assessmentId)?.toDomain()
    }

    override suspend fun getResults(assessmentId: String): List<AssessmentResult> =
        withContext(ioDispatcher) {
            resultDao.getForAssessment(assessmentId).map { it.toDomain() }
        }

    override suspend fun getSafetyAlerts(assessmentId: String): List<AssessmentSafetyAlert> =
        withContext(ioDispatcher) {
            alertDao.getForAssessment(assessmentId).map { it.toDomain() }
        }

    /**
     * Creates or updates a draft. A draft is always persisted as [AssessmentStatus.DRAFT] with no
     * completion time (a completed assessment is written by [complete]), while the original start
     * time is preserved so questionnaire progress keeps its timeline (Section 4).
     */
    override suspend fun saveDraft(assessment: Assessment): String = withContext(ioDispatcher) {
        val assessmentId = assessment.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = assessmentDao.getById(assessmentId)
            requireVehicleExists(assessment.vehicleId)
            syncCatalog(now)
            assessmentDao.upsert(
                assessment.copy(
                    id = assessmentId,
                    status = AssessmentStatus.DRAFT,
                    startedAt = existing?.startedAt ?: assessment.startedAt.takeIf { it > 0 } ?: now,
                    completedAt = null
                ).toEntity()
            )
        }
        assessmentId
    }
    /**
     * Persists a completed assessment together with everything the engine produced: the ranked issues
     * (rank, posterior probability, severity and the supporting/missing/contradicting evidence ids)
     * and the safety alerts, which stay an independent list rather than a property of a ranking.
     *
     * Engine and knowledge-base versions come from [analysis] so a stored report is reproducible.
     * When the analysis carries the evidence it resolved, that snapshot wins over the caller's sets,
     * which keeps the stored assessment consistent with the stored results; only when the analysis
     * resolved no evidence at all are the answered draft sets kept.
     */
    override suspend fun complete(assessment: Assessment, analysis: DiagnosticAnalysis): String =
        withContext(ioDispatcher) {
            val assessmentId = assessment.id.ifBlank { UUID.randomUUID().toString() }
            val generatedAt = analysis.generatedAtEpochMillis.takeIf { it > 0 }
                ?: System.currentTimeMillis()
            val analysisCarriesEvidence =
                analysis.reportedEvidence.isNotEmpty() || analysis.absentEvidence.isNotEmpty()

            database.withTransaction {
                requireVehicleExists(assessment.vehicleId)
                val existing = assessmentDao.getById(assessmentId)
                val present = if (analysisCarriesEvidence) {
                    analysis.reportedEvidence.map { it.id }
                } else {
                    assessment.presentEvidenceIds.toList()
                }
                val absent = if (analysisCarriesEvidence) {
                    analysis.absentEvidence.map { it.id }
                } else {
                    assessment.absentEvidenceIds.toList()
                }

                syncCatalog(generatedAt)
                assessmentDao.upsert(
                    assessment.copy(
                        id = assessmentId,
                        status = AssessmentStatus.COMPLETED,
                        presentEvidenceIds = LinkedHashSet(present),
                        absentEvidenceIds = LinkedHashSet(absent),
                        engineVersion = analysis.engineVersion,
                        knowledgeBaseVersion = analysis.knowledgeBaseVersion,
                        startedAt = existing?.startedAt
                            ?: assessment.startedAt.takeIf { it > 0 }
                            ?: generatedAt,
                        completedAt = generatedAt
                    ).toEntity()
                )

                // Re-completing an assessment replaces its previous output instead of appending to it.
                resultDao.deleteForAssessment(assessmentId)
                if (analysis.rankedIssues.isNotEmpty()) {
                    resultDao.upsertAll(
                        analysis.rankedIssues.map { it.toResultEntity(assessmentId, generatedAt) }
                    )
                }

                alertDao.deleteForAssessment(assessmentId)
                if (analysis.safetyAlerts.isNotEmpty()) {
                    alertDao.upsertAll(analysis.safetyAlerts.map { it.toAlertEntity(assessmentId) })
                }
            }
            assessmentId
        }

    override suspend fun setResolved(assessmentId: String, resolved: Boolean) =
        withContext(ioDispatcher) { assessmentDao.setResolved(assessmentId, resolved) }

    override suspend fun delete(assessmentId: String) = withContext(ioDispatcher) {
        database.withTransaction {
            alertDao.deleteForAssessment(assessmentId)
            resultDao.deleteForAssessment(assessmentId)
            assessmentDao.deleteById(assessmentId)
        }
    }

    override suspend fun counts(): UsageCounts = withContext(ioDispatcher) {
        UsageCounts(
            vehicleCount = vehicleDao.count(),
            assessmentCount = assessmentDao.countByStatus(COMPLETED),
            draftCount = assessmentDao.countDrafts(DRAFT),
            openConcernCount = assessmentDao.countByStatusAndResolved(COMPLETED, resolved = false),
            resolvedCount = assessmentDao.countByStatusAndResolved(COMPLETED, resolved = true)
        )
    }

    override fun observeTrends(): Flow<HealthTrends> =
        combine(
            assessmentDao.observeTrendRows(COMPLETED),
            resultDao.observeTopProbabilityPoints(COMPLETED),
            symptomDao.observeAll(),
            hypothesisDao.observeAll()
        ) { assessmentRows, probabilityPoints, symptoms, hypotheses ->
            val symptomsById = symptoms.associateBy { it.id }
            val hypothesesById = hypotheses.associateBy { it.id }
            TrendMapper.build(
                assessments = assessmentRows,
                probabilityPoints = probabilityPoints,
                evidenceLabel = { evidenceId ->
                    knowledgeBase.evidence[evidenceId]?.label ?: symptomsById[evidenceId]?.label
                },
                evidenceSystemLabel = { evidenceId ->
                    knowledgeBase.evidence[evidenceId]?.system?.label
                        ?: symptomsById[evidenceId]?.systemId?.let { VehicleSystem.fromId(it).label }
                },
                hypothesisName = { hypothesisId ->
                    knowledgeBase.hypothesis(hypothesisId)?.name ?: hypothesesById[hypothesisId]?.name
                }
            )
        }.flowOn(ioDispatcher)

    override suspend fun latestSummaryForVehicle(vehicleId: String): AssessmentSummary? =
        observeSummariesForVehicle(vehicleId).first().firstOrNull()

    /**
     * Removes every assessment with its results and safety alerts, and clears the cached
     * knowledge-base catalogue so a "delete all data" action leaves nothing behind (the catalogue is
     * rebuilt on the next completed assessment).
     */
    override suspend fun deleteAll() = withContext(ioDispatcher) {
        database.withTransaction {
            assessmentDao.deleteAll()
            resultDao.deleteAll()
            alertDao.deleteAll()
            symptomDao.deleteAll()
            hypothesisDao.deleteAll()
        }
    }

    private fun summaries(rows: Flow<List<AssessmentSummaryRow>>): Flow<List<AssessmentSummary>> =
        combine(rows, symptomDao.observeAll(), hypothesisDao.observeAll()) { rowList, symptoms, hypotheses ->
            val symptomsById = symptoms.associateBy { it.id }
            val hypothesesById = hypotheses.associateBy { it.id }
            rowList.map { row ->
                row.toSummary(
                    hypothesisName = { hypothesisId ->
                        knowledgeBase.hypothesis(hypothesisId)?.name ?: hypothesesById[hypothesisId]?.name
                    },
                    hypothesisSystem = { hypothesisId ->
                        knowledgeBase.hypothesis(hypothesisId)?.system
                            ?: hypothesesById[hypothesisId]?.systemId?.let { VehicleSystem.fromId(it) }
                    },
                    evidenceLabel = { evidenceId ->
                        knowledgeBase.evidence[evidenceId]?.label ?: symptomsById[evidenceId]?.label
                    }
                )
            }
        }.flowOn(ioDispatcher)

    /**
     * Refreshes the local catalogue snapshot of the knowledge base. Called on every write of an
     * assessment so history and reports keep resolving names and systems even if a future
     * knowledge-base revision renames or removes an item.
     */
    private suspend fun syncCatalog(now: Long) {
        hypothesisDao.upsertAll(knowledgeBase.hypotheses.map { it.toHypothesisEntity(now) })
        symptomDao.upsertAll(knowledgeBase.evidence.values.map { it.toSymptomEntity(now) })
    }

    /** The foreign key would reject an orphan assessment; fail with a message that says why. */
    private suspend fun requireVehicleExists(vehicleId: String) {
        check(vehicleDao.getById(vehicleId) != null) {
            "Cannot store an assessment for the unknown vehicle '$vehicleId'"
        }
    }

    private companion object {
        val COMPLETED = AssessmentStatus.COMPLETED.id
        val DRAFT = AssessmentStatus.DRAFT.id
    }
}
