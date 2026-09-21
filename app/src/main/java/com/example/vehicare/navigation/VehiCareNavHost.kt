package com.example.vehicare.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vehicare.presentation.assessment.AssessmentSetupScreen
import com.example.vehicare.presentation.assessment.CategorySelectionScreen
import com.example.vehicare.presentation.assessment.ProcessingScreen
import com.example.vehicare.presentation.assessment.QuestionnaireScreen
import com.example.vehicare.presentation.history.HistoryScreen
import com.example.vehicare.presentation.home.HomeScreen
import com.example.vehicare.presentation.onboarding.OnboardingScreen
import com.example.vehicare.presentation.profile.AboutScreen
import com.example.vehicare.presentation.profile.DiagnosticMethodScreen
import com.example.vehicare.presentation.profile.EditProfileScreen
import com.example.vehicare.presentation.profile.HelpScreen
import com.example.vehicare.presentation.profile.NotificationsScreen
import com.example.vehicare.presentation.profile.PrivacyScreen
import com.example.vehicare.presentation.profile.ProfileScreen
import com.example.vehicare.presentation.profile.TermsScreen
import com.example.vehicare.presentation.results.IssueDetailScreen
import com.example.vehicare.presentation.results.RankedIssuesScreen
import com.example.vehicare.presentation.results.ReportScreen
import com.example.vehicare.presentation.results.ResultsScreen
import com.example.vehicare.presentation.splash.SplashScreen
import com.example.vehicare.presentation.trends.TrendsScreen
import com.example.vehicare.presentation.vehicles.AddEditVehicleScreen
import com.example.vehicare.presentation.vehicles.VehicleDetailScreen
import com.example.vehicare.presentation.vehicles.VehiclesScreen

private val bottomBarRoutes = bottomNavItems.map { it.route }.toSet()

/**
 * The single navigation graph (Section 4): Splash -> (one-time, skippable Onboarding) -> Home, with
 * the five bottom-navigation destinations and shallow pushes for the assessment flow, results,
 * reports and settings screens. There is no authentication step anywhere.
 */
