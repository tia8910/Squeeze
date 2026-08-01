package com.squeeze.app.ui.measurement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Manual measurement entry.
 *
 * This is the path that does not depend on a camera, a model, or good lighting, and it is
 * the most accurate one available: a tape read carefully has better repeatability than any
 * photo method. The scan exists for convenience; this exists for precision.
 */
@Composable
fun AddMeasurementScreen(
    onSaved: () -> Unit,
    viewModel: AddMeasurementViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) { if (saved) onSaved() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GuidanceCard()

        Section("Weight") {
            NumberField(
                value = form.weightKg,
                onValueChange = { v -> viewModel.update { it.copy(weightKg = v) } },
                label = "Bodyweight (kg)",
            )
        }

        Section("Tape measurements") {
            // Neck and waist come first because they are the two the body-fat equation
            // actually needs; the rest are tracked for their own sake.
            NumberField(form.neckCm, { v -> viewModel.update { it.copy(neckCm = v) } }, "Neck (cm)")
            NumberField(form.waistCm, { v -> viewModel.update { it.copy(waistCm = v) } }, "Waist (cm)")
            NumberField(form.hipCm, { v -> viewModel.update { it.copy(hipCm = v) } }, "Hip (cm)")
            NumberField(form.chestCm, { v -> viewModel.update { it.copy(chestCm = v) } }, "Chest (cm)")
            NumberField(form.thighCm, { v -> viewModel.update { it.copy(thighCm = v) } }, "Thigh (cm)")
            NumberField(form.armCm, { v -> viewModel.update { it.copy(armCm = v) } }, "Arm (cm)")
            NumberField(form.calfCm, { v -> viewModel.update { it.copy(calfCm = v) } }, "Calf (cm)")

            if (!form.canEstimateBodyFat) {
                Text(
                    text = "Neck and waist are both needed for a body fat estimate. Without " +
                        "them these measurements are still tracked individually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("Reference scan") {
            NumberField(
                value = form.referenceBodyFat,
                onValueChange = { v -> viewModel.update { it.copy(referenceBodyFat = v) } },
                label = "DEXA / BodPod body fat (%)",
            )
            Text(
                // The single highest-value thing a user can enter, so it gets an explanation
                // rather than sitting unlabelled at the bottom of a form.
                text = "Entering even one scan result calibrates every future measurement to " +
                    "your body, removing the fixed offset every population equation carries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Note") {
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                label = { Text("Optional") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = viewModel::save,
            enabled = form.hasAnything,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save measurement")
        }
    }
}

@Composable
private fun GuidanceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Measure the same way every time", style = MaterialTheme.typography.titleSmall)
            Text(
                // Protocol advice beats algorithm advice here: consistency is the single
                // biggest lever the user controls over whether their trend is readable.
                text = "First thing in the morning, before eating or drinking, tape snug but " +
                    "not compressing. Waist at the navel, neck below the larynx. Consistency " +
                    "matters more than getting the absolute number right.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        // Filtering here rather than validating on submit means the field cannot hold
        // something unparseable in the first place.
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

