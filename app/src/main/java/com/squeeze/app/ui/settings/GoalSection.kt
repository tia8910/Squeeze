package com.squeeze.app.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.squeeze.core.model.Goal
import java.time.LocalDate

/** Preset horizons, in weeks. */
private val HORIZONS = listOf(8, 12, 16, 24)

/**
 * The goals the app can actually judge, and what each one needs to be judged against.
 *
 * A goal is only useful here if the app can tell the user whether it is working, and that
 * takes a target it can measure the distance to. So each option below names the fields it
 * requires rather than offering every field to everyone: someone building muscle has no
 * reason to type a body fat figure, and asking for one produces a number they invented and
 * the app then reports on.
 *
 * Recomposition is the case that motivated all of this. It cannot be stated as one number in
 * either direction — the whole point is holding weight while the percentage falls, or holding
 * the percentage while weight rises — so it is the one goal that needs both fields, and the
 * old body-fat-only form could not express it at all.
 */
private enum class GoalOption(
    val goal: Goal,
    val label: String,
    val blurb: String,
    val wantsBodyFat: Boolean,
    val wantsWeight: Boolean,
) {
    LOSE_FAT(
        Goal.CUT,
        "Lose fat",
        "Bring body fat down while defending the muscle you have.",
        wantsBodyFat = true,
        wantsWeight = false,
    ),
    BUILD_MUSCLE(
        Goal.HYPERTROPHY,
        "Build muscle",
        "Add size, accepting some fat gain. Judged on the scale and on your lean mass.",
        wantsBodyFat = false,
        wantsWeight = true,
    ),
    RECOMP(
        Goal.RECOMP,
        "Both at once",
        "Hold your weight while the percentage falls — muscle up, fat down. Slower than " +
            "either alone, and the only goal that needs both numbers to be checkable.",
        wantsBodyFat = true,
        wantsWeight = true,
    ),
    REACH_WEIGHT(
        Goal.MAKE_WEIGHT,
        "Reach a weight",
        "A number on the scale by a date, up or down.",
        wantsBodyFat = false,
        wantsWeight = true,
    ),
    ;

    companion object {
        fun of(goal: Goal): GoalOption = entries.firstOrNull { it.goal == goal } ?: LOSE_FAT
    }
}

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
    goal: Goal,
    targetBodyFatPercent: Double?,
    targetWeightKg: Double?,
    targetEpochDay: Long?,
    onGoalChange: (Goal, Double?, Double?, Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted
    val today = LocalDate.now()

    var option by remember(goal) { mutableStateOf(GoalOption.of(goal)) }
    var fatText by remember(targetBodyFatPercent) {
        mutableStateOf(targetBodyFatPercent?.let { "%.0f".format(it) } ?: "")
    }
    var weightText by remember(targetWeightKg) {
        mutableStateOf(targetWeightKg?.let { "%.1f".format(it) } ?: "")
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

    val fat = fatText.trim().replace(',', '.').toDoubleOrNull()
    val weight = weightText.trim().replace(',', '.').toDoubleOrNull()
    val fatValid = fat != null && fat in 3.0..60.0
    val weightValid = weight != null && weight in 30.0..300.0

    // Every field the chosen goal asks for has to be filled. Saving a recomp with only a
    // weight would store a goal the progress card then cannot report half of.
    val valid = (!option.wantsBodyFat || fatValid) && (!option.wantsWeight || weightValid)
    val deadline = today.plusWeeks(weeks.toLong())
    val hasGoal = targetBodyFatPercent != null || targetWeightKg != null

    BrandCard(modifier.fillMaxWidth()) {
        Text("Your goal", style = MaterialTheme.typography.titleSmall)

        Text(
            text = "A target with a date is what lets the app tell you whether what you are " +
                "doing is working. Without one it can only show you a number.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            GoalOption.entries.forEach { candidate ->
                FilterChip(
                    selected = option == candidate,
                    onClick = { justSaved = false; option = candidate },
                    label = { Text(candidate.label) },
                )
            }
        }

        Text(
            text = option.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )

        if (option.wantsBodyFat) {
            OutlinedTextField(
                value = fatText,
                onValueChange = { justSaved = false; fatText = it.take(4) },
                label = { Text("Target body fat (%)") },
                isError = fatText.isNotBlank() && !fatValid,
                supportingText = if (fatText.isNotBlank() && !fatValid) {
                    { Text("Enter a percentage between 3 and 60.") }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        if (option.wantsWeight) {
            OutlinedTextField(
                value = weightText,
                onValueChange = { justSaved = false; weightText = it.take(5) },
                label = { Text("Target weight (kg)") },
                isError = weightText.isNotBlank() && !weightValid,
                supportingText = if (weightText.isNotBlank() && !weightValid) {
                    { Text("Enter a weight between 30 and 300 kg.") }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        Text(
            text = "By when",
            style = MaterialTheme.typography.labelLarge,
            color = muted,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HORIZONS.forEach { horizon ->
                FilterChip(
                    selected = weeks == horizon,
                    onClick = { justSaved = false; weeks = horizon },
                    label = { Text("${horizon}w") },
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
                        onGoalChange(
                            option.goal,
                            fat.takeIf { option.wantsBodyFat },
                            weight.takeIf { option.wantsWeight },
                            deadline.toEpochDay(),
                        )
                        justSaved = true
                    }
                },
            )

            if (hasGoal) {
                TextButton(onClick = { justSaved = false; onGoalChange(option.goal, null, null, null) }) {
                    Text("Remove goal")
                }
            }
        }
    }
}
