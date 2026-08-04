package com.squeeze.app.ui.composition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.BrandRow
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.components.SecondaryButton
import com.squeeze.app.ui.components.Sparkline
import com.squeeze.app.ui.components.StatRow
import com.squeeze.app.ui.components.StatTile
import com.squeeze.app.ui.components.NoticePill
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.bodycomp.CompositionPanel
import com.squeeze.core.bodycomp.PersonalCalibration
import com.squeeze.core.trend.RepeatabilityScore
import com.squeeze.core.trend.TrendPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * The body composition dashboard.
 *
 * Laid out in the brand sheet's order, which is also the right order by importance: the
 * estimate and its uncertainty first, the verdict on whether it is moving directly beneath,
 * then momentum, then the two actions, then the log. A user glancing at this for two seconds
 * should get "34.8%, nothing confirmed yet" without reading a chart.
 *
 * The deeper charts sit below the log rather than competing with the hero card. They are
 * evidence for the number above, not the headline.
 */
@Composable
fun CompositionScreen(
    trend: List<TrendPoint>,
    leanMassTrend: List<TrendPoint>,
    repeatability: RepeatabilityScore?,
    calibration: PersonalCalibration,
    measurements: List<MeasurementEntity>,
    panel: CompositionPanel?,
    onStartScan: () -> Unit,
    onAddMeasurement: () -> Unit,
    onDelete: (MeasurementEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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

        HeroCard(latest, calibration, trend)

        StatRow {
            StatTile(
                value = measurements.size.toString(),
                label = if (measurements.size == 1) "Entry" else "Entries",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = daysTracked(measurements).toString(),
                label = "Days tracked",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = lastEntryLabel(measurements),
                label = "Last entry",
                modifier = Modifier.weight(1f),
            )
        }

        PrimaryButton(
            text = "Scan",
            onClick = onStartScan,
            leading = {
                Icon(Icons.Default.CameraAlt, contentDescription = null, Modifier.size(18.dp))
            },
        )
        SecondaryButton(
            text = "Enter",
            onClick = onAddMeasurement,
            leading = {
                Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(18.dp))
            },
        )

        // Everything derivable from the latest measurement, collapsed. The hero number
        // answers what most people opened the app to ask; this is for interrogating it.
        panel?.let { AnalysisSection(it, Modifier.padding(top = 4.dp)) }

        HistorySection(measurements, onDelete)

        // Supporting detail, deliberately below the fold. Someone who wants to interrogate
        // the number can; someone who just wants the number never has to scroll past a chart.
        if (trend.size >= 2) {
            TrendChart(
                title = "Body fat",
                unitSuffix = "%",
                points = trend,
                lineColor = MaterialTheme.colorScheme.primary,
            )
        }

        // A separate chart rather than a second axis. Body fat and lean mass are different
        // quantities on different scales; sharing an axis would let the apparent crossing
        // point be decided by axis choice instead of by the data.
        if (leanMassTrend.size >= 2) {
            TrendChart(
                title = "Lean mass",
                unitSuffix = " kg",
                points = leanMassTrend,
                lineColor = Brand.Navy,
            )
        }

        repeatability?.let { RepeatabilityCard(it) }
    }
}

/**
 * The hero: current estimate, its interval, its calibration status, and the trend line.
 *
 * The interval is never omitted. An estimate shown without it is the central dishonesty of
 * this app category, and it is what destroys trust the first time the app disagrees with a
 * scan the user has paid for.
 */
@Composable
private fun HeroCard(
    latest: TrendPoint,
    calibration: PersonalCalibration,
    trend: List<TrendPoint>,
) {
    BrandCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                text = "Body fat",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // The number animates to its new value on each refresh. Beyond feel, this is a
        // state-change cue: after a save the user sees the estimate move, which confirms the
        // new measurement was absorbed without reading anything.
        val animatedLevel by animateFloatAsState(
            targetValue = latest.level.toFloat(),
            animationSpec = tween(durationMillis = 700),
            label = "heroLevel",
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "%.1f".format(animatedLevel),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "%",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp, start = 3.dp),
            )
        }

        val subColour = if (LocalIsDarkTheme.current) Brand.DarkSub else Brand.Sub

        Text(
            text = "± %.1f points, 95%% confidence".format(latest.levelConfidence95),
            style = MaterialTheme.typography.bodySmall,
            color = subColour,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            text = if (calibration.isActive) {
                "Calibrated to your own scan results."
            } else {
                "Uncalibrated — add a DEXA or BodPod result to anchor this to your body."
            },
            style = MaterialTheme.typography.bodySmall,
            color = subColour,
            modifier = Modifier.padding(top = 12.dp),
        )

        // One reading is a dot, not a trend. Reserving the chart's full height for it left
        // a band of empty white in the middle of the hero card that read as a rendering
        // fault — the card looked broken rather than early.
        if (trend.size >= 2) {
            Sparkline(
                values = trend.map { it.level },
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }

        NoticePill(
            text = if (latest.isChangeSignificant) {
                val direction = if (latest.weeklyChange < 0) "down" else "up"
                "%.2f points per week %s — larger than your noise".format(
                    abs(latest.weeklyChange),
                    direction,
                )
            } else {
                "No confirmed change yet"
            },
        )
    }
}

