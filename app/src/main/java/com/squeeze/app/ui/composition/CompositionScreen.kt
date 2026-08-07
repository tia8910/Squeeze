package com.squeeze.app.ui.composition

import android.graphics.Bitmap
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.squeeze.app.ui.theme.chartPalette
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.bodycomp.CompositionPanel
import com.squeeze.core.bodycomp.GoalProgress
import com.squeeze.core.bodycomp.PersonalCalibration
import com.squeeze.core.model.Profile
import com.squeeze.core.trend.RepeatabilityScore
import com.squeeze.core.trend.TrendFactor
import com.squeeze.core.trend.TrendFactors
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
    weightTrend: List<TrendPoint>,
    leanMassTrend: List<TrendPoint>,
    repeatability: RepeatabilityScore?,
    calibration: PersonalCalibration,
    measurements: List<MeasurementEntity>,
    /** Needed to draw a record's figure: stature sets the scale, sex sets the proportions. */
    profile: Profile?,
    loadPhoto: suspend (String) -> Bitmap?,
    analysisFor: (MeasurementEntity) -> CompositionPanel?,
    goalProgress: GoalProgress?,
    onEditGoal: () -> Unit,
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
            EmptyState(
                onStartScan = onStartScan,
                onAddMeasurement = onAddMeasurement,
                measurements = measurements,
            )
            if (measurements.isNotEmpty()) {
                // Entries exist but none carries enough sites for an estimate — show them,
                // so the user can see their data went somewhere and fix what is missing.
                HistorySection(measurements, profile, loadPhoto, analysisFor, onDelete)
            }
            return@Column
        }

        HeroCard(latest, calibration, trend)

        // Directly under the hero, above everything else. It is the only card here that
        // answers "so what": the number says where you are, this says whether that is
        // enough and what to change if it is not.
        goalProgress?.let { GoalCard(it, onEditGoal) }

        // Body fat alone answers half the question. A percentage can fall because fat went
        // or because muscle did, and those want opposite responses — so the two masses sit
        // beside it rather than behind a scroll.
        StatRow {
            StatTile(
                value = "%.1f%%".format(latest.level),
                label = "Body fat",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = weightTrend.lastOrNull()?.let { "%.1f".format(it.level) }
                    ?: latestWeight(measurements)?.let { "%.1f".format(it) }
                    ?: "—",
                label = "Weight (kg)",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = leanMassTrend.lastOrNull()?.let { "%.1f".format(it.level) } ?: "—",
                label = "Muscle (kg)",
                modifier = Modifier.weight(1f),
            )
        }

        // On the dashboard, not below the history. The hero says where you are; this says
        // how you got there, and that is the second question almost everyone asks.
        //
        // One chart with a filter rather than three stacked charts. Stacked, the second and
        // third are below the fold and effectively never seen; filtered, all three occupy the
        // same well-placed slot and switching between them is a tap. It also puts the three
        // series at the same size in the same position, which is the only way a user can
        // compare their shapes by eye.
        FilteredTrend(
            bodyFat = trend,
            weight = weightTrend,
            muscle = leanMassTrend,
        )

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

        // The full analysis lives inside each history entry rather than here. On the
        // dashboard it described whichever fields happened to be newest across different
        // sessions, which is a fine answer to "where am I now" and a poor thing to call an
        // analysis, because no single measurement ever produced that combination.
        HistorySection(measurements, profile, loadPhoto, analysisFor, onDelete)

        repeatability?.let { RepeatabilityCard(it) }
    }
}

/**
 * The trend chart with a factor filter above it.
 *
 * The three chips are the three things a body does independently, and the reason to switch
 * between them is diagnostic rather than decorative: body fat falling with muscle holding is
 * a cut working, body fat falling with muscle falling too is a cut going wrong, and the
 * percentage on its own reads identically in both cases.
 *
 * A chip appears only when its series has data. Someone who has never entered a bodyweight
 * has neither a weight series nor a muscle series — muscle is derived from the two together —
 * and a chip leading to an empty chart is worse than no chip.
 */
