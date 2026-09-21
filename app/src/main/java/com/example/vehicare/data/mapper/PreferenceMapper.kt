package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.entities.PreferenceEntity
import com.example.vehicare.data.local.entities.PreferenceKeys
import com.example.vehicare.domain.model.AppPreferences
import com.example.vehicare.domain.model.MeasurementUnit

/**
 * Conversions for the Room-backed key/value preference store.
 *
 * A missing or unreadable row always falls back to the documented default instead of throwing: a
 * corrupt preference must never stop the app from starting (Section 2.2).
 */
fun List<PreferenceEntity>.toAppPreferences(): AppPreferences {
    val values = associate { it.key to it.value }
    return AppPreferences(
        displayName = values[PreferenceKeys.DISPLAY_NAME].orEmpty(),
        onboardingCompleted = values.booleanValue(PreferenceKeys.ONBOARDING_COMPLETED, default = false),
        notificationsEnabled = values.booleanValue(PreferenceKeys.NOTIFICATIONS_ENABLED, default = true),
        measurementUnit = MeasurementUnit.fromId(values[PreferenceKeys.MEASUREMENT_UNIT].orEmpty()),
        disclaimerAcknowledged = values.booleanValue(PreferenceKeys.DISCLAIMER_ACKNOWLEDGED, default = false),
        selectedVehicleId = values.selectedVehicleId()
    )
}

/** Blank values are treated as "not selected" rather than as a vehicle id. */
fun Map<String, String>.selectedVehicleId(): String? =
    this[PreferenceKeys.SELECTED_VEHICLE_ID]?.takeIf { it.isNotBlank() }

fun Map<String, String>.booleanValue(key: String, default: Boolean): Boolean =
    when (this[key]?.trim()?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> default
    }
