package com.squeeze.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.squeeze.app.ads.AdSurface
import com.squeeze.app.ui.ads.AdBanner
import com.squeeze.app.ui.composition.CompositionScreen

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Which ad policy applies to this tab; see [com.squeeze.app.ads.AdGate]. */
    val adSurface: AdSurface,
) {
    COMPOSITION("composition", "Body", Icons.Default.MonitorWeight, AdSurface.BODY_COMPOSITION),
    TRAINING("training", "Training", Icons.Default.FitnessCenter, AdSurface.WORKOUT_LOG),
    SETTINGS("settings", "Settings", Icons.Default.Settings, AdSurface.SETTINGS),
}

@Composable
fun SqueezeApp(viewModel: SqueezeViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    val state by viewModel.state.collectAsStateWithLifecycle()

    val activeDestination = Destination.entries.firstOrNull { destination ->
        currentRoute?.hierarchy?.any { it.route == destination.route } == true
    } ?: Destination.COMPOSITION

    Scaffold(
        bottomBar = {
            Column {
                // The banner sits above the navigation bar and asks the gate, which returns
                // false outright on the body composition tab and for paying users.
                AdBanner(surface = activeDestination.adSurface, adGate = viewModel.adGate)

                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = activeDestination == destination,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.COMPOSITION.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.COMPOSITION.route) {
                CompositionScreen(
                    trend = state.bodyFatTrend,
                    repeatability = state.repeatability,
                    calibration = state.calibration,
                )
            }
            composable(Destination.TRAINING.route) {
                TrainingPlaceholder()
            }
            composable(Destination.SETTINGS.route) {
                SettingsPlaceholder()
            }
        }
    }
}

@Composable
private fun TrainingPlaceholder() {
    Text("Workout logging and generated programmes land here.", Modifier.padding(16.dp))
}

@Composable
private fun SettingsPlaceholder() {
    Text("Profile, calibration, export and purchases land here.", Modifier.padding(16.dp))
}
