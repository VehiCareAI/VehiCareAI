package com.example.vehicare.domain.model

/**
 * A vehicle the user tracks. Identifiers such as [licensePlate] and [vin] are optional and never
 * required (Section 9: minimal personal data).
 */
data class Vehicle(
    val id: String = "",
    val nickname: String = "",
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val vehicleType: String = VehicleType.SEDAN,
    val fuelType: String = FuelType.GASOLINE,
    val transmission: String = Transmission.AUTOMATIC,
    val engineDisplacement: String = "",
    val mileageKm: Int? = null,
    val licensePlate: String = "",
    val vin: String = "",
    val notes: String = "",
    val isSample: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    /** "2020 Toyota Vios" or the user's nickname when provided. */
    val displayName: String
        get() = when {
            nickname.isNotBlank() -> nickname
            else -> listOf(year.takeIf { it > 0 }?.toString(), make, model)
                .filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
        }

    companion object {
        /** Model-year validation range (Section 5.5). */
        val YEAR_RANGE = 1950..(java.time.LocalDate.now().year + 1)

        fun sample(now: Long = System.currentTimeMillis()): Vehicle = Vehicle(
            id = SAMPLE_ID,
            nickname = "",
            make = "Toyota",
            model = "Vios",
            year = 2020,
            vehicleType = VehicleType.SEDAN,
            fuelType = FuelType.GASOLINE,
            transmission = Transmission.AUTOMATIC,
            mileageKm = 68_500,
            licensePlate = "SAMPLE",
            notes = "Sample vehicle supplied with VehiCare AI so the app can be explored immediately. You can edit or delete it.",
            isSample = true,
            createdAt = now,
            updatedAt = now
        )

        const val SAMPLE_ID = "vehicle_sample_vios"
    }
}

object VehicleType {
    const val SEDAN = "Sedan"
    const val SUV = "SUV"
    const val PICKUP = "Pickup"
    const val HATCHBACK = "Hatchback"
    const val VAN = "Van"
    const val MOTORCYCLE = "Motorcycle"
    const val OTHER = "Other"

    val all = listOf(SEDAN, SUV, PICKUP, HATCHBACK, VAN, MOTORCYCLE, OTHER)
}

object FuelType {
    const val GASOLINE = "Gasoline"
    const val DIESEL = "Diesel"
    const val HYBRID = "Hybrid"
    const val ELECTRIC = "Electric"
    const val OTHER = "Other"

    val all = listOf(GASOLINE, DIESEL, HYBRID, ELECTRIC, OTHER)
}

object Transmission {
    const val MANUAL = "Manual"
    const val AUTOMATIC = "Automatic"
    const val CVT = "CVT"
    const val OTHER = "Other"

    val all = listOf(MANUAL, AUTOMATIC, CVT, OTHER)
}

/** Optional maintenance log entry (Section 5.6 / 8). */
data class MaintenanceRecord(
    val id: String = "",
    val vehicleId: String,
    val serviceType: String,
    val description: String = "",
    val serviceDate: Long,
    val mileageKm: Int? = null,
    val cost: Double? = null,
    val notes: String = ""
)
