package com.squeeze.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    // Today, as tiles: what to eat, how much protein, and what the AI says to focus on —
    // each one a door into its tab.
    val dark = com.squeeze.app.ui.theme.LocalIsDarkTheme.current
    val muted = if (dark) com.squeeze.app.ui.theme.Brand.DarkMuted else com.squeeze.app.ui.theme.Brand.Muted
    val fuel = summary.fuel
    if (fuel != null || summary.todaysTraining != null) {
        Row(Modifier.fillMaxWidth().entrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fuel?.let {
                MetricTile(
                    icon = Icons.Rounded.Restaurant,
                    label = if (summary.trainingToday) "EAT · TRAINING DAY" else "EAT TODAY",
                    value = "%,d".format(it.calories),
                    unit = "kcal",
                    detail = "${it.carbsG} C · ${it.fatG} F",
                    onClick = onOpenNutrition,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    icon = Icons.Rounded.WaterDrop,
                    label = "PROTEIN",
                    value = it.proteinG.toString(),
                    unit = "g",
                    detail = "spread over 4 meals",
                    onClick = onOpenNutrition,
                    modifier = Modifier.weight(1f),
                )
            } ?: summary.todaysTraining?.let { training ->
                MetricTile(
                    icon = Icons.Rounded.FitnessCenter,
                    label = "TRAIN TODAY",
                    value = summary.sessionsDone.toString(),
                    unit = "/ ${summary.sessionsPlanned ?: 0} this week",
                    detail = training,
                    onClick = onOpenTraining,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (summary.weakPoints.isNotEmpty() || summary.strengths.isNotEmpty()) {
        BrandCard(Modifier.fillMaxWidth().entrance(2).clickable(onClick = onOpenScan), contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.CenterFocusStrong)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("AI FOCUS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = muted)
                    Text(
                        listOfNotNull(
                            summary.weakPoints.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                        ).joinToString().ifEmpty { "Balanced" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (summary.strengths.isNotEmpty()) {
                        Text("Strong: ${summary.strengths.joinToString()}", style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (summary.tips.isNotEmpty()) TipCarousel(summary.tips, Modifier.entrance(3))
}

/** A small number tile: icon and label, the value, one line under it. */
@Composable
private fun MetricTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    unit: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = com.squeeze.app.ui.theme.LocalIsDarkTheme.current
    val muted = if (dark) com.squeeze.app.ui.theme.Brand.DarkMuted else com.squeeze.app.ui.theme.Brand.Muted
    BrandCard(modifier.clickable(onClick = onClick), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = muted, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(" $unit", style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/**
 * One coach tip at a time, sliding to the next every few seconds or on a tap, with dots for
 * where it is in the set.
 */
@Composable
private fun TipCarousel(tips: List<String>, modifier: Modifier = Modifier) {
    var index by androidx.compose.runtime.remember(tips) { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(tips) {
        while (tips.size > 1) {
            kotlinx.coroutines.delay(6_000)
            index = (index + 1) % tips.size
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .clickable { index = (index + 1) % tips.size }
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.animation.AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (androidx.compose.animation.slideInHorizontally { it / 3 } + androidx.compose.animation.fadeIn()) togetherWith
                        (androidx.compose.animation.slideOutHorizontally { -it / 3 } + androidx.compose.animation.fadeOut())
                },
                label = "tip",
            ) { i ->
                Text(tips[i.coerceIn(0, tips.lastIndex)], style = MaterialTheme.typography.bodyMedium)
            }
            if (tips.size > 1) PageDots(count = tips.size, current = index)
        }
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
