package com.example.vehicare.presentation.vehicles

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.model.AssessmentSummary
import com.example.vehicare.domain.model.MaintenanceRecord
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.MaintenanceRepository
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.domain.repository.VehicleRepository
import com.example.vehicare.ui.components.AssessmentSummaryCard
import com.example.vehicare.ui.components.ConfirmationDialog
import com.example.vehicare.ui.components.EmptyState
import com.example.vehicare.ui.components.IconTile
import com.example.vehicare.ui.components.KeyValueRow
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.SampleDataBadge
import com.example.vehicare.ui.components.SecondaryButton
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.StatTile
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareTextField
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.utils.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class VehicleDetailUiState(
    val isLoading: Boolean = true,
    val vehicle: Vehicle? = null,
    val unit: MeasurementUnit = MeasurementUnit.METRIC,
    val assessments: List<AssessmentSummary> = emptyList(),
    val maintenance: List<MaintenanceRecord> = emptyList(),
    val deleteCandidate: Vehicle? = null
) {
    val totalAssessments: Int get() = assessments.size
    val lastAssessment: AssessmentSummary? get() = assessments.maxByOrNull { it.completedAt }
    val openConcerns: Int get() = assessments.count { !it.resolved }
    val mostFrequentSystem: String?
        get() = assessments.groupingBy { it.primarySystem }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.takeIf { it.isNotBlank() && assessments.isNotEmpty() }
}

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val assessmentRepository: AssessmentRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val vehicleId = MutableStateFlow<String?>(null)
    private val deleteCandidate = MutableStateFlow<Vehicle?>(null)
    private val assessmentsFlow = MutableStateFlow<List<AssessmentSummary>>(emptyList())
    private val maintenanceFlow = MutableStateFlow<List<MaintenanceRecord>>(emptyList())
    private var assessmentsJob: Job? = null
    private var maintenanceJob: Job? = null

    private val _state = MutableStateFlow(VehicleDetailUiState())
    val state: StateFlow<VehicleDetailUiState> = _state

    init {
        viewModelScope.launch {
            vehicleId.collect { id ->
                if (id == null) return@collect
                // Cancel the previous vehicle's collectors so switching ids never leaks subscriptions.
                assessmentsJob?.cancel()
                maintenanceJob?.cancel()
                assessmentsJob = launch { assessmentRepository.observeSummariesForVehicle(id).collect { assessmentsFlow.value = it } }
                maintenanceJob = launch { maintenanceRepository.observeForVehicle(id).collect { maintenanceFlow.value = it } }
            }
        }
        viewModelScope.launch {
            combine(
                vehicleId,
                preferencesRepository.preferences,
                assessmentsFlow,
                maintenanceFlow,
                deleteCandidate
            ) { id, preferences, assessments, maintenance, candidate ->
                VehicleDetailUiState(
                    isLoading = false,
                    vehicle = id?.let { vehicleRepository.getVehicle(it) },
                    unit = preferences.measurementUnit,
                    assessments = assessments.sortedByDescending { it.completedAt },
                    maintenance = maintenance.sortedByDescending { it.serviceDate },
                    deleteCandidate = candidate
                )
            }.collect { _state.value = it }
        }
    }

    fun load(id: String) {
        vehicleId.value = id
    }

    /** Selects this vehicle before starting an assessment so setup cannot fall back to another one. */
    fun startAssessment(onReady: (String) -> Unit) {
        val id = vehicleId.value ?: return
        viewModelScope.launch {
            preferencesRepository.setSelectedVehicleId(id)
            onReady(id)
        }
    }

    fun requestDelete() {
        val vehicle = _state.value.vehicle ?: return
        deleteCandidate.value = vehicle
    }

    fun cancelDelete() {
        deleteCandidate.value = null
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        val target = deleteCandidate.value ?: return
        viewModelScope.launch {
            vehicleRepository.delete(target.id)
            deleteCandidate.value = null
            onDeleted()
        }
    }

    fun addMaintenance(serviceType: String, dateIso: String, mileage: String, cost: String, notes: String) {
        val id = vehicleId.value ?: return
        viewModelScope.launch {
            maintenanceRepository.save(
                MaintenanceRecord(
                    vehicleId = id,
                    serviceType = serviceType,
                    // A service date is a calendar date, so anchor it to local midnight; storing it as
                    // UTC midnight would display as the previous day in timezones west of UTC.
                    serviceDate = runCatching {
                        LocalDate.parse(dateIso)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }.getOrElse { System.currentTimeMillis() },
                    mileageKm = mileage.toIntOrNull(),
                    cost = cost.toDoubleOrNull(),
                    notes = notes
                )
            )
        }
    }

    fun deleteMaintenance(recordId: String) {
        viewModelScope.launch { maintenanceRepository.delete(recordId) }
    }
}

/**
 * Vehicle Details (Section 5.6): overview, quick actions, summary metrics, assessment history and an
 * optional maintenance log. Deletion is confirmed and cascades to this vehicle's assessments.
 */
