package com.example.vehicare.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every route in the app, in one place (Section 4: "keep routes centralized").
 * Argument names are declared here and never built inline by screens.
 */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"

    const val HOME = "home"
    const val VEHICLES = "vehicles"
    const val HISTORY = "history"
    const val TRENDS = "trends"
    const val PROFILE = "profile"

    const val ADD_VEHICLE = "vehicles/add"
    const val VEHICLE_DETAIL = "vehicles/detail/{vehicleId}"
    const val EDIT_VEHICLE = "vehicles/edit/{vehicleId}"

    const val ASSESSMENT_SETUP = "assessment/setup"
    const val ASSESSMENT_CATEGORIES = "assessment/categories"
    const val QUESTIONNAIRE = "assessment/questionnaire"
    const val PROCESSING = "assessment/processing"
    const val RESULTS = "assessment/results/{assessmentId}"
    const val RANKED_ISSUES = "assessment/ranked/{assessmentId}"
    const val ISSUE_DETAIL = "assessment/issue/{assessmentId}/{hypothesisId}"
    const val REPORT = "assessment/report/{assessmentId}"

    const val EDIT_PROFILE = "profile/edit"
    const val NOTIFICATIONS = "profile/notifications"
    const val DIAGNOSTIC_METHOD = "profile/method"
    const val ABOUT = "profile/about"
    const val HELP = "profile/help"
    const val TERMS = "profile/terms"
    const val PRIVACY = "profile/privacy"

    const val ARG_VEHICLE_ID = "vehicleId"
    const val ARG_ASSESSMENT_ID = "assessmentId"
    const val ARG_HYPOTHESIS_ID = "hypothesisId"

    fun vehicleDetail(vehicleId: String) = "vehicles/detail/$vehicleId"
    fun editVehicle(vehicleId: String) = "vehicles/edit/$vehicleId"
    fun results(assessmentId: String) = "assessment/results/$assessmentId"
    fun rankedIssues(assessmentId: String) = "assessment/ranked/$assessmentId"
    fun issueDetail(assessmentId: String, hypothesisId: String) =
        "assessment/issue/$assessmentId/$hypothesisId"
    fun report(assessmentId: String) = "assessment/report/$assessmentId"
}

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

/** Home | My Vehicles | Assessments | Reports | Profile (Section 4). */
val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "Home", Icons.Default.Home),
    BottomNavItem(Routes.VEHICLES, "My Vehicles", Icons.Default.DirectionsCar),
    BottomNavItem(Routes.HISTORY, "Assessments", Icons.Default.FactCheck),
    BottomNavItem(Routes.TRENDS, "Reports", Icons.Default.BarChart),
    BottomNavItem(Routes.PROFILE, "Profile", Icons.Default.Person)
)
