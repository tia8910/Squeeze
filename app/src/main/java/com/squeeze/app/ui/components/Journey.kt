package com.squeeze.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * The top of the dashboard, in three short cards:
 *
 *  1. **Next step** — the one thing to do now, with one button. During setup it also shows how
 *     far along the user is, as a thin bar rather than a checklist.
 *  2. **Today** — one line each for training, food and the physique focus, each tappable.
 *  3. **Coach tips** — what today holds (session timing, rest day), then one for the goal.
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
        HeroCard(Modifier.fillMaxWidth().entrance(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (journey.setupComplete) "NEXT UP" else "STEP ${journey.setupDone + 1} OF ${journey.setup.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Text(next.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                // Setup progress, or this week's sessions once set up.
                val (done, total) = if (!journey.setupComplete) {
                    journey.setupDone to journey.setup.size
                } else {
                    summary.sessionsDone to (summary.sessionsPlanned ?: 0)
                }
                if (total > 0) {
                    ProgressRing(
                        progress = done.toFloat() / total,
                        size = 64.dp,
                        stroke = 7.dp,
                        track = Color.White.copy(alpha = 0.2f),
                    ) {
                        Text("$done/$total", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Text(
                next.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
            )
            if (!journey.setupComplete) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    journey.setup.forEach { step ->
                        Text(
                            (if (step.done) "✓ " else "") + shortLabel(step.id),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (step.done || step.id == next.id) Color.White else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = if (step.id == next.id) 0.22f else 0.1f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            HeroButton(text = next.action, onClick = { onStep(next.id) })
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
        BrandCard(Modifier.fillMaxWidth().entrance(1)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TODAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                lines.forEachIndexed { i, (label, body, onClick) ->
                    if (i > 0) HorizontalDivider()
                    TodayLine(label, body, onClick)
                }
            }
        }
    }

    if (summary.tips.isNotEmpty()) {
        BrandCard(Modifier.fillMaxWidth().entrance(2)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("COACH TIPS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                summary.tips.forEach { tip ->
                    Row {
                        Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
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
        Box(
            Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when (label) {
                    "Train" -> Icons.Rounded.FitnessCenter
                    "Eat" -> Icons.Rounded.Restaurant
                    else -> Icons.Rounded.CenterFocusStrong
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.padding(start = 12.dp).width(52.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
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

private fun shortLabel(id: StepId) = when (id) {
    StepId.AI_SCAN -> "Scan"
    StepId.CHOOSE_SPORTS -> "Sports"
    StepId.PICK_FOODS -> "Foods"
    StepId.TODAYS_SESSION -> "Train"
    StepId.WEEKLY_CHECK_IN -> "Check-in"
}
