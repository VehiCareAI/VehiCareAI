package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.entities.MaintenanceRecordEntity
import com.example.vehicare.data.local.entities.VehicleEntity
import com.example.vehicare.domain.model.MaintenanceRecord
import com.example.vehicare.domain.model.Vehicle

/**
 * Entity <-> domain conversions for vehicles. Entities never leave the data layer, so these
 * functions are the only place where the two representations meet.
 */

fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id,
    nickname = nickname,
    make = make,
    model = model,
    year = year,
    vehicleType = vehicleType,
    fuelType = fuelType,
    transmission = transmission,
    engineDisplacement = engineDisplacement,
    mileageKm = mileageKm,
    licensePlate = licensePlate,
    vin = vin,
    notes = notes,
    isSample = isSample,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Vehicle.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    nickname = nickname,
    make = make,
    model = model,
    year = year,
    vehicleType = vehicleType,
    fuelType = fuelType,
    transmission = transmission,
    engineDisplacement = engineDisplacement,
    mileageKm = mileageKm,
    licensePlate = licensePlate,
    vin = vin,
    notes = notes,
    isSample = isSample,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MaintenanceRecordEntity.toDomain(): MaintenanceRecord = MaintenanceRecord(
    id = id,
    vehicleId = vehicleId,
    serviceType = serviceType,
    description = description,
    serviceDate = serviceDate,
    mileageKm = mileageKm,
    cost = cost,
    notes = notes
)

fun MaintenanceRecord.toEntity(): MaintenanceRecordEntity = MaintenanceRecordEntity(
    id = id,
    vehicleId = vehicleId,
    serviceType = serviceType,
    description = description,
    serviceDate = serviceDate,
    mileageKm = mileageKm,
    cost = cost,
    notes = notes
)
