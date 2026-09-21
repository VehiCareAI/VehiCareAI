package com.example.vehicare.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.domain.repository.VehicleRepository
import com.example.vehicare.ui.components.ConfirmationDialog
import com.example.vehicare.ui.components.IconTile
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.StatTile
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val notificationsEnabled: Boolean = true,
    val measurementUnit: MeasurementUnit = MeasurementUnit.METRIC,
    val vehicleCount: Int = 0,
    val assessmentCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val vehicleRepository: VehicleRepository,
    private val assessmentRepository: AssessmentRepository
) : ViewModel() {

    val state: StateFlow<ProfileUiState> = combine(
        preferencesRepository.preferences,
        vehicleRepository.observeVehicles(),
        assessmentRepository.observeSummaries()
    ) { preferences, vehicles, summaries ->
        ProfileUiState(
            isLoading = false,
            displayName = preferences.displayName,
            notificationsEnabled = preferences.notificationsEnabled,
            measurementUnit = preferences.measurementUnit,
            vehicleCount = vehicles.size,
            assessmentCount = summaries.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setDisplayName(name: String) {
        viewModelScope.launch { preferencesRepository.setDisplayName(name) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotificationsEnabled(enabled) }
    }

    fun setMeasurementUnit(unit: MeasurementUnit) {
        viewModelScope.launch { preferencesRepository.setMeasurementUnit(unit) }
    }

    /** Privacy and Data: deletes every local record (vehicles, assessments, results, preferences). */
    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            assessmentRepository.deleteAll()
            vehicleRepository.deleteAll()
            preferencesRepository.clearAll()
            onDone()
        }
    }
}

/** Profile and info hub (Section 5.17): no account, no email, no password anywhere. */
@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onNotifications: () -> Unit,
    onDiagnosticMethod: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Delete all local data?",
            message = "This permanently removes every vehicle, assessment, report and preference stored on " +
                "this device. There is no cloud copy, so it cannot be recovered.",
            confirmText = "Delete everything",
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteAllData {
                    scope.launch { snackbarHostState.showSnackbar("All local data deleted") }
                }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { VehiCareTopBar(title = "Profile") }
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
                VehiCareCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(Dimens.AvatarSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.displayName.trim().firstOrNull()?.uppercase() ?: "VC",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(Dimens.SpaceLg))
                        Column {
                            Text(
                                text = state.displayName.ifBlank { "Local user" },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.height(Dimens.SpaceXs))
                            Text(
                                text = "Stored only on this device · no account required",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                    StatTile(
                        label = "Vehicles",
                        value = state.vehicleCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Assessments",
                        value = state.assessmentCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { SectionHeader(title = "Settings") }
            item {
                VehiCareCard {
                    SettingsRow(
                        icon = Icons.Default.Badge,
                        title = "Edit profile",
                        subtitle = if (state.displayName.isBlank()) {
                            "Add an optional local display name"
                        } else {
                            "Change or clear your display name"
                        },
                        onClick = onEditProfile
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        title = "Notification preferences",
                        subtitle = "Reminder information shown in the app",
                        onClick = onNotifications
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Default.Straighten,
                        title = "Measurement units",
                        subtitle = state.measurementUnit.label,
                        onClick = {
                            val next = if (state.measurementUnit == MeasurementUnit.METRIC) {
                                MeasurementUnit.IMPERIAL
                            } else {
                                MeasurementUnit.METRIC
                            }
                            viewModel.setMeasurementUnit(next)
                        },
                        trailingText = "Switch"
                    )
                }
            }

            item { SectionHeader(title = "Diagnostics") }
            item {
                VehiCareCard {
                    SettingsRow(
                        icon = Icons.Default.Psychology,
                        title = "Diagnostic method information",
                        subtitle = "How Bayesian inference produces these estimates",
                        onClick = onDiagnosticMethod
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Default.Insights,
                        title = "Reminder information",
                        subtitle = "Local reminders derived from your assessments",
                        onClick = onNotifications
                    )
                }
            }

            item { SectionHeader(title = "Privacy and legal") }
            item {
                VehiCareCard {
                    SettingsRow(
                        icon = Icons.Default.PrivacyTip,
                        title = "Privacy and data",
                        subtitle = "Local storage explained, delete all data",
                        onClick = onPrivacy
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Default.Description,
                        title = "Terms and disclaimer",
                        subtitle = "Preliminary assessment wording",
                        onClick = onTerms
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "About VehiCare AI",
                        subtitle = "Version, thesis context, knowledge base version",
                        onClick = onAbout
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Default.HelpOutline,
                        title = "Help and FAQs",
                        subtitle = "Common questions about symptoms and results",
                        onClick = onHelp
                    )
                }
            }

            item { SectionHeader(title = "Data control") }
            item {
                VehiCareCard(onClick = { showDeleteDialog = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            icon = Icons.Default.DeleteForever,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(Dimens.SpaceLg))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delete all data", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Removes everything VehiCare AI stored locally on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "VehiCare AI is a diagnostic aid, not a replacement for professional inspection. " +
                            "All results are preliminary probability estimates based on the symptoms you report.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(Dimens.SpaceXl)) }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingText: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon = icon)
        Spacer(Modifier.width(Dimens.SpaceLg))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(Dimens.SpaceSm))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Dimens.SpaceXs))
    }
}
