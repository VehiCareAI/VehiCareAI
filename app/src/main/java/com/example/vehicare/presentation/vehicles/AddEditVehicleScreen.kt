package com.example.vehicare.presentation.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.model.FuelType
import com.example.vehicare.domain.model.Transmission
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.model.VehicleType
import com.example.vehicare.domain.repository.VehicleRepository
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.SecondaryButton
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareDropdownField
import com.example.vehicare.ui.components.VehiCareTextField
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Form state for Add / Edit Vehicle (Section 5.5). */
data class VehicleFormState(
    val vehicleId: String = "",
    val isLoading: Boolean = false,
    val nickname: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val vehicleType: String = VehicleType.SEDAN,
    val fuelType: String = FuelType.GASOLINE,
    val transmission: String = Transmission.AUTOMATIC,
    val engineDisplacement: String = "",
    val mileage: String = "",
    val licensePlate: String = "",
    val vin: String = "",
    val notes: String = "",
    val isSample: Boolean = false,
    val createdAt: Long = 0L,
    val showErrors: Boolean = false,
    val savedVehicleId: String? = null
) {
    val isEditing: Boolean get() = vehicleId.isNotBlank()

    val makeError: String? get() = when {
        !showErrors -> null
        make.isBlank() -> "Make is required"
        else -> null
    }
    val modelError: String? get() = when {
        !showErrors -> null
        model.isBlank() -> "Model is required"
        else -> null
    }
    val yearError: String? get() = when {
        !showErrors -> null
        year.isBlank() -> "Year is required"
        year.toIntOrNull() == null -> "Enter a four digit year"
        year.toIntOrNull() !in Vehicle.YEAR_RANGE ->
            "Year must be between ${Vehicle.YEAR_RANGE.first} and ${Vehicle.YEAR_RANGE.last}"
        else -> null
    }
    val mileageError: String? get() = when {
        !showErrors -> null
        mileage.isBlank() -> null
        mileage.toIntOrNull() == null -> "Enter mileage as a whole number"
        (mileage.toIntOrNull() ?: 0) < 0 -> "Mileage cannot be negative"
        else -> null
    }
    val isValid: Boolean
        get() = makeError == null && modelError == null && yearError == null && mileageError == null
}

@HiltViewModel
class VehicleFormViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VehicleFormState())
    val state: StateFlow<VehicleFormState> = _state.asStateFlow()

    fun load(vehicleId: String?) {
        if (vehicleId == null) {
            _state.update { VehicleFormState(isLoading = false) }
            return
        }
        if (_state.value.vehicleId == vehicleId) return
        viewModelScope.launch {
            val vehicle = vehicleRepository.getVehicle(vehicleId)
            if (vehicle == null) {
                _state.update { VehicleFormState(isLoading = false) }
            } else {
                _state.value = VehicleFormState(
                    vehicleId = vehicle.id,
                    nickname = vehicle.nickname,
                    make = vehicle.make,
                    model = vehicle.model,
                    year = vehicle.year.takeIf { it > 0 }?.toString() ?: "",
                    vehicleType = vehicle.vehicleType,
                    fuelType = vehicle.fuelType,
                    transmission = vehicle.transmission,
                    engineDisplacement = vehicle.engineDisplacement,
                    mileage = vehicle.mileageKm?.toString() ?: "",
                    licensePlate = vehicle.licensePlate,
                    vin = vehicle.vin,
                    notes = vehicle.notes,
                    isSample = vehicle.isSample,
                    createdAt = vehicle.createdAt
                )
            }
        }
    }

    fun onNicknameChange(value: String) = _state.update { it.copy(nickname = value) }
    fun onMakeChange(value: String) = _state.update { it.copy(make = value) }
    fun onModelChange(value: String) = _state.update { it.copy(model = value) }
    fun onYearChange(value: String) = _state.update { it.copy(year = value.take(4).filter { c -> c.isDigit() }) }
    fun onTypeChange(value: String) = _state.update { it.copy(vehicleType = value) }
    fun onFuelChange(value: String) = _state.update { it.copy(fuelType = value) }
    fun onTransmissionChange(value: String) = _state.update { it.copy(transmission = value) }
    fun onEngineChange(value: String) = _state.update { it.copy(engineDisplacement = value) }
    fun onMileageChange(value: String) = _state.update { it.copy(mileage = value.filter { c -> c.isDigit() }) }
    fun onPlateChange(value: String) = _state.update { it.copy(licensePlate = value) }
    fun onVinChange(value: String) = _state.update { it.copy(vin = value) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    /** Validates and saves. Returns the stored vehicle id through [VehicleFormState.savedVehicleId]. */
    fun save() {
        _state.update { it.copy(showErrors = true) }
        val current = _state.value
        if (!current.isValid) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val vehicle = Vehicle(
                id = current.vehicleId,
                nickname = current.nickname.trim(),
                make = current.make.trim(),
                model = current.model.trim(),
                year = current.year.toIntOrNull() ?: 0,
                vehicleType = current.vehicleType,
                fuelType = current.fuelType,
                transmission = current.transmission,
                engineDisplacement = current.engineDisplacement.trim(),
                mileageKm = current.mileage.toIntOrNull(),
                licensePlate = current.licensePlate.trim(),
                vin = current.vin.trim(),
                notes = current.notes.trim(),
                isSample = current.isSample,
                createdAt = if (current.createdAt == 0L) now else current.createdAt,
                updatedAt = now
            )
            val savedId = vehicleRepository.save(vehicle)
            _state.update { it.copy(savedVehicleId = savedId) }
        }
    }

    fun consumeSaved() = _state.update { it.copy(savedVehicleId = null) }
}

