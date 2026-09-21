package com.example.vehicare.presentation.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.R
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.domain.repository.VehicleRepository
import com.example.vehicare.navigation.Routes
import com.example.vehicare.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Splash: white background, centred logo, name, subtitle and a subtle loading indicator.
 * It loads local preferences and leaves immediately - no artificial delay (Section 5.1).
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<String?>(null)
    val destination: StateFlow<String?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val destination = try {
                val preferences = preferencesRepository.current()
                if (!preferences.onboardingCompleted) {
                    // First launch: make the app explorable immediately using the documented sample
                    // vehicle and sample assessment (Section 9). Seeding is idempotent and only fills
                    // an empty database, so it can never overwrite real records.
                    vehicleRepository.seedIfEmpty()
                }
                if (preferences.onboardingCompleted) Routes.HOME else Routes.ONBOARDING
            } catch (throwable: Throwable) {
                // Startup work must never leave the user on an infinite spinner; Home renders its own
                // explicit, retryable error state if the database is unavailable.
                Routes.HOME
            }
            _destination.value = destination
        }
    }
}

@Composable
fun SplashScreen(
    onDestinationReady: (String) -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        destination?.let(onDestinationReady)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_vehicare_logo),
                contentDescription = "VehiCare AI logo",
                modifier = Modifier.size(132.dp)
            )
            Spacer(Modifier.height(Dimens.SpaceLg))
            Text(
                text = "VehiCare AI",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Dimens.SpaceXs))
            Text(
                text = "Intelligent Vehicle Health Assessment",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.SpaceXl))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Dimens.SpaceLg))
            Text(
                text = "Powered by Bayesian Inference",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
