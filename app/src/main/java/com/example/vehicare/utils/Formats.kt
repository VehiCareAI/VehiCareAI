package com.example.vehicare.utils

import com.example.vehicare.domain.model.MeasurementUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Display formatting helpers. Uses a fixed [Locale.US] for locale-stable output; dates are rendered
 * in the device's default time zone, and date-only values are anchored to local midnight by their
 * callers so they round-trip to the same calendar day.
 */
object Formats {

    private const val KM_PER_MILE = 1.609344

    fun date(epochMillis: Long): String =
        formatter("d MMM yyyy").format(Date(epochMillis))

    fun dateTime(epochMillis: Long): String =
        formatter("d MMM yyyy, HH:mm").format(Date(epochMillis))

    fun monthLabel(epochMillis: Long): String =
        formatter("MMM yyyy").format(Date(epochMillis))

    fun mileage(km: Int?, unit: MeasurementUnit): String {
        if (km == null) return "Not provided"
        return when (unit) {
            MeasurementUnit.METRIC -> "${thousands(km)} km"
            MeasurementUnit.IMPERIAL -> "${thousands((km / KM_PER_MILE).roundToInt())} mi"
        }
    }

    fun thousands(value: Int): String =
        String.format(Locale.US, "%,d", value)

    fun cost(value: Double?): String =
        if (value == null) "" else String.format(Locale.US, "%.2f", value)

    /**
     * Report identifier derived deterministically from the assessment id, so the same assessment
     * always shows the same report id while the string stays short and human-citable.
     */
    fun reportId(assessmentId: String): String {
        if (assessmentId.isBlank()) return "VCA-UNKNOWN"
        val hash = assessmentId.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
        return "VCA-" + hash.toString(16).uppercase(Locale.US).padStart(8, '0')
    }

    fun severityLabel(value: Int?) = value?.let { "$it of 5" } ?: "Not reported"

    /** Locale-stable posterior formatting: one decimal place, always with the % sign. */
    fun percent(value: Double): String {
        if (value.isNaN()) return "—"
        val bounded = value.coerceIn(0.0, 1.0) * 1000
        val percent = kotlin.math.round(bounded) / 10.0
        return if (percent == percent.toLong().toDouble()) "${percent.toLong()}%" else "$percent%"
    }

    private fun formatter(pattern: String) =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getDefault() }
}
