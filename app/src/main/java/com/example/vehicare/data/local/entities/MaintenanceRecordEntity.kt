package com.example.vehicare.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Optional maintenance log entry (Section 5.6). Owned by a vehicle; deleting the vehicle deletes its
 * log via [ForeignKey.CASCADE].
 */
@Entity(
    tableName = "maintenance_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vehicleId"], name = "index_maintenance_records_vehicleId"),
        Index(value = ["vehicleId", "serviceDate"], name = "index_maintenance_records_vehicle_date")
    ]
)
data class MaintenanceRecordEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val serviceType: String,
    val description: String,
    val serviceDate: Long,
    val mileageKm: Int?,
    val cost: Double?,
    val notes: String
)
