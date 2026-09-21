package com.example.vehicare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.vehicare.data.local.entities.MaintenanceRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceRecordDao {

    @Query("SELECT * FROM maintenance_records WHERE vehicleId = :vehicleId ORDER BY serviceDate DESC, id ASC")
    fun observeForVehicle(vehicleId: String): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT * FROM maintenance_records WHERE id = :recordId")
    suspend fun getById(recordId: String): MaintenanceRecordEntity?

    @Upsert
    suspend fun upsert(record: MaintenanceRecordEntity)

    @Query("DELETE FROM maintenance_records WHERE id = :recordId")
    suspend fun deleteById(recordId: String)

    @Query("DELETE FROM maintenance_records WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Query("DELETE FROM maintenance_records")
    suspend fun deleteAll()
}
