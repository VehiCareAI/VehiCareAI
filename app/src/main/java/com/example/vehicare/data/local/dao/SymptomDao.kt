package com.example.vehicare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.vehicare.data.local.entities.SymptomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SymptomDao {

    @Query("SELECT * FROM symptoms ORDER BY label ASC, id ASC")
    fun observeAll(): Flow<List<SymptomEntity>>

    @Query("SELECT * FROM symptoms WHERE id = :symptomId")
    suspend fun getById(symptomId: String): SymptomEntity?

    @Query("SELECT COUNT(*) FROM symptoms")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(symptoms: List<SymptomEntity>)

    @Query("DELETE FROM symptoms")
    suspend fun deleteAll()
}
