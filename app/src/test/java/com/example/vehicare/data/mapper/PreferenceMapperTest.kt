package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.entities.PreferenceEntity
import com.example.vehicare.data.local.entities.PreferenceKeys
import com.example.vehicare.domain.model.MeasurementUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceMapperTest {

    @Test
    fun `an empty store yields the documented defaults`() {
        val preferences = emptyList<PreferenceEntity>().toAppPreferences()

        assertEquals("", preferences.displayName)
        assertFalse(preferences.onboardingCompleted)
        assertTrue(preferences.notificationsEnabled)
        assertEquals(MeasurementUnit.METRIC, preferences.measurementUnit)
        assertFalse(preferences.disclaimerAcknowledged)
    }

    @Test
    fun `stored values are read back`() {
        val preferences = listOf(
            PreferenceEntity(PreferenceKeys.DISPLAY_NAME, "Alex"),
            PreferenceEntity(PreferenceKeys.ONBOARDING_COMPLETED, "true"),
            PreferenceEntity(PreferenceKeys.NOTIFICATIONS_ENABLED, "false"),
            PreferenceEntity(PreferenceKeys.MEASUREMENT_UNIT, MeasurementUnit.IMPERIAL.id),
            PreferenceEntity(PreferenceKeys.DISCLAIMER_ACKNOWLEDGED, "true"),
            PreferenceEntity(PreferenceKeys.SELECTED_VEHICLE_ID, "v1")
        ).toAppPreferences()

        assertEquals("Alex", preferences.displayName)
        assertTrue(preferences.onboardingCompleted)
        assertFalse(preferences.notificationsEnabled)
        assertEquals(MeasurementUnit.IMPERIAL, preferences.measurementUnit)
        assertTrue(preferences.disclaimerAcknowledged)
        assertEquals("v1", preferences.selectedVehicleId)
    }

    @Test
    fun `unreadable values fall back to defaults instead of throwing`() {
        val preferences = listOf(
            PreferenceEntity(PreferenceKeys.ONBOARDING_COMPLETED, "yes please"),
            PreferenceEntity(PreferenceKeys.NOTIFICATIONS_ENABLED, ""),
            PreferenceEntity(PreferenceKeys.MEASUREMENT_UNIT, "furlongs")
        ).toAppPreferences()

        assertFalse(preferences.onboardingCompleted)
        assertTrue(preferences.notificationsEnabled)
        assertEquals(MeasurementUnit.METRIC, preferences.measurementUnit)
    }

    @Test
    fun `boolean parsing accepts trimmed and differently cased text`() {
        val values = mapOf("flag" to " TRUE ")
        assertTrue(values.booleanValue("flag", default = false))
        assertFalse(mapOf("flag" to "False").booleanValue("flag", default = true))
        assertTrue(emptyMap<String, String>().booleanValue("flag", default = true))
    }

    @Test
    fun `selected vehicle id is null when absent or blank`() {
        assertNull(emptyMap<String, String>().selectedVehicleId())
        assertNull(mapOf(PreferenceKeys.SELECTED_VEHICLE_ID to "  ").selectedVehicleId())
        assertEquals("v1", mapOf(PreferenceKeys.SELECTED_VEHICLE_ID to "v1").selectedVehicleId())
    }
}
