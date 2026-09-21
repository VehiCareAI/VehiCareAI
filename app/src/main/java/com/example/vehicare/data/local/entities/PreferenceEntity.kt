package com.example.vehicare.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the Room-backed key/value preference store. Deliberately a table rather than DataStore
 * so preferences are covered by the same local, transactional storage as everything else (and so
 * "delete all data" is one transaction).
 *
 * Only non-identifying values are stored: display name, onboarding flag, notification flag,
 * measurement unit, disclaimer acknowledgement and the last selected vehicle id.
 */
@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String
)

/** Stable keys of the preference key/value store. */
object PreferenceKeys {
    const val DISPLAY_NAME = "display_name"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val MEASUREMENT_UNIT = "measurement_unit"
    const val DISCLAIMER_ACKNOWLEDGED = "disclaimer_acknowledged"
    const val SELECTED_VEHICLE_ID = "selected_vehicle_id"
}
