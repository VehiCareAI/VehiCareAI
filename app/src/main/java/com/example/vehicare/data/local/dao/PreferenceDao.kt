package com.example.vehicare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.vehicare.data.local.entities.PreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {

    @Query("SELECT * FROM preferences ORDER BY key ASC")
    fun observeAll(): Flow<List<PreferenceEntity>>

    @Query("SELECT * FROM preferences")
    suspend fun getAll(): List<PreferenceEntity>

    @Query("SELECT value FROM preferences WHERE key = :key")
    suspend fun getValue(key: String): String?

    @Upsert
    suspend fun put(preference: PreferenceEntity)

    @Query("DELETE FROM preferences WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM preferences")
    suspend fun deleteAll()
}
