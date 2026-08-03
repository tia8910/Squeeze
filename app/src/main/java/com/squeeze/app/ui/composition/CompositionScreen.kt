package com.squeeze.app.ui.composition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.app.ui.theme.chartPalette
import com.squeeze.core.bodycomp.PersonalCalibration
import com.squeeze.core.trend.RepeatabilityScore
import com.squeeze.core.trend.TrendPoint
import kotlin.math.abs

/**
 * The body composition dashboard.
 *
 * Structured so the most important thing is the largest thing: the current estimate is a
 * hero number, the verdict on whether it is moving sits directly beneath it, and the charts
 * are supporting evidence rather than the headline. A user glancing at this for two seconds
 * should get "18.4%, down 0.3 a week, that's real" without reading a chart.
 */
@Composable
fun CompositionScreen(
    trend: List<TrendPoint>,
    leanMassTrend: List<TrendPoint>,
    repeatability: RepeatabilityScore?,
    calibration: PersonalCalibration,
    measurements: List<MeasurementEntity>,
    onStartScan: () -> Unit,
    onAddMeasurement: () -> Unit,
    onDelete: (MeasurementEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = chartPalette

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val latest = trend.lastOrNull()

        if (latest == null) {
            EmptyState(onStartScan = onStartScan, onAddMeasurement = onAddMeasurement)
            if (measurements.isNotEmpty()) {
                // Entries exist but none carries enough sites for an estimate — show them,
                // so the user can see their data went somewhere and fix what is missing.
                HistorySection(measurements, onDelete)
            }
            return@Column
        }

        HeroEstimate(latest, calibration, palette.bodyFat)
        TrendVerdictCard(latest)
        StatsRow(measurements)

        ActionRow(onStartScan = onStartScan, onAddMeasurement = onAddMeasurement)

        TrendChart(
            title = "Body fat",
            unitSuffix = "%",
            points = trend,
            lineColor = palette.bodyFat,
        )

        // A separate chart rather than a second axis. Body fat and lean mass are different
        // quantities on different scales; sharing an axis would let the apparent crossing
        // point be decided by axis choice instead of by the data.
        if (leanMassTrend.size >= 2) {
            TrendChart(
                title = "Lean mass",
                unitSuffix = " kg",
                points = leanMassTrend,
                lineColor = palette.leanMass,
            )
        }

        repeatability?.let { RepeatabilityCard(it) }

        HistorySection(measurements, onDelete)
    }
}

/**
 * Momentum at a glance: how much data exists and how consistently it arrives.
 *
 * Consistency is the input the trend engine actually needs, so it is the behaviour worth
 * celebrating — not streaks for their own sake.
 */
