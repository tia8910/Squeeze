package com.squeeze.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.squeeze.app.data.DashboardSummary
import com.squeeze.core.coach.JourneyStep
import com.squeeze.core.coach.StepId

/**
 * The top of the dashboard, in two cards and no more:
 *
 *  1. **Next step** — the one thing to do now, with one button. During setup it also shows how
 *     far along the user is, as a thin bar rather than a checklist.
 *  2. **Today** — one line each for training, food and the physique focus, each tappable.
 *
 * Everything else lives on its own tab; the dashboard's job is to say what to do and where.
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
    val next = journey.next

    if (next != null) {
        BrandCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (journey.setupComplete) "NEXT" else "STEP ${journey.setupDone + 1} OF ${journey.setup.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (!journey.setupComplete) {
                        Text(
                            journey.setup.joinToString("  ") { (if (it.done) "✓ " else "") + it.title.substringBefore(' ') },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!journey.setupComplete) {
                    LinearProgressIndicator(
                        progress = { journey.setupDone.toFloat() / journey.setup.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(next.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(next.detail, style = MaterialTheme.typography.bodyMedium)
                PrimaryButton(text = next.action, onClick = { onStep(next.id) })
            }
        }
    } else {
        NoticePill("You're all caught up today ✓")
    }

    val lines = buildList {
        summary.todaysTraining?.let { training ->
            add(
                Triple(
                    "Train",
                    training + (summary.sessionsPlanned?.let { " · ${summary.sessionsDone}/$it this week" } ?: ""),
                    onOpenTraining,
                ),
            )
        }
        summary.fuelToday?.let { add(Triple("Eat", it, onOpenNutrition)) }
        if (summary.weakPoints.isNotEmpty() || summary.strengths.isNotEmpty()) {
            add(
                Triple(
                    "Focus",
                    listOfNotNull(
                        summary.weakPoints.takeIf { it.isNotEmpty() }?.joinToString(),
                        summary.strengths.takeIf { it.isNotEmpty() }?.let { "strong: ${it.joinToString()}" },
                    ).joinToString(" · "),
                    onOpenScan,
                ),
            )
        }
    }
    if (lines.isNotEmpty()) {
        BrandCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TODAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                lines.forEachIndexed { i, (label, body, onClick) ->
                    if (i > 0) HorizontalDivider()
                    TodayLine(label, body, onClick)
                }
            }
        }
    }
}

@Composable
private fun TodayLine(label: String, body: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
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
        }
        Text(
            "${step.action} ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
