package com.squeeze.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.squeeze.app.ui.brand.SqueezeWordmark
import com.squeeze.app.ui.components.AuroraScaffoldBackground
import com.squeeze.app.ui.composition.CompositionScreen
import com.squeeze.app.ui.landing.LandingScreen
import com.squeeze.app.ui.measurement.AddMeasurementScreen
import com.squeeze.app.ui.scan.ScanScreen
import com.squeeze.app.ui.settings.SettingsScreen
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.training.TrainingScreen

/**
 * Full-screen destinations outside the tab bar: tasks with a beginning and an end, rather
 * than places the user lives.
 */
private const val ROUTE_SCAN = "scan"
private const val ROUTE_ADD_MEASUREMENT = "add_measurement"

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    COMPOSITION("composition", "Body", Icons.Default.MonitorWeight),
    TRAINING("training", "Train", Icons.Default.FitnessCenter),
    SETTINGS("settings", "You", Icons.Default.Tune),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqueezeApp(viewModel: SqueezeViewModel = hiltViewModel()) {
    val landingSeen by viewModel.landingSeen.collectAsStateWithLifecycle()

    if (!landingSeen) {
        LandingScreen(onGetStarted = viewModel::markLandingSeen)
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val state by viewModel.state.collectAsStateWithLifecycle()

    val activeTab = Destination.entries.firstOrNull { destination ->
        backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
    }
    val isFullScreenTask = currentRoute == ROUTE_SCAN || currentRoute == ROUTE_ADD_MEASUREMENT

    // The aurora lives beneath the whole app, so the drifting colour is continuous across
    // navigation instead of restarting per screen. Every surface above it is transparent.
    AuroraScaffoldBackground(intensity = 0.5f) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                if (currentRoute == ROUTE_SCAN) return@Scaffold

                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    title = {
                        when {
                            currentRoute == ROUTE_ADD_MEASUREMENT -> Text("New measurement")
                            // The wordmark is the title on the home tab; a text label there
                            // would waste the one place the brand is always visible.
                            activeTab == Destination.COMPOSITION ->
                                SqueezeWordmark(markSize = 26.dp, fontSize = 20.sp)

                            activeTab == Destination.TRAINING -> Text("Training")
                            else -> Text("You")
                        }
                    },
                    navigationIcon = {
                        if (isFullScreenTask) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (isFullScreenTask) return@Scaffold

                NavigationBar(containerColor = Color.Transparent) {
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
                            icon = {
                                Icon(destination.icon, contentDescription = destination.label)
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Brand.Teal,
                                selectedTextColor = Brand.Teal,
                                indicatorColor = Brand.Teal.copy(alpha = 0.16f),
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.COMPOSITION.route,
                    // Short fade with a slight rise: enough that a screen change registers,
                    // short enough that navigation never feels slower for it.
                    enterTransition = {
                        fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 }
                    },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(120)) },
                ) {
                    composable(Destination.COMPOSITION.route) {
                        CompositionScreen(
                            trend = state.bodyFatTrend,
                            leanMassTrend = state.leanMassTrend,
                            repeatability = state.repeatability,
                            calibration = state.calibration,
                            measurements = state.measurements,
                            onStartScan = { navController.navigate(ROUTE_SCAN) },
                            onAddMeasurement = { navController.navigate(ROUTE_ADD_MEASUREMENT) },
                            onDelete = viewModel::deleteMeasurement,
                        )
                    }

                    composable(Destination.TRAINING.route) { TrainingScreen() }

                    composable(Destination.SETTINGS.route) {
                        val blockScreenshots by viewModel.blockScreenshots
                            .collectAsStateWithLifecycle()
                        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

                        SettingsScreen(
                            blockScreenshots = blockScreenshots,
                            onBlockScreenshotsChange = viewModel::setBlockScreenshots,
                            themeMode = themeMode,
                            onThemeModeChange = viewModel::setThemeMode,
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
    }
}
