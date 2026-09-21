package com.example.vehicare.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.vehicare.data.local.converters.Converters
import com.example.vehicare.data.local.dao.AssessmentDao
import com.example.vehicare.data.local.dao.AssessmentResultDao
import com.example.vehicare.data.local.dao.AssessmentSafetyAlertDao
import com.example.vehicare.data.local.dao.DiagnosticHypothesisDao
import com.example.vehicare.data.local.dao.MaintenanceRecordDao
import com.example.vehicare.data.local.dao.PreferenceDao
import com.example.vehicare.data.local.dao.SymptomDao
import com.example.vehicare.data.local.dao.VehicleDao
import com.example.vehicare.data.local.entities.AssessmentEntity
import com.example.vehicare.data.local.entities.AssessmentResultEntity
import com.example.vehicare.data.local.entities.AssessmentSafetyAlertEntity
import com.example.vehicare.data.local.entities.DiagnosticHypothesisEntity
import com.example.vehicare.data.local.entities.MaintenanceRecordEntity
import com.example.vehicare.data.local.entities.PreferenceEntity
import com.example.vehicare.data.local.entities.SymptomEntity
import com.example.vehicare.data.local.entities.VehicleEntity

/**
 * The single local database. Everything the app stores lives here (Section 2.4: fully offline,
 * local-first). Schema export is disabled because the prototype ships without migrations; any future
 * schema change must add a real migration rather than destructive fallback.
 */
@Database(
    entities = [
        VehicleEntity::class,
        AssessmentEntity::class,
        AssessmentResultEntity::class,
        AssessmentSafetyAlertEntity::class,
        MaintenanceRecordEntity::class,
        SymptomEntity::class,
        DiagnosticHypothesisEntity::class,
        PreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VehiCareDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun assessmentResultDao(): AssessmentResultDao
    abstract fun assessmentSafetyAlertDao(): AssessmentSafetyAlertDao
    abstract fun maintenanceRecordDao(): MaintenanceRecordDao
    abstract fun symptomDao(): SymptomDao
    abstract fun diagnosticHypothesisDao(): DiagnosticHypothesisDao
    abstract fun preferenceDao(): PreferenceDao

    companion object {
        const val NAME = "vehicare.db"
    }
}
