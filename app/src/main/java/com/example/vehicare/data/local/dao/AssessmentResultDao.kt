package com.example.vehicare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.vehicare.data.local.entities.AssessmentResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentResultDao {

    @Query("SELECT * FROM assessment_results WHERE assessmentId = :assessmentId ORDER BY rank ASC, id ASC")
    fun observeForAssessment(assessmentId: String): Flow<List<AssessmentResultEntity>>

    @Query("SELECT * FROM assessment_results WHERE assessmentId = :assessmentId ORDER BY rank ASC, id ASC")
    suspend fun getForAssessment(assessmentId: String): List<AssessmentResultEntity>
    /**
     * Rank-1 result of every completed assessment, in chronological order: the input of the
     * probability trend chart.
     */
    @Query(
        """
        SELECT r.assessmentId AS assessmentId,
               a.completedAt AS completedAt,
               r.hypothesisId AS hypothesisId,
               r.posteriorProbability AS posteriorProbability
        FROM assessment_results r
        INNER JOIN assessments a ON a.id = r.assessmentId
        WHERE r.rank = 1 AND a.status = :assessmentStatus AND a.completedAt IS NOT NULL
        ORDER BY a.completedAt ASC, a.id ASC
        """
    )
    fun observeTopProbabilityPoints(assessmentStatus: String): Flow<List<ProbabilityPointRow>>

    @Upsert
    suspend fun upsertAll(results: List<AssessmentResultEntity>)

    @Query("DELETE FROM assessment_results WHERE assessmentId = :assessmentId")
    suspend fun deleteForAssessment(assessmentId: String)

    @Query("DELETE FROM assessment_results")
    suspend fun deleteAll()
}
