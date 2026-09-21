package com.example.vehicare.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vehicare.ui.components.AssessmentSummaryCard
import com.example.vehicare.ui.components.ErrorState
import com.example.vehicare.ui.components.IconTile
import com.example.vehicare.ui.components.LoadingState
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.SafetyBanner
import com.example.vehicare.ui.components.SecondaryButton
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehicleSummaryCard
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.utils.Formats
import java.util.Calendar

/**
 * Home dashboard (Section 5.3). Splash leads straight here; onboarding is optional and never blocks
 * access. Every card, icon and quick action performs a real action.
 */
@Composable
fun HomeScreen(
    onStartAssessment: () -> Unit,
    onResumeAssessment: () -> Unit,
    onAddVehicle: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenResults: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        LoadingState(message = "Loading your garage…")
        return
    }

    state.errorMessage?.let { message ->
        ErrorState(
            title = "Your data could not be loaded",
            message = message,
            onRetry = viewModel::retry
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)
    ) {
        item { Spacer(Modifier.height(Dimens.SpaceSm)) }

        item {
            HomeHeader(
                displayName = state.displayName,
                onOpenProfile = onOpenProfile,
                onOpenNotifications = onOpenNotifications
            )
        }

        if (state.draft != null) {
            item {
                VehiCareCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = "Unfinished assessment",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(Dimens.SpaceXs))
                    Text(
                        text = "Your answers are saved locally. Resume whenever you are ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    PrimaryButton(
                        text = "Resume assessment",
                        onClick = onResumeAssessment,
                        icon = Icons.Default.Refresh
                    )
                }
            }
        }

        item {
            VehiCareCard {
                Text(
                    text = "Vehicle Health Assessment",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(Dimens.SpaceXs))
                Text(
                    text = "Identify possible vehicle issues based on reported symptoms.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                PrimaryButton(
                    text = "Start Assessment",
                    onClick = onStartAssessment,
                    icon = Icons.Default.PlayArrow,
                    enabled = state.hasVehicles
                )
                if (!state.hasVehicles) {
                    Spacer(Modifier.height(Dimens.SpaceSm))
                    Text(
                        text = "Add a vehicle first - assessments always belong to a vehicle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val selectedVehicle = state.selectedVehicle
        if (selectedVehicle == null) {
            item {
                VehiCareCard {
                    Text(
                        text = "Your garage is empty",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(Dimens.SpaceSm))
                    Text(
                        text = "Add your first vehicle to begin tracking its health.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Dimens.SpaceLg))
                    PrimaryButton(
                        text = "Add Your First Vehicle",
                        onClick = onAddVehicle,
                        icon = Icons.Default.Add
                    )
                }
            }
        } else {
            item {
                SectionHeader(title = "Selected vehicle")
            }
            item {
                VehicleSummaryCard(
                    vehicle = selectedVehicle,
                    unit = state.unit,
                    subtitle = state.selectedVehicleLatestAssessment?.let {
                        "Last assessment: ${Formats.date(it.completedAt)} · ${it.issueCount} possible issue${if (it.issueCount == 1) "" else "s"}"
                    } ?: "No assessment recorded yet",
                    onClick = { onOpenVehicle(selectedVehicle.id) }
                )
            }
            item {
                SecondaryButton(
                    text = "View Vehicle",
                    onClick = { onOpenVehicle(selectedVehicle.id) },
                    icon = Icons.Default.DirectionsCar
                )
            }
        }

        item {
            SectionHeader(title = "Quick actions")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
            ) {
                QuickAction(
                    icon = Icons.Default.Add,
                    label = "Add Vehicle",
                    modifier = Modifier.weight(1f),
                    onClick = onAddVehicle
                )
                QuickAction(
                    icon = Icons.Default.PlayArrow,
                    label = "Assess",
                    modifier = Modifier.weight(1f),
                    onClick = onStartAssessment,
                    enabled = state.hasVehicles
                )
                QuickAction(
                    icon = Icons.Default.Insights,
                    label = "Reports",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenReports
                )
                QuickAction(
                    icon = Icons.Default.FactCheck,
                    label = "History",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenHistory
                )
            }
        }

        item {
            SectionHeader(
                title = "Recent assessments",
                actionText = if (state.recentAssessments.isNotEmpty()) "See all" else null,
                onAction = if (state.recentAssessments.isNotEmpty()) onOpenHistory else null
            )
        }

        if (state.recentAssessments.isEmpty()) {
            item {
                VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "No assessments yet",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(Dimens.SpaceXs))
                    Text(
                        text = "Complete your first vehicle health assessment to begin building your diagnostic history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(state.recentAssessments, key = { it.assessmentId }) { summary ->
                AssessmentSummaryCard(summary = summary, onClick = { onOpenResults(summary.assessmentId) })
            }
        }

        if (state.notices.isNotEmpty()) {
            item {
                SectionHeader(title = "Notices")
            }
            items(state.notices, key = { it.id }) { notice ->
                VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(notice.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Dimens.SpaceXs))
                    Text(
                        text = notice.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SafetyBanner()
        }

        item { Spacer(Modifier.height(Dimens.SpaceSm)) }
    }
}

@Composable
private fun HomeHeader(
    displayName: String,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val initial = displayName.trim().firstOrNull()?.uppercase() ?: ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onOpenProfile,
            modifier = Modifier
                .size(Dimens.MinTouchTarget)
                .semantics { contentDescription = "Profile" }
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial.ifBlank { "VC" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.size(Dimens.SpaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (displayName.isBlank()) "$greeting," else "$greeting, $displayName",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Keep your vehicle running with confidence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenNotifications) {
            Icon(Icons.Default.Notifications, contentDescription = "Reminders and information")
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    VehiCareCard(modifier = modifier, onClick = if (enabled) onClick else null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconTile(icon = icon)
            Spacer(Modifier.height(Dimens.SpaceSm))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
