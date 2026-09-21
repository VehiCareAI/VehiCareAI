package com.example.vehicare.presentation.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.ui.components.ConfirmationDialog
import com.example.vehicare.ui.components.EmptyState
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.components.VehicleSummaryCard
import com.example.vehicare.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * My Vehicles (Section 5.4): every card exposes View Details / Assess Vehicle / More Options, and
 * deletion is confirmed because it also deletes that vehicle's assessments.
 */
@Composable
fun VehiclesScreen(
    onAddVehicle: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    onAssessVehicle: () -> Unit,
    onEditVehicle: (String) -> Unit,
    viewModel: VehicleListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var expandedForVehicleId by remember { mutableStateOf<String?>(null) }

    state.deleteCandidate?.let { candidate ->
        ConfirmationDialog(
            title = "Delete ${candidate.displayName}?",
            message = if (state.deleteCandidateAssessmentCount > 0) {
                "This permanently deletes the vehicle and its ${state.deleteCandidateAssessmentCount} " +
                    "assessment${if (state.deleteCandidateAssessmentCount == 1) "" else "s"}. This cannot be undone."
            } else {
                "This permanently deletes the vehicle. This cannot be undone."
            },
            confirmText = "Delete",
            onConfirm = {
                viewModel.confirmDelete { name ->
                    scope.launch { snackbarHostState.showSnackbar("$name deleted") }
                }
            },
            onDismiss = { viewModel.cancelDelete() }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VehiCareTopBar(
                title = "My Vehicles",
                subtitle = "Manage your vehicles and their health records.",
                actions = {
                    IconButton(onClick = onAddVehicle) {
                        Icon(Icons.Default.Add, contentDescription = "Add vehicle")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddVehicle,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Vehicle") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(modifier = Modifier.padding(innerPadding)) { LoadingState() }

            state.isEmpty -> Column(modifier = Modifier.padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Default.DirectionsCar,
                    title = "Your garage is empty",
                    message = "Add your first vehicle to begin tracking its health.",
                    actionText = "Add Your First Vehicle",
                    onAction = onAddVehicle
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    start = Dimens.ScreenPadding,
                    end = Dimens.ScreenPadding,
                    top = Dimens.SpaceMd,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
            ) {
                item {
                    Text(
                        text = "${state.vehicles.size} vehicle${if (state.vehicles.size == 1) "" else "s"} in your garage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(state.vehicles, key = { it.id }) { vehicle ->
                    VehicleRow(
                        vehicle = vehicle,
                        unit = state.unit,
                        menuExpanded = expandedForVehicleId == vehicle.id,
                        onMenuExpandChange = { expanded ->
                            expandedForVehicleId = if (expanded) vehicle.id else null
                        },
                        onOpen = { onOpenVehicle(vehicle.id) },
                        onAssess = { viewModel.selectVehicle(vehicle.id, onAssessVehicle) },
                        onEdit = { onEditVehicle(vehicle.id) },
                        onDelete = { viewModel.requestDelete(vehicle) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleRow(
    vehicle: Vehicle,
    unit: com.example.vehicare.domain.model.MeasurementUnit,
    menuExpanded: Boolean,
    onMenuExpandChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onAssess: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        VehicleSummaryCard(
            vehicle = vehicle,
            unit = unit,
            onClick = onOpen,
            trailing = {
                IconButton(onClick = { onMenuExpandChange(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for ${vehicle.displayName}")
                }
            }
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpandChange(false) }) {
            DropdownMenuItem(
                text = { Text("View details") },
                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                onClick = {
                    onMenuExpandChange(false)
                    onOpen()
                }
            )
            DropdownMenuItem(
                text = { Text("Assess vehicle") },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                onClick = {
                    onMenuExpandChange(false)
                    onAssess()
                }
            )
            DropdownMenuItem(
                text = { Text("Edit vehicle") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    onMenuExpandChange(false)
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete vehicle") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    onMenuExpandChange(false)
                    onDelete()
                }
            )
        }
    }
    Spacer(Modifier.size(Dimens.SpaceXs))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpen, modifier = Modifier.size(Dimens.MinTouchTarget)) {
            Icon(Icons.Default.Visibility, contentDescription = "View details for ${vehicle.displayName}")
        }
        IconButton(onClick = onAssess, modifier = Modifier.size(Dimens.MinTouchTarget)) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Assess ${vehicle.displayName}")
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(Dimens.MinTouchTarget)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit ${vehicle.displayName}")
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.MinTouchTarget)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${vehicle.displayName}")
        }
    }
}
