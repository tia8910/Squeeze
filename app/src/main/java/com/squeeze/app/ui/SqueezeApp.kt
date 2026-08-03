package com.squeeze.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.squeeze.app.ui.celebration.CelebrationScreen
import com.squeeze.app.ui.composition.CompositionScreen
import com.squeeze.app.ui.landing.LandingScreen
import com.squeeze.app.ui.measurement.AddMeasurementScreen
import com.squeeze.app.ui.scan.ScanScreen
import com.squeeze.app.ui.settings.SettingsScreen
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.app.ui.training.TrainingScreen
import java.time.LocalDate

/**
 * Full-screen destinations outside the tab bar: tasks with a beginning and an end, rather
 * than places the user lives.
 */
private const val ROUTE_SCAN = "scan"
private const val ROUTE_ADD_MEASUREMENT = "add_measurement"
private const val ROUTE_CELEBRATION = "celebration"

private enum class Destination(val route: String, val label: String) {
    COMPOSITION("composition", "Body"),
    TRAINING("training", "Train"),
    SETTINGS("settings", "You"),
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
    val isFullScreenTask = currentRoute == ROUTE_SCAN ||
        currentRoute == ROUTE_ADD_MEASUREMENT ||
        currentRoute == ROUTE_CELEBRATION

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            if (currentRoute == ROUTE_SCAN || currentRoute == ROUTE_CELEBRATION) return@Scaffold

            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    when {
                        currentRoute == ROUTE_ADD_MEASUREMENT -> Text("New measurement")
                        // The wordmark is the title on the home tab; a text label there
                        // would waste the one place the brand is always visible.
                        activeTab == Destination.COMPOSITION ->
                            SqueezeWordmark(markSize = 34.dp, fontSize = 18.sp)

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

            BrandNavBar(
                active = activeTab,
                onSelect = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding ->
        // Navigating to the celebration screen and popping back to the dashboard, used after
        // both save paths so the two entry points behave identically.
        val celebrate: () -> Unit = {
            viewModel.refresh()
            navController.navigate(ROUTE_CELEBRATION) {
                popUpTo(Destination.COMPOSITION.route)
            }
        }

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
                    ScanScreen(onFinished = celebrate)
                }

                composable(ROUTE_ADD_MEASUREMENT) {
                    AddMeasurementScreen(onSaved = celebrate)
                }

                composable(ROUTE_CELEBRATION) {
                    val measurements = state.measurements

                    CelebrationScreen(
                        bodyFatPercent = state.bodyFatTrend.lastOrNull()?.level,
                        entries = measurements.size,
                        daysTracked = if (measurements.isEmpty()) {
                            0L
                        } else {
                            val first = measurements.minOf { it.epochDay }
                            val last = maxOf(measurements.maxOf { it.epochDay },
                                LocalDate.now().toEpochDay())
                            last - first + 1
                        },
                        trend = state.bodyFatTrend.map { it.level },
                        onViewProgress = {
                            navController.popBackStack(
                                route = Destination.COMPOSITION.route,
                                inclusive = false,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * The brand sheet's navigation: three labels, the active one in blue.
 *
 * Text-only and without icons, which is what the design specifies. With three destinations
 * whose names are concrete nouns, an icon would be decoration rather than a second channel —
 * and a wrong-feeling icon for "You" would cost more clarity than it bought.
 */
@Composable
private fun BrandNavBar(active: Destination?, onSelect: (Destination) -> Unit) {
    val dark = LocalIsDarkTheme.current

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (dark) Brand.DarkLine else Brand.Line),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                val selected = destination == active
                // No ripple: the labels sit on a plain background with no container to
                // bound the ripple, so it would spill as a bare circle over the bar.
                val interaction = remember { MutableInteractionSource() }

                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        dark -> Brand.DarkMuted
                        else -> Brand.NavIdle
                    },
                    modifier = Modifier
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(destination) },
                        )
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
        }
    }
}
