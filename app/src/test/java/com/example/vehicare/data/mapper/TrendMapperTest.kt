package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.dao.AssessmentTrendRow
import com.example.vehicare.data.local.dao.ProbabilityPointRow
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendMapperTest {

    private val zone: ZoneId = ZoneId.of("Asia/Manila")

    private fun at(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assessment(
        id: String,
        completedAt: Long,
        present: Set<String>,
        resolved: Boolean = false
    ) = AssessmentTrendRow(
        assessmentId = id,
        completedAt = completedAt,
        presentEvidenceIds = present,
        resolved = resolved
    )

    private val evidenceSystems = mapOf(
        "slow_cranking" to "Starting & Ignition",
        "dim_lights" to "Electrical System",
        "cranks_no_start" to "Starting & Ignition"
    )

    private val evidenceLabels = mapOf(
        "slow_cranking" to "Engine cranks slowly",
        "dim_lights" to "Dashboard or headlights dim / flickering",
        "cranks_no_start" to "Cranks but does not start"
    )

    private val hypothesisNames = mapOf("weak_battery" to "Weak or failing battery")

    private fun build(
        assessments: List<AssessmentTrendRow>,
        points: List<ProbabilityPointRow> = emptyList()
    ) = TrendMapper.build(
        assessments = assessments,
        probabilityPoints = points,
        evidenceLabel = { evidenceLabels[it] },
        evidenceSystemLabel = { evidenceSystems[it] },
        hypothesisName = { hypothesisNames[it] },
        zoneId = zone
    )

    @Test
    fun `month label is stable and English`() {
        assertEquals("Sep 2026", TrendMapper.monthLabel(at(2026, 9, 20), zone))
        assertEquals("Jan 2027", TrendMapper.monthLabel(at(2027, 1, 3), zone))
    }

    @Test
    fun `assessments over time are grouped by month in chronological order`() {
        val trends = build(
            listOf(
                assessment("a3", at(2026, 10, 1), emptySet()),
                assessment("a1", at(2026, 9, 5), emptySet()),
                assessment("a2", at(2026, 9, 20), emptySet())
            )
        )

        assertEquals(listOf("Sep 2026", "Oct 2026"), trends.assessmentsOverTime.map { it.label })
        assertEquals(listOf(2, 1), trends.assessmentsOverTime.map { it.value })
        assertEquals(3, trends.totalAssessments)
        assertTrue(trends.hasData)
    }

    @Test
    fun `symptoms are grouped by vehicle system with the most reported system first`() {
        val trends = build(
            listOf(
                assessment("a1", at(2026, 9, 5), linkedSetOf("slow_cranking", "dim_lights")),
                assessment("a2", at(2026, 9, 20), linkedSetOf("slow_cranking", "cranks_no_start"))
            )
        )

        assertEquals(
            listOf("Starting & Ignition", "Electrical System"),
            trends.symptomsBySystem.map { it.systemLabel }
        )
        assertEquals(listOf(3, 1), trends.symptomsBySystem.map { it.count })
        assertEquals("Starting & Ignition", trends.mostReportedSystem)
    }

    @Test
    fun `systems with equal counts are ordered by label for a deterministic chart`() {
        val trends = build(
            listOf(assessment("a1", at(2026, 9, 5), linkedSetOf("dim_lights", "cranks_no_start")))
        )
        assertEquals(
            listOf("Electrical System", "Starting & Ignition"),
            trends.symptomsBySystem.map { it.systemLabel }
        )
    }

    @Test
    fun `repeated symptoms need two assessments and keep their occurrence count`() {
        val trends = build(
            listOf(
                assessment("a1", at(2026, 9, 5), linkedSetOf("slow_cranking", "dim_lights")),
                assessment("a2", at(2026, 9, 20), linkedSetOf("slow_cranking", "cranks_no_start")),
                assessment("a3", at(2026, 10, 2), linkedSetOf("slow_cranking"))
            )
        )

        assertEquals(listOf("Engine cranks slowly"), trends.repeatedSymptoms.map { it.label })
        assertEquals(listOf(3), trends.repeatedSymptoms.map { it.occurrences })
        // "dim_lights" and "cranks_no_start" were reported once each and are therefore not repeated.
        assertEquals(1, trends.repeatedSymptoms.size)
    }

    @Test
    fun `probability trend follows the assessments chronologically and names the hypothesis`() {
        val trends = build(
            assessments = listOf(
                assessment("a2", at(2026, 10, 2), emptySet()),
                assessment("a1", at(2026, 9, 5), emptySet())
            ),
            points = listOf(
                ProbabilityPointRow("a2", at(2026, 10, 2), "faulty_alternator", 0.33),
                ProbabilityPointRow("a1", at(2026, 9, 5), "weak_battery", 0.41)
            )
        )

        assertEquals(listOf("Sep 2026", "Oct 2026"), trends.probabilityTrend.map { it.label })
        assertEquals(listOf(0.41, 0.33), trends.probabilityTrend.map { it.probability })
        assertEquals("Weak or failing battery", trends.probabilityTrend.first().hypothesisName)
        // Unknown hypothesis ids fall back to the id instead of an empty label.
        assertEquals("faulty_alternator", trends.probabilityTrend.last().hypothesisName)
    }

    @Test
    fun `resolved counters describe the completed assessments`() {
        val trends = build(
            listOf(
                assessment("a1", at(2026, 9, 5), emptySet(), resolved = true),
                assessment("a2", at(2026, 9, 20), emptySet(), resolved = false),
                assessment("a3", at(2026, 10, 2), emptySet(), resolved = false)
            )
        )

        assertEquals(3, trends.totalAssessments)
        assertEquals(1, trends.resolvedCount)
        assertEquals(2, trends.unresolvedCount)
    }

    @Test
    fun `empty history produces an empty, non crashing payload`() {
        val trends = build(emptyList())

        assertFalse(trends.hasData)
        assertEquals(0, trends.totalAssessments)
        assertTrue(trends.assessmentsOverTime.isEmpty())
        assertTrue(trends.symptomsBySystem.isEmpty())
        assertTrue(trends.repeatedSymptoms.isEmpty())
        assertTrue(trends.probabilityTrend.isEmpty())
        assertNull(trends.mostReportedSystem)
    }

    @Test
    fun `evidence that is no longer in the catalogue is still counted as repeated but not charted`() {
        val trends = build(
            listOf(
                assessment("a1", at(2026, 9, 5), linkedSetOf("removed_evidence")),
                assessment("a2", at(2026, 9, 20), linkedSetOf("removed_evidence"))
            )
        )

        assertTrue(trends.symptomsBySystem.isEmpty())
        assertNull(trends.mostReportedSystem)
        assertEquals(listOf("removed_evidence"), trends.repeatedSymptoms.map { it.label })
    }
}
