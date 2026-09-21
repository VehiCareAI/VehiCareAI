package com.example.vehicare.data.seed

import androidx.room.withTransaction
import com.example.vehicare.data.di.IoDispatcher
import com.example.vehicare.data.local.dao.VehicleDao
import com.example.vehicare.data.local.database.VehiCareDatabase
import com.example.vehicare.data.mapper.toEntity
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.usecase.EvaluateSymptomsUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Inserts the Section 9 demo content — the sample Toyota Vios and one already completed assessment —
 * but only into an empty database.
 *
 * The demo ranking is **computed**: the seeded answers are run through the same questionnaire engine
 * and the same Bayesian engine the live app uses, and the result is then persisted through
 * [AssessmentRepository.complete], exactly like a session the user just finished. No probability is
 * ever hardcoded, and the stored engine/knowledge-base versions come from the analysis.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val database: VehiCareDatabase,
    private val vehicleDao: VehicleDao,
    private val assessmentRepository: AssessmentRepository,
    private val evaluateSymptoms: EvaluateSymptomsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun seedIfEmpty() = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val vehicle = SampleData.sampleVehicle(now)

        // The guard and the insert share one transaction so a second launch cannot seed twice.
        val seeded = database.withTransaction {
            if (vehicleDao.count() > 0) {
                false
            } else {
                vehicleDao.upsert(vehicle.toEntity())
                true
            }
        }
        if (!seeded) return@withContext

        val assessment = SampleData.sampleAssessment(vehicle.id, now)
        assessmentRepository.complete(assessment, evaluateSymptoms(assessment, vehicle))
    }
}
