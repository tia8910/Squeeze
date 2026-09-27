package com.squeeze.app.health

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.components.SecondaryButton
import com.squeeze.app.ui.components.SectionHeader

/**
 * Connect watches, fitness and nutrition apps — through Health Connect, which all of them
 * already write to. One button, then Android's own per-type permission screen.
 */
@Composable
fun ConnectedAppsSection(viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(viewModel.permissionContract()) { viewModel.refresh() }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No store or settings screen on this device; nothing more to offer.
        }
    }

    SectionHeader(
        eyebrow = "Watch, steps and food",
        title = "Connected apps",
        caption = "Through Health Connect: Pixel and Wear OS watches, Galaxy Watch (Samsung Health), " +
            "Fitbit, Garmin, Google Fit, MyFitnessPal, Cronometer and more. Stays on your phone.",
    )

    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.loading -> Text("Checking…", style = MaterialTheme.typography.bodyMedium)

                state.availability == HealthAvailability.NOT_SUPPORTED -> {
                    Text("Health Connect isn't available on this phone", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "It needs Android 9 or later. Everything else in the app works without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.availability == HealthAvailability.NEEDS_UPDATE -> {
                    Text("Install or update Health Connect", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "It's free from Google and takes a minute. Then come back and connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton(
                        text = "Open Play Store",
                        onClick = {
                            open(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=${HealthConnectManager.PROVIDER_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding"),
                                ).setPackage("com.android.vending"),
                            )
                        },
                    )
                }

                !state.connected -> {
                    Text("Not connected", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Reads steps, workouts, sleep, resting heart rate and food you log elsewhere. " +
                            "Writes your workouts, weight and body fat back. You choose each type.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton(text = "Connect", onClick = { request.launch(viewModel.permissions) })
                }

                else -> {
                    Text(
                        "Connected · ${state.grantedCount} of ${viewModel.permissions.size} data types",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    state.today?.steps?.let {
                        Text(
                            "Today: ${"%,d".format(it)} steps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SecondaryButton(
                        text = "Choose data types",
                        onClick = { request.launch(viewModel.permissions) },
                    )
                    TextButton(
                        onClick = {
                            // Android 14+ has Health Connect built in; earlier versions use the app.
                            open(
                                Intent(
                                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                                        "android.health.connect.action.HEALTH_HOME_SETTINGS"
                                    } else {
                                        "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.padding(top = 0.dp),
                    ) { Text("Manage in Health Connect") }
                }
            }
        }
    }
}
