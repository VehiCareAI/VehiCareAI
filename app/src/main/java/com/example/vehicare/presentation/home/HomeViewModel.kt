package com.example.vehicare.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentSummary
import com.example.vehicare.domain.model.InfoNotice
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.model.UsageCounts
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.domain.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Dashboard state (Section 5.3). */
data class HomeUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val unit: MeasurementUnit = MeasurementUnit.METRIC,
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val selectedVehicleLatestAssessment: AssessmentSummary? = null,
    val counts: UsageCounts = UsageCounts(),
    val recentAssessments: List<AssessmentSummary> = emptyList(),
    val draft: Assessment? = null,
    val notices: List<InfoNotice> = emptyList(),
    val errorMessage: String? = null
) {
    val hasVehicles: Boolean get() = vehicles.isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    vehicleRepository: VehicleRepository,
    assessmentRepository: AssessmentRepository,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = refreshTrigger.flatMapLatest {
        combine(
            vehicleRepository.observeVehicles(),
            assessmentRepository.observeSummaries(),
            assessmentRepository.observeDraft(),
            preferencesRepository.preferences
        ) { vehicles, summaries, draft, preferences ->
            val selectedVehicle = vehicles.firstOrNull { it.id == preferences.selectedVehicleId }
                ?: vehicles.firstOrNull()
            val summariesForSelected = summaries
                .filter { it.vehicleId == selectedVehicle?.id }
                .sortedByDescending { it.completedAt }

            HomeUiState(
                isLoading = false,
                unit = preferences.measurementUnit,
                displayName = preferences.displayName,
                vehicles = vehicles,
                selectedVehicle = selectedVehicle,
                selectedVehicleLatestAssessment = summariesForSelected.firstOrNull(),
                counts = UsageCounts(
                    vehicleCount = vehicles.size,
                    assessmentCount = summaries.size,
                    draftCount = if (draft != null) 1 else 0,
                    openConcernCount = summaries.count { !it.resolved },
                    resolvedCount = summaries.count { it.resolved }
                ),
                recentAssessments = summaries.sortedByDescending { it.completedAt }.take(3),
                draft = draft,
                notices = buildNotices(summaries, draft, vehicles.size)
            )
        }.catch { throwable ->
            // A failure to read the local database must never leave the user on a blank screen.
            emit(
                HomeUiState(
                    isLoading = false,
                    errorMessage = throwable.message ?: "The local database could not be read."
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState()
    )

    /** Re-subscribes to the local data sources after a failure (Error state Retry action). */
    fun retry() {
        refreshTrigger.value += 1
    }

    private fun buildNotices(
        summaries: List<AssessmentSummary>,
        draft: Assessment?,
        vehicleCount: Int
    ): List<InfoNotice> {
        val notices = mutableListOf<InfoNotice>()
        if (draft != null) {
            notices += InfoNotice(
                id = "draft_${draft.id}",
                title = "Assessment in progress",
                message = "You can resume your unfinished assessment and keep your answers."
            )
        }
        summaries.filter { it.hasSafetyAlerts }.maxByOrNull { it.completedAt }?.let { flagged ->
            notices += InfoNotice(
                id = "safety_${flagged.assessmentId}",
                title = "Safety alert in your latest assessment for ${flagged.vehicleName}",
                message = "A reported symptom may make driving unsafe. Review the safety guidance in that report."
            )
        }
        val unresolved = summaries.count { !it.resolved }
        if (unresolved > 0) {
            notices += InfoNotice(
                id = "open_concerns",
                title = "$unresolved assessment${if (unresolved == 1) "" else "s"} still open",
                message = "Mark an assessment as resolved once the issue has been checked or repaired."
            )
        }
        if (vehicleCount == 0) {
            notices += InfoNotice(
                id = "no_vehicles",
                title = "Add your first vehicle",
                message = "Assessments need a vehicle, so start by adding one to your garage."
            )
        }
        notices += InfoNotice(
            id = "how_it_works",
            title = "How estimates are produced",
            message = "VehiCare AI compares your reported symptoms with a diagnostic knowledge base using Bayesian inference. Estimates are preliminary."
        )
        return notices
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
