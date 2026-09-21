package com.example.vehicare.presentation.assessment

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.diagnostic.knowledgebase.Question
import com.example.vehicare.domain.diagnostic.knowledgebase.QuestionInput
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.usecase.QuestionnaireEngine
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.SecondaryButton
import com.example.vehicare.ui.components.SelectableCard
import com.example.vehicare.ui.components.SeveritySliderField
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestionnaireUiState(
    val isLoading: Boolean = true,
    val draftId: String = "",
    val questions: List<Question> = emptyList(),
    val answers: Map<String, String> = emptyMap(),
    val currentIndex: Int = 0,
    val finished: Boolean = false
) {
    val currentQuestion: Question?
        get() = questions.getOrNull(currentIndex)
    val stepNumber: Int get() = (currentIndex + 1).coerceAtMost(questions.size.coerceAtLeast(1))
    val totalSteps: Int get() = questions.size
    val isLastStep: Boolean get() = currentIndex >= questions.size - 1
    val answeredCount: Int get() = questions.count { answers.containsKey(it.id) }
    val progress: Float
        get() = if (questions.isEmpty()) 0f else answeredCount.toFloat() / questions.size.toFloat()
}

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val assessmentRepository: AssessmentRepository,
    private val questionnaireEngine: QuestionnaireEngine
) : ViewModel() {

    private val _state = MutableStateFlow(QuestionnaireUiState())
    val state: StateFlow<QuestionnaireUiState> = _state.asStateFlow()

    private var selectedCategoryIds: Set<String> = emptySet()

    /** The single in-flight draft write; a newer answer cancels and replaces it (latest wins). */
    private var persistJob: Job? = null

    /** Loads the persisted draft so progress survives back navigation and process death. */
    fun start(onNoDraft: () -> Unit) {
        if (_state.value.draftId.isNotBlank()) return
        viewModelScope.launch {
            val draft = assessmentRepository.observeDraft().first()
            if (draft == null) {
                _state.value = QuestionnaireUiState(isLoading = false)
                onNoDraft()
                return@launch
            }
            selectedCategoryIds = draft.selectedCategoryIds.toSet()
            val questions = questionnaireEngine.visibleQuestions(draft.answers, selectedCategoryIds)
            _state.value = QuestionnaireUiState(
                isLoading = false,
                draftId = draft.id,
                questions = questions,
                answers = draft.answers,
                currentIndex = firstUnansweredIndex(questions, draft.answers)
            )
        }
    }

    private fun firstUnansweredIndex(questions: List<Question>, answers: Map<String, String>): Int {
        val index = questions.indexOfFirst { !answers.containsKey(it.id) }
        return if (index < 0) (questions.size - 1).coerceAtLeast(0) else index
    }

    fun selectOption(optionId: String) {
        val question = _state.value.currentQuestion ?: return
        when (question.input) {
            QuestionInput.MULTI -> {
                val current = _state.value.answers[question.id].orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toMutableSet()
                if (optionId in current) current.remove(optionId) else current.add(optionId)
                persist(question.id, current.joinToString(","))
            }

            else -> persist(question.id, optionId)
        }
    }

    fun setSeverity(value: Int) {
        val question = _state.value.currentQuestion ?: return
        persist(question.id, value.toString())
    }

    fun skipCurrent() {
        advance()
    }

    fun next() {
        advance()
    }

    fun previous() {
        _state.value = _state.value.copy(
            currentIndex = (_state.value.currentIndex - 1).coerceAtLeast(0)
        )
    }

    private fun advance() {
        val current = _state.value
        val nextIndex = current.currentIndex + 1
        if (nextIndex >= current.questions.size) {
            _state.value = current.copy(finished = true)
        } else {
            _state.value = current.copy(currentIndex = nextIndex)
        }
    }

    /**
     * Clears the one-shot [QuestionnaireUiState.finished] flag after the screen has handed over to
     * processing, so returning to the questionnaire (e.g. "Back to questions") does not immediately
     * re-trigger the finished navigation.
     */
    fun acknowledgeFinished() {
        if (_state.value.finished) {
            _state.value = _state.value.copy(finished = false)
        }
    }

    /** Writes the answer into the Room draft immediately, so nothing is lost on process death. */
    private fun persist(questionId: String, value: String) {
        val updatedAnswers = _state.value.answers + (questionId to value)
        val questions = questionnaireEngine.visibleQuestions(updatedAnswers, selectedCategoryIds)
        _state.value = _state.value.copy(
            answers = updatedAnswers,
            questions = questions,
            currentIndex = _state.value.currentIndex.coerceAtMost((questions.size - 1).coerceAtLeast(0))
        )
        val draftId = _state.value.draftId
        if (draftId.isBlank()) return
        // Serialize writes: each snapshot is cumulative, so cancelling the previous write and
        // persisting only the latest one prevents an older read-modify-write from clobbering it.
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            val draft = assessmentRepository.getAssessment(draftId) ?: return@launch
            assessmentRepository.saveDraft(
                draft.copy(
                    answers = updatedAnswers,
                    presentEvidenceIds = questionnaireEngine.presentEvidence(updatedAnswers),
                    absentEvidenceIds = questionnaireEngine.absentEvidence(updatedAnswers),
                    reportedSeverity = questionnaireEngine.reportedSeverity(updatedAnswers),
                    onsetId = questionnaireEngine.onsetId(updatedAnswers),
                    selectedCategoryIds = selectedCategoryIds.toList()
                )
            )
        }
    }

    /** Waits for the last draft write to land before the analysis reads the draft. */
    suspend fun flushPendingPersistence() {
        persistJob?.join()
    }
}

