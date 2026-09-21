package com.example.vehicare.utils

import com.example.vehicare.domain.model.MeasurementUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {

    @Test
    fun `percent rounds to one decimal place and clamps to range`() {
        assertEquals("0%", Formats.percent(0.0))
        assertEquals("25%", Formats.percent(0.25))
        assertEquals("41.7%", Formats.percent(0.41666))
        assertEquals("100%", Formats.percent(1.0))
        assertEquals("100%", Formats.percent(1.5))
        assertEquals("0%", Formats.percent(-0.2))
    }

    @Test
    fun `percent shows a placeholder for NaN instead of NaN percent`() {
        assertEquals("—", Formats.percent(Double.NaN))
    }

    @Test
    fun `mileage follows the selected unit`() {
        assertEquals("100,000 km", Formats.mileage(100_000, MeasurementUnit.METRIC))
        assertEquals("62,137 mi", Formats.mileage(100_000, MeasurementUnit.IMPERIAL))
        assertEquals("Not provided", Formats.mileage(null, MeasurementUnit.METRIC))
    }

    @Test
    fun `report id is deterministic and short`() {
        assertEquals(Formats.reportId("assessment_sample_vios"), Formats.reportId("assessment_sample_vios"))
        assertEquals("VCA-UNKNOWN", Formats.reportId(""))
    }

    @Test
    fun `severity label handles reported and missing values`() {
        assertEquals("3 of 5", Formats.severityLabel(3))
        assertEquals("Not reported", Formats.severityLabel(null))
    }
}
