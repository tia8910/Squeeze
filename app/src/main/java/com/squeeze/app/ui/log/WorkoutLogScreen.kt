package com.squeeze.app.ui.log

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.core.program.ExerciseLibrary
import com.squeeze.core.workout.Intensity
import com.squeeze.core.workout.Sport

/** Log any workout: strength set by set, or any other sport as a session. */
@Composable
fun WorkoutLogScreen(
    onScanMachine: () -> Unit,
    onDone: () -> Unit,
    viewModel: WorkoutLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.strength, onClick = { viewModel.setStrength(true) }, label = { Text("Strength — sets & reps") })
            FilterChip(selected = !state.strength, onClick = { viewModel.setStrength(false) }, label = { Text("Any other sport") })
        }

        state.message?.let { message ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }

        if (state.strength) StrengthLog(state, viewModel, onScanMachine) else SportLog(state, viewModel)

        if (state.recent.isNotEmpty()) {
            Text("Last two weeks", style = MaterialTheme.typography.titleSmall)
            state.recent.forEach { session ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${java.time.LocalDate.ofEpochDay(session.epochDay)} · ${session.title} · " +
                            "${session.minutes} min · ${session.netKcal} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.deleteSession(session) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun StrengthLog(state: WorkoutLogUiState, viewModel: WorkoutLogViewModel, onScanMachine: () -> Unit) {
    Text(state.title, style = MaterialTheme.typography.titleLarge)

    state.exercises.forEach { exercise -> ExerciseCard(exercise, state, viewModel) }

    // Folded away once the session has exercises: the list is long, and mid-workout the
    // sets are what matter.
    var adding by remember { mutableStateOf(false) }
    val showAdd = adding || state.exercises.isEmpty()
    TextButton(onClick = { adding = !adding }, modifier = Modifier.fillMaxWidth()) {
        Text(if (showAdd && state.exercises.isNotEmpty()) "Hide exercise list" else "+ Add an exercise")
    }
    if (showAdd) AddExercise(state, viewModel, onScanMachine)

    var minutes by remember(state.minutes) { mutableStateOf(state.minutes.toString()) }
    NumberField("Session length (min)", minutes) { v -> minutes = v; v.toIntOrNull()?.let(viewModel::setMinutes) }
    Button(
        onClick = viewModel::finishStrength,
        enabled = state.todaysSets.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Finish workout · ~${state.estimatedKcal} kcal")
    }
}

@Composable
private fun AddExercise(state: WorkoutLogUiState, viewModel: WorkoutLogViewModel, onScanMachine: () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExerciseLibrary.ALL.filter { lib -> state.exercises.none { it.name == lib.name } }.forEach { ex ->
            OutlinedButton(onClick = { viewModel.addExercise(ex.name) }) { Text(ex.name) }
        }
    }
    var custom by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text("Other exercise") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { if (custom.isNotBlank()) viewModel.addExercise(custom.trim()); custom = "" }) { Text("Add") }
    }
    OutlinedButton(onClick = onScanMachine, modifier = Modifier.fillMaxWidth()) {
        Text("Not sure what a machine is? Scan it")
    }
}

@Composable
private fun ExerciseCard(exercise: ExerciseEntry, state: WorkoutLogUiState, viewModel: WorkoutLogViewModel) {
    val suggestion = exercise.suggestion
    var weight by remember(exercise.name) { mutableStateOf(suggestion.weightKg?.let { fmt(it) } ?: "") }
    var reps by remember(exercise.name) { mutableStateOf(suggestion.reps.first.toString()) }
    var rir by remember(exercise.name) { mutableStateOf(exercise.prescription.rir.toString()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                exercise.name + if (exercise.weakPoint) "  ★ weak point" else "",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(exercise.prescription.summary, style = MaterialTheme.typography.bodySmall)
            if (exercise.lastSets.isNotEmpty()) {
                Text(
                    "Last time: " + exercise.lastSets.joinToString(", ") { "${fmt(it.weightKg)}×${it.reps}" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("Today: ${suggestion.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

            state.todaysSets.filter { it.exerciseName == exercise.name }.forEachIndexed { i, set ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Set ${i + 1}: ${fmt(set.weightKg)} kg × ${set.reps}" + (set.rir?.let { " · $it in reserve" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.deleteSet(set) }) { Text("Undo") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("kg", weight, Modifier.weight(1f)) { weight = it }
                NumberField("Reps", reps, Modifier.weight(1f)) { reps = it }
                NumberField("In reserve", rir, Modifier.weight(1f)) { rir = it }
            }
            Button(
                onClick = {
                    viewModel.logSet(exercise, weight.replace(',', '.').toDoubleOrNull() ?: 0.0, reps.toIntOrNull() ?: 0, rir.toIntOrNull())
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log set") }
        }
    }
}

@Composable
private fun SportLog(state: WorkoutLogUiState, viewModel: WorkoutLogViewModel) {
    Text("Sport", style = MaterialTheme.typography.titleSmall)
    Sport.entries.filter { it != Sport.STRENGTH }.chunked(4).forEach { row ->
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { sport ->
                FilterChip(selected = state.sport == sport, onClick = { viewModel.setSport(sport) }, label = { Text(sport.label) })
            }
        }
    }
    var minutes by remember(state.minutes) { mutableStateOf(state.minutes.toString()) }
    NumberField("Duration (min)", minutes) { v -> minutes = v; v.toIntOrNull()?.let(viewModel::setMinutes) }
    Text("Intensity", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Intensity.entries.forEach { i ->
            FilterChip(selected = state.intensity == i, onClick = { viewModel.setIntensity(i) }, label = { Text(i.label) })
        }
    }
    if (state.sport.hasDistance) {
        var distance by remember { mutableStateOf(state.distanceKm?.toString() ?: "") }
        NumberField("Distance (km, optional)", distance) { v ->
            distance = v
            viewModel.setDistance(v.replace(',', '.').toDoubleOrNull())
        }
    }
    Text(
        "About ${state.estimatedKcal} kcal above rest at ${fmt(state.weightKg)} kg.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = viewModel::saveSport, modifier = Modifier.fillMaxWidth()) { Text("Save session") }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun fmt(kg: Double) = if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.1f".format(kg)