/**
 * Guided symptom questionnaire (Section 5.9): one step at a time, with a progress bar, "Step X of N",
 * a back button, skip for optional questions and immediate local persistence of every answer.
 */
@Composable
fun QuestionnaireScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onNoDraft: () -> Unit,
    viewModel: QuestionnaireViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.start(onNoDraft) }
    androidx.compose.runtime.LaunchedEffect(state.finished) {
        if (state.finished) {
            viewModel.flushPendingPersistence()
            onFinished()
            viewModel.acknowledgeFinished()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VehiCareTopBar(
                title = "Vehicle Symptoms",
                subtitle = if (state.totalSteps > 0) {
                    "Step ${state.stepNumber} of ${state.totalSteps}"
                } else {
                    null
                },
                onBack = { if (state.currentIndex > 0) viewModel.previous() else onBack() }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }
            return@Scaffold
        }

        val question = state.currentQuestion
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ProgressHeader(progress = state.progress, answered = state.answeredCount, total = state.totalSteps)

            if (question == null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "No questions are available for this assessment.",
                        modifier = Modifier.padding(Dimens.ScreenPadding)
                    )
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
            ) {
                item {
                    VehiCareCard {
                        Text(question.prompt, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Dimens.SpaceSm))
                        Text(
                            text = question.helpText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when (question.input) {
                    QuestionInput.SLIDER -> item {
                        VehiCareCard {
                            SeveritySliderField(
                                value = state.answers[question.id]?.toIntOrNull() ?: 3,
                                onValueChange = viewModel::setSeverity
                            )
                        }
                    }

                    else -> items(question.options, key = { it.id }) { option ->
                        val selectedIds = state.answers[question.id].orEmpty()
                            .split(",")
                            .map { it.trim() }
                        val selected = option.id in selectedIds
                        SelectableCard(
                            title = option.label,
                            description = if (question.input == QuestionInput.MULTI) {
                                "Tap to ${if (selected) "remove" else "add"} this answer"
                            } else {
                                "Tap to choose this answer"
                            },
                            selected = selected,
                            icon = if (selected) Icons.Default.Check else null,
                            onClick = { viewModel.selectOption(option.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(Dimens.SpaceMd)) }
            }

            Column(modifier = Modifier.padding(Dimens.ScreenPadding)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                    SecondaryButton(
                        text = if (state.currentIndex == 0) "Back" else "Previous",
                        onClick = { if (state.currentIndex > 0) viewModel.previous() else onBack() },
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = if (state.isLastStep) "Analyse Symptoms" else "Next",
                        onClick = viewModel::next,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (question.optional) {
                    TextButton(
                        onClick = viewModel::skipCurrent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skip this question (it will count as unknown, not as a no)")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(progress: Float, answered: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceSm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.SpaceSm)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(Dimens.SpaceSm)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.height(Dimens.SpaceXs))
        Text(
            text = "$answered of $total answered",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
