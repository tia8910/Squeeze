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
            val headline = when {
                match.noMachine -> "I don't see gym equipment in this photo. Try again with the whole machine in frame — or pick it below."
                !match.confident -> "Not sure — which one is it?"
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

        state.selected?.let { guide -> GuideCard(guide, state) { viewModel.prepareLog(); onLog() } }
    }
}

@Composable
private fun GuideCard(guide: MachineGuide, state: MachineScanUiState, onLog: () -> Unit) {
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
            state.prescription?.let { p ->
                Text("For your goal: ${p.summary}", style = MaterialTheme.typography.titleSmall)
            }
            state.suggestion?.let { Text("Today: ${it.reason}", style = MaterialTheme.typography.bodySmall) }

            Text("Set up", style = MaterialTheme.typography.titleSmall)
            Text(guide.setup, style = MaterialTheme.typography.bodyMedium)
            Text("How to do it", style = MaterialTheme.typography.titleSmall)
            guide.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
            Text("Avoid", style = MaterialTheme.typography.titleSmall)
            guide.mistakes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }

            Button(onClick = onLog, modifier = Modifier.fillMaxWidth()) {
                Text(if (guide.cardio) "Log this session" else "Log sets on this machine")
            }
        }
    }
}
