package com.squeeze.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import java.time.LocalDate

/** Preset horizons, in weeks. */
private val HORIZONS = listOf(8, 12, 16, 24)

/**
 * The user's target and its deadline.
 *
 * Both are collected together and neither is optional once the section is used, because
 * separately they are useless. A target with no date cannot be behind schedule, so the app
 * can never tell the user anything about it; a date with no target is a deadline for
 * nothing. Together they produce a required rate, which is the only thing the measured rate
 * can honestly be compared against.
 *
 * The horizon is offered as presets rather than a date picker. Nobody's real goal is "17
 * September"; it is "before the summer" or "in three months", and a picker turns a choice
 * of pace into a calendar puzzle. The resulting date is shown so the abstraction stays
 * visible.
 */
@Composable
fun GoalSection(
    targetBodyFatPercent: Double?,
    targetEpochDay: Long?,
    onGoalChange: (Double?, Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted
    val today = LocalDate.now()

    var targetText by remember(targetBodyFatPercent) {
        mutableStateOf(targetBodyFatPercent?.let { "%.0f".format(it) } ?: "")
    }
    var weeks by remember(targetEpochDay) {
        mutableStateOf(
            targetEpochDay
                ?.let { ((it - today.toEpochDay()) / 7.0).toInt() }
                ?.takeIf { it > 0 }
                ?: 12,
        )
    }
    var justSaved by remember { mutableStateOf(false) }

    val parsed = targetText.trim().replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed in 3.0..60.0
    val deadline = today.plusWeeks(weeks.toLong())

    BrandCard(modifier.fillMaxWidth()) {
        Text("Your goal", style = MaterialTheme.typography.titleSmall)

        Text(
            text = "A target with a date is what lets the app tell you whether what you are " +
                "doing is working. Without one it can only show you a number.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = targetText,
            onValueChange = { justSaved = false; targetText = it.take(4) },
            label = { Text("Target body fat (%)") },
            isError = targetText.isNotBlank() && !valid,
            supportingText = if (targetText.isNotBlank() && !valid) {
                { Text("Enter a percentage between 3 and 60.") }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "By when",
            style = MaterialTheme.typography.labelLarge,
            color = muted,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HORIZONS.forEach { option ->
                FilterChip(
                    selected = weeks == option,
                    onClick = { justSaved = false; weeks = option },
                    label = { Text("${option}w") },
                )
            }
        }

        Text(
            text = "That is ${deadline.dayOfMonth} ${deadline.month.name.lowercase()
                .replaceFirstChar { it.uppercase() }} ${deadline.year}.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton(
                text = if (justSaved) "Saved" else "Save goal",
                onClick = {
                    if (valid) {
                        onGoalChange(parsed, deadline.toEpochDay())
                        justSaved = true
                    }
                },
            )

            if (targetBodyFatPercent != null) {
                TextButton(onClick = { justSaved = false; onGoalChange(null, null) }) {
                    Text("Remove goal")
                }
            }
        }
    }
}
