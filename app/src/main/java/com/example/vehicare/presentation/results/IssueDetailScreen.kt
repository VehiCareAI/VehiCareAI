package com.example.vehicare.presentation.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vehicare.ui.components.DisclaimerNote
import com.example.vehicare.ui.components.EmptyState
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.ProbabilityBar
import com.example.vehicare.ui.components.SeverityBadge
import com.example.vehicare.ui.components.SeverityMeter
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.theme.CriticalRed
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.ui.theme.WarningAmber
import com.example.vehicare.utils.Formats

/**
 * Issue detail (Section 5.13): why the engine raised this possibility, what supports it, what is
 * missing or contradicts it, what to check, and when to involve a professional.
 */
@Composable
fun IssueDetailScreen(
    assessmentId: String,
    hypothesisId: String,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(assessmentId) { viewModel.load(assessmentId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VehiCareTopBar(
                title = "Issue Details",
                subtitle = state.vehicleName.ifBlank { null },
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }
            return@Scaffold
        }

        val issue = state.analysis?.rankedIssues?.firstOrNull { it.hypothesisId == hypothesisId }
        if (issue == null) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Default.Science,
                    title = "Issue not available",
                    message = "This possibility is not part of the current ranking for this assessment.",
                    actionText = "Back",
                    onAction = onBack
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            item {
                VehiCareCard {
                    Text(issue.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Dimens.SpaceXs))
                    Text(
                        text = "${issue.system.label} · probability rank #${issue.rank}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Dimens.SpaceLg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Probability estimate", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = Formats.percent(issue.posteriorProbability),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(Modifier.height(Dimens.SpaceSm))
                            ProbabilityBar(probability = issue.posteriorProbability, showLabel = false)
                            Spacer(Modifier.height(Dimens.SpaceSm))
                            Text(
                                text = "Prior ${Formats.percent(issue.priorProbability)} before your symptoms were " +
                                    "taken into account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Severity if confirmed", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(Dimens.SpaceXs))
                            SeverityBadge(severity = issue.severity)
                            Spacer(Modifier.height(Dimens.SpaceSm))
                            SeverityMeter(severity = issue.severity)
                        }
                    }
                }
            }

            item {
                VehiCareCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.padding(Dimens.SpaceSm))
                        Text("Why this issue was identified", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(Dimens.SpaceSm))
                    Text(issue.explanation, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Dimens.SpaceSm))
                    Text(
                        text = "Evidence strength: ${issue.matchedEvidenceCount} of " +
                            "${issue.consideredEvidenceCount} considered findings match this possibility.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                EvidenceSection(
                    title = "Supporting symptoms",
                    items = issue.supportingEvidence.map { it.label },
                    emptyText = "No reported finding directly supports this possibility."
                )
            }
            item {
                EvidenceSection(
                    title = "Missing or contradicting evidence",
                    items = issue.contradictingEvidence.map { "Reported as not present: ${it.label}" } +
                        issue.missingEvidence.map { "${it.label} was not provided" },
                    emptyText = "Nothing reported contradicts this possibility, and no obviously relevant detail is missing."
                )
            }
            item {
                EvidenceSection(
                    title = "Possible causes",
                    items = issue.possibleCauses,
                    emptyText = "No specific cause is documented for this possibility."
                )
            }
            item {
                EvidenceSection(
                    title = "Recommended checks",
                    items = issue.recommendedChecks,
                    emptyText = "No specific checks are documented.",
                    icon = Icons.Default.Build
                )
            }
            if (issue.safetyWarnings.isNotEmpty()) {
                item {
                    VehiCareCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CriticalRed)
                            Spacer(Modifier.padding(Dimens.SpaceSm))
                            Text(
                                text = "Safety information",
                                style = MaterialTheme.typography.titleSmall,
                                color = CriticalRed
                            )
                        }
                        Spacer(Modifier.height(Dimens.SpaceSm))
                        issue.safetyWarnings.forEach { warning ->
                            Text(warning, style = MaterialTheme.typography.bodyMedium, color = CriticalRed)
                            Spacer(Modifier.height(Dimens.SpaceXs))
                        }
                    }
                }
            }

            item {
                VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = WarningAmber)
                        Spacer(Modifier.padding(Dimens.SpaceSm))
                        Text("Professional assistance", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(Dimens.SpaceSm))
                    Text(
                        text = "Have a qualified technician verify these findings before parts are replaced. " +
                            "Recommended service area: ${issue.recommendedServiceCategory}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { DisclaimerNote() }
            item { Spacer(Modifier.height(Dimens.SpaceXl)) }
        }
    }
}

@Composable
private fun EvidenceSection(
    title: String,
    items: List<String>,
    emptyText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Science
) {
    VehiCareCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(Dimens.SpaceSm))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(Dimens.SpaceSm))
        if (items.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(Dimens.SpaceXs))
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(Dimens.SpaceXs))
            }
        }
    }
}
