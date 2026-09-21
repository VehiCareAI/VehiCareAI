package com.example.vehicare.presentation.trends

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
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.model.HealthTrends
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.ui.components.CountBarChart
import com.example.vehicare.ui.components.EmptyState
import com.example.vehicare.ui.components.ErrorState
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.ProbabilityTrendChart
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.StatTile
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.components.formatPercent
import com.example.vehicare.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TrendsUiState(
    val isLoading: Boolean = true,
    val trends: HealthTrends = HealthTrends(),
    val errorMessage: String? = null
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    assessmentRepository: AssessmentRepository
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val repository = assessmentRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TrendsUiState> = refreshTrigger
        .flatMapLatest {
            repository.observeTrends()
                .map { TrendsUiState(isLoading = false, trends = it) }
                .catch { throwable ->
                    // Trend queries failing (for example a corrupted database) must be visible and
                    // retryable; catching inside flatMapLatest keeps the trigger able to restart it.
                    emit(
                        TrendsUiState(
                            isLoading = false,
                            errorMessage = throwable.message ?: "The local database could not be read."
                        )
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsUiState())

    /** Re-subscribes to the local data sources after a failure (Error state Retry action). */
    fun retry() {
        refreshTrigger.value += 1
    }
}

/**
 * Health trends (Section 5.16). Trends describe what was *reported* and what the model estimated -
 * never a physical inspection - so the wording deliberately avoids "better" or "worse".
 */
@Composable
fun TrendsScreen(
    onStartAssessment: () -> Unit,
    viewModel: TrendsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trends = state.trends

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VehiCareTopBar(
                title = "Reports & Trends",
                subtitle = "Patterns across your recorded assessments."
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isLoading) {
                LoadingState(message = "Loading your trends…")
                return@Box
            }
            state.errorMessage?.let { message ->
                ErrorState(
                    title = "Trends could not be loaded",
                    message = message,
                    onRetry = viewModel::retry
                )
                return@Box
            }
            if (!trends.hasData) {
                EmptyState(
                    icon = Icons.Default.Insights,
                    title = "No trends yet",
                    message = "Trends appear once you have completed assessments. At least two assessments are " +
                        "needed for the probability chart.",
                    actionText = "Start Assessment",
                    onAction = onStartAssessment
                )
                return@Box
            }

            LazyColumn(
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        StatTile(
                            label = "Assessments",
                            value = trends.totalAssessments.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Open / resolved",
                            value = "${trends.unresolvedCount} / ${trends.resolvedCount}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    StatTile(
                        label = "Most reported system",
                        value = trends.mostReportedSystem ?: "Not enough data",
                        supporting = "Based on the symptoms you reported across assessments."
                    )
                }

                item { SectionHeader(title = "Assessments over time") }
                item {
                    VehiCareCard {
                        CountBarChart(
                            entries = trends.assessmentsOverTime.map { it.label to it.value },
                            emptyMessage = "No assessments recorded yet"
                        )
                    }
                }

                item { SectionHeader(title = "Symptoms by system") }
                item {
                    VehiCareCard {
                        CountBarChart(
                            entries = trends.symptomsBySystem.map { it.systemLabel to it.count },
                            barColor = MaterialTheme.colorScheme.tertiary,
                            emptyMessage = "No symptoms recorded yet"
                        )
                    }
                }

                item { SectionHeader(title = "Repeated symptoms") }
                item {
                    VehiCareCard {
                        if (trends.repeatedSymptoms.isEmpty()) {
                            Text(
                                text = "No symptom has been reported more than once so far.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            trends.repeatedSymptoms.forEach { symptom ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = symptom.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${symptom.occurrences}×",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Spacer(Modifier.height(Dimens.SpaceSm))
                            }
                        }
                    }
                }

                item { SectionHeader(title = "Estimates across comparable assessments") }
                item {
                    VehiCareCard {
                        if (trends.probabilityTrend.size < 2) {
                            Text(
                                text = "At least two assessments are needed to plot how estimates change.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            ProbabilityTrendChart(points = trends.probabilityTrend.map { it.probability })
                            Spacer(Modifier.height(Dimens.SpaceSm))
                            trends.probabilityTrend.forEach { point ->
                                Text(
                                    text = "${point.label} · ${point.hypothesisName} · " +
                                        formatPercent(point.probability),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("How to read this", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(Dimens.SpaceXs))
                        Text(
                            text = "Changing estimates reflect the symptoms you reported and the model's output. " +
                                "They are not a physical inspection of the vehicle, and this screen never " +
                                "concludes that a vehicle is healthier or worse on that basis alone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    PrimaryButton(text = "Start a new assessment", onClick = onStartAssessment)
                }
                item { Spacer(Modifier.height(Dimens.SpaceXl)) }
            }
        }
    }
}
