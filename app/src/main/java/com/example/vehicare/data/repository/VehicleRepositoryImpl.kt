package com.example.vehicare.data.repository

import androidx.room.withTransaction
import com.example.vehicare.data.di.IoDispatcher
import com.example.vehicare.data.local.dao.AssessmentDao
import com.example.vehicare.data.local.dao.MaintenanceRecordDao
import com.example.vehicare.data.local.dao.VehicleDao
import com.example.vehicare.data.local.database.VehiCareDatabase
import com.example.vehicare.data.mapper.toDomain
import com.example.vehicare.data.mapper.toEntity
import com.example.vehicare.data.seed.DemoDataSeeder
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.repository.VehicleRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local-first [VehicleRepository]; every read is a Room `Flow` and every write runs on `Dispatchers.IO`. */
class VehicleRepositoryImpl @Inject constructor(
    private val database: VehiCareDatabase,
    private val vehicleDao: VehicleDao,
    private val assessmentDao: AssessmentDao,
    private val maintenanceRecordDao: MaintenanceRecordDao,
    private val demoDataSeeder: DemoDataSeeder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : VehicleRepository {

    override fun observeVehicles(): Flow<List<Vehicle>> =
        vehicleDao.observeAll()
            .map { vehicles -> vehicles.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeVehicle(vehicleId: String): Flow<Vehicle?> =
        vehicleDao.observeById(vehicleId)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun getVehicle(vehicleId: String): Vehicle? = withContext(ioDispatcher) {
        vehicleDao.getById(vehicleId)?.toDomain()
    }

    override suspend fun countVehicles(): Int = withContext(ioDispatcher) { vehicleDao.count() }

    override suspend fun save(vehicle: Vehicle): String = withContext(ioDispatcher) {
        val id = vehicle.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val existing = vehicleDao.getById(id)
        val entity = vehicle.copy(
            id = id,
            createdAt = existing?.createdAt ?: vehicle.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now
        ).toEntity()
        database.withTransaction { vehicleDao.upsert(entity) }
        id
    }

    /**
     * Deletes the vehicle and, deliberately, its assessments (Section 8). Children are removed in
     * dependency order — assessments first (their results and safety alerts disappear with the FK
     * `CASCADE`), then maintenance records, then the vehicle — so the outcome is identical whether or
     * not the SQLite foreign-key pragma is enabled.
     */
    override suspend fun delete(vehicleId: String) = withContext(ioDispatcher) {
        database.withTransaction {
            assessmentDao.deleteForVehicle(vehicleId)
            maintenanceRecordDao.deleteForVehicle(vehicleId)
            vehicleDao.deleteById(vehicleId)
        }
    }

    override suspend fun assessmentCountFor(vehicleId: String): Int = withContext(ioDispatcher) {
        assessmentDao.countForVehicle(vehicleId)
    }

    /**
     * Seeds the Section 9 demo content, and only into an empty database: the sample Toyota Vios 2020
     * plus one already completed assessment so Home, History, Reports and the trends charts have real
     * data on first launch. The demo ranking is computed by the engine at seed time (see
     * [DemoDataSeeder]); nothing is hardcoded.
     */
    override suspend fun seedIfEmpty() = demoDataSeeder.seedIfEmpty()

    /** Removes every vehicle with its assessments and maintenance records. Preferences are untouched. */
    override suspend fun deleteAll() = withContext(ioDispatcher) {
        database.withTransaction {
            assessmentDao.deleteAll()
            maintenanceRecordDao.deleteAll()
            vehicleDao.deleteAll()
        }
    }
}
