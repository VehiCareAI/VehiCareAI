package com.example.vehicare.presentation.assessment

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.diagnostic.models.SymptomCategory
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentStatus
import com.example.vehicare.domain.model.AssessmentType
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.domain.repository.VehicleRepository
import com.example.vehicare.ui.components.EmptyState
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.SelectableCard
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.utils.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val isLoading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val unit: MeasurementUnit = MeasurementUnit.METRIC,
    val selectedTypeId: String = AssessmentType.SYMPTOM_BASED.id,
    val draftId: String? = null
) {
    val hasVehicles: Boolean get() = vehicles.isNotEmpty()
}

@HiltViewModel
class AssessmentSetupViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val assessmentRepository: AssessmentRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val selectedVehicleId = MutableStateFlow<String?>(null)
    private val selectedTypeId = MutableStateFlow(AssessmentType.SYMPTOM_BASED.id)
    private val draftId = MutableStateFlow<String?>(null)
    private val ongoingDraft = MutableStateFlow<Assessment?>(null)

    val state: StateFlow<SetupUiState> = combine(
        vehicleRepository.observeVehicles(),
        preferencesRepository.preferences,
        selectedVehicleId,
        selectedTypeId,
        draftId
    ) { vehicles, preferences, vehicleId, typeId, draft ->
        val chosen = vehicles.firstOrNull { it.id == vehicleId }
            ?: vehicles.firstOrNull { it.id == preferences.selectedVehicleId }
            ?: vehicles.firstOrNull()
        SetupUiState(
            isLoading = false,
            vehicles = vehicles,
            selectedVehicle = chosen,
            unit = preferences.measurementUnit,
            selectedTypeId = typeId,
            draftId = draft
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    init {
        viewModelScope.launch {
            ongoingDraft.value = assessmentRepository.observeDraft().first()
            draftId.value = ongoingDraft.value?.id
        }
    }

    fun selectVehicle(vehicleId: String) {
        selectedVehicleId.value = vehicleId
        viewModelScope.launch { preferencesRepository.setSelectedVehicleId(vehicleId) }
    }

    fun selectType(typeId: String) {
        selectedTypeId.value = typeId
    }

    /**
     * Creates (or replaces) the single live draft and hands over to category selection. Replacing a
     * previous draft keeps "resume" unambiguous and avoids orphaned drafts on the device.
     */
    fun continueToCategories(onReady: (String) -> Unit) {
        val current = state.value
        val vehicle = current.selectedVehicle ?: return
        val type = AssessmentType.fromId(current.selectedTypeId)
        val existing = ongoingDraft.value
        // An existing draft can only be resumed when it belongs to the same vehicle; otherwise its
        // answers and resolved evidence would leak into the new assessment.
        val reusable = existing?.takeIf { it.id.isNotBlank() && it.vehicleId == vehicle.id }

        viewModelScope.launch {
            if (existing != null && existing.id.isNotBlank() && existing.vehicleId != vehicle.id) {
                assessmentRepository.delete(existing.id)
            }
            val draft = Assessment(
                id = reusable?.id ?: "",
                vehicleId = vehicle.id,
                assessmentTypeId = type.id,
                selectedCategoryIds = type.presetCategoryIds,
                answers = reusable?.answers ?: emptyMap(),
                presentEvidenceIds = reusable?.presentEvidenceIds ?: emptySet(),
                absentEvidenceIds = reusable?.absentEvidenceIds ?: emptySet(),
                reportedSeverity = reusable?.reportedSeverity,
                onsetId = reusable?.onsetId,
                status = AssessmentStatus.DRAFT,
                disclaimerAccepted = true,
                startedAt = reusable?.startedAt ?: System.currentTimeMillis()
            )
            val id = assessmentRepository.saveDraft(draft)
            draftId.value = id
            ongoingDraft.value = draft.copy(id = id)
            onReady(id)
        }
    }
}

/** Assessment setup: choose the vehicle and the assessment type (Section 5.7). */
@Composable
fun AssessmentSetupScreen(
    onBack: () -> Unit,
    onAddVehicle: () -> Unit,
    onContinue: (String) -> Unit,
    viewModel: AssessmentSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var vehicleMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VehiCareTopBar(
                title = "Start Vehicle Assessment",
                subtitle = "Tell us what your vehicle is experiencing.",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }
            return@Scaffold
        }
        if (!state.hasVehicles) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Default.DirectionsCar,
                    title = "No vehicle to assess",
                    message = "Add a vehicle first so the assessment can be stored against it.",
                    actionText = "Add Vehicle",
                    onAction = onAddVehicle
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
                SectionHeader(title = "Vehicle")
            }
            item {
                Box {
                    VehiCareCard(onClick = { vehicleMenuExpanded = true }) {
                        Text(
                            text = state.selectedVehicle?.displayName ?: "Select a vehicle",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(Dimens.SpaceXs))
                        state.selectedVehicle?.let { vehicle ->
                            Text(
                                text = listOf(
                                    vehicle.year.toString(),
                                    vehicle.fuelType,
                                    Formats.mileage(vehicle.mileageKm, state.unit)
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(Dimens.SpaceSm))
                        TextButton(onClick = { vehicleMenuExpanded = true }) {
                            Text("Switch vehicle")
                        }
                    }
                    DropdownMenu(
                        expanded = vehicleMenuExpanded,
                        onDismissRequest = { vehicleMenuExpanded = false }
                    ) {
                        state.vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = { Text(vehicle.displayName) },
                                leadingIcon = {
                                    if (vehicle.id == state.selectedVehicle?.id) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected")
                                    }
                                },
                                onClick = {
                                    viewModel.selectVehicle(vehicle.id)
                                    vehicleMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Dimens.SpaceSm)) }
            item { SectionHeader(title = "Assessment type") }
            items(AssessmentType.entries.toList(), key = { it.id }) { type ->
                SelectableCard(
                    title = type.label,
                    description = type.description,
                    selected = state.selectedTypeId == type.id,
                    onClick = { viewModel.selectType(type.id) }
                )
            }

            item {
                PrimaryButton(
                    text = "Continue",
                    onClick = { viewModel.continueToCategories(onContinue) },
                    enabled = state.selectedVehicle != null
                )
            }
            item {
                Text(
                    text = "All assessment types use the same Bayesian engine; the type only pre-selects the " +
                        "symptom categories that are most relevant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { Spacer(Modifier.height(Dimens.SpaceXl)) }
        }
    }
}
