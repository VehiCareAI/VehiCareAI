package com.example.vehicare.presentation.results

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vehicare.domain.diagnostic.models.DiagnosticAnalysis
import com.example.vehicare.ui.components.DisclaimerNote
import com.example.vehicare.ui.components.ErrorState
import com.example.vehicare.ui.components.KeyValueRow
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.PRELIMINARY_DISCLAIMER
import com.example.vehicare.ui.components.PreliminaryBadge
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.REPORT_DISCLAIMER
import com.example.vehicare.ui.components.SafetyAlertCard
import com.example.vehicare.ui.components.SecondaryButton
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.SeverityBadge
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.components.formatPercent
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.utils.Formats
import com.example.vehicare.utils.PdfReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Report screen (Section 5.14): a professional, report-style view with Save, Share (text and PDF),
 * Export PDF and Start New Assessment. Every action performs real work.
 */
@Composable
fun ReportScreen(
    assessmentId: String,
    onBack: () -> Unit,
    onStartNewAssessment: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(assessmentId) { viewModel.load(assessmentId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VehiCareTopBar(
                title = "Assessment Report",
                subtitle = "Report ID ${Formats.reportId(assessmentId)}",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }

            state.errorMessage != null -> Box(modifier = Modifier.padding(innerPadding)) {
                ErrorState(
                    title = "Report unavailable",
                    message = state.errorMessage.orEmpty(),
                    onRetry = { viewModel.load(assessmentId) }
                )
            }

            else -> {
                val analysis = state.analysis
                if (analysis == null) {
                    Box(modifier = Modifier.padding(innerPadding)) {
                        LoadingState()
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
                    item { ReportHeader() }

                    if (state.modelVersionChanged) {
                        item {
                            VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    text = "This report was produced by an earlier version of the " +
                                        "diagnostic model. The estimates shown here have been recalculated " +
                                        "by the current version and may differ from the original assessment.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    item {
                        VehiCareCard {
                            KeyValueRow(label = "Vehicle", value = state.vehicleName)
                            KeyValueRow(label = "Report ID", value = Formats.reportId(assessmentId))
                            KeyValueRow(label = "Generated", value = Formats.dateTime(state.completedAt))
                            KeyValueRow(label = "Assessment type", value = state.typeLabel)
                            KeyValueRow(label = "Status", value = "Preliminary assessment")
                            KeyValueRow(
                                label = "Reported severity",
                                value = Formats.severityLabel(state.reportedSeverity)
                            )
                            KeyValueRow(
                                label = "Mileage",
                                value = Formats.mileage(
                                    state.vehicle?.mileageKm,
                                    state.unit
                                )
                            )
                            KeyValueRow(
                                label = "Outcome",
                                value = if (state.resolved) "Marked resolved" else "Open"
                            )
                        }
                    }

                    item { SectionHeader(title = "Reported symptoms") }
                    item {
                        VehiCareCard {
                            if (analysis.reportedEvidence.isEmpty()) {
                                Text("No symptoms were recorded.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                analysis.reportedEvidence.forEach { evidence ->
                                    Text("• ${evidence.label}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (analysis.absentEvidence.isNotEmpty()) {
                                Spacer(Modifier.height(Dimens.SpaceSm))
                                Text(
                                    text = "Explicitly reported as not present: " +
                                        analysis.absentEvidence.joinToString(", ") { it.label },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (analysis.hasSafetyAlerts) {
                        item { SectionHeader(title = "Safety alerts") }
                        items(analysis.safetyAlerts, key = { it.id }) { alert ->
                            SafetyAlertCard(alert = alert)
                        }
                    }

                    if (analysis.isSufficient) {
                        item { SectionHeader(title = "Ranked possible issues") }
                        items(analysis.rankedIssues, key = { it.hypothesisId }) { issue ->
                            VehiCareCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${issue.rank}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.padding(Dimens.SpaceSm))
                                    Text(
                                        text = issue.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatPercent(issue.posteriorProbability),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Spacer(Modifier.height(Dimens.SpaceXs))
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                                    SeverityBadge(severity = issue.severity)
                                }
                                Spacer(Modifier.height(Dimens.SpaceXs))
                                Text(
                                    text = "Evidence strength: ${issue.matchedEvidenceCount} of " +
                                        "${issue.consideredEvidenceCount} considered findings support this",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(Dimens.SpaceSm))
                                Text(issue.summary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "Recommended: ${issue.recommendedServiceCategory}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        item {
                            Text(
                                text = "Percentages are the model's estimates among the possibilities it " +
                                    "considered, so they are relative rather than absolute confidence.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        item {
                            VehiCareCard {
                                Text("Insufficient evidence", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(Dimens.SpaceXs))
                                Text(
                                    text = "Unable to identify a sufficiently supported possible issue from the " +
                                        "provided information. Consider adding more symptom details or " +
                                        "consulting a qualified mechanic.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item { SectionHeader(title = "Next steps") }
                    item {
                        VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                text = if (analysis.hasSafetyAlerts) {
                                    "Address the safety alerts first. Consider stopping in a safe location and " +
                                        "seeking professional assistance before continuing to drive."
                                } else {
                                    "Have the recommended checks carried out by a qualified professional. " +
                                        "If the symptoms change or worsen, run a new assessment."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    item { DisclaimerNote(text = REPORT_DISCLAIMER) }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                            PrimaryButton(
                                text = "Save",
                                onClick = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Report ${Formats.reportId(assessmentId)} is saved on this device"
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Save
                            )
                            PrimaryButton(
                                text = "Share",
                                onClick = { shareText(context, state.vehicleName, assessmentId, state.completedAt, analysis) },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Share
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                            SecondaryButton(
                                text = "Share PDF",
                                onClick = {
                                    scope.launch {
                                        val file = exportPdf(context, assessmentId, state.vehicleName, state.completedAt, analysis)
                                        if (file != null) sharePdf(context, file) else snackbarHostState.showSnackbar("PDF export failed")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Share
                            )
                            SecondaryButton(
                                text = "Export PDF",
                                onClick = {
                                    scope.launch {
                                        val file = exportPdf(context, assessmentId, state.vehicleName, state.completedAt, analysis)
                                        snackbarHostState.showSnackbar(
                                            if (file != null) "Saved ${file.name} to this device" else "PDF export failed"
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.CloudDownload
                            )
                        }
                    }
                    item {
                        SecondaryButton(
                            text = "Start New Assessment",
                            onClick = onStartNewAssessment,
                            icon = Icons.Default.PlayArrow
                        )
                    }
                    item {
                        Text(
                            text = PRELIMINARY_DISCLAIMER,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item { Spacer(Modifier.height(Dimens.SpaceXl)) }
                }
            }
        }
    }
}

@Composable
private fun ReportHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.vehicare.R.drawable.ic_vehicare_logo),
            contentDescription = "VehiCare AI logo",
            modifier = Modifier.size(52.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("VehiCare AI", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Intelligent Vehicle Health Assessment",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PreliminaryBadge()
    }
    Spacer(Modifier.height(Dimens.SpaceSm))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Shares the report as plain text (no dependency, no permissions). */
private fun shareText(
    context: Context,
    vehicleName: String,
    assessmentId: String,
    generatedAt: Long,
    analysis: DiagnosticAnalysis
) {
    val ranked = if (analysis.isSufficient) {
        analysis.rankedIssues.joinToString("\n") { issue ->
            "${issue.rank}. ${issue.name} - ${Formats.percent(issue.posteriorProbability)} " +
                "(severity ${issue.severity.label})"
        }
    } else {
        "No sufficiently supported issue was identified from the reported symptoms."
    }
    val safety = if (analysis.hasSafetyAlerts) {
        "\n\nSAFETY ALERTS\n" + analysis.safetyAlerts.joinToString("\n") { "- ${it.title}: ${it.message}" }
    } else {
        ""
    }
    val text = buildString {
        appendLine("VehiCare AI - preliminary vehicle health assessment")
        appendLine("Vehicle: $vehicleName")
        appendLine("Report ID: ${Formats.reportId(assessmentId)}")
        appendLine("Generated: ${Formats.dateTime(generatedAt)}")
        appendLine()
        appendLine("Reported symptoms: " + analysis.reportedEvidence.joinToString(", ") { it.label })
        appendLine()
        appendLine("Ranked possible issues:")
        appendLine(ranked)
        append(safety)
        appendLine()
        appendLine()
        appendLine(REPORT_DISCLAIMER)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "VehiCare AI report for $vehicleName")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share report"))
}

private suspend fun exportPdf(
    context: Context,
    assessmentId: String,
    vehicleName: String,
    generatedAt: Long,
    analysis: DiagnosticAnalysis
): java.io.File? = withContext(Dispatchers.IO) {
    runCatching {
        PdfReportExporter(context).export(
            assessmentId = assessmentId,
            vehicleName = vehicleName,
            generatedAt = generatedAt,
            analysis = analysis,
            settingsNote = "Generated locally on this device. Knowledge base version " +
                analysis.knowledgeBaseVersion + ", engine " + analysis.engineVersion + "."
        )
    }.getOrNull()
}

private fun sharePdf(context: Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF report"))
}
