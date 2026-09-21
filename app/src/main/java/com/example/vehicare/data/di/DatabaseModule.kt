package com.example.vehicare.data.di

import android.content.Context
import androidx.room.Room
import com.example.vehicare.data.local.dao.AssessmentDao
import com.example.vehicare.data.local.dao.AssessmentResultDao
import com.example.vehicare.data.local.dao.AssessmentSafetyAlertDao
import com.example.vehicare.data.local.dao.DiagnosticHypothesisDao
import com.example.vehicare.data.local.dao.MaintenanceRecordDao
import com.example.vehicare.data.local.dao.PreferenceDao
import com.example.vehicare.data.local.dao.SymptomDao
import com.example.vehicare.data.local.dao.VehicleDao
import com.example.vehicare.data.local.database.VehiCareDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the single Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VehiCareDatabase =
        Room.databaseBuilder(context, VehiCareDatabase::class.java, VehiCareDatabase.NAME).build()

    @Provides
    fun provideVehicleDao(database: VehiCareDatabase): VehicleDao = database.vehicleDao()

    @Provides
    fun provideAssessmentDao(database: VehiCareDatabase): AssessmentDao = database.assessmentDao()

    @Provides
    fun provideAssessmentResultDao(database: VehiCareDatabase): AssessmentResultDao =
        database.assessmentResultDao()

    @Provides
    fun provideAssessmentSafetyAlertDao(database: VehiCareDatabase): AssessmentSafetyAlertDao =
        database.assessmentSafetyAlertDao()

    @Provides
    fun provideMaintenanceRecordDao(database: VehiCareDatabase): MaintenanceRecordDao =
        database.maintenanceRecordDao()

    @Provides
    fun provideSymptomDao(database: VehiCareDatabase): SymptomDao = database.symptomDao()

    @Provides
    fun provideDiagnosticHypothesisDao(database: VehiCareDatabase): DiagnosticHypothesisDao =
        database.diagnosticHypothesisDao()

    @Provides
    fun providePreferenceDao(database: VehiCareDatabase): PreferenceDao = database.preferenceDao()
}
