package com.squeeze.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.model.ProfileValidation
import com.squeeze.core.model.Sex
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Height, sex and year of birth, committed explicitly.
 *
 * These are not optional preferences: every body-fat equation is sex-specific and
 * age-dependent, and the photo scan uses height as its scale reference, so nothing in the
 * app produces a number until they are set.
 *
 * Edits are held locally and only written when the user confirms them. Saving on every
 * keystroke — which this screen used to do — persists every intermediate state on the way
 * to a real value: typing "175" stored a height of 1, then 17, then 175. Height is the
 * scale reference for the entire photo scan, so a half-typed value left behind by someone
 * who navigated away mid-edit would rescale every measurement they take afterwards.
 *
 * The button also gives the screen something it lacked entirely: a way to know the change
 * was accepted.
 */
@Composable
fun ProfileSection(
    heightCm: Double?,
    birthYear: Int?,
    sex: Sex?,
    onProfileChange: (heightCm: Double?, birthYear: Int?, sex: Sex?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the stored values so an external change reloads the fields, but otherwise
    // local — typing does not reach storage until Save.
    var heightText by remember(heightCm) {
        mutableStateOf(heightCm?.let { "%.0f".format(it) } ?: "")
    }
    var yearText by remember(birthYear) { mutableStateOf(birthYear?.toString() ?: "") }
    var pendingSex by remember(sex) { mutableStateOf(sex) }
    var justSaved by remember { mutableStateOf(false) }

    val currentYear = LocalDate.now().year
    val parsedHeight = heightText.toDoubleOrNull()
    val parsedYear = yearText.toIntOrNull()

    // The same rules onboarding uses. Kept in :core precisely so these two screens cannot
    // disagree about what a valid height is.
    val heightError = ProfileValidation.heightError(heightText)
    val yearError = ProfileValidation.birthYearError(yearText, currentYear)

    val complete = ProfileValidation.isComplete(heightText, yearText, pendingSex, currentYear)
    val changed = parsedHeight != heightCm || parsedYear != birthYear || pendingSex != sex

    // The confirmation clears itself; a tick that stays put stops meaning "just now".
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(2_500)
            justSaved = false
        }
    }

    val muted = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted

    BrandCard(modifier.fillMaxWidth()) {
        Text("About you", style = MaterialTheme.typography.titleSmall)

        Text(
            text = "Body fat equations are sex- and age-specific, and the photo scan uses " +
                "your height to convert the image into real measurements. Be accurate with " +
                "height: every measurement scales with it.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )

        OutlinedTextField(
            value = heightText,
            onValueChange = { text ->
                heightText = text.filter { it.isDigit() || it == '.' }.take(5)
                justSaved = false
            },
            label = { Text("Height (cm)") },
            isError = heightError != null,
            supportingText = heightError?.let { { Text(it.message) } },
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
                justSaved = false
            },
            label = { Text("Year of birth") },
            isError = yearError != null,
            supportingText = yearError?.let { { Text(it.message) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )

        Text(
            text = "Equation variant",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { option ->
                FilterChip(
                    selected = pendingSex == option,
                    onClick = {
                        pendingSex = option
                        justSaved = false
                    },
                    label = { Text(if (option == Sex.MALE) "Male" else "Female") },
                )
            }
        }

        Text(
            // Stated plainly because the field is unavoidable but its purpose is narrow.
            text = "This selects which validated equation is used. The equations were " +
                "derived from sex-separated study groups and have no defined form outside them.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        )

        PrimaryButton(
            text = when {
                justSaved -> "Saved"
                else -> "Save"
            },
            onClick = {
                onProfileChange(parsedHeight, parsedYear, pendingSex)
                justSaved = true
            },
            enabled = complete && changed,
            leading = if (justSaved) {
                { Icon(Icons.Default.Check, contentDescription = null) }
            } else {
                null
            },
        )

        if (!complete) {
            Text(
                text = "All three are needed before the app can estimate anything.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
