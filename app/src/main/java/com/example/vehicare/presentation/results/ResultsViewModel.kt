package com.example.vehicare.presentation.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.diagnostic.models.DiagnosticAnalysis
import com.example.vehicare.domain.model.AssessmentType
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.domain.usecase.LoadAssessmentAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultsUiState(
    val isLoading: Boolean = true,
    val assessmentId: String = "",
    val vehicleName: String = "",
    val vehicle: Vehicle? = null,
    val typeLabel: String = AssessmentType.SYMPTOM_BASED.label,
    val unit: MeasurementUnit = MeasurementUnit.METRIC,
    val completedAt: Long = 0L,
    val reportedSeverity: Int? = null,
    val resolved: Boolean = false,
    val analysis: DiagnosticAnalysis? = null,
    /** True when the stored assessment was produced by a different engine/knowledge-base version. */
    val modelVersionChanged: Boolean = false,
    val errorMessage: String? = null
) {
    val hasIssues: Boolean get() = analysis?.rankedIssues?.isNotEmpty() == true
    val safetyAlerts get() = analysis?.safetyAlerts.orEmpty()
}

/**
 * Results state.
 *
 * The analysis comes from the persisted result/alert snapshot ([LoadAssessmentAnalysisUseCase]), so a
 * saved assessment keeps the numbers it was generated with even after the model or knowledge base
 * changes. [ResultsUiState.modelVersionChanged] tells the UI when the stored model version differs
 * from the running build, so regenerated descriptive text is never passed off silently as original.
 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val assessmentRepository: AssessmentRepository,
    private val preferencesRepository: PreferencesRepository,
    private val loadAnalysis: LoadAssessmentAnalysisUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ResultsUiState())
    val state: StateFlow<ResultsUiState> = _state.asStateFlow()

    fun load(assessmentId: String) {
        if (_state.value.assessmentId == assessmentId && _state.value.analysis != null) return
        viewModelScope.launch {
            _state.value = ResultsUiState(isLoading = true, assessmentId = assessmentId)
            try {
                val loaded = loadAnalysis(assessmentId)
                if (loaded == null) {
                    _state.value = ResultsUiState(
                        isLoading = false,
                        assessmentId = assessmentId,
                        errorMessage = "This assessment could not be found on this device."
                    )
                    return@launch
                }
                val assessment = loaded.assessment
                val vehicle = loaded.vehicle
                _state.value = ResultsUiState(
                    isLoading = false,
                    assessmentId = assessmentId,
                    vehicleName = vehicle?.displayName ?: "Unknown vehicle",
                    vehicle = vehicle,
                    typeLabel = assessment.type.label,
                    unit = preferencesRepository.current().measurementUnit,
                    completedAt = assessment.completedAt ?: assessment.startedAt,
                    reportedSeverity = assessment.reportedSeverity,
                    resolved = assessment.resolved,
                    analysis = loaded.analysis,
                    modelVersionChanged = loaded.modelVersionChanged
                )
            } catch (throwable: Throwable) {
                _state.value = ResultsUiState(
                    isLoading = false,
                    assessmentId = assessmentId,
                    errorMessage = "The assessment could not be loaded: " +
                        (throwable.message ?: "unknown error")
                )
            }
        }
    }

    fun setResolved(resolved: Boolean) {
        val id = _state.value.assessmentId
        viewModelScope.launch {
            assessmentRepository.setResolved(id, resolved)
            _state.update { it.copy(resolved = resolved) }
        }
    }
}
