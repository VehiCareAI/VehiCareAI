package com.example.vehicare.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted vehicle (Section 8). Column names mirror the domain [com.example.vehicare.domain.model.Vehicle]
 * one-to-one so mappers stay trivial and no presentation concern leaks into the schema.
 */
@Entity(
    tableName = "vehicles",
    indices = [
        Index(value = ["make", "model"], name = "index_vehicles_make_model"),
        Index(value = ["updatedAt"], name = "index_vehicles_updatedAt")
    ]
)
data class VehicleEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val make: String,
    val model: String,
    val year: Int,
    val vehicleType: String,
    val fuelType: String,
    val transmission: String,
    val engineDisplacement: String,
    val mileageKm: Int?,
    val licensePlate: String,
    val vin: String,
    val notes: String,
    val isSample: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
