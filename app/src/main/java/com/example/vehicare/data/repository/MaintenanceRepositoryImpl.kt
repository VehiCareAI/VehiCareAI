package com.example.vehicare.data.repository

import com.example.vehicare.data.di.IoDispatcher
import com.example.vehicare.data.local.dao.MaintenanceRecordDao
import com.example.vehicare.data.local.dao.VehicleDao
import com.example.vehicare.data.mapper.toDomain
import com.example.vehicare.data.mapper.toEntity
import com.example.vehicare.domain.model.MaintenanceRecord
import com.example.vehicare.domain.repository.MaintenanceRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local-only maintenance log (Section 8). */
class MaintenanceRepositoryImpl @Inject constructor(
    private val maintenanceRecordDao: MaintenanceRecordDao,
    private val vehicleDao: VehicleDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MaintenanceRepository {

    override fun observeForVehicle(vehicleId: String): Flow<List<MaintenanceRecord>> =
        maintenanceRecordDao.observeForVehicle(vehicleId)
            .map { records -> records.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun save(record: MaintenanceRecord): String = withContext(ioDispatcher) {
        val id = record.id.ifBlank { UUID.randomUUID().toString() }
        // A record always belongs to a vehicle; the foreign key would reject an orphan row, so fail
        // with a message that names the actual problem instead of a raw SQLite constraint error.
        val vehicleExists = vehicleDao.getById(record.vehicleId) != null
        check(vehicleExists) {
            "Cannot save a maintenance record for the unknown vehicle '${record.vehicleId}'"
        }
        maintenanceRecordDao.upsert(record.copy(id = id).toEntity())
        id
    }

    override suspend fun delete(recordId: String) = withContext(ioDispatcher) {
        maintenanceRecordDao.deleteById(recordId)
    }
}
