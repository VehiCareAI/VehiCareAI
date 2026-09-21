package com.example.vehicare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.vehicare.data.local.entities.AssessmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentDao {

    @Query("SELECT * FROM assessments WHERE id = :assessmentId")
    fun observeById(assessmentId: String): Flow<AssessmentEntity?>

    @Query("SELECT * FROM assessments WHERE id = :assessmentId")
    suspend fun getById(assessmentId: String): AssessmentEntity?

    /**
     * The single resumable draft: the most recently started assessment that is still a draft
     * (Section 4: progress survives process death).
     */
    @Query(
        """
        SELECT * FROM assessments
        WHERE status = :draftStatus
        ORDER BY startedAt DESC, id ASC
        LIMIT 1
        """
    )
    fun observeLatestDraft(draftStatus: String): Flow<AssessmentEntity?>

    @Query(
        """
        SELECT * FROM assessments
        WHERE status = :status
        ORDER BY completedAt DESC, id ASC
        """
    )
    fun observeByStatus(status: String): Flow<List<AssessmentEntity>>

    @Query(
        """
        SELECT * FROM assessments
        WHERE status = :status AND vehicleId = :vehicleId
        ORDER BY completedAt DESC, id ASC
        """
    )
    fun observeByStatusForVehicle(status: String, vehicleId: String): Flow<List<AssessmentEntity>>

    /**
     * History/report rows in one query: assessment + owning vehicle + rank-1 result (with its
     * catalogue name/system when known) + issue and safety-alert counts.
     */
    @Query(
        """
        SELECT
            a.id AS assessmentId,
            a.vehicleId AS vehicleId,
            v.nickname AS vehicleNickname,
            v.make AS vehicleMake,
            v.model AS vehicleModel,
            v.year AS vehicleYear,
            a.assessmentTypeId AS assessmentTypeId,
            a.completedAt AS completedAt,
            a.presentEvidenceIds AS presentEvidenceIds,
            a.resolved AS resolved,
            r.hypothesisId AS topHypothesisId,
            h.name AS topIssueName,
            h.systemId AS topSystemId,
            r.posteriorProbability AS topProbability,
            COALESCE(r.supportingEvidenceIds, '') AS topSupportingEvidenceIds,
            COALESCE(r.contradictingEvidenceIds, '') AS topContradictingEvidenceIds,
            (SELECT COUNT(*) FROM assessment_results ar WHERE ar.assessmentId = a.id) AS issueCount,
            (SELECT COUNT(*) FROM assessment_safety_alerts sa WHERE sa.assessmentId = a.id) AS safetyAlertCount
        FROM assessments a
        INNER JOIN vehicles v ON v.id = a.vehicleId
        LEFT JOIN assessment_results r ON r.assessmentId = a.id AND r.rank = 1
        LEFT JOIN hypotheses h ON h.id = r.hypothesisId
        WHERE a.status = :status
        ORDER BY a.completedAt DESC, a.id ASC
        """
    )
    fun observeSummaryRows(status: String): Flow<List<AssessmentSummaryRow>>

    @Query(
        """
        SELECT
            a.id AS assessmentId,
            a.vehicleId AS vehicleId,
            v.nickname AS vehicleNickname,
            v.make AS vehicleMake,
            v.model AS vehicleModel,
            v.year AS vehicleYear,
            a.assessmentTypeId AS assessmentTypeId,
            a.completedAt AS completedAt,
            a.presentEvidenceIds AS presentEvidenceIds,
            a.resolved AS resolved,
            r.hypothesisId AS topHypothesisId,
            h.name AS topIssueName,
            h.systemId AS topSystemId,
            r.posteriorProbability AS topProbability,
            COALESCE(r.supportingEvidenceIds, '') AS topSupportingEvidenceIds,
            COALESCE(r.contradictingEvidenceIds, '') AS topContradictingEvidenceIds,
            (SELECT COUNT(*) FROM assessment_results ar WHERE ar.assessmentId = a.id) AS issueCount,
            (SELECT COUNT(*) FROM assessment_safety_alerts sa WHERE sa.assessmentId = a.id) AS safetyAlertCount
        FROM assessments a
        INNER JOIN vehicles v ON v.id = a.vehicleId
        LEFT JOIN assessment_results r ON r.assessmentId = a.id AND r.rank = 1
        LEFT JOIN hypotheses h ON h.id = r.hypothesisId
        WHERE a.status = :status AND a.vehicleId = :vehicleId
        ORDER BY a.completedAt DESC, a.id ASC
        """
    )
    fun observeSummaryRowsForVehicle(status: String, vehicleId: String): Flow<List<AssessmentSummaryRow>>

    /** Completed assessments in chronological order, for the trends aggregation. */
    @Query(
        """
        SELECT id AS assessmentId,
               completedAt AS completedAt,
               presentEvidenceIds AS presentEvidenceIds,
               resolved AS resolved
        FROM assessments
        WHERE status = :status AND completedAt IS NOT NULL
        ORDER BY completedAt ASC, id ASC
        """
    )
    fun observeTrendRows(status: String): Flow<List<AssessmentTrendRow>>

    @Upsert
    suspend fun upsert(assessment: AssessmentEntity)

    @Query("UPDATE assessments SET resolved = :resolved WHERE id = :assessmentId")
    suspend fun setResolved(assessmentId: String, resolved: Boolean)

    @Query("DELETE FROM assessments WHERE id = :assessmentId")
    suspend fun deleteById(assessmentId: String)

    @Query("DELETE FROM assessments WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM assessments")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM assessments")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM assessments WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM assessments WHERE status = :status AND resolved = :resolved")
    suspend fun countByStatusAndResolved(status: String, resolved: Boolean): Int

    @Query("SELECT COUNT(*) FROM assessments WHERE status = :draftStatus")
    suspend fun countDrafts(draftStatus: String): Int

    @Query("SELECT COUNT(*) FROM assessments WHERE vehicleId = :vehicleId")
    suspend fun countForVehicle(vehicleId: String): Int
}
