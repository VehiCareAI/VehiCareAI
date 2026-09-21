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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vehicare.domain.diagnostic.models.RankedIssue
import com.example.vehicare.ui.components.DisclaimerNote
import com.example.vehicare.ui.components.EmptyState
import com.example.vehicare.ui.components.ErrorState
import com.example.vehicare.ui.components.KeyValueRow
import com.example.vehicare.ui.components.LikelihoodLabel
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.PreliminaryBadge
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.ProbabilityBar
import com.example.vehicare.ui.components.ProbabilityRing
import com.example.vehicare.ui.components.RELATIVE_ESTIMATE_NOTE
import com.example.vehicare.ui.components.SafetyAlertCard
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.SeverityBadge
import com.example.vehicare.ui.components.SeverityMeter
import com.example.vehicare.ui.components.StatusChip
import com.example.vehicare.ui.components.ChipTone
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.components.formatPercent
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.utils.Formats

/**
 * Results overview (Section 5.11): safety alerts first, then the summary, the primary issue and the
 * probability disclaimer. Probability is always text + graphic, severity is a separate visual.
 */
@Composable
fun ResultsScreen(
    assessmentId: String,
    onBack: () -> Unit,
    onOpenRankedIssues: (String) -> Unit,
    onOpenIssue: (String, String) -> Unit,
    onOpenReport: (String) -> Unit,
    onStartNewAssessment: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(assessmentId) { viewModel.load(assessmentId) }
    val analysis = state.analysis

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VehiCareTopBar(
                title = "Assessment Results",
                subtitle = "Possible causes based on your reported symptoms.",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onOpenReport(assessmentId) }) {
                        Icon(Icons.Default.Description, contentDescription = "Open report")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }

            state.errorMessage != null -> Box(modifier = Modifier.padding(innerPadding)) {
                ErrorState(
                    title = "Results unavailable",
                    message = state.errorMessage.orEmpty(),
                    onRetry = { viewModel.load(assessmentId) }
                )
            }

            analysis == null -> Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
            ) {
                // Safety alerts always come first (Section 7) and never depend on ranking.
                items(state.safetyAlerts, key = { it.id }) { alert ->
                    SafetyAlertCard(alert = alert)
                }

                item {
                    VehiCareCard {
                        PreliminaryBadge()
                        Spacer(Modifier.height(Dimens.SpaceSm))
                        KeyValueRow(label = "Vehicle", value = state.vehicleName)
                        KeyValueRow(label = "Assessment date", value = Formats.dateTime(state.completedAt))
                        KeyValueRow(label = "Report ID", value = Formats.reportId(state.assessmentId))
                        KeyValueRow(label = "Type", value = state.typeLabel)
                        KeyValueRow(
                            label = "Reported severity",
                            value = Formats.severityLabel(state.reportedSeverity)
                        )
                        Spacer(Modifier.height(Dimens.SpaceSm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                            StatusChip(
                                label = if (state.resolved) "Marked resolved" else "Still open",
                                tone = if (state.resolved) ChipTone.HEALTHY else ChipTone.WARNING
                            )
                            val highestSeverity = state.analysis?.rankedIssues
                                ?.maxByOrNull { it.severity.rank }?.severity
                            if (highestSeverity != null) {
                                SeverityBadge(severity = highestSeverity)
                            }
                        }
                    }
                }

                if (!analysis.isSufficient) {
                    item {
                        VehiCareCard {
                            Text("Insufficient evidence", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(Dimens.SpaceSm))
                            Text(
                                text = "Unable to identify a sufficiently supported possible issue from the " +
                                    "provided information. Consider adding more symptom details or consulting " +
                                    "a qualified mechanic.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Dimens.SpaceLg))
                            PrimaryButton(text = "Start a new assessment", onClick = onStartNewAssessment)
                        }
                    }
                } else {
                    item {
                        VehiCareCard {
                            Text(
                                text = "${analysis.rankedIssues.size} possible " +
                                    "issue${if (analysis.rankedIssues.size == 1) "" else "s"} identified",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(Dimens.SpaceXs))
                            Text(
                                text = "These may require further inspection. Percentages are relative " +
                                    "estimates among the possibilities considered.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Dimens.SpaceLg))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)
                            ) {
                                ProbabilityRing(
                                    probability = analysis.primaryIssue?.posteriorProbability ?: 0.0,
                                    label = "leading estimate"
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Undetermined share",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatPercent(analysis.undeterminedProbability),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(Modifier.height(Dimens.SpaceSm))
                                    Text(
                                        text = RELATIVE_ESTIMATE_NOTE,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    val primary = analysis.primaryIssue
                    if (primary != null) {
                        item {
                            SectionHeader(title = "Leading possibility")
                        }
                        item {
                            VehiCareCard(onClick = { onOpenIssue(assessmentId, primary.hypothesisId) }) {
                                Text(primary.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(Dimens.SpaceXs))
                                Text(
                                    text = primary.system.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(Dimens.SpaceLg))
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                                    LikelihoodLabel(probability = primary.posteriorProbability)
                                    SeverityBadge(severity = primary.severity)
                                }
                                Spacer(Modifier.height(Dimens.SpaceLg))
                                ProbabilityBar(probability = primary.posteriorProbability)
                                Spacer(Modifier.height(Dimens.SpaceMd))
                                Text(primary.explanation, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(Dimens.SpaceMd))
                                Text(
                                    text = "Next step: ${primary.recommendedServiceCategory}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(Modifier.height(Dimens.SpaceSm))
                                Text(
                                    text = "Evidence strength: ${primary.matchedEvidenceCount} of " +
                                        "${primary.consideredEvidenceCount} considered findings support this",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(Dimens.SpaceMd))
                                SeverityMeter(severity = primary.severity)
                                Spacer(Modifier.height(Dimens.SpaceMd))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Open full details",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    item {
                        PrimaryButton(
                            text = "See all ${analysis.rankedIssues.size} ranked issues",
                            onClick = { onOpenRankedIssues(assessmentId) }
                        )
                    }
                }

                item { DisclaimerNote() }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        PrimaryButton(
                            text = "View report",
                            onClick = { onOpenReport(assessmentId) },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Description
                        )
                        PrimaryButton(
                            text = if (state.resolved) "Reopen" else "Mark resolved",
                            onClick = { viewModel.setResolved(!state.resolved) },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CheckCircle
                        )
                    }
                }
                item { Spacer(Modifier.height(Dimens.SpaceXl)) }
            }
        }
    }
}

/** Ranked possible issues, ordered by posterior (Section 5.12). */
@Composable
fun RankedIssuesScreen(
    assessmentId: String,
    onBack: () -> Unit,
    onOpenIssue: (String, String) -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(assessmentId) { viewModel.load(assessmentId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VehiCareTopBar(
                title = "Ranked Possible Issues",
                subtitle = "Ordered by estimated probability, not by urgency.",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }
            return@Scaffold
        }
        val issues = state.analysis?.rankedIssues.orEmpty()
        if (issues.isEmpty()) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Default.Science,
                    title = "No ranked issues",
                    message = "The engine did not find a sufficiently supported possibility for this assessment.",
                    actionText = "Back to results",
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
                Text(
                    text = "Probability rank does not equal urgency. A less likely issue can still be more " +
                        "serious, so check the severity badge on each card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Text(
                    text = "Calculated ${Formats.dateTime(state.completedAt)} · engine " +
                        "${state.analysis?.engineVersion.orEmpty()} · knowledge base " +
                        "${state.analysis?.knowledgeBaseVersion.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(issues, key = { it.hypothesisId }) { issue ->
                RankedIssueCard(
                    issue = issue,
                    onOpenDetails = { onOpenIssue(assessmentId, issue.hypothesisId) }
                )
            }
            item { DisclaimerNote() }
            item { Spacer(Modifier.height(Dimens.SpaceXl)) }
        }
    }
}

@Composable
private fun RankedIssueCard(issue: RankedIssue, onOpenDetails: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Tapping the card expands the inline summary; the explicit action opens the full issue screen.
    VehiCareCard(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "#${issue.rank}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.padding(Dimens.SpaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(issue.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = issue.system.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatPercent(issue.posteriorProbability),
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(Modifier.height(Dimens.SpaceMd))
        ProbabilityBar(probability = issue.posteriorProbability, showLabel = false)
        Spacer(Modifier.height(Dimens.SpaceMd))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            SeverityBadge(severity = issue.severity)
            LikelihoodLabel(probability = issue.posteriorProbability)
        }
        Spacer(Modifier.height(Dimens.SpaceXs))
        Text(
            text = "Evidence strength: ${issue.matchedEvidenceCount} of " +
                "${issue.consideredEvidenceCount} considered findings support this",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (expanded) {
            Spacer(Modifier.height(Dimens.SpaceMd))
            Text(issue.explanation, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Dimens.SpaceSm))
            Text(
                text = "Supporting: " + issue.supportingEvidence.joinToString(", ") { it.label },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (issue.missingEvidence.isNotEmpty()) {
                Text(
                    text = "Not provided: " + issue.missingEvidence.joinToString(", ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (issue.contradictingEvidence.isNotEmpty()) {
                Text(
                    text = "Contradicting: " + issue.contradictingEvidence.joinToString(", ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Dimens.SpaceMd))
            androidx.compose.material3.TextButton(onClick = onOpenDetails) {
                Text(
                    text = "Open full details",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Spacer(Modifier.height(Dimens.SpaceSm))
            androidx.compose.material3.TextButton(onClick = { expanded = true }) {
                Text("Show why this was identified", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
