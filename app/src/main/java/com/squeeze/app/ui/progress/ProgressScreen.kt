package com.squeeze.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.CoachRepository
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.components.SectionHeader
import com.squeeze.app.ui.components.Sparkline
import com.squeeze.core.workout.ExerciseProgress
import com.squeeze.core.workout.Trend
import com.squeeze.core.workout.WorkoutSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt

data class ProgressUiState(
    val loading: Boolean = true,
    val today: WorkoutSummary? = null,
    val exercises: List<ExerciseProgress> = emptyList(),
)

@HiltViewModel
class ProgressViewModel @Inject constructor(private val coach: CoachRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = ProgressUiState(loading = false, today = coach.workoutSummary(), exercises = coach.progress())
        }
    }
}

/**
 * Progressive overload, exercise by exercise: the estimated max over every session, whether it
 * is still climbing, and what to do when it is not. Today's workout sits on top.
 */
@Composable
fun ProgressScreen(onLog: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.loading) return@Column
        state.today?.let { WorkoutSummaryCard(it) }

        if (state.exercises.isEmpty()) {
            SectionHeader(
                title = "No lifts logged yet",
                eyebrow = "Progress",
                caption = "Log a strength workout and every exercise gets its own progress line.",
            )
            PrimaryButton(text = "Log a workout", onClick = onLog)
            return@Column
        }

        val stalled = state.exercises.count { it.trend == Trend.STALLED || it.trend == Trend.REGRESSING }
        SectionHeader(
            title = "Progressive overload",
            eyebrow = "Progress",
            caption = "${state.exercises.size} exercises · " +
                if (stalled == 0) "all moving" else "$stalled need${if (stalled == 1) "s" else ""} a change",
        )
        state.exercises.forEach { ExerciseCard(it) }
    }
}

@Composable
private fun ExerciseCard(p: ExerciseProgress) {
    var open by rememberSaveable(p.exercise) { mutableStateOf(false) }
    val colour = when (p.trend) {
        Trend.PROGRESSING -> MaterialTheme.colorScheme.primary
        Trend.NEW -> MaterialTheme.colorScheme.onSurfaceVariant
        Trend.STALLED, Trend.REGRESSING -> MaterialTheme.colorScheme.error
    }
    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(p.exercise, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(p.trend.label, style = MaterialTheme.typography.labelLarge, color = colour)
            }
            Text(
                "Est. max ${p.latest.estimatedMax.roundToInt()} kg" +
                    (p.changePercent?.let { " · ${if (it >= 0) "+" else ""}${it.roundToInt()}% since you started" } ?: "") +
                    " · best ${kg(p.best.bestSet.weightKg)} × ${p.best.bestSet.reps}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (p.sessions.size >= 2) {
                Sparkline(values = p.sessions.map { it.estimatedMax }, height = 56.dp, color = colour)
            }
            Text(p.advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.TextButton(onClick = { open = !open }) {
                Text(if (open) "Hide sessions" else "All ${p.sessions.size} sessions")
            }
            if (open) {
                p.sessions.asReversed().forEach { s ->
                    Text(
                        LocalDate.ofEpochDay(s.epochDay).format(DateTimeFormatter.ofPattern("d MMM")) + " · " +
                            s.sets.joinToString(", ") { "${kg(it.weightKg)}×${it.reps}" } +
                            " · %,d kg".format(s.volume.roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