/**
 * The raw log, newest first.
 *
 * Tapping a row opens its detail, which is where delete lives. Delete is reachable because a
 * single mis-entered reading — a waist typed as 850 instead of 85.0 — visibly bends the
 * trend, and the fix has to be available where the damage shows.
 */
@Composable
private fun HistorySection(
    measurements: List<MeasurementEntity>,
    onDelete: (MeasurementEntity) -> Unit,
) {
    if (measurements.isEmpty()) return

    var selected by remember { mutableStateOf<MeasurementEntity?>(null) }

    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "History",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        measurements.take(HISTORY_LIMIT).forEach { entry ->
            BrandRow(onClick = { selected = entry }) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = formatDate(entry.epochDay),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = entrySummary(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open this measurement",
                    tint = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
                )
            }
        }

        if (measurements.size > HISTORY_LIMIT) {
            Text(
                text = "${measurements.size - HISTORY_LIMIT} older entries not shown",
                style = MaterialTheme.typography.bodySmall,
                color = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
            )
        }
    }

    selected?.let { entry ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(formatDate(entry.epochDay)) },
            text = { Text(entrySummary(entry), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry)
                        selected = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("Close") }
            },
        )
    }
}

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy"))

private fun daysTracked(measurements: List<MeasurementEntity>): Long =
    (measurements.maxOf { it.epochDay } - measurements.minOf { it.epochDay }) + 1

private fun lastEntryLabel(measurements: List<MeasurementEntity>): String =
    when (val days = LocalDate.now().toEpochDay() - measurements.maxOf { it.epochDay }) {
        0L -> "Today"
        1L -> "1 day"
        else -> "$days days"
    }

private fun entrySummary(entry: MeasurementEntity): String {
    val parts = buildList {
        entry.referenceBodyFatPercent?.let { add("DEXA %.1f%%".format(it)) }
        entry.waistCm?.let { add("waist %.1f".format(it)) }
        entry.weightKg?.let { add("%.1f kg".format(it)) }
        entry.neckCm?.let { add("neck %.1f".format(it)) }
    }
    val source = when (entry.source) {
        "PHOTO" -> "Scan"
        "PHOTO_FRONT_ONLY" -> "Scan (front)"
        "REFERENCE_SCAN" -> "Reference"
        else -> "Tape"
    }
    return if (parts.isEmpty()) source else "$source · " + parts.joinToString(" · ")
}

private const val HISTORY_LIMIT = 14

/**
 * What a new user sees.
 *
 * Two concrete actions rather than an explanation of why the screen is blank. The tape
 * option is described as the more precise one because it is — the scan is the convenient
 * path, not the accurate one, and saying so up front sets an honest expectation.
 */
@Composable
private fun EmptyState(onStartScan: () -> Unit, onAddMeasurement: () -> Unit) {
    val subColour = if (LocalIsDarkTheme.current) Brand.DarkSub else Brand.Sub

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BrandCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = "Body fat",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "--",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp, start = 3.dp),
                )
            }

            Text(
                text = "Take your first measurement and this becomes your number.",
                style = MaterialTheme.typography.bodySmall,
                color = subColour,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(14.dp))

            NoticePill(text = "No measurements yet")
        }

        PrimaryButton(
            text = "Scan with camera or photos",
            onClick = onStartScan,
            leading = {
                Icon(Icons.Default.CameraAlt, contentDescription = null, Modifier.size(18.dp))
            },
        )
        SecondaryButton(
            text = "Enter tape measurements",
            onClick = onAddMeasurement,
            leading = {
                Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(18.dp))
            },
        )

        Text(
            text = "Two measurements are needed before a trend appears, and about three weeks " +
                "before the app can tell a real change from measurement noise. A tape is more " +
                "repeatable than any photo method — the scan is faster, the tape is more precise.",
            style = MaterialTheme.typography.bodySmall,
            color = subColour,
        )
    }
}

@Composable
private fun RepeatabilityCard(score: RepeatabilityScore) {
    val subColour = if (LocalIsDarkTheme.current) Brand.DarkSub else Brand.Sub

    BrandCard(Modifier.fillMaxWidth()) {
        Text(
            text = "Your measurement precision",
            style = MaterialTheme.typography.labelLarge,
            color = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
        )

        Text(
            text = when (score.grade) {
                RepeatabilityScore.Grade.EXCELLENT -> "Excellent"
                RepeatabilityScore.Grade.GOOD -> "Good"
                RepeatabilityScore.Grade.FAIR -> "Fair"
                RepeatabilityScore.Grade.POOR -> "Poor"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            text = (
                "Repeat measurements vary by ±%.2f points. Two readings must differ by more " +
                    "than %.2f points before the difference is real."
                ).format(score.withinSessionStdDev, score.repeatabilityCoefficient),
            style = MaterialTheme.typography.bodySmall,
            color = subColour,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (score.grade == RepeatabilityScore.Grade.FAIR ||
            score.grade == RepeatabilityScore.Grade.POOR
        ) {
            // Precision is the one error source the user directly controls, so coaching
            // protocol improves every future reading more than any algorithm change would.
            Text(
                text = "Measure at the same time of day, before eating, with the tape at the " +
                    "same tension. This is the fastest way to make your trend readable.",
                style = MaterialTheme.typography.bodySmall,
                color = subColour,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