@Composable
private fun StatsRow(measurements: List<MeasurementEntity>) {
    if (measurements.isEmpty()) return

    val daysTracked = (measurements.maxOf { it.epochDay } - measurements.minOf { it.epochDay }) + 1
    val daysSinceLast = java.time.LocalDate.now().toEpochDay() - measurements.maxOf { it.epochDay }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Entries", measurements.size.toString(), Modifier.weight(1f))
        StatTile("Days tracked", daysTracked.toString(), Modifier.weight(1f))
        StatTile(
            label = "Last entry",
            value = when (daysSinceLast) {
                0L -> "Today"
                1L -> "1 day ago"
                else -> "$daysSinceLast days"
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The raw log, newest first, with one-tap delete.
 *
 * Delete lives here because a single mis-entered reading — a waist typed as 850 instead
 * of 85.0 — visibly bends the trend, and the fix belongs next to where the damage shows.
 */
@Composable
private fun HistorySection(
    measurements: List<MeasurementEntity>,
    onDelete: (MeasurementEntity) -> Unit,
) {
    if (measurements.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("History", style = MaterialTheme.typography.titleSmall)

        measurements.take(HISTORY_LIMIT).forEach { entry ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = java.time.LocalDate.ofEpochDay(entry.epochDay)
                                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = entrySummary(entry),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete this measurement",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (measurements.size > HISTORY_LIMIT) {
            Text(
                text = "${measurements.size - HISTORY_LIMIT} older entries not shown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun entrySummary(entry: MeasurementEntity): String {
    val parts = buildList {
        entry.referenceBodyFatPercent?.let { add("DEXA %.1f%%".format(it)) }
        entry.waistCm?.let { add("waist %.1f".format(it)) }
        entry.weightKg?.let { add("%.1f kg".format(it)) }
        entry.neckCm?.let { add("neck %.1f".format(it)) }
    }
    val source = if (entry.source == "PHOTO") "Scan" else "Tape"
    return if (parts.isEmpty()) source else "$source · " + parts.joinToString(" · ")
}

private const val HISTORY_LIMIT = 14

/**
 * The one number worth reading at a glance, with its uncertainty attached.
 *
 * The interval is never omitted. An estimate shown without it is the central dishonesty of
 * this app category, and it is what destroys trust the first time the app disagrees with a
 * scan the user has paid for.
 */
@Composable
private fun HeroEstimate(
    latest: TrendPoint,
    calibration: PersonalCalibration,
    accent: Color,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            // A wash of the series colour fading into the surface. This is the one place
            // colour is allowed to be loud, because it is the same hue that identifies
            // body fat in the chart below — the card and the line read as one thing.
            Modifier
                .background(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = 0.28f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                Text(
                    text = "Body fat",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // The number animates to its new value on each refresh. Beyond feel, this is
            // a state-change cue: after a save the user sees the estimate move, which
            // confirms the new measurement was absorbed without reading anything.
            val animatedLevel by animateFloatAsState(
                targetValue = latest.level.toFloat(),
                animationSpec = tween(durationMillis = 700),
                label = "heroLevel",
            )

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(animatedLevel),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
                )
            }

            Text(
                text = "± %.1f points, 95%% confidence".format(latest.levelConfidence95),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = if (calibration.isActive) {
                    "Calibrated to your own scan results."
                } else {
                    "Uncalibrated — add a DEXA or BodPod result to anchor this to your body."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(onStartScan: () -> Unit, onAddMeasurement: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStartScan, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, Modifier.size(18.dp))
            Text("Scan", Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onAddMeasurement, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(18.dp))
            Text("Enter", Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * What a new user sees.
 *
 * Two concrete actions rather than an explanation of why the screen is blank. The tape
 * option is presented as the more accurate one because it is — the scan is the convenient
 * path, not the precise one, and saying so up front sets an honest expectation.
 */
@Composable
private fun EmptyState(onStartScan: () -> Unit, onAddMeasurement: () -> Unit) {
    val palette = chartPalette

    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The first screen a new user ever sees carries the identity: wordmark over a
            // gradient of the two data colours, and the one-line reason this app exists.
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                0f to palette.bodyFat.copy(alpha = 0.35f),
                                1f to palette.leanMass.copy(alpha = 0.35f),
                            ),
                        )
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "SQUEEZE",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(
                            4f, androidx.compose.ui.unit.TextUnitType.Sp,
                        ),
                    )
                    Text(
                        text = "Track what's really changing. Nothing leaves this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Text("Take your first measurement", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Two measurements are needed before a trend appears, and about three " +
                    "weeks before the app can tell a real change from measurement noise.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, Modifier.size(18.dp))
                Text("Scan with camera or photos", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onAddMeasurement, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(18.dp))
                Text("Enter tape measurements", Modifier.padding(start = 8.dp))
            }

            Text(
                text = "A tape is more repeatable than any photo method. The scan is faster; " +
                    "the tape is more precise.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendVerdictCard(latest: TrendPoint) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (latest.isChangeSignificant) {
                val direction = if (latest.weeklyChange < 0) "down" else "up"
                Text(
                    text = "%.2f points per week %s".format(abs(latest.weeklyChange), direction),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Larger than your measurement noise, so this is a real change.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                // "Not yet" is more useful than a confident arrow through scatter, and it is
                // the only claim the data actually supports.
                Text("No confirmed change yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Movement so far is within measurement noise. A real trend usually " +
                        "separates out after two to three more weekly measurements.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RepeatabilityCard(score: RepeatabilityScore) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Your measurement precision",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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

            if (score.grade == RepeatabilityScore.Grade.FAIR ||
                score.grade == RepeatabilityScore.Grade.POOR
            ) {
                // Precision is the one error source the user directly controls, so coaching
                // protocol improves every future reading more than any algorithm change would.
                Text(
                    text = "Measure at the same time of day, before eating, with the tape at " +
                        "the same tension. This is the fastest way to make your trend readable.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
