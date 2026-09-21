package com.example.vehicare.presentation.assessment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.VehicleRepository
import com.example.vehicare.domain.usecase.EvaluateSymptomsUseCase
import com.example.vehicare.ui.components.ErrorState
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stage labels are the real pipeline phases the engine goes through (retrieve -> compare -> score ->
 * rank -> persist). The indicator is indeterminate: no fake percentage is ever shown (Section 5.10).
 */
enum class ProcessingStage(val message: String) {
    REVIEWING("Reviewing reported symptoms…"),
    COMPARING("Comparing symptom patterns…"),
    CALCULATING("Calculating probability estimates…"),
    RANKING("Ranking possible vehicle issues…"),
    PREPARING("Preparing your assessment report…")
}

data class ProcessingUiState(
    val stage: ProcessingStage = ProcessingStage.REVIEWING,
    val completedAssessmentId: String? = null,
    val blockedMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    private val assessmentRepository: AssessmentRepository,
    private val vehicleRepository: VehicleRepository,
    private val evaluateSymptoms: EvaluateSymptomsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ProcessingUiState())
    val state: StateFlow<ProcessingUiState> = _state.asStateFlow()

    private var started = false

    fun run() {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                _state.value = ProcessingUiState(stage = ProcessingStage.REVIEWING)
                val draft = assessmentRepository.observeDraft().first()
                if (draft == null) {
                    _state.value = ProcessingUiState(
                        blockedMessage = "This assessment is no longer available. Please start a new one."
                    )
                    return@launch
                }
                if (draft.answers.isEmpty()) {
                    _state.value = ProcessingUiState(
                        blockedMessage = "No symptoms were reported, so there is nothing to analyse yet. " +
                            "Answer at least three questions to receive a preliminary assessment."
                    )
                    return@launch
                }

                delay(STAGE_DELAY_MILLIS)
                _state.value = ProcessingUiState(stage = ProcessingStage.COMPARING)
                val vehicle = vehicleRepository.getVehicle(draft.vehicleId)

                delay(STAGE_DELAY_MILLIS)
                _state.value = ProcessingUiState(stage = ProcessingStage.CALCULATING)
                val analysis = evaluateSymptoms(draft, vehicle)

                delay(STAGE_DELAY_MILLIS)
                _state.value = ProcessingUiState(stage = ProcessingStage.RANKING)

                delay(STAGE_DELAY_MILLIS)
                _state.value = ProcessingUiState(stage = ProcessingStage.PREPARING)
                val assessmentId = assessmentRepository.complete(draft, analysis)

                delay(STAGE_DELAY_MILLIS)
                _state.value = ProcessingUiState(
                    stage = ProcessingStage.PREPARING,
                    completedAssessmentId = assessmentId
                )
            } catch (throwable: Throwable) {
                _state.value = ProcessingUiState(
                    errorMessage = "The assessment could not be completed: " +
                        (throwable.message ?: "unknown error")
                )
            }
        }
    }

    fun retry() {
        started = false
        run()
    }

    private companion object {
        /** Short, purely presentational pacing so each real stage is readable. */
        const val STAGE_DELAY_MILLIS = 420L
    }
}

/** Analysis processing screen (Section 5.10). Navigates to results automatically. */
@Composable
fun ProcessingScreen(
    onCompleted: (String) -> Unit,
    onBackToQuestionnaire: () -> Unit,
    onBackToHome: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.run() }
    LaunchedEffect(state.completedAssessmentId) {
        state.completedAssessmentId?.let(onCompleted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.blockedMessage != null -> Column(
                modifier = Modifier.padding(Dimens.ScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Nothing to analyse yet",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                Text(
                    text = state.blockedMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Dimens.SpaceXl))
                PrimaryButton(text = "Back to questions", onClick = onBackToQuestionnaire)
                Spacer(Modifier.height(Dimens.SpaceSm))
                androidx.compose.material3.TextButton(onClick = onBackToHome) {
                    Text("Back to Home")
                }
            }

            state.errorMessage != null -> ErrorState(
                title = "Analysis failed",
                message = state.errorMessage.orEmpty(),
                onRetry = viewModel::retry
            )

            else -> Column(
                modifier = Modifier.padding(Dimens.ScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(Modifier.height(Dimens.SpaceXl))
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Dimens.SpaceXl))
                Text(
                    text = state.stage.message,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Dimens.SpaceSm))
                Text(
                    text = "VehiCare AI is estimating probabilities from the symptoms you reported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
