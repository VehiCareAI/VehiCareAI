package com.example.vehicare.presentation.profile

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.vehicare.domain.diagnostic.bayesian.NaiveBayesDiagnosticEngine
import com.example.vehicare.domain.diagnostic.knowledgebase.VehiCareKnowledgeBase
import com.example.vehicare.domain.model.InfoNotice
import com.example.vehicare.domain.repository.AssessmentRepository
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.ui.components.ConfirmationDialog
import com.example.vehicare.ui.components.DisclaimerNote
import com.example.vehicare.ui.components.IconTextRow
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.components.SectionHeader
import com.example.vehicare.ui.components.VehiCareCard
import com.example.vehicare.ui.components.VehiCareTextField
import com.example.vehicare.ui.components.VehiCareTopBar
import com.example.vehicare.ui.theme.Dimens
import com.example.vehicare.utils.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------------------------
// Edit profile (optional local display name - never an email address)
// ---------------------------------------------------------------------------------------------

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var name by remember(state.displayName) { mutableStateOf(state.displayName) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { VehiCareTopBar(title = "Edit Profile", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.ScreenPadding)
        ) {
            VehiCareCard {
                Text("Display name", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimens.SpaceXs))
                Text(
                    text = "Optional and stored only on this device. VehiCare AI never asks for an email " +
                        "address, password, or phone number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Dimens.SpaceLg))
                VehiCareTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Display name",
                    placeholder = "Alex"
                )
                Spacer(Modifier.height(Dimens.SpaceLg))
                PrimaryButton(
                    text = "Save",
                    onClick = {
                        viewModel.setDisplayName(name.trim())
                        scope.launch { snackbarHostState.showSnackbar("Profile updated") }
                    }
                )
                Spacer(Modifier.height(Dimens.SpaceSm))
                androidx.compose.material3.TextButton(
                    onClick = {
                        name = ""
                        viewModel.setDisplayName("")
                        scope.launch { snackbarHostState.showSnackbar("Display name cleared") }
                    }
                ) {
                    Text("Clear display name")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Notification preferences / local reminder information
// ---------------------------------------------------------------------------------------------

data class NotificationsUiState(
    val enabled: Boolean = true,
    val notices: List<InfoNotice> = emptyList()
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    assessmentRepository: AssessmentRepository
) : ViewModel() {

    val state: StateFlow<NotificationsUiState> = combine(
        preferencesRepository.preferences,
        assessmentRepository.observeSummaries()
    ) { preferences, summaries ->
        val notices = mutableListOf<InfoNotice>()
        summaries.filter { it.hasSafetyAlerts }.maxByOrNull { it.completedAt }?.let { flagged ->
            notices += InfoNotice(
                id = "safety",
                title = "Safety alert in the assessment for ${flagged.vehicleName}",
                message = "Review the safety guidance in that report before further driving."
            )
        }
        val open = summaries.count { !it.resolved }
        if (open > 0) {
            notices += InfoNotice(
                id = "open",
                title = "$open open assessment${if (open == 1) "" else "s"}",
                message = "Mark an assessment as resolved once the vehicle has been checked."
            )
        }
        val newest = summaries.maxByOrNull { it.completedAt }
        if (newest != null) {
            notices += InfoNotice(
                id = "last",
                title = "Last assessment: ${Formats.date(newest.completedAt)}",
                message = "Vehicle: ${newest.vehicleName} · main symptom: ${newest.mainSymptomLabel}"
            )
        }
        notices += InfoNotice(
            id = "info",
            title = "How reminders work",
            message = "VehiCare AI has no server and sends no background notifications. This list is " +
                "generated on demand from the records stored on this device."
        )
        NotificationsUiState(enabled = preferences.notificationsEnabled, notices = notices)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotificationsEnabled(enabled) }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { VehiCareTopBar(title = "Reminders & Information", onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            item {
                VehiCareCard {
                    IconTextRow(
                        icon = Icons.Default.Notifications,
                        title = "Show reminder information",
                        subtitle = if (state.enabled) "Enabled" else "Disabled",
                        trailing = {
                            Switch(
                                checked = state.enabled,
                                onCheckedChange = viewModel::setEnabled
                            )
                        }
                    )
                }
            }
            item { SectionHeader(title = "Current reminders") }
            if (!state.enabled) {
                item {
                    VehiCareCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = "Reminder information is turned off. Turn it back on to see the " +
                                "on-device reminders generated from your assessments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.notices, key = { it.id }) { notice ->
                    VehiCareCard {
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
            item { Spacer(Modifier.height(Dimens.SpaceXl)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Diagnostic method information (the plain-language Bayesian explanation required by Section 5.17)
// ---------------------------------------------------------------------------------------------

@Composable
fun DiagnosticMethodScreen(onBack: () -> Unit) {
    InfoScreenLayout(
        title = "Diagnostic Method",
        onBack = onBack,
        intro = "How VehiCare AI turns your symptoms into probability estimates."
    ) {
        InfoCard(
            "What the engine does",
            "When you report symptoms, VehiCare AI compares them with a knowledge base of possible " +
                "vehicle issues. Each issue has a starting likelihood (its prior probability) and a " +
                "documented chance of producing each symptom. The engine combines those with what you " +
                "reported to produce a posterior probability for every candidate issue."
        )
        InfoCard(
            "The formula in plain language",
            "P(issue | symptoms) = P(symptoms | issue) x P(issue) / P(symptoms).\n\n" +
                "A common issue can remain likely, but an issue that starts out uncommon can become " +
                "likely if several reported symptoms are consistent with it. Conversely, reporting that " +
                "a symptom is absent makes an issue that would have predicted it less likely."
        )
        InfoCard(
            "Example",
            "An issue may initially be uncommon, but if several reported symptoms are consistent with " +
                "it, its estimated probability may increase. For instance, slow cranking together with " +
                "dim lights and a clicking sound raises an electrical starting issue far above its " +
                "starting likelihood."
        )
        InfoCard(
            "Why percentages are relative",
            "The estimates are normalised across the issues the engine considered, so they add up to " +
                "100% among those possibilities. They are relative estimates, not absolute confidence, " +
                "which is why every screen shows the percentage together with the evidence strength."
        )
        InfoCard(
            "Missing answers",
            "Skipped or \"unsure\" answers are treated as unknown information and do not count as " +
                "evidence that a symptom is absent. Only an explicit \"No\" counts as negative evidence."
        )
        InfoCard(
            "When the evidence is too thin",
            "If too little information is provided, or no issue is meaningfully supported, the engine " +
                "returns \"insufficient evidence\" instead of guessing. It never invents a result."
        )
        InfoCard(
            "Probability and severity are different",
            "Probability describes how well your symptoms match an issue. Severity describes how " +
                "serious that issue would be if confirmed. An uncommon issue can be dangerous, which is " +
                "why severity is shown separately and why safety alerts are evaluated directly from " +
                "symptoms rather than from the ranking."
        )
        InfoCard(
            "Illustrative values",
            "The priors and likelihoods in version ${VehiCareKnowledgeBase.VERSION} of the knowledge " +
                "base are illustrative prototype values written for this thesis prototype. They are not " +
                "validated automotive statistics and have not been calibrated against a workshop dataset."
        )
        InfoCard(
            "Reproducibility",
            "Each assessment stores the engine version (${NaiveBayesDiagnosticEngine.ENGINE_VERSION}) and " +
                "the knowledge base version, so any result can be reproduced exactly."
        )
        DisclaimerNote()
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    InfoScreenLayout(
        title = "About VehiCare AI",
        onBack = onBack,
        intro = "Describe the symptoms. Understand the possibilities. Make informed decisions about " +
            "your vehicle's health."
    ) {
        InfoCard(
            "What this app is",
            "VehiCare AI is a local-first vehicle health assessment assistant for vehicle owners and " +
                "mechanics. It stores everything on your device, works fully offline, and requires no " +
                "account, email address or phone number."
        )
        InfoCard(
            "Thesis context",
            "This build accompanies the work \"VehiCare AI: Development of an Intelligent Vehicle " +
                "Health Assessment and Diagnostic System Using Bayesian Inference for Symptom-Based " +
                "Diagnosis and Probabilistic Issue Ranking\"."
        )
        InfoCard(
            "Model versions",
            "Engine: ${NaiveBayesDiagnosticEngine.ENGINE_VERSION}\nKnowledge base: " +
                "${VehiCareKnowledgeBase.VERSION}\nCandidate hypotheses: " +
                "${VehiCareKnowledgeBase.instance.rankedHypotheses.size}\nEvidence items: " +
                "${VehiCareKnowledgeBase.instance.evidence.size}\nEvidence groups: " +
                "${VehiCareKnowledgeBase.instance.groups.size}"
        )
        InfoCard(
            "What it is not",
            "It is a diagnostic aid. It does not replace professional inspection, and it cannot read " +
                "your vehicle's onboard diagnostics by itself: every result is based on the symptoms you " +
                "report and a documented knowledge base."
        )
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    InfoScreenLayout(
        title = "Help and FAQs",
        onBack = onBack,
        intro = "Short answers to the questions users ask most often."
    ) {
        InfoCard("Do I need an account?", "No. There is no sign-in, no email and no password. Everything is stored locally on this device.")
        InfoCard("How many questions will I answer?", "Usually between eight and fifteen. Follow-up questions appear only when an earlier answer makes them relevant.")
        InfoCard("What if I do not know an answer?", "Skip it or choose \"Unsure\". It will be treated as unknown information rather than as a \"No\".")
        InfoCard("Why is a serious issue ranked low?", "Ranking follows the reported symptoms only. Severity is shown separately, and safety alerts are raised directly from symptoms rather than from the ranking.")
        InfoCard("Can I stop halfway through?", "Yes. Your answers are saved locally as a draft, and the Home screen offers to resume where you left off.")
        InfoCard("Can I delete my data?", "Yes. Profile > Delete all data removes every vehicle, assessment and preference from this device without any cloud copy remaining.")
        InfoCard("Does the app work offline?", "Yes. The app never uses the network; the diagnostic engine runs entirely on the device.")
    }
}

@Composable
fun TermsScreen(onBack: () -> Unit) {
    InfoScreenLayout(
        title = "Terms and Disclaimer",
        onBack = onBack,
        intro = "Please read this before relying on any result."
    ) {
        InfoCard(
            "Preliminary assessment",
            "Every result in VehiCare AI is a preliminary probability estimate produced from the " +
                "symptoms you reported. It is not a confirmed mechanical diagnosis and must not be " +
                "treated as one."
        )
        InfoCard(
            "Professional verification",
            "Always consult a qualified automotive professional for verification, especially when " +
                "safety-related symptoms are present. Have recommended checks carried out before parts " +
                "are replaced."
        )
        InfoCard(
            "Safety",
            "If you notice a serious vehicle problem, stop driving if necessary and seek professional " +
                "assistance. VehiCare AI provides general guidance only and is not an emergency service."
        )
        InfoCard(
            "No warranty",
            "The knowledge base values are illustrative prototype values written for a thesis " +
                "prototype and have not been validated against a fleet or workshop dataset. Use of the " +
                "app is at your own discretion."
        )
        DisclaimerNote(
            text = com.example.vehicare.ui.components.REPORT_DISCLAIMER
        )
    }
}

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        ConfirmationDialog(
            title = "Delete all local data?",
            message = "Every vehicle, assessment, report and preference stored on this device will be " +
                "permanently removed. There is no cloud copy.",
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
        topBar = { VehiCareTopBar(title = "Privacy and Data", onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            item {
                InfoCard(
                    "Everything stays on this device",
                    "Vehicles, assessments, ranked results, safety alerts and preferences are stored in a " +
                        "local database on your phone. The app contains no networking code and sends nothing " +
                        "anywhere."
                )
            }
            item {
                InfoCard(
                    "What is stored",
                    "Vehicle details you enter (make, model, year, and optionally mileage, plate or VIN), " +
                        "your questionnaire answers, and the probability estimates produced from them. " +
                        "Identifiers such as the license plate or VIN are optional."
                )
            }
            item {
                InfoCard(
                    "What is never collected",
                    "No email address, password, phone number, contacts, location or advertising " +
                        "identifier is requested or stored."
                )
            }
            item {
                InfoCard(
                    "Delete everything",
                    "You can remove all local data at any time. This also clears your preferences, so the " +
                        "app returns to its first-launch state."
                )
            }
            item {
                PrimaryButton(
                    text = "Delete all data",
                    onClick = { showDeleteDialog = true }
                )
            }
            item { Spacer(Modifier.height(Dimens.SpaceXl)) }
        }
    }
}

@Composable
private fun InfoScreenLayout(
    title: String,
    onBack: () -> Unit,
    intro: String,
    content: @Composable () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { VehiCareTopBar(title = title, onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.ScreenPadding)
        ) {
            Text(
                text = intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dimens.SpaceLg))
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
            ) {
                content()
                Spacer(Modifier.height(Dimens.SpaceXl))
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    VehiCareCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.padding(Dimens.SpaceSm))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(Dimens.SpaceSm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
