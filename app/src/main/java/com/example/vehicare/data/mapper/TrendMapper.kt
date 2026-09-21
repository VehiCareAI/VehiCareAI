package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.dao.AssessmentTrendRow
import com.example.vehicare.data.local.dao.ProbabilityPointRow
import com.example.vehicare.domain.model.HealthTrends
import com.example.vehicare.domain.model.ProbabilityTrendPoint
import com.example.vehicare.domain.model.RepeatedSymptom
import com.example.vehicare.domain.model.SystemFrequency
import com.example.vehicare.domain.model.TrendPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure aggregation of persisted assessments into the Reports trend payload (Section 5.16).
 *
 * Everything is computed from rows that were already read (no further I/O), so the whole trends
 * calculation is reproducible and unit-testable without Android. Evidence ids are resolved through
 * the injected lookups: the knowledge base first, the local symptom catalogue as fallback.
 */
object TrendMapper {

    private val MONTH_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    /** Chart label of a point in time, e.g. "Sep 2026". */
    fun monthLabel(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        MONTH_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    fun build(
        assessments: List<AssessmentTrendRow>,
        probabilityPoints: List<ProbabilityPointRow>,
        evidenceLabel: (String) -> String?,
        evidenceSystemLabel: (String) -> String?,
        hypothesisName: (String) -> String?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): HealthTrends {
        val ordered = assessments.sortedWith(compareBy({ it.completedAt }, { it.assessmentId }))

        val perMonth = LinkedHashMap<String, Int>()
        val perSystem = LinkedHashMap<String, Int>()
        val perEvidenceAssessments = LinkedHashMap<String, Int>()

        ordered.forEach { row ->
            perMonth.merge(monthLabel(row.completedAt, zoneId), 1, Int::plus)
            row.presentEvidenceIds.forEach { evidenceId ->
                perEvidenceAssessments.merge(evidenceId, 1, Int::plus)
                evidenceSystemLabel(evidenceId)?.let { systemLabel ->
                    perSystem.merge(systemLabel, 1, Int::plus)
                }
            }
        }

        val symptomsBySystem = perSystem.entries
            .map { SystemFrequency(systemLabel = it.key, count = it.value) }
            .sortedWith(compareByDescending<SystemFrequency> { it.count }.thenBy { it.systemLabel })

        val repeatedSymptoms = perEvidenceAssessments.entries
            .filter { it.value >= MIN_OCCURRENCES_FOR_REPEATED }
            .map { RepeatedSymptom(label = evidenceLabel(it.key) ?: it.key, occurrences = it.value) }
            .sortedWith(compareByDescending<RepeatedSymptom> { it.occurrences }.thenBy { it.label })

        val probabilityTrend = probabilityPoints
            .sortedWith(compareBy({ it.completedAt }, { it.assessmentId }))
            .map { point ->
                ProbabilityTrendPoint(
                    label = monthLabel(point.completedAt, zoneId),
                    probability = point.posteriorProbability,
                    hypothesisName = hypothesisName(point.hypothesisId) ?: point.hypothesisId
                )
            }

        val resolvedCount = ordered.count { it.resolved }

        return HealthTrends(
            assessmentsOverTime = perMonth.entries.map { TrendPoint(label = it.key, value = it.value) },
            symptomsBySystem = symptomsBySystem,
            mostReportedSystem = symptomsBySystem.firstOrNull()?.systemLabel,
            repeatedSymptoms = repeatedSymptoms,
            probabilityTrend = probabilityTrend,
            resolvedCount = resolvedCount,
            unresolvedCount = ordered.size - resolvedCount,
            totalAssessments = ordered.size,
            hasData = ordered.isNotEmpty()
        )
    }

    /** A symptom counts as repeated once it was reported in at least this many assessments. */
    const val MIN_OCCURRENCES_FOR_REPEATED = 2
}
