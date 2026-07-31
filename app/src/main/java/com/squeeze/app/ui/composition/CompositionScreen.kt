package com.squeeze.app.ui.composition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.core.bodycomp.PersonalCalibration
import com.squeeze.core.trend.RepeatabilityScore
import com.squeeze.core.trend.TrendPoint
import kotlin.math.abs

/**
 * The body composition dashboard.
 *
 * Note there is no [com.squeeze.app.ui.ads.AdBanner] anywhere on this screen, and there
 * must never be: [com.squeeze.app.ads.AdSurface.BODY_COMPOSITION] is permanently barred in
 * [com.squeeze.app.ads.AdGate].
 */
@Composable
fun CompositionScreen(
    trend: List<TrendPoint>,
    repeatability: RepeatabilityScore?,
    calibration: PersonalCalibration,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
            Text("Scan with camera")
        }

        val latest = trend.lastOrNull()

        if (latest != null) {
            CurrentEstimateCard(latest, calibration)
            TrendVerdictCard(latest)
        }

        TrendChart(points = trend)

        repeatability?.let { RepeatabilityCard(it) }
    }
}

@Composable
private fun CurrentEstimateCard(latest: TrendPoint, calibration: PersonalCalibration) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Body fat", style = MaterialTheme.typography.labelMedium)

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(latest.level),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            }

            // The interval is shown next to every number, always. An estimate presented
            // without its uncertainty is the central dishonesty of this app category, and
            // it is what makes users distrust the app the first time it disagrees with a scan.
            Text(
                text = "± %.1f points (95%% confidence)".format(latest.levelConfidence95),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = if (calibration.isActive) {
                    "Calibrated to your own scan results."
                } else {
                    "Uncalibrated. Enter a DEXA or BodPod result to anchor this to your body."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendVerdictCard(latest: TrendPoint) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Trend", style = MaterialTheme.typography.labelMedium)

            if (latest.isChangeSignificant) {
                val direction = if (latest.weeklyChange < 0) "down" else "up"
                Text(
                    text = "%.2f points/week %s".format(abs(latest.weeklyChange), direction),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "This change is larger than your measurement noise, so it is real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Saying "not yet" is more useful than drawing a confident arrow through
                // scatter, and it is the claim the data actually supports.
                Text("No confirmed change yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Any movement so far is within measurement noise. Keep measuring — " +
                        "a real trend usually separates out after two to three more weeks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RepeatabilityCard(score: RepeatabilityScore) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your measurement precision", style = MaterialTheme.typography.labelMedium)

            Text(
                text = when (score.grade) {
                    RepeatabilityScore.Grade.EXCELLENT -> "Excellent"
                    RepeatabilityScore.Grade.GOOD -> "Good"
                    RepeatabilityScore.Grade.FAIR -> "Fair"
                    RepeatabilityScore.Grade.POOR -> "Poor"
                },
                style = MaterialTheme.typography.titleLarge,
            )

            Text(
                text = (
                    "Repeat measurements vary by ±%.2f points. Two readings must differ by " +
                        "more than %.2f points before the difference is real."
                    ).format(score.withinSessionStdDev, score.repeatabilityCoefficient),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (score.grade == RepeatabilityScore.Grade.FAIR || score.grade == RepeatabilityScore.Grade.POOR) {
                // Precision is the one error source the user directly controls, so coaching
                // protocol here improves every future reading more than any algorithm change.
                Text(
                    text = "Measure at the same time of day, before eating, with the tape at " +
                        "the same tension. This is the fastest way to make your trend readable.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
