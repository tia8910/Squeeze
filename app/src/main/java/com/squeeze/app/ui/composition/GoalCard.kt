package com.squeeze.app.ui.composition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.bodycomp.GoalProgress
import com.squeeze.core.bodycomp.GoalVerdict
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where the user stands against their deadline, and what to change.
 *
 * Placed high on the dashboard because it is the only thing here that answers "so what".
 * The hero number says where the user is and the chart says how they got there; neither
 * says whether it is enough, and "enough" is the question a goal exists to make answerable.
 *
 * The copy is arithmetic rather than encouragement throughout. Someone eight weeks from a
 * deadline they will miss by four points is not helped by being told to keep going, and is
 * helped a great deal by being told the daily calorie gap between the rate they have and
 * the rate they need.
 */
@Composable
fun GoalCard(progress: GoalProgress, onEditGoal: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val muted = if (dark) Brand.DarkMuted else Brand.Muted

    // Colour states the verdict before a word is read, and only two states earn a warning
    // tint: one where the plan is failing and one where it was never possible.
    val accent = when (progress.verdict) {
        GoalVerdict.ON_TRACK -> if (dark) Brand.DarkBlue else Brand.BlueDeep
        GoalVerdict.BEHIND, GoalVerdict.WRONG_DIRECTION, GoalVerdict.UNREALISTIC -> Brand.Warning
        GoalVerdict.TOO_EARLY -> muted
    }

    BrandCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (progress.verdict) {
                    GoalVerdict.ON_TRACK -> "On track"
                    GoalVerdict.BEHIND -> "Behind schedule"
                    GoalVerdict.WRONG_DIRECTION -> "Going the wrong way"
                    GoalVerdict.UNREALISTIC -> "That date is not reachable"
                    GoalVerdict.TOO_EARLY -> "Too early to tell"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = remainingLabel(progress.daysRemaining),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
        }

        Text(
            text = progress.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp),
        )

        // Both rates side by side, because the whole judgement is the comparison between
        // them and stating only the verdict asks the user to take it on trust.
        if (progress.verdict != GoalVerdict.TOO_EARLY && progress.requiredRatePerWeek != 0.0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                RateColumn("Needed", progress.requiredRatePerWeek, muted)
                progress.actualRatePerWeek?.let { RateColumn("Actual", it, muted) }
            }
        }

        if (progress.actions.isNotEmpty()) {
            Text(
                text = "What to change",
                style = MaterialTheme.typography.labelLarge,
                color = muted,
                modifier = Modifier.padding(top = 18.dp),
            )
            progress.actions.forEach { action ->
                Text(
                    text = "· $action",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        TextButton(onClick = onEditGoal, modifier = Modifier.padding(top = 4.dp)) {
            Text("Change goal or date")
        }
    }
}

@Composable
private fun RateColumn(label: String, ratePerWeek: Double, muted: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
        Text(
            // Signed, because the direction is half the information: "0.30 a week" reads as
            // progress whichever way the body is actually going.
            text = "%s%.2f pts/wk".format(if (ratePerWeek < 0) "−" else "+", abs(ratePerWeek)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun remainingLabel(days: Long): String = when {
    days < 0 -> "${-days} days over"
    days == 0L -> "today"
    days < 14 -> "$days days left"
    else -> "${(days / 7.0).roundToInt()} weeks left"
}