/**
 * Add / Edit Vehicle form with three clearly separated sections and field-level validation.
 * Input survives temporary navigation because it lives in the ViewModel until saved or cancelled.
 */
@Composable
fun AddEditVehicleScreen(
    vehicleId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: VehicleFormViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(vehicleId) { viewModel.load(vehicleId) }

    LaunchedEffect(state.savedVehicleId) {
        state.savedVehicleId?.let { savedId ->
            // Navigate immediately; the destination (vehicle detail or the previous screen) is the
            // feedback. Waiting on a snackbar here blocked the user for several seconds.
            viewModel.consumeSaved()
            onSaved(savedId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VehiCareTopBar(
                title = if (state.isEditing) "Edit Vehicle" else "Add Vehicle",
                subtitle = "Only make, model and year are required.",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding)
        ) {
            SectionHeader(title = "A. Basic information")
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareTextField(
                value = state.nickname,
                onValueChange = viewModel::onNicknameChange,
                label = "Nickname (optional)",
                placeholder = "Family car",
                helperText = "Shown instead of the make and model when set.",
                leadingIcon = Icons.Default.Tag
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareTextField(
                value = state.make,
                onValueChange = viewModel::onMakeChange,
                label = "Make",
                placeholder = "Toyota",
                leadingIcon = Icons.Default.DirectionsCar,
                errorMessage = state.makeError
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareTextField(
                value = state.model,
                onValueChange = viewModel::onModelChange,
                label = "Model",
                placeholder = "Vios",
                errorMessage = state.modelError
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareTextField(
                value = state.year,
                onValueChange = viewModel::onYearChange,
                label = "Model year",
                placeholder = "2020",
                leadingIcon = Icons.Default.CalendarMonth,
                keyboardType = KeyboardType.Number,
                errorMessage = state.yearError
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareDropdownField(
                label = "Vehicle type",
                options = VehicleType.all,
                selected = state.vehicleType,
                onSelect = viewModel::onTypeChange
            )

            Spacer(Modifier.height(Dimens.SpaceXl))
            SectionHeader(title = "B. Specifications")
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareDropdownField(
                label = "Fuel type",
                options = FuelType.all,
                selected = state.fuelType,
                onSelect = viewModel::onFuelChange,
                leadingIcon = Icons.Default.LocalGasStation
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareDropdownField(
                label = "Transmission",
                options = Transmission.all,
                selected = state.transmission,
                onSelect = viewModel::onTransmissionChange,
                leadingIcon = Icons.Default.Settings
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareTextField(
                value = state.engineDisplacement,
                onValueChange = viewModel::onEngineChange,
                label = "Engine displacement (optional)",
                placeholder = "1.5L",
                leadingIcon = Icons.Default.Speed
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareTextField(
                value = state.mileage,
                onValueChange = viewModel::onMileageChange,
                label = "Mileage in km (optional)",
                placeholder = "68500",
                keyboardType = KeyboardType.Number,
                errorMessage = state.mileageError
            )

            Spacer(Modifier.height(Dimens.SpaceXl))
            SectionHeader(title = "C. Additional details")
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareTextField(
                value = state.licensePlate,
                onValueChange = viewModel::onPlateChange,
                label = "License plate (optional)",
                placeholder = "ABC 1234",
                leadingIcon = Icons.Default.Badge,
                helperText = "Stored only on this device."
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareTextField(
                value = state.vin,
                onValueChange = viewModel::onVinChange,
                label = "VIN (optional)",
                helperText = "Never required - skip it if you prefer."
            )
            Spacer(Modifier.height(Dimens.SpaceMd))
            VehiCareTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = "Notes (optional)",
                placeholder = "Anything worth remembering about this vehicle",
                leadingIcon = Icons.Default.Notes,
                singleLine = false,
                minLines = 3
            )

            Spacer(Modifier.height(Dimens.SpaceXl))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = if (state.isEditing) "Save Changes" else "Save Vehicle",
                    onClick = viewModel::save,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Dimens.SpaceXl))
            VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = "Privacy",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(Dimens.SpaceXs))
                Text(
                    text = "VehiCare AI stores everything locally on this device. No account, email or " +
                        "phone number is ever requested, and the app works fully offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Dimens.SpaceXl))
        }
    }
}
