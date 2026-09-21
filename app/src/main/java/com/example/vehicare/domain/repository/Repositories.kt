package com.example.vehicare.domain.repository

import com.example.vehicare.domain.diagnostic.models.DiagnosticAnalysis
import com.example.vehicare.domain.model.AppPreferences
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentResult
import com.example.vehicare.domain.model.AssessmentSafetyAlert
import com.example.vehicare.domain.model.AssessmentSummary
import com.example.vehicare.domain.model.HealthTrends
import com.example.vehicare.domain.model.MaintenanceRecord
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.model.UsageCounts
import com.example.vehicare.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

/** Vehicle CRUD. Implemented in the data layer over Room. */
interface VehicleRepository {
    fun observeVehicles(): Flow<List<Vehicle>>
    fun observeVehicle(vehicleId: String): Flow<Vehicle?>
    suspend fun getVehicle(vehicleId: String): Vehicle?
    suspend fun countVehicles(): Int
    /** Returns the id of the inserted/updated vehicle. */
    suspend fun save(vehicle: Vehicle): String
    /** Deletes the vehicle and, deliberately, its assessments (cascade, Section 8). */
    suspend fun delete(vehicleId: String)
    suspend fun assessmentCountFor(vehicleId: String): Int
    /** Inserts the sample vehicle when the database is empty (Section 9). */
    suspend fun seedIfEmpty()
    suspend fun deleteAll()
}

/** Assessments, drafts and persisted diagnostic results. */
interface AssessmentRepository {
    fun observeSummaries(): Flow<List<AssessmentSummary>>
    fun observeSummariesForVehicle(vehicleId: String): Flow<List<AssessmentSummary>>
    fun observeAssessment(assessmentId: String): Flow<Assessment?>
    fun observeDraft(): Flow<Assessment?>
    suspend fun getAssessment(assessmentId: String): Assessment?
    /** Persisted ranked-result snapshot rows for an assessment, ordered by rank. */
    suspend fun getResults(assessmentId: String): List<AssessmentResult>
    /** Persisted safety-alert snapshot rows for an assessment. */
    suspend fun getSafetyAlerts(assessmentId: String): List<AssessmentSafetyAlert>
    /** Creates or updates a draft and returns its id. */
    suspend fun saveDraft(assessment: Assessment): String
    /** Persists a completed assessment plus its ranked results and safety alerts. */
    suspend fun complete(assessment: Assessment, analysis: DiagnosticAnalysis): String
    suspend fun setResolved(assessmentId: String, resolved: Boolean)
    suspend fun delete(assessmentId: String)
    suspend fun counts(): UsageCounts
    fun observeTrends(): Flow<HealthTrends>
    suspend fun latestSummaryForVehicle(vehicleId: String): AssessmentSummary?
    suspend fun deleteAll()
}

/** Optional maintenance log (Section 8). */
interface MaintenanceRepository {
    fun observeForVehicle(vehicleId: String): Flow<List<MaintenanceRecord>>
    suspend fun save(record: MaintenanceRecord): String
    suspend fun delete(recordId: String)
}

/** Local preferences and data-control operations. */
interface PreferencesRepository {
    val preferences: Flow<AppPreferences>
    suspend fun current(): AppPreferences
    suspend fun setDisplayName(name: String)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setMeasurementUnit(unit: MeasurementUnit)
    suspend fun setDisclaimerAcknowledged(acknowledged: Boolean)
    suspend fun setSelectedVehicleId(vehicleId: String?)
    suspend fun selectedVehicleIdOnce(): String?
    suspend fun clearAll()
}
