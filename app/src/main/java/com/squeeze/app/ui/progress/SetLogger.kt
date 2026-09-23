package com.squeeze.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.squeeze.app.data.db.LoggedSetEntity
import com.squeeze.app.ui.components.BrandRow
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.core.workout.Coaching
import com.squeeze.core.workout.LoggedSet
import com.squeeze.core.workout.Prescription
import com.squeeze.core.workout.Suggestion

/**
 * Logging one exercise, set by set, with a coach's call before each set.
 *
 * Above the inputs: what last time was and what today's target is. After every set: whether
 * it was too light, too heavy or right, and exactly what to load for the next one — and the
 * inputs are filled in with it, so following the advice is one tap.
 */
@Composable
fun SetLogger(
    exercise: String,
    prescription: Prescription,
    suggestion: Suggestion?,
    lastSets: List<LoggedSet>,
    todaysSets: List<LoggedSetEntity>,
    compound: Boolean,
    lowerBody: Boolean,
    onLog: (weightKg: Double, reps: Int, rir: Int?) -> Unit,
    onDelete: (LoggedSetEntity) -> Unit,
) {
    val done = todaysSets.map { LoggedSet(it.weightKg, it.reps, it.rir) }
    val advice = Coaching.nextSet(done, prescription, compound, lowerBody)
    val startWeight = advice.weightKg ?: suggestion?.weightKg
    // Re-filled after every set, with the weight and reps the advice asks for.
    var weight by remember(exercise, todaysSets.size) { mutableStateOf(startWeight?.let(::kg).orEmpty()) }
    var reps by remember(exercise, todaysSets.size) { mutableStateOf(advice.reps.toString()) }
    var rir by remember(exercise, todaysSets.size) { mutableStateOf(prescription.rir.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Plan: ${prescription.summary}", style = MaterialTheme.typography.bodySmall)
        if (lastSets.isNotEmpty()) {
            Text(
                "Last time: " + lastSets.joinToString(", ") { "${kg(it.weightKg)}×${it.reps}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (todaysSets.isEmpty() && suggestion != null) {
            Text("Today's target: ${suggestion.reason}", style = MaterialTheme.typography.bodySmall)
        }

        todaysSets.forEachIndexed { i, set ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Set ${i + 1}  ${kg(set.weightKg)} kg × ${set.reps}" + (set.rir?.let { "  ·  $it left" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onDelete(set) }) { Text("Undo") }
            }
        }

        // The coach's call for the next set.
        BrandRow {
            Column {
                Text(
                    if (advice.finished) "DONE" else "NEXT SET",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(advice.text, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("kg", weight, Modifier.weight(1f)) { weight = it }
            Field("Reps", reps, Modifier.weight(1f)) { reps = it }
            Field("Reps left", rir, Modifier.weight(1f)) { rir = it }
        }
        PrimaryButton(
            text = if (advice.finished) "Log an extra set" else "Log set ${todaysSets.size + 1}",
            onClick = { onLog(weight.replace(',', '.').toDoubleOrNull() ?: 0.0, reps.toIntOrNull() ?: 0, rir.toIntOrNull()) },
        )
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
