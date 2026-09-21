package com.example.vehicare.data.repository

import com.example.vehicare.data.di.IoDispatcher
import com.example.vehicare.data.local.dao.PreferenceDao
import com.example.vehicare.data.local.entities.PreferenceEntity
import com.example.vehicare.data.local.entities.PreferenceKeys
import com.example.vehicare.data.mapper.toAppPreferences
import com.example.vehicare.domain.model.AppPreferences
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed key/value preferences. Deliberately not DataStore: preferences then share the app's
 * single transactional store, so "delete all data" and the onboarding flag behave like any other row
 * (and no extra dependency is needed).
 */
class PreferencesRepositoryImpl @Inject constructor(
    private val preferenceDao: PreferenceDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PreferencesRepository {

    override val preferences: Flow<AppPreferences> =
        preferenceDao.observeAll()
            .map { rows -> rows.toAppPreferences() }
            .flowOn(ioDispatcher)

    override suspend fun current(): AppPreferences = withContext(ioDispatcher) {
        preferenceDao.getAll().toAppPreferences()
    }

    override suspend fun setDisplayName(name: String) = put(PreferenceKeys.DISPLAY_NAME, name)

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        put(PreferenceKeys.ONBOARDING_COMPLETED, completed.toString())

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        put(PreferenceKeys.NOTIFICATIONS_ENABLED, enabled.toString())

    override suspend fun setMeasurementUnit(unit: MeasurementUnit) =
        put(PreferenceKeys.MEASUREMENT_UNIT, unit.id)

    override suspend fun setDisclaimerAcknowledged(acknowledged: Boolean) =
        put(PreferenceKeys.DISCLAIMER_ACKNOWLEDGED, acknowledged.toString())

    /** A null id clears the selection instead of storing a blank vehicle id. */
    override suspend fun setSelectedVehicleId(vehicleId: String?) = withContext(ioDispatcher) {
        if (vehicleId.isNullOrBlank()) {
            preferenceDao.delete(PreferenceKeys.SELECTED_VEHICLE_ID)
        } else {
            preferenceDao.put(PreferenceEntity(PreferenceKeys.SELECTED_VEHICLE_ID, vehicleId))
        }
    }

    override suspend fun selectedVehicleIdOnce(): String? = withContext(ioDispatcher) {
        preferenceDao.getValue(PreferenceKeys.SELECTED_VEHICLE_ID)?.takeIf { it.isNotBlank() }
    }

    override suspend fun clearAll() = withContext(ioDispatcher) { preferenceDao.deleteAll() }

    private suspend fun put(key: String, value: String) = withContext(ioDispatcher) {
        preferenceDao.put(PreferenceEntity(key, value))
    }
}
