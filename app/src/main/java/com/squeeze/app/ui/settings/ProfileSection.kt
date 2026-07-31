package com.squeeze.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.squeeze.core.model.Sex

/**
 * Height, sex and year of birth.
 *
 * These are not optional preferences: every body-fat equation is sex-specific and
 * age-dependent, and the photo scan uses height as its scale reference, so nothing in the
 * app produces a number until they are set. The copy says so rather than leaving the user
 * to discover it when a scan fails.
 */
@Composable
fun ProfileSection(
    heightCm: Double?,
    birthYear: Int?,
    sex: Sex?,
    onProfileChange: (heightCm: Double?, birthYear: Int?, sex: Sex?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var heightText by remember(heightCm) { mutableStateOf(heightCm?.let { "%.0f".format(it) } ?: "") }
    var yearText by remember(birthYear) { mutableStateOf(birthYear?.toString() ?: "") }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("About you", style = MaterialTheme.typography.titleSmall)

            Text(
                text = "Body fat equations are sex- and age-specific, and the photo scan uses " +
                    "your height to convert the image into real measurements. Be accurate " +
                    "with height: every measurement scales with it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = heightText,
                onValueChange = { text ->
                    heightText = text.filter { it.isDigit() || it == '.' }
                    onProfileChange(heightText.toDoubleOrNull(), yearText.toIntOrNull(), sex)
                },
                label = { Text("Height (cm)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = yearText,
                onValueChange = { text ->
                    yearText = text.filter { it.isDigit() }.take(4)
                    onProfileChange(heightText.toDoubleOrNull(), yearText.toIntOrNull(), sex)
                },
                label = { Text("Year of birth") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Equation variant", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = {
                            onProfileChange(heightText.toDoubleOrNull(), yearText.toIntOrNull(), option)
                        },
                        label = { Text(if (option == Sex.MALE) "Male" else "Female") },
                    )
                }
            }

            Text(
                // Stated plainly because the field is unavoidable but its purpose is narrow.
                text = "This selects which validated equation is used. The equations were " +
                    "derived from sex-separated study groups and have no defined form outside " +
                    "them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
