package com.squeeze.app.ui.training

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.core.model.Goal
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.program.Equipment
import com.squeeze.core.program.WeakPoint
import com.squeeze.core.program.WeakPointAnalysis
import com.squeeze.core.program.Session
import com.squeeze.core.program.TrainingWeek

@Composable
fun TrainingScreen(viewModel: TrainingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.profileMissing) {
            InfoCard(
                title = "Set your profile first",
                body = "Training volume is scaled by your training age and goal, both set in " +
                    "Settings.",
            )
            return@Column
        }

        SetupSection(state, viewModel)

        Button(onClick = viewModel::generate, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.mesocycle == null) "Generate training block" else "Regenerate")
        }

        state.adjustmentRationale?.let { rationale ->
            // The reason the prescription changed is shown before the prescription itself.
            // An adjustment the user cannot explain reads as the app being erratic.
            InfoCard(title = "Adjusted from your measurements", body = rationale)
        }

        // Before the block, because it is the reason the block prioritises what it does.
        // A programme that quietly favours calves is indistinguishable from a random one
        // unless the user is told why.
        if (state.weakPoints.isNotEmpty()) WeakPointCard(state.weakPoints)

        state.mesocycle?.let { mesocycle ->
            Text(mesocycle.name, style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                mesocycle.weeks.forEachIndexed { index, week ->
                    FilterChip(
                        selected = state.selectedWeek == index,
                        onClick = { viewModel.selectWeek(index) },
                        label = { Text(if (week.isDeload) "Deload" else "Week ${index + 1}") },
                    )
                }
            }

            mesocycle.weeks.getOrNull(state.selectedWeek)?.let { WeekDetail(it) }

            if (mesocycle.notes.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("How to run this", style = MaterialTheme.typography.titleSmall)
                        mesocycle.notes.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSection(state: TrainingUiState, viewModel: TrainingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Days per week", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..6).forEach { days ->
                FilterChip(
                    selected = state.daysPerWeek == days,
                    onClick = { viewModel.setDaysPerWeek(days) },
                    label = { Text("$days") },
                )
            }
        }

        Text("Goal", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Goal.entries.forEach { goal ->
                FilterChip(
                    selected = state.goal == goal,
                    onClick = { viewModel.setGoal(goal) },
                    label = { Text(goal.label()) },
                )
            }
        }

        Text("Experience", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainingAge.entries.forEach { age ->
                FilterChip(
                    selected = state.trainingAge == age,
                    onClick = { viewModel.setTrainingAge(age) },
                    label = { Text(age.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Text("Equipment", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Equipment.entries.forEach { equipment ->
                FilterChip(
                    selected = equipment in state.equipment,
                    onClick = { viewModel.toggleEquipment(equipment) },
                    label = {
                        Text(equipment.name.lowercase().replaceFirstChar { it.uppercase() })
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekDetail(week: TrainingWeek) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (week.isDeload) {
            InfoCard(
                title = "Deload week",
                body = "Volume drops to maintenance and effort backs off. This is scheduled, " +
                    "not earned: waiting until you feel you need one costs two weeks of progress.",
            )
        }

        week.sessions.forEach { SessionCard(it) }
    }
}

@Composable
private fun SessionCard(session: Session) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(session.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${session.totalSets} sets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            session.prescriptions.forEach { prescription ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = prescription.exerciseName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        // Sets × reps @ RIR. The load is deliberately absent: the programme
                        // prescribes proximity to failure and the lifter picks the weight,
                        // which is both safer and more accurate than a percentage table.
                        text = "%d × %d–%d @ %d RIR".format(
                            prescription.sets,
                            prescription.repRangeLow,
                            prescription.repRangeHigh,
                            prescription.targetRir,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Goal.label(): String = when (this) {
    Goal.HYPERTROPHY -> "Muscle"
    Goal.STRENGTH -> "Strength"
    Goal.CUT -> "Cut"
    Goal.RECOMP -> "Recomp"
    Goal.MAKE_WEIGHT -> "Make weight"
}

/**
 * What the scan says is lagging, and what the programme did about it.
 *
 * Every ratio behind this comes from a single photograph at a single scale, so the scale
 * error that troubles the absolute centimetres divides out — which is what makes it
 * defensible to prescribe from measurements that may themselves be a few per cent off.
 *
 * Framed as a training decision rather than a judgement. The user did not ask to be graded
 * against an ideal physique, and the useful content is which of their own parts is furthest
 * from where the rest of their body sits.
 */
@Composable
private fun WeakPointCard(weakPoints: List<WeakPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Prioritised from your measurements",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "These are proportions, not absolutes — the parts furthest from where " +
                    "the rest of your body sits. The block below gives the first " +
                    "${WeakPointAnalysis.MAX_PRIORITIES} of them extra sets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            weakPoints.forEachIndexed { index, point ->
                Column(Modifier.padding(top = 10.dp)) {
                    Text(
                        text = point.group.name.lowercase().replaceFirstChar { it.uppercase() } +
                            if (index < WeakPointAnalysis.MAX_PRIORITIES) " · prioritised" else "",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = point.finding,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = point.prescription,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
