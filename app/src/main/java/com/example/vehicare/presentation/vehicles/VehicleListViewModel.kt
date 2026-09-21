package com.example.vehicare.presentation.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.domain.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VehicleListUiState(
    val isLoading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val unit: MeasurementUnit = MeasurementUnit.METRIC,
    /** Vehicle awaiting delete confirmation, with the number of assessments that would be removed. */
    val deleteCandidate: Vehicle? = null,
    val deleteCandidateAssessmentCount: Int = 0
) {
    val isEmpty: Boolean get() = vehicles.isEmpty()
}

@HiltViewModel
class VehicleListViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val assessmentRepository: AssessmentRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val deleteCandidate = MutableStateFlow<Vehicle?>(null)
    private val deleteCandidateCount = MutableStateFlow(0)

    val state: StateFlow<VehicleListUiState> = combine(
        vehicleRepository.observeVehicles(),
        preferencesRepository.preferences,
        deleteCandidate,
        deleteCandidateCount
    ) { vehicles, preferences, candidate, count ->
        VehicleListUiState(
            isLoading = false,
            vehicles = vehicles,
            unit = preferences.measurementUnit,
            deleteCandidate = candidate,
            deleteCandidateAssessmentCount = count
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleListUiState())

    /** Deletion always asks first and states how many assessments will be removed (Section 8). */
    fun requestDelete(vehicle: Vehicle) {
        viewModelScope.launch {
            deleteCandidateCount.value =
                assessmentRepository.observeSummariesForVehicle(vehicle.id).first().size
            deleteCandidate.value = vehicle
        }
    }

    fun cancelDelete() {
        deleteCandidate.value = null
        deleteCandidateCount.value = 0
    }

    fun confirmDelete(onDeleted: (String) -> Unit) {
        val target = deleteCandidate.value ?: return
        viewModelScope.launch {
            vehicleRepository.delete(target.id)
            deleteCandidate.value = null
            deleteCandidateCount.value = 0
            onDeleted(target.displayName)
        }
    }

    /** Selects a vehicle (so the dashboard and assessment flow follow the user's intent). */
    fun selectVehicle(vehicleId: String, onSelected: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setSelectedVehicleId(vehicleId)
            onSelected()
        }
    }
}