@Composable
private fun FilteredTrend(
    bodyFat: List<TrendPoint>,
    weight: List<TrendPoint>,
    muscle: List<TrendPoint>,
) {
    val available = TrendFactors.available(bodyFat, weight, muscle)
    var chosen by remember { mutableStateOf<TrendFactor?>(null) }

    // Resolved every recomposition rather than stored. Deleting the entry that carried the
    // only weight while Weight is selected must not leave the chart on an empty series.
    val factor = TrendFactors.resolve(chosen, available)

    if (factor == null) {
        InfoCard(
            "Scan or weigh in once more and the trend appears here. One reading is a " +
                "point — the direction only exists once there are two.",
        )
        return
    }

    val points = when (factor) {
        TrendFactor.BODY_FAT -> bodyFat
        TrendFactor.WEIGHT -> weight
        TrendFactor.MUSCLE -> muscle
    }
    val palette = chartPalette
    val colour = when (factor) {
        TrendFactor.BODY_FAT -> palette.bodyFat
        TrendFactor.WEIGHT -> palette.weight
        TrendFactor.MUSCLE -> palette.leanMass
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Shown even when there is only one factor. A row that appears the day a second
        // series arrives moves everything below it and reads as the layout having shifted;
        // a stable single chip reads as a label.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            available.forEach { option ->
                FilterChip(
                    selected = option == factor,
                    onClick = { chosen = option },
                    label = { Text(option.label) },
                )
            }
        }

        if (points.size >= 2) {
            TrendChart(
                title = factor.label,
                unitSuffix = factor.unitSuffix,
                points = points,
                lineColor = colour,
            )
        } else {
            // Said rather than left blank. An empty space where a chart belongs reads as a
            // broken feature; naming what is missing turns it into an instruction.
            InfoCard(
                "Only one ${factor.label.lowercase()} reading so far. One reading is a " +
                    "point — the direction only exists once there are two.",
            )
        }
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
    profile: Profile?,
    loadPhoto: suspend (String) -> Bitmap?,
    analysisFor: (MeasurementEntity) -> CompositionPanel?,
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
        MeasurementDetailDialog(
            entry = entry,
            profile = profile,
            loadPhoto = loadPhoto,
            panel = analysisFor(entry),
            onDelete = {
                onDelete(entry)
                selected = null
            },
            onDismiss = { selected = null },
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
private fun EmptyState(
    onStartScan: () -> Unit,
    onAddMeasurement: () -> Unit,
    measurements: List<MeasurementEntity>,
) {
    // Entries can exist while no estimate does, when a scan saved some sites but not the
    // ones the equation needs. Saying "no measurements yet" then is simply false, and it
    // hides the one thing worth telling the user: which field is missing.
    val blocked = measurements.isNotEmpty()
    val hasNeck = measurements.any { it.neckCm != null }
    val hasWaist = measurements.any { it.waistCm != null }

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
                text = when {
                    !blocked -> "Take your first measurement and this becomes your number."
                    !hasNeck && !hasWaist ->
                        "Your entry is saved, but it has neither a neck nor a waist " +
                            "measurement, and the equation needs both."
                    !hasNeck ->
                        "Your entry is saved and has a waist, but no neck. The equation " +
                            "works on the gap between the two, so it cannot run without it. " +
                            "A tape around the narrowest part of your neck is all it takes."
                    !hasWaist ->
                        "Your entry is saved and has a neck, but no waist. The equation " +
                            "works on the gap between the two, so it cannot run without it."
                    else ->
                        "Your entry is saved, but the values it holds are outside the range " +
                            "the equation is defined for. Open it from History to check them."
                },
                style = MaterialTheme.typography.bodySmall,
                color = subColour,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(14.dp))

            NoticePill(
                text = if (blocked) "Missing a measurement" else "No measurements yet",
            )
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

/** Most recent recorded bodyweight, or null when none has been entered. */
private fun latestWeight(measurements: List<MeasurementEntity>): Double? =
    measurements.firstNotNullOfOrNull { it.weightKg }

/**
 * A short, quiet notice.
 *
 * Used where a section would otherwise be empty. Blank space where a chart belongs reads as
 * something broken; a sentence saying what is missing reads as a next step.
 */
@Composable
private fun InfoCard(text: String) {
    BrandCard(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
        )
    }
}
