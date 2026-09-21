package com.example.vehicare.data.di

import com.example.vehicare.data.repository.AssessmentRepositoryImpl
import com.example.vehicare.data.repository.MaintenanceRepositoryImpl
import com.example.vehicare.data.repository.PreferencesRepositoryImpl
import com.example.vehicare.data.repository.VehicleRepositoryImpl
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.MaintenanceRepository
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.domain.repository.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the frozen repository contracts to their Room-backed implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVehicleRepository(impl: VehicleRepositoryImpl): VehicleRepository

    @Binds
    @Singleton
    abstract fun bindAssessmentRepository(impl: AssessmentRepositoryImpl): AssessmentRepository

    @Binds
    @Singleton
    abstract fun bindMaintenanceRepository(impl: MaintenanceRepositoryImpl): MaintenanceRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository
}
