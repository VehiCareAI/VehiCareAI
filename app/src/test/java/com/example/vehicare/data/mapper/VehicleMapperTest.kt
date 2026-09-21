package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.entities.MaintenanceRecordEntity
import com.example.vehicare.data.local.entities.VehicleEntity
import com.example.vehicare.domain.model.FuelType
import com.example.vehicare.domain.model.MaintenanceRecord
import com.example.vehicare.domain.model.Transmission
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleMapperTest {

    private val vehicle = Vehicle(
        id = "v1",
        nickname = "",
        make = "Toyota",
        model = "Vios",
        year = 2020,
        vehicleType = VehicleType.SEDAN,
        fuelType = FuelType.GASOLINE,
        transmission = Transmission.AUTOMATIC,
        engineDisplacement = "1.3L",
        mileageKm = 68_500,
        licensePlate = "SAMPLE",
        vin = "VIN123",
        notes = "notes",
        isSample = true,
        createdAt = 111L,
        updatedAt = 222L
    )

    @Test
    fun `vehicle entity round trips without losing a field`() {
        val entity = vehicle.toEntity()
        assertEquals(vehicle, entity.toDomain())
    }

    @Test
    fun `vehicle mapping keeps optional values nullable`() {
        val minimal = Vehicle(id = "v2", make = "Honda", model = "Civic", year = 2019, mileageKm = null)
        val entity: VehicleEntity = minimal.toEntity()
        assertEquals(null, entity.mileageKm)
        assertEquals(null, entity.toDomain().mileageKm)
    }

    @Test
    fun `vehicle display name falls back to year make model when the nickname is blank`() {
        assertEquals("2020 Toyota Vios", vehicle.toEntity().toDomain().displayName)
    }

    @Test
    fun `maintenance record round trips`() {
        val record = MaintenanceRecord(
            id = "m1",
            vehicleId = "v1",
            serviceType = "Oil change",
            description = "Full synthetic",
            serviceDate = 1_700_000_000_000L,
            mileageKm = 70_000,
            cost = 59.99,
            notes = "QuickLube"
        )
        val entity: MaintenanceRecordEntity = record.toEntity()
        assertEquals(record, entity.toDomain())
    }

    @Test
    fun `maintenance record keeps null cost and mileage`() {
        val record = MaintenanceRecord(
            id = "m2",
            vehicleId = "v1",
            serviceType = "Inspection",
            serviceDate = 0L
        )
        val roundTripped = record.toEntity().toDomain()
        assertEquals(null, roundTripped.cost)
        assertEquals(null, roundTripped.mileageKm)
    }
}
