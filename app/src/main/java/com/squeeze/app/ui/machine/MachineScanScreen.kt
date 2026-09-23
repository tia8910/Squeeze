package com.squeeze.app.ui.machine

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.core.workout.MachineGuide

/** Photograph a gym machine and learn how to use it, for your goal. */
@Composable
fun MachineScanScreen(
    onLog: () -> Unit,
    viewModel: MachineScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let(viewModel::onPhoto)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(null)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPicked)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Scan a machine", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Point the camera at a gym machine. The on-device AI names it and shows how to use it, " +
                "with sets and reps for your goal. The photo never leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) camera.launch(null) else permission.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.weight(1f),
            ) { Text("Take photo") }
            OutlinedButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f),
            ) { Text("From gallery") }
        }

        state.photo?.let { photo ->
            val image = remember(photo) { photo.asImageBitmap() }
            Image(
                image,
                contentDescription = "The machine photographed",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            )
        }

        if (state.analysing) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.padding(4.dp))
                Text("AI identifying the machine…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        state.match?.let { match ->
            val top = match.candidates.firstOrNull()
            val headline = when {
                match.noMachine -> "No gym equipment found in this photo. Get the whole machine in frame and try again — or pick it below."
                match.confident && top != null -> "Identified: ${top.first.name} (${(top.second * 100).toInt()}% sure). Not this one? Pick below."
                top != null -> "Best guess: ${top.first.name} (${(top.second * 100).toInt()}%). Not this one? Pick below."
                else -> null
            }
            headline?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                match.candidates.forEach { (guide, p) ->
                    FilterChip(
                        selected = state.selected == guide,
                        onClick = { viewModel.select(guide) },
                        label = { Text("${guide.name} · ${(p * 100).toInt()}%") },
                    )
                }
            }
        }

        state.selected?.let { guide ->
            GuideCard(guide, state)
            WorkoutOnMachine(guide, state, viewModel, onOpenLog = { viewModel.prepareLog(); onLog() })
        }
    }
}

@Composable
private fun GuideCard(guide: MachineGuide, state: MachineScanUiState) {
    var steps by androidx.compose.runtime.saveable.rememberSaveable(guide.id) { androidx.compose.runtime.mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(guide.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Works: ${guide.muscles}", style = MaterialTheme.typography.bodyMedium)
            if (state.weakPoint) {
                Text(
                    "★ Your AI scan flagged this area as a weak point — one extra set is built in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(guide.setup, style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.TextButton(onClick = { steps = !steps }) {
                Text(if (steps) "Hide how-to" else "How to do it · mistakes to avoid")
            }
            if (steps) {
                guide.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
                guide.mistakes.forEach { Text("✗ $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/**
 * The workout itself, on the scanner screen: pick which exercise, then log it set by set with
 * the coach's call before each set; cardio machines log as a session.
 */
@Composable
private fun WorkoutOnMachine(
    guide: MachineGuide,
    state: MachineScanUiState,
    viewModel: MachineScanViewModel,
    onOpenLog: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (guide.cardio) {
                var minutes by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("20") }
                var intensity by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(com.squeeze.core.workout.Intensity.MODERATE) }
                Text(guide.steps.getOrNull(1) ?: "", style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    label = { Text("Minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.squeeze.core.workout.Intensity.entries.forEach { i ->
                        FilterChip(selected = intensity == i, onClick = { intensity = i }, label = { Text(i.label) })
                    }
                }
                Button(onClick = { viewModel.finish(minutes.toIntOrNull() ?: 20, intensity) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save ${guide.name.lowercase()} session")
                }
            } else {
                if (state.workouts.size > 1) {
                    Text("Exercises on this machine", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.workouts.forEach { ex ->
                            FilterChip(
                                selected = state.exercise == ex,
                                onClick = { viewModel.chooseExercise(ex) },
                                label = { Text(ex.name) },
                            )
                        }
                    }
                }
                state.exercise?.let { ex ->
                    if (ex.name != guide.exercise) Text(ex.cue, style = MaterialTheme.typography.bodySmall)
                    state.prescription?.let { p ->
                        com.squeeze.app.ui.progress.SetLogger(
                            exercise = ex.name,
                            prescription = p,
                            suggestion = state.suggestion,
                            lastSets = state.lastSets,
                            todaysSets = state.todaysSets,
                            compound = ex.compound,
                            lowerBody = ex.lowerBody,
                            onLog = viewModel::logSet,
                            onDelete = viewModel::deleteSet,
                        )
                    }
                }
                if (state.todaysSets.isNotEmpty()) {
                    var minutes by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("45") }
                    androidx.compose.material3.OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = { Text("Session length (min)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { viewModel.finish(minutes.toIntOrNull() ?: 45) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Finish workout")
                    }
                }
            }
            state.saved?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            androidx.compose.material3.TextButton(onClick = onOpenLog, modifier = Modifier.fillMaxWidth()) {
                Text("Open the full workout log ›")
            }
        }
    }
    state.summary?.let { com.squeeze.app.ui.progress.WorkoutSummaryCard(it) }
}
