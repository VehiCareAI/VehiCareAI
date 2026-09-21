package com.example.vehicare.presentation.assessment

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.diagnostic.models.SymptomCategory
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.SelectableCard
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategorySelectionUiState(
    val isLoading: Boolean = true,
    val draftId: String = "",
    val selectedCategoryIds: Set<String> = emptySet()
)

@HiltViewModel
class CategorySelectionViewModel @Inject constructor(
    private val assessmentRepository: AssessmentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategorySelectionUiState())
    val state: StateFlow<CategorySelectionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val draft = assessmentRepository.observeDraft().first()
            _state.value = CategorySelectionUiState(
                isLoading = false,
                draftId = draft?.id.orEmpty(),
                selectedCategoryIds = draft?.selectedCategoryIds?.toSet().orEmpty()
            )
        }
    }

    /** Multi-select: the user may pick as many systems as they can describe symptoms for. */
    fun toggle(categoryId: String) {
        _state.update { current ->
            val updated = if (categoryId in current.selectedCategoryIds) {
                current.selectedCategoryIds - categoryId
            } else {
                current.selectedCategoryIds + categoryId
            }
            current.copy(selectedCategoryIds = updated)
        }
    }

    fun continueToSymptoms(onReady: () -> Unit, onUnavailable: () -> Unit) {
        val draft = _state.value
        if (draft.draftId.isBlank()) {
            // No draft means there is nothing to continue from (e.g. it was removed); fail visibly
            // instead of leaving the button as a silent no-op.
            onUnavailable()
            return
        }
        viewModelScope.launch {
            val existing = assessmentRepository.getAssessment(draft.draftId)
            if (existing == null) {
                onUnavailable()
                return@launch
            }
            assessmentRepository.saveDraft(
                existing.copy(selectedCategoryIds = draft.selectedCategoryIds.toList())
            )
            onReady()
        }
    }
}

/** Symptom category selection: multi-select cards (Section 5.8). */
@Composable
fun CategorySelectionScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: CategorySelectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VehiCareTopBar(
                title = "Symptom Categories",
                subtitle = "Select every area where you have noticed something.",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }
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
                SectionHeader(title = "Where do you notice problems?")
            }
            items(SymptomCategory.all, key = { it.id }) { category ->
                SelectableCard(
                    title = category.label,
                    description = category.description,
                    selected = category.id in state.selectedCategoryIds,
                    onClick = { viewModel.toggle(category.id) }
                )
            }
            item {
                PrimaryButton(
                    text = "Continue to Symptoms",
                    onClick = { viewModel.continueToSymptoms(onContinue, onBack) }
                )
            }
            item {
                Text(
                    text = "${state.selectedCategoryIds.size} of ${SymptomCategory.all.size} categories selected. " +
                        "Selecting none is allowed - the base questions still run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { Spacer(Modifier.height(Dimens.SpaceXl)) }
        }
    }
}
