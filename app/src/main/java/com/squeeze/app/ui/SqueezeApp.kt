package com.squeeze.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.squeeze.app.ui.composition.CompositionScreen
import com.squeeze.app.ui.measurement.AddMeasurementScreen
import com.squeeze.app.ui.scan.ScanScreen
import com.squeeze.app.ui.settings.SettingsScreen
import com.squeeze.app.ui.training.TrainingScreen

/**
 * Full-screen destinations that sit outside the tab bar.
 *
 * Scanning and data entry are tasks with a beginning and an end, so they take over the
 * screen and offer a back action rather than living in a tab the user might wander out of
 * halfway through.
 */
private const val ROUTE_SCAN = "scan"
private const val ROUTE_ADD_MEASUREMENT = "add_measurement"

private enum class Destination(
    val route: String,
    val label: String,
    val title: String,
    val icon: ImageVector,
) {
    COMPOSITION("composition", "Body", "Body composition", Icons.Default.MonitorWeight),
    TRAINING("training", "Training", "Training", Icons.Default.FitnessCenter),
    SETTINGS("settings", "Settings", "Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqueezeApp(viewModel: SqueezeViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val state by viewModel.state.collectAsStateWithLifecycle()

    val activeTab = Destination.entries.firstOrNull { destination ->
        backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
    }

    val isFullScreenTask = currentRoute == ROUTE_SCAN || currentRoute == ROUTE_ADD_MEASUREMENT

    Scaffold(
        topBar = {
            // The camera viewfinder is its own full-bleed surface; a title bar over it would
            // only obscure the framing guide.
            if (currentRoute == ROUTE_SCAN) return@Scaffold

            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (currentRoute) {
                            ROUTE_ADD_MEASUREMENT -> "New measurement"
                            else -> activeTab?.title ?: "Squeeze"
                        },
                    )
                },
                navigationIcon = {
                    if (isFullScreenTask) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isFullScreenTask) return@Scaffold

            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = activeTab == destination,
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
                    leanMassTrend = state.leanMassTrend,
                    repeatability = state.repeatability,
                    calibration = state.calibration,
                    onStartScan = { navController.navigate(ROUTE_SCAN) },
                    onAddMeasurement = { navController.navigate(ROUTE_ADD_MEASUREMENT) },
                )
            }

            composable(Destination.TRAINING.route) { TrainingScreen() }

            composable(Destination.SETTINGS.route) {
                val blockScreenshots by viewModel.blockScreenshots.collectAsStateWithLifecycle()
                SettingsScreen(
                    blockScreenshots = blockScreenshots,
                    onBlockScreenshotsChange = viewModel::setBlockScreenshots,
                    heightCm = state.profile?.heightCm,
                    birthYear = state.profile?.birthYear,
                    sex = state.profile?.sex,
                    onProfileChange = viewModel::updateProfile,
                )
            }

            composable(ROUTE_SCAN) {
                ScanScreen(
                    onFinished = {
                        navController.popBackStack()
                        // The dashboard reads measurements once, so a new one only appears
                        // if the shared state is refreshed on return.
                        viewModel.refresh()
                    },
                )
            }

            composable(ROUTE_ADD_MEASUREMENT) {
                AddMeasurementScreen(
                    onSaved = {
                        navController.popBackStack()
                        viewModel.refresh()
                    },
                )
            }
        }
    }
}
