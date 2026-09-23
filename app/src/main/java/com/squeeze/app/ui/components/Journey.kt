package com.squeeze.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.app.data.DashboardSummary
import com.squeeze.core.coach.JourneyStep
import com.squeeze.core.coach.StepId

/**
 * The top of the dashboard: the one next step, how far through setup the user is, and a line
 * from every feature — each tappable, so the dashboard is the way into everything.
 */
@Composable
fun JourneyCard(
    summary: DashboardSummary,
    onStep: (StepId) -> Unit,
    onOpenTraining: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenScan: () -> Unit,
) {
    val journey = summary.journey
    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!journey.setupComplete) {
                Text(
                    "Setting up · ${journey.setupDone} of ${journey.setup.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(
                    progress = { journey.setupDone.toFloat() / journey.setup.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                journey.setup.forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (step.done) "✓" else "${i + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (step.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            step.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (step.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            val next = journey.next
            if (next != null) {
                NextStepBlock(next) { onStep(next.id) }
            } else {
                Text("You're all caught up today ✓", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Everything is logged. Come back tomorrow — the plan moves with you.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    // A line from every feature.
    summary.todaysTraining?.let { today ->
        SummaryRow(
            "Training",
            today + (summary.sessionsPlanned?.let { " · ${summary.sessionsDone} of $it sessions this week" } ?: "") +
                (if (summary.setsDone > 0) " · ${summary.setsDone} sets" else ""),
            "Train",
            onOpenTraining,
        )
    }
    summary.fuelToday?.let { fuel ->
        SummaryRow(
            "Fuel today",
            fuel + (summary.microGaps.takeIf { it.isNotEmpty() }?.let { "\nWatch: ${it.joinToString()}" } ?: ""),
            "Plan",
            onOpenNutrition,
        )
    }
    if (summary.strengths.isNotEmpty() || summary.weakPoints.isNotEmpty()) {
        SummaryRow(
            "AI physique",
            listOfNotNull(
                summary.strengths.takeIf { it.isNotEmpty() }?.let { "Strong: ${it.joinToString()}" },
                summary.weakPoints.takeIf { it.isNotEmpty() }?.let { "Focus: ${it.joinToString()} (prioritised in training)" },
            ).joinToString("\n"),
            "Rescan",
            onOpenScan,
        )
    }
}

@Composable
private fun SummaryRow(title: String, body: String, action: String, onClick: () -> Unit) {
    BrandRow(onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "$action ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun NextStepBlock(step: JourneyStep, onGo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("NEXT STEP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(step.detail, style = MaterialTheme.typography.bodyMedium)
        Text("Unlocks: ${step.unlocks}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton(text = step.action, onClick = onGo)
    }
}

/**
 * The hand-off at the end of a flow: what to do next, one tap away. Shown wherever a step
 * finishes so the user is never left wondering where to go.
 */
@Composable
fun NextStepBanner(step: JourneyStep, onGo: () -> Unit, modifier: Modifier = Modifier) {
    BrandRow(modifier = modifier.fillMaxWidth(), onClick = onGo) {
        Column(Modifier.weight(1f)) {
            Text("Next step", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(step.title, style = MaterialTheme.typography.titleSmall)
            Text("Unlocks: ${step.unlocks}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "${step.action} ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
