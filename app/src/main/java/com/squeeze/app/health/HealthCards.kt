package com.squeeze.app.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.core.health.ActivityInsights
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Today from the user's watch and phone: steps against the goal, the week's steps, active
 * calories, and a recovery note when sleep or resting pulse stand out. Draws nothing when
 * no app is connected — an empty card would only advertise a feature.
 */
@Composable
fun TodayActivityCard(viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = state.today ?: return
    if (!state.connected || !today.hasActivity && today.sleepMinutes == null) return

    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today", style = MaterialTheme.typography.titleSmall)

            today.steps?.let { steps ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%,d".format(steps),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        " / %,d steps".format(ActivityInsights.DEFAULT_STEP_GOAL),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                LinearProgressIndicator(
                    progress = { ActivityInsights.stepFraction(steps) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    ActivityInsights.stepsLine(steps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.week.count { it.steps != null } >= 2) WeekSteps(state.week)

            val facts = listOfNotNull(
                today.activeKcal?.let { "${it.roundToInt()} active kcal" },
                today.exerciseMinutes?.takeIf { it > 0 }?.let { "$it min workout" },
                today.sleepMinutes?.let { "${ActivityInsights.hoursMinutes(it)} sleep" },
                today.restingHeartRate?.let { "$it bpm resting" },
            )
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }

            ActivityInsights.recoveryNote(today, state.baselineRestingHr)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Seven small bars, today last and highlighted, with the goal as a faint line. */
@Composable
private fun WeekSteps(week: List<com.squeeze.core.health.DailyActivity>) {
    val accent = MaterialTheme.colorScheme.primary
    val goalColour = MaterialTheme.colorScheme.outline
    val goal = ActivityInsights.DEFAULT_STEP_GOAL.toFloat()
    val top = maxOf(goal * 1.25f, week.maxOf { (it.steps ?: 0L).toFloat() })
    Column {
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val slot = size.width / week.size
            val barWidth = slot * 0.55f
            week.forEachIndexed { i, day ->
                val h = (day.steps ?: 0L) / top * size.height
                drawRoundRect(
                    color = if (i == week.lastIndex) accent else accent.copy(alpha = 0.3f),
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            val gy = size.height - goal / top * size.height
            drawLine(goalColour, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                Text(
                    LocalDate.ofEpochDay(day.epochDay).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Food logged in a nutrition app today, against today's plan. Draws nothing without a food
 * log: "0 kcal eaten" would be a claim, not an absence.
 */
@Composable
fun IntakeCard(targetKcal: Int, targetProteinG: Int, viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val intake = ActivityInsights.intake(state.today, targetKcal, targetProteinG) ?: return

    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Eaten today · from your food app", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%,d".format(intake.eatenKcal),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    " / %,d kcal".format(intake.targetKcal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            LinearProgressIndicator(
                progress = { intake.fraction.coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                ActivityInsights.intakeLine(intake),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