@Composable
fun VehicleDetailScreen(
    vehicleId: String,
    onBack: () -> Unit,
    onNewAssessment: (String) -> Unit,
    onEditVehicle: (String) -> Unit,
    onOpenReports: () -> Unit,
    onOpenResults: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: VehicleDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showMaintenanceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vehicleId) { viewModel.load(vehicleId) }

    state.deleteCandidate?.let { candidate ->
        ConfirmationDialog(
            title = "Delete ${candidate.displayName}?",
            message = "This permanently deletes the vehicle and its ${state.totalAssessments} " +
                "assessment${if (state.totalAssessments == 1) "" else "s"}. This cannot be undone.",
            confirmText = "Delete",
            onConfirm = { viewModel.confirmDelete(onDeleted) },
            onDismiss = { viewModel.cancelDelete() }
        )
    }

    if (showMaintenanceDialog) {
        MaintenanceDialog(
            onDismiss = { showMaintenanceDialog = false },
            onSave = { type, date, mileage, cost, notes ->
                viewModel.addMaintenance(type, date, mileage, cost, notes)
                showMaintenanceDialog = false
                scope.launch { snackbarHostState.showSnackbar("Maintenance record added") }
            }
        )
    }

    val vehicle = state.vehicle

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VehiCareTopBar(
                title = vehicle?.displayName ?: "Vehicle",
                subtitle = vehicle?.let { "${it.year} · ${it.fuelType} · ${it.transmission}" },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Vehicle options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit vehicle") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditVehicle(vehicleId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View reports") },
                            leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenReports()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete vehicle") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.requestDelete()
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }
            return@Scaffold
        }
        if (vehicle == null) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Default.Build,
                    title = "Vehicle not found",
                    message = "This vehicle may have been deleted on this device.",
                    actionText = "Back",
                    onAction = onBack
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
                VehiCareCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon = Icons.Default.Build)
                        Spacer(Modifier.padding(Dimens.SpaceSm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(vehicle.displayName, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(Dimens.SpaceXs))
                            Text(
                                text = "${vehicle.year} · ${vehicle.fuelType} · ${vehicle.transmission}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (vehicle.isSample) SampleDataBadge()
                    }
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    KeyValueRow(label = "Mileage", value = Formats.mileage(vehicle.mileageKm, state.unit))
                    KeyValueRow(label = "Vehicle type", value = vehicle.vehicleType)
                    KeyValueRow(
                        label = "Engine",
                        value = vehicle.engineDisplacement.ifBlank { "Not provided" }
                    )
                    KeyValueRow(
                        label = "License plate",
                        value = vehicle.licensePlate.ifBlank { "Not provided" }
                    )
                    if (vehicle.notes.isNotBlank()) {
                        Spacer(Modifier.height(Dimens.SpaceSm))
                        Text(
                            text = vehicle.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                PrimaryButton(
                    text = "New Assessment",
                    onClick = { viewModel.startAssessment(onNewAssessment) },
                    icon = Icons.Default.PlayArrow
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                    SecondaryButton(
                        text = "Edit Vehicle",
                        onClick = { onEditVehicle(vehicleId) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "View Reports",
                        onClick = onOpenReports,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionHeader(title = "Health summary")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                    StatTile(
                        label = "Assessments",
                        value = state.totalAssessments.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Open concerns",
                        value = state.openConcerns.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                    StatTile(
                        label = "Last assessment",
                        value = state.lastAssessment?.let { Formats.date(it.completedAt) } ?: "None",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Most reported system",
                        value = state.mostFrequentSystem ?: "Not enough data",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionHeader(title = "Assessment history")
            }
            if (state.assessments.isEmpty()) {
                item {
                    VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("No assessments for this vehicle", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(Dimens.SpaceXs))
                        Text(
                            text = "Run a health assessment to build this vehicle's diagnostic history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.assessments, key = { it.assessmentId }) { summary ->
                    AssessmentSummaryCard(
                        summary = summary,
                        showVehicle = false,
                        onClick = { onOpenResults(summary.assessmentId) }
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Maintenance records (optional)",
                    actionText = "Add",
                    onAction = { showMaintenanceDialog = true }
                )
            }
            if (state.maintenance.isEmpty()) {
                item {
                    VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("No maintenance records", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(Dimens.SpaceXs))
                        Text(
                            text = "Log oil changes, tires and repairs to keep a complete service history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.maintenance, key = { it.id }) { record ->
                    VehiCareCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(icon = Icons.Default.Build)
                            Spacer(Modifier.padding(Dimens.SpaceSm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(record.serviceType, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = listOf(
                                        Formats.date(record.serviceDate),
                                        Formats.mileage(record.mileageKm, state.unit),
                                        record.cost?.let { "\$" + Formats.cost(it) }.orEmpty()
                                    ).filter { it.isNotBlank() && it != "Not provided" }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (record.notes.isNotBlank()) {
                                    Text(
                                        text = record.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteMaintenance(record.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete maintenance record")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Dimens.SpaceXl)) }
        }
    }
}

@Composable
private fun MaintenanceDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var serviceType by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var mileage by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    val dateValid = runCatching { LocalDate.parse(date) }.isSuccess

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add maintenance record") },
        text = {
            Column {
                VehiCareTextField(
                    value = serviceType,
                    onValueChange = { serviceType = it },
                    label = "Service type",
                    placeholder = "Oil change",
                    errorMessage = if (showErrors && serviceType.isBlank()) "Service type is required" else null
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                VehiCareTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = "Service date (YYYY-MM-DD)",
                    placeholder = LocalDate.now().toString(),
                    errorMessage = if (showErrors && !dateValid) "Use the format YYYY-MM-DD" else null
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                VehiCareTextField(
                    value = mileage,
                    onValueChange = { mileage = it.filter { c -> c.isDigit() } },
                    label = "Mileage in km (optional)",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                VehiCareTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = "Cost (optional)",
                    keyboardType = KeyboardType.Decimal
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                VehiCareTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes (optional)",
                    singleLine = false,
                    minLines = 2
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                showErrors = true
                if (serviceType.isNotBlank() && dateValid) {
                    onSave(serviceType.trim(), date, mileage, cost, notes)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}
