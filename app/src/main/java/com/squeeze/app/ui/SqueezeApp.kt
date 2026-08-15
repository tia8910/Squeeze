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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
import com.squeeze.app.ui.label.LabelScreen
import com.squeeze.app.ui.landing.LandingScreen
import com.squeeze.app.ui.measurement.AddMeasurementScreen
import com.squeeze.app.ui.onboarding.OnboardingScreen
import com.squeeze.app.ui.scan.ScanScreen
import com.squeeze.app.ui.settings.SettingsScreen
import com.squeeze.app.ui.theme.Brand
import com.squeeze.core.model.Goal
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

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    COMPOSITION("composition", "Body", Icons.Default.MonitorWeight),
    TRAINING("training", "Train", Icons.Default.FitnessCenter),
    SETTINGS("settings", "You", Icons.Default.Person),
}

/**
 * The labelling screen, deliberately not a tab.
 *
 * It is a tool for building the training set, not part of using the app to track a body, and
 * a fourth tab would put it in front of every user for the sake of the few who will ever open
 * it. Reached from Settings instead.
 */
private const val LABEL_ROUTE = "label"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqueezeApp(viewModel: SqueezeViewModel = hiltViewModel()) {
    val landingSeen by viewModel.landingSeen.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!landingSeen) {
        LandingScreen(onGetStarted = viewModel::markLandingSeen)
        return
    }

    // Nothing is drawn until the stored profile has actually been read. Deciding on a null
    // profile while the query is still in flight would flash onboarding at every returning
    // user for as long as the database takes to open.
    if (state.loading) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    // The three fields the equations and the scale recovery need. Without them the app can
    // produce no number at all, so they are collected before the app rather than left in a
    // settings screen a user might never open — a scan that fails for a missing height
    // fails after they have undressed and framed a photograph.
    if (state.profile == null) {
        OnboardingScreen(
            onComplete = { heightCm, birthYear, sex, goal, targetBodyFat, targetWeight, day ->
                viewModel.updateProfile(
                    heightCm = heightCm,
                    birthYear = birthYear,
                    sex = sex,
                    goal = goal,
                    targetBodyFatPercent = targetBodyFat,
                    targetWeightKg = targetWeight,
                    targetEpochDay = day,
                )
            },
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
                        weightTrend = state.weightTrend,
                        leanMassTrend = state.leanMassTrend,
                        repeatability = state.repeatability,
                        calibration = state.calibration,
                        measurements = state.measurements,
                        profile = state.profile,
                        loadPhoto = viewModel::loadPhoto,
                        analysisFor = viewModel::analysisFor,
                        goalProgress = state.goalProgress,
                        onEditGoal = { navController.navigate(Destination.SETTINGS.route) },
                        onStartScan = { navController.navigate(ROUTE_SCAN) },
                        onAddMeasurement = { navController.navigate(ROUTE_ADD_MEASUREMENT) },
                        onDelete = viewModel::deleteMeasurement,
                    )
                }

                composable(Destination.TRAINING.route) { TrainingScreen() }

                composable(LABEL_ROUTE) { LabelScreen() }

                composable(Destination.SETTINGS.route) {
                    val blockScreenshots by viewModel.blockScreenshots
                        .collectAsStateWithLifecycle()
                    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                    val soundEnabled by viewModel.soundEnabled
                        .collectAsStateWithLifecycle()
                    val ambientEnabled by viewModel.ambientEnabled
                        .collectAsStateWithLifecycle()

                    SettingsScreen(
                        blockScreenshots = blockScreenshots,
                        onBlockScreenshotsChange = viewModel::setBlockScreenshots,
                        themeMode = themeMode,
                        onThemeModeChange = viewModel::setThemeMode,
                        soundEnabled = soundEnabled,
                        onSoundEnabledChange = viewModel::setSoundEnabled,
                        ambientEnabled = ambientEnabled,
                        onAmbientEnabledChange = viewModel::setAmbientEnabled,
                        heightCm = state.profile?.heightCm,
                        birthYear = state.profile?.birthYear,
                        sex = state.profile?.sex,
                        onProfileChange = viewModel::updateProfile,
                        goal = state.profile?.goal ?: Goal.HYPERTROPHY,
                        targetBodyFatPercent = state.profile?.targetBodyFatPercent,
                        targetWeightKg = state.profile?.targetWeightKg,
                        targetEpochDay = state.profile?.targetEpochDay,
                        onGoalChange = viewModel::setGoal,
                        onLabelPhotos = { navController.navigate(LABEL_ROUTE) },
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
 * Bottom navigation: an icon over a label, the active item on a tinted pill.
 *
 * The brand sheet draws this as three bare words, because in a static mockup a word is
 * enough to say which screen is which. On a real device it is not: bare text at the bottom
 * edge gives no tap target to aim at, no indication of where the touchable area ends, and
 * nothing to recognise at a glance once the app is familiar. The icon carries recognition,
 * the pill carries the tap target and the selected state, and the label stays because three
 * abstract glyphs would be a guessing game.
 *
 * Everything else follows the sheet — blue for active, the same muted grey for the rest, and
 * the hairline rule above.
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                val selected = destination == active

                val tint = when {
                    selected -> MaterialTheme.colorScheme.primary
                    dark -> Brand.DarkMuted
                    else -> Brand.NavIdle
                }

                // Ripple is suppressed and replaced by the pill, which is a clearer
                // indication of state than a fading circle and, unlike a ripple, persists.
                val interaction = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) {
                                if (dark) Brand.DarkIce else Brand.Ice
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(destination) },
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = destination.icon,
                        // The label directly beneath already names the destination, so
                        // repeating it here would make a screen reader say it twice.
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = tint,
                    )
                }
            }
        }
    }
}
