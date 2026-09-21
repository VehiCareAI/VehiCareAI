package com.example.vehicare.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.vehicare.data.local.database.VehiCareDatabase
import com.example.vehicare.data.mapper.toEntity
import com.example.vehicare.domain.model.AppPreferences
import com.example.vehicare.domain.model.MaintenanceRecord
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.model.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room-backed preference store and maintenance log behaviour. */
@RunWith(AndroidJUnit4::class)
class PreferencesAndMaintenanceTest {

    private lateinit var database: VehiCareDatabase
    private lateinit var preferencesRepository: PreferencesRepositoryImpl
    private lateinit var maintenanceRepository: MaintenanceRepositoryImpl

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VehiCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesRepository = PreferencesRepositoryImpl(
            preferenceDao = database.preferenceDao(),
            ioDispatcher = Dispatchers.IO
        )
        maintenanceRepository = MaintenanceRepositoryImpl(
            maintenanceRecordDao = database.maintenanceRecordDao(),
            vehicleDao = database.vehicleDao(),
            ioDispatcher = Dispatchers.IO
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun preferencesStartFromDocumentedDefaults() = runBlocking {
        assertEquals(AppPreferences(), preferencesRepository.preferences.first())
        assertEquals(AppPreferences(), preferencesRepository.current())
        assertNull(preferencesRepository.selectedVehicleIdOnce())
    }

    @Test
    fun preferencesRoundTripThroughTheKeyValueTable() = runBlocking {
        preferencesRepository.setDisplayName("Alex")
        preferencesRepository.setOnboardingCompleted(true)
        preferencesRepository.setNotificationsEnabled(false)
        preferencesRepository.setMeasurementUnit(MeasurementUnit.IMPERIAL)
        preferencesRepository.setDisclaimerAcknowledged(true)
        preferencesRepository.setSelectedVehicleId("v1")

        val stored = preferencesRepository.current()
        assertEquals("Alex", stored.displayName)
        assertTrue(stored.onboardingCompleted)
        assertFalse(stored.notificationsEnabled)
        assertEquals(MeasurementUnit.IMPERIAL, stored.measurementUnit)
        assertTrue(stored.disclaimerAcknowledged)
        assertEquals("v1", preferencesRepository.selectedVehicleIdOnce())
        assertEquals(stored, preferencesRepository.preferences.first())

        // A null selection clears the key instead of storing a blank id.
        preferencesRepository.setSelectedVehicleId(null)
        assertNull(preferencesRepository.selectedVehicleIdOnce())

        preferencesRepository.clearAll()
        assertEquals(AppPreferences(), preferencesRepository.current())
        assertEquals(0, database.preferenceDao().getAll().size)
    }

    @Test
    fun maintenanceRecordsAreScopedToTheirVehicle() = runBlocking {
        val firstVehicle = "v1"
        val secondVehicle = "v2"
        database.vehicleDao().upsert(
            Vehicle(id = firstVehicle, make = "Toyota", model = "Vios", year = 2020).toEntity()
        )
        database.vehicleDao().upsert(
            Vehicle(id = secondVehicle, make = "Honda", model = "City", year = 2019).toEntity()
        )

        val newerId = maintenanceRepository.save(
            MaintenanceRecord(
                id = "",
                vehicleId = firstVehicle,
                serviceType = "Brake inspection",
                serviceDate = 2_000L,
                mileageKm = 70_000,
                cost = 40.0
            )
        )
        maintenanceRepository.save(
            MaintenanceRecord(id = "", vehicleId = firstVehicle, serviceType = "Oil change", serviceDate = 1_000L)
        )
        maintenanceRepository.save(
            MaintenanceRecord(id = "", vehicleId = secondVehicle, serviceType = "Tyre rotation", serviceDate = 3_000L)
        )

        // Newest service first, and never another vehicle's records.
        val records = maintenanceRepository.observeForVehicle(firstVehicle).first()
        assertEquals(listOf("Brake inspection", "Oil change"), records.map { it.serviceType })
        assertEquals(newerId, records.first().id)
        assertEquals(40.0, records.first().cost!!, 0.0)
        assertEquals(1, maintenanceRepository.observeForVehicle(secondVehicle).first().size)

        maintenanceRepository.delete(newerId)
        assertEquals(
            listOf("Oil change"),
            maintenanceRepository.observeForVehicle(firstVehicle).first().map { it.serviceType }
        )
    }

    @Test
    fun maintenanceRecordForAnUnknownVehicleIsRejected() {
        val failure = runCatching {
            runBlocking {
                maintenanceRepository.save(
                    MaintenanceRecord(id = "", vehicleId = "missing", serviceType = "Oil change", serviceDate = 1L)
                )
            }
        }
        assertTrue("an orphan maintenance record must be rejected", failure.isFailure)
        assertEquals(
            "Cannot save a maintenance record for the unknown vehicle 'missing'",
            failure.exceptionOrNull()?.message
        )
    }
}
