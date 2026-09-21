package com.example.vehicare.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.vehicare.data.local.entities.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    /** Sample vehicle first (it is seeded first), then by creation time; deterministic ordering. */
    @Query("SELECT * FROM vehicles ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    fun observeById(vehicleId: String): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    suspend fun getById(vehicleId: String): VehicleEntity?

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :vehicleId")
    suspend fun deleteById(vehicleId: String)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()
}
