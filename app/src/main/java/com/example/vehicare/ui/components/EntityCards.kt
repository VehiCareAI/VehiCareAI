package com.example.vehicare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.vehicare.domain.model.AssessmentSummary
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.utils.Formats

/**
 * Vehicle summary card with a silhouette, key specs and status. Used by My Vehicles and Home.
 * The overflow/trailing actions are supplied by the caller so the card stays reusable.
 */
@Composable
fun VehicleSummaryCard(
    vehicle: Vehicle,
    unit: MeasurementUnit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    VehiCareCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.size(Dimens.SpaceLg))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = vehicle.displayName.ifBlank { "Unnamed vehicle" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (vehicle.isSample) {
                        Spacer(Modifier.size(Dimens.SpaceSm))
                        SampleDataBadge()
                    }
                }
                Spacer(Modifier.height(Dimens.SpaceXs))
                Text(
                    text = listOf(
                        vehicle.fuelType,
                        vehicle.transmission,
                        Formats.mileage(vehicle.mileageKm, unit)
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!vehicle.licensePlate.isNullOrBlank()) {
                    Spacer(Modifier.height(Dimens.SpaceXs))
                    Text(
                        text = "Plate ${vehicle.licensePlate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(Dimens.SpaceXs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * One assessment row: vehicle, date, main symptom, number of possible issues and status.
 * Probability is shown as text (never as colour alone) whenever it is available.
 */
@Composable
fun AssessmentSummaryCard(
    summary: AssessmentSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    showVehicle: Boolean = true
) {
    VehiCareCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = if (summary.hasSafetyAlerts) Icons.Default.ReportProblem else Icons.Default.Schedule,
                containerColor = if (summary.hasSafetyAlerts) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (summary.hasSafetyAlerts) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.size(Dimens.SpaceLg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.topIssueName ?: "No issue ranked",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Dimens.SpaceXs))
                Text(
                    text = listOf(
                        if (showVehicle) summary.vehicleName else null,
                        Formats.date(summary.completedAt),
                        summary.typeLabel
                    ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(Modifier.height(Dimens.SpaceXs))
                Text(
                    text = "Main symptom: ${summary.mainSymptomLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(Dimens.SpaceMd))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            if (summary.topProbability != null) {
                StatusChip(
                    label = "${formatPercent(summary.topProbability)} relative estimate",
                    tone = ChipTone.INFO
                )
            }
            if (summary.topConsideredEvidenceCount > 0) {
                StatusChip(
                    label = "${summary.topMatchedEvidenceCount} of " +
                        "${summary.topConsideredEvidenceCount} findings support this",
                    tone = ChipTone.NEUTRAL
                )
            }
            StatusChip(
                label = "${summary.issueCount} possible issue${if (summary.issueCount == 1) "" else "s"}",
                tone = ChipTone.NEUTRAL
            )
            if (summary.hasSafetyAlerts) {
                StatusChip(label = "Safety alert", tone = ChipTone.CRITICAL)
            }
            if (summary.resolved) {
                StatusChip(label = "Resolved", tone = ChipTone.HEALTHY)
            }
        }
    }
}

/** Small probability/percentage chip used where a full bar would be too heavy. */
@Composable
fun ProbabilityChip(probability: Double, modifier: Modifier = Modifier) {
    val tone = when {
        probability >= 0.5 -> ChipTone.WARNING
        probability >= 0.25 -> ChipTone.INFO
        else -> ChipTone.NEUTRAL
    }
    StatusChip(label = formatPercent(probability), tone = tone, modifier = modifier)
}

/** Healthy/unresolved indicator used on report lists. */
@Composable
fun ResolvedChip(resolved: Boolean, modifier: Modifier = Modifier) {
    if (resolved) {
        StatusChip(label = "Resolved", tone = ChipTone.HEALTHY, modifier = modifier)
    } else {
        StatusChip(label = "Open", tone = ChipTone.WARNING, modifier = modifier)
    }
}