@Composable
fun VehiCareNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.HOME) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onDestinationReady = { destination ->
                        navController.navigate(destination) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onStartAssessment = { navController.navigate(Routes.ASSESSMENT_SETUP) },
                    onResumeAssessment = { navController.navigate(Routes.QUESTIONNAIRE) },
                    onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) },
                    onOpenVehicle = { vehicleId ->
                        navController.navigate(Routes.vehicleDetail(vehicleId))
                    },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenReports = { navController.navigate(Routes.TRENDS) },
                    onOpenResults = { assessmentId ->
                        navController.navigate(Routes.results(assessmentId))
                    }
                )
            }

            composable(Routes.VEHICLES) {
                VehiclesScreen(
                    onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) },
                    onOpenVehicle = { vehicleId ->
                        navController.navigate(Routes.vehicleDetail(vehicleId))
                    },
                    onAssessVehicle = { navController.navigate(Routes.ASSESSMENT_SETUP) },
                    onEditVehicle = { vehicleId ->
                        navController.navigate(Routes.editVehicle(vehicleId))
                    }
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenResults = { assessmentId ->
                        navController.navigate(Routes.results(assessmentId))
                    },
                    onStartAssessment = { navController.navigate(Routes.ASSESSMENT_SETUP) }
                )
            }

            composable(Routes.TRENDS) {
                TrendsScreen(
                    onStartAssessment = { navController.navigate(Routes.ASSESSMENT_SETUP) }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onDiagnosticMethod = { navController.navigate(Routes.DIAGNOSTIC_METHOD) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
                    onHelp = { navController.navigate(Routes.HELP) },
                    onTerms = { navController.navigate(Routes.TERMS) },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) }
                )
            }

            composable(Routes.ADD_VEHICLE) {
                AddEditVehicleScreen(
                    vehicleId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { vehicleId ->
                        navController.popBackStack()
                        if (vehicleId.isNotBlank()) navController.navigate(Routes.vehicleDetail(vehicleId))
                    }
                )
            }

            composable(
                route = Routes.VEHICLE_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType })
            ) { entry ->
                val vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID).orEmpty()
                VehicleDetailScreen(
                    vehicleId = vehicleId,
                    onBack = { navController.popBackStack() },
                    onNewAssessment = {
                        navController.navigate(Routes.ASSESSMENT_SETUP)
                    },
                    onEditVehicle = { id -> navController.navigate(Routes.editVehicle(id)) },
                    onOpenReports = { navController.navigate(Routes.TRENDS) },
                    onOpenResults = { assessmentId ->
                        navController.navigate(Routes.results(assessmentId))
                    },
                    onDeleted = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.EDIT_VEHICLE,
                arguments = listOf(navArgument(Routes.ARG_VEHICLE_ID) { type = NavType.StringType })
            ) { entry ->
                val vehicleId = entry.arguments?.getString(Routes.ARG_VEHICLE_ID)
                AddEditVehicleScreen(
                    vehicleId = vehicleId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(Routes.ASSESSMENT_SETUP) {
                AssessmentSetupScreen(
                    onBack = { navController.popBackStack() },
                    onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) },
                    onContinue = { navController.navigate(Routes.ASSESSMENT_CATEGORIES) }
                )
            }

            composable(Routes.ASSESSMENT_CATEGORIES) {
                CategorySelectionScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = { navController.navigate(Routes.QUESTIONNAIRE) }
                )
            }

            composable(Routes.QUESTIONNAIRE) {
                QuestionnaireScreen(
                    onBack = { navController.popBackStack() },
                    onFinished = { navController.navigate(Routes.PROCESSING) },
                    onNoDraft = { navController.popBackStack() }
                )
            }

            composable(Routes.PROCESSING) {
                ProcessingScreen(
                    onCompleted = { assessmentId ->
                        navController.navigate(Routes.results(assessmentId)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                    onBackToQuestionnaire = {
                        navController.popBackStack(Routes.QUESTIONNAIRE, inclusive = false)
                    },
                    onBackToHome = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Routes.RESULTS,
                arguments = listOf(navArgument(Routes.ARG_ASSESSMENT_ID) { type = NavType.StringType })
            ) { entry ->
                val assessmentId = entry.arguments?.getString(Routes.ARG_ASSESSMENT_ID).orEmpty()
                ResultsScreen(
                    assessmentId = assessmentId,
                    onBack = { navController.popBackStack() },
                    onOpenRankedIssues = { id -> navController.navigate(Routes.rankedIssues(id)) },
                    onOpenIssue = { id, hypothesisId ->
                        navController.navigate(Routes.issueDetail(id, hypothesisId))
                    },
                    onOpenReport = { id -> navController.navigate(Routes.report(id)) },
                    onStartNewAssessment = { navController.navigate(Routes.ASSESSMENT_SETUP) }
                )
            }

            composable(
                route = Routes.RANKED_ISSUES,
                arguments = listOf(navArgument(Routes.ARG_ASSESSMENT_ID) { type = NavType.StringType })
            ) { entry ->
                val assessmentId = entry.arguments?.getString(Routes.ARG_ASSESSMENT_ID).orEmpty()
                RankedIssuesScreen(
                    assessmentId = assessmentId,
                    onBack = { navController.popBackStack() },
                    onOpenIssue = { id, hypothesisId ->
                        navController.navigate(Routes.issueDetail(id, hypothesisId))
                    }
                )
            }

            composable(
                route = Routes.ISSUE_DETAIL,
                arguments = listOf(
                    navArgument(Routes.ARG_ASSESSMENT_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_HYPOTHESIS_ID) { type = NavType.StringType }
                )
            ) { entry ->
                IssueDetailScreen(
                    assessmentId = entry.arguments?.getString(Routes.ARG_ASSESSMENT_ID).orEmpty(),
                    hypothesisId = entry.arguments?.getString(Routes.ARG_HYPOTHESIS_ID).orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.REPORT,
                arguments = listOf(navArgument(Routes.ARG_ASSESSMENT_ID) { type = NavType.StringType })
            ) { entry ->
                ReportScreen(
                    assessmentId = entry.arguments?.getString(Routes.ARG_ASSESSMENT_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                    onStartNewAssessment = { navController.navigate(Routes.ASSESSMENT_SETUP) }
                )
            }

            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DIAGNOSTIC_METHOD) {
                DiagnosticMethodScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HELP) {
                HelpScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TERMS) {
                TermsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
