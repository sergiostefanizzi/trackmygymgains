package com.trackmygymgains.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.ui.home.HomeRoute
import com.trackmygymgains.ui.importplan.ImportRoute
import com.trackmygymgains.ui.summary.SummaryRoute
import com.trackmygymgains.ui.summary.SummaryViewModel
import com.trackmygymgains.ui.workout.WorkoutRoute
import com.trackmygymgains.ui.workout.WorkoutViewModel
import kotlinx.coroutines.flow.first

object Routes {
    const val IMPORT = "import"
    const val HOME = "home"
    const val WORKOUT = "workout/{${WorkoutViewModel.SESSION_ID_ARG}}"
    const val SUMMARY = "summary/{${SummaryViewModel.SESSION_ID_ARG}}"

    fun workout(sessionId: Long) = "workout/$sessionId"

    fun summary(sessionId: Long) = "summary/$sessionId"
}

const val START_LOADING_TAG = "start_loading"

@Composable
fun AppNavHost() {
    val app = LocalContext.current.applicationContext as TrackMyGymGainsApp
    // Decided once from the first emission, so importing a plan later does not reset the graph.
    var startDestination by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (startDestination == null) {
            val hasPlan = app.container.planRepository.observeHasActivePlan().first()
            startDestination = if (hasPlan) Routes.HOME else Routes.IMPORT
        }
    }
    val start = startDestination
    if (start == null) {
        Box(Modifier.fillMaxSize().testTag(START_LOADING_TAG), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.IMPORT) {
            ImportRoute(
                canNavigateBack = navController.previousBackStackEntry != null,
                onBack = { navController.popBackStack() },
                onImported = {
                    // Re-import: home is already below on the stack and observes the database.
                    if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.IMPORT) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeRoute(
                onImportNew = { navController.navigate(Routes.IMPORT) },
                onOpenWorkout = { sessionId -> navController.navigate(Routes.workout(sessionId)) },
            )
        }
        composable(
            route = Routes.WORKOUT,
            arguments = listOf(navArgument(WorkoutViewModel.SESSION_ID_ARG) { type = NavType.LongType }),
        ) {
            WorkoutRoute(
                onBack = { navController.popBackStack() },
                onFinished = { sessionId ->
                    // Drop the finished workout so back from the summary goes to home.
                    navController.navigate(Routes.summary(sessionId)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
        composable(
            route = Routes.SUMMARY,
            arguments = listOf(navArgument(SummaryViewModel.SESSION_ID_ARG) { type = NavType.LongType }),
        ) {
            SummaryRoute(
                onHome = {
                    if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
