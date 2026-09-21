package com.example.vehicare.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Search
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
import com.example.vehicare.domain.model.AssessmentSummary
import com.example.vehicare.domain.model.AssessmentType
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.ui.components.AssessmentSummaryCard
import com.example.vehicare.ui.components.ConfirmationDialog
import com.example.vehicare.ui.components.EmptyState
import com.example.vehicare.ui.components.ErrorState
import com.example.vehicare.ui.components.FilterChipsRow
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.SearchField
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryDateFilter(val label: String, val days: Int?) {
    ALL("All time", null),
    WEEK("Last 7 days", 7),
    MONTH("Last 30 days", 30),
    QUARTER("Last 90 days", 90)
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val vehicleFilter: String = ALL_VEHICLES,
    val dateFilter: HistoryDateFilter = HistoryDateFilter.ALL,
    val typeFilter: String = ALL_TYPES,
    val systemFilter: String = ALL_SYSTEMS,
    val vehicleOptions: List<String> = listOf(ALL_VEHICLES),
    val systemOptions: List<String> = listOf(ALL_SYSTEMS),
    val visible: List<AssessmentSummary> = emptyList(),
    val totalCount: Int = 0,
    val deleteCandidate: AssessmentSummary? = null,
    val errorMessage: String? = null
) {
    val hasRecords: Boolean get() = totalCount > 0
    val hasFiltersApplied: Boolean
        get() = query.isNotBlank() || vehicleFilter != ALL_VEHICLES ||
            dateFilter != HistoryDateFilter.ALL || typeFilter != ALL_TYPES || systemFilter != ALL_SYSTEMS

    companion object {
        const val ALL_VEHICLES = "All vehicles"
        const val ALL_TYPES = "All types"
        const val ALL_SYSTEMS = "All systems"
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val assessmentRepository: AssessmentRepository
) : ViewModel() {

    private val uiFilters = MutableStateFlow(HistoryUiState())
    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HistoryUiState> = refreshTrigger.flatMapLatest {
        combine(
            assessmentRepository.observeSummaries(),
            uiFilters
        ) { summaries, filters ->
            val cutoff = filters.dateFilter.days?.let {
                System.currentTimeMillis() - it.toLong() * MILLIS_PER_DAY
            }
            val filtered = summaries.filter { summary ->
                val matchesQuery = filters.query.isBlank() ||
                    listOf(
                        summary.vehicleName,
                        summary.mainSymptomLabel,
                        summary.topIssueName.orEmpty(),
                        summary.typeLabel,
                        summary.primarySystem
                    ).any { it.contains(filters.query, ignoreCase = true) }
                val matchesVehicle = filters.vehicleFilter == HistoryUiState.ALL_VEHICLES ||
                    summary.vehicleName == filters.vehicleFilter
                val matchesDate = cutoff == null || summary.completedAt >= cutoff
                val matchesType = filters.typeFilter == HistoryUiState.ALL_TYPES ||
                    summary.typeLabel == filters.typeFilter
                val matchesSystem = filters.systemFilter == HistoryUiState.ALL_SYSTEMS ||
                    summary.primarySystem == filters.systemFilter
                matchesQuery && matchesVehicle && matchesDate && matchesType && matchesSystem
            }.sortedByDescending { it.completedAt }

            filters.copy(
                isLoading = false,
                totalCount = summaries.size,
                visible = filtered,
                vehicleOptions = listOf(HistoryUiState.ALL_VEHICLES) +
                    summaries.map { it.vehicleName }.distinct().sorted(),
                systemOptions = listOf(HistoryUiState.ALL_SYSTEMS) +
                    summaries.map { it.primarySystem }.filter { it.isNotBlank() }.distinct().sorted()
            )
        }.catch { throwable ->
            // Reading the local database can fail; the user gets an explicit, retryable error state.
            emit(
                HistoryUiState(
                    isLoading = false,
                    errorMessage = throwable.message ?: "The local database could not be read."
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** Re-subscribes to the local data sources after a failure (Error state Retry action). */
    fun retry() {
        refreshTrigger.value += 1
    }

    fun onQueryChange(value: String) = uiFilters.update { it.copy(query = value) }
    fun onVehicleFilterChange(value: String) = uiFilters.update { it.copy(vehicleFilter = value) }
    fun onDateFilterChange(value: HistoryDateFilter) = uiFilters.update { it.copy(dateFilter = value) }
    fun onTypeFilterChange(value: String) = uiFilters.update { it.copy(typeFilter = value) }
    fun onSystemFilterChange(value: String) = uiFilters.update { it.copy(systemFilter = value) }

    fun clearFilters() = uiFilters.update {
        it.copy(
            query = "",
            vehicleFilter = HistoryUiState.ALL_VEHICLES,
            dateFilter = HistoryDateFilter.ALL,
            typeFilter = HistoryUiState.ALL_TYPES,
            systemFilter = HistoryUiState.ALL_SYSTEMS
        )
    }

    fun requestDelete(summary: AssessmentSummary) = uiFilters.update { it.copy(deleteCandidate = summary) }

    fun cancelDelete() = uiFilters.update { it.copy(deleteCandidate = null) }

    fun confirmDelete() {
        val target = uiFilters.value.deleteCandidate ?: return
        viewModelScope.launch {
            assessmentRepository.delete(target.assessmentId)
            uiFilters.update { it.copy(deleteCandidate = null) }
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

/** Assessment history with search and filters (Section 5.15). */
@Composable
fun HistoryScreen(
    onOpenResults: (String) -> Unit,
    onStartAssessment: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    state.deleteCandidate?.let { candidate ->
        ConfirmationDialog(
            title = "Delete this assessment?",
            message = "The assessment for ${candidate.vehicleName} on " +
                "${com.example.vehicare.utils.Formats.date(candidate.completedAt)} will be permanently removed.",
            confirmText = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VehiCareTopBar(
                title = "Assessment History",
                subtitle = "Search and filter your previous vehicle health assessments."
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }
            return@Scaffold
        }
        state.errorMessage?.let { message ->
            Box(modifier = Modifier.padding(innerPadding)) {
                ErrorState(
                    title = "Assessment history could not be loaded",
                    message = message,
                    onRetry = viewModel::retry
                )
            }
            return@Scaffold
        }
        if (!state.hasRecords) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Default.FactCheck,
                    title = "No assessments yet",
                    message = "Complete your first vehicle health assessment to begin building your diagnostic history.",
                    actionText = "Start Assessment",
                    onAction = onStartAssessment
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
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "Search vehicle, symptom or issue",
                    leadingIcon = Icons.Default.Search
                )
            }
            item {
                FilterChipsRow(
                    options = HistoryDateFilter.entries.map { it.label },
                    selected = state.dateFilter.label,
                    onSelect = { label ->
                        HistoryDateFilter.entries.firstOrNull { it.label == label }
                            ?.let(viewModel::onDateFilterChange)
                    }
                )
            }
            item {
                FilterChipsRow(
                    options = state.vehicleOptions,
                    selected = state.vehicleFilter,
                    onSelect = viewModel::onVehicleFilterChange
                )
            }
            item {
                FilterChipsRow(
                    options = listOf(HistoryUiState.ALL_SYSTEMS) + state.systemOptions.drop(1),
                    selected = state.systemFilter,
                    onSelect = viewModel::onSystemFilterChange
                )
            }
            item {
                FilterChipsRow(
                    options = listOf(HistoryUiState.ALL_TYPES) + AssessmentType.entries.map { it.label },
                    selected = state.typeFilter,
                    onSelect = viewModel::onTypeFilterChange
                )
            }
            item {
                Text(
                    text = if (state.hasFiltersApplied) {
                        "${state.visible.size} of ${state.totalCount} assessments match your filters"
                    } else {
                        "${state.totalCount} assessment${if (state.totalCount == 1) "" else "s"} in total"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.visible.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = "No matching assessments",
                        message = "Try a different search term or clear the filters.",
                        actionText = "Clear filters",
                        onAction = viewModel::clearFilters
                    )
                }
            } else {
                items(state.visible, key = { it.assessmentId }) { summary ->
                    AssessmentSummaryCard(
                        summary = summary,
                        onClick = { onOpenResults(summary.assessmentId) }
                    )
                    Spacer(Modifier.height(Dimens.SpaceXs))
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.requestDelete(summary) }
                    ) {
                        Text("Delete assessment")
                    }
                }
            }
            item { Spacer(Modifier.height(Dimens.SpaceXl)) }
        }
    }
}
