package com.example.vehicare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.vehicare.data.local.entities.AssessmentSafetyAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentSafetyAlertDao {

    @Query("SELECT * FROM assessment_safety_alerts WHERE assessmentId = :assessmentId ORDER BY level DESC, alertId ASC")
    fun observeForAssessment(assessmentId: String): Flow<List<AssessmentSafetyAlertEntity>>

    @Query("SELECT * FROM assessment_safety_alerts WHERE assessmentId = :assessmentId")
    suspend fun getForAssessment(assessmentId: String): List<AssessmentSafetyAlertEntity>

    /** Alert counts per assessment are computed in the summary query, so no extra read is needed. */
    @Upsert
    suspend fun upsertAll(alerts: List<AssessmentSafetyAlertEntity>)

    @Query("DELETE FROM assessment_safety_alerts WHERE assessmentId = :assessmentId")
    suspend fun deleteForAssessment(assessmentId: String)

    @Query("DELETE FROM assessment_safety_alerts")
    suspend fun deleteAll()
}
