package com.example.vehicare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.vehicare.data.local.entities.DiagnosticHypothesisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticHypothesisDao {

    @Query("SELECT * FROM hypotheses ORDER BY name ASC, id ASC")
    fun observeAll(): Flow<List<DiagnosticHypothesisEntity>>

    @Query("SELECT * FROM hypotheses WHERE id = :hypothesisId")
    suspend fun getById(hypothesisId: String): DiagnosticHypothesisEntity?

    @Query("SELECT COUNT(*) FROM hypotheses")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(hypotheses: List<DiagnosticHypothesisEntity>)

    @Query("DELETE FROM hypotheses")
    suspend fun deleteAll()
}
