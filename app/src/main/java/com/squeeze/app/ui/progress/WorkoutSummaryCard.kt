package com.squeeze.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.StatRow
import com.squeeze.app.ui.components.StatTile
import com.squeeze.core.workout.ExerciseSummary
import com.squeeze.core.workout.WorkoutSummary
import kotlin.math.roundToInt

/**
 * Today's workout at a glance: sets, volume, records, and every exercise against the last
 * time it was trained — the answer to "did I beat last time?".
 */
@Composable
fun WorkoutSummaryCard(summary: WorkoutSummary, modifier: Modifier = Modifier) {
    if (summary.exercises.isEmpty()) return
    BrandCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TODAY'S WORKOUT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            StatRow {
                StatTile("${summary.totalSets}", "Sets", Modifier.weight(1f))
                StatTile("%,d kg".format(summary.totalVolume.roundToInt()), "Volume", Modifier.weight(1f), tinted = true)
                StatTile("${summary.records.size}", if (summary.records.size == 1) "Record" else "Records", Modifier.weight(1f))
            }
            summary.exercises.forEachIndexed { i, ex ->
                if (i > 0) HorizontalDivider()
                ExerciseLine(ex)
            }
        }
    }
}

@Composable
private fun ExerciseLine(ex: ExerciseSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text(ex.exercise, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (ex.personalRecord) {
                Text("🏆 New best", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        val best = ex.today.bestSet
        Text(
            "${ex.today.sets.size} sets · best ${kg(best.weightKg)} kg × ${best.reps} · est. max ${ex.today.estimatedMax.roundToInt()} kg" +
                ex.maxChangePercent.signed(),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Volume %,d kg".format(ex.today.volume.roundToInt()) + ex.volumeChangePercent.signed() +
                (ex.previous?.let { " vs last time" } ?: " · first time logged"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Double?.signed(): String = this?.let { " (${if (it >= 0) "+" else ""}${it.roundToInt()}%)" }.orEmpty()

internal fun kg(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
