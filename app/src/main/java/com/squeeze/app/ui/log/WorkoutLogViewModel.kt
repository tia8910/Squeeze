package com.squeeze.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.CoachRepository
import com.squeeze.app.data.db.ActivitySessionEntity
import com.squeeze.app.data.db.LoggedSetEntity
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.ProfileDao
import com.squeeze.core.model.Goal
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.program.ExerciseLibrary
import com.squeeze.core.program.MuscleGroup
import com.squeeze.core.workout.ActivityCalories
import com.squeeze.core.workout.Coaching
import com.squeeze.core.workout.EquipmentCatalog
import com.squeeze.core.workout.Intensity
import com.squeeze.core.workout.LoggedSet
import com.squeeze.core.workout.Prescription
import com.squeeze.core.workout.Sport
import com.squeeze.core.workout.Suggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One exercise on today's log, with what the log says to do on it. */
data class ExerciseEntry(
    val name: String,
    val group: MuscleGroup?,
    val prescription: Prescription,
    val suggestion: Suggestion,
    val lastSets: List<LoggedSet>,
    val weakPoint: Boolean,
)

data class WorkoutLogUiState(
    val strength: Boolean = true,
    val title: String = "Workout",
    val exercises: List<ExerciseEntry> = emptyList(),
    val todaysSets: List<LoggedSetEntity> = emptyList(),
    val sport: Sport = Sport.RUNNING,
    val minutes: Int = 45,
    val intensity: Intensity = Intensity.MODERATE,
    val distanceKm: Double? = null,
    val weightKg: Double = 75.0,
    val recent: List<ActivitySessionEntity> = emptyList(),
    val message: String? = null,
) {
    val estimatedKcal: Int
        get() = ActivityCalories.netKcal(if (strength) Sport.STRENGTH else sport, intensity, minutes, weightKg)
}

/**
 * The log for every sport. Strength is logged set by set, and each exercise shows what the
 * last session was and what to do today because of it (see [Coaching.next]); every other
 * sport is logged as a session with its duration and intensity. Both feed the nutrition plan
 * and this week's volume, so logging here is what makes the rest of the app follow reality.
 */
@HiltViewModel
class WorkoutLogViewModel @Inject constructor(
    private val coach: CoachRepository,
    private val profileDao: ProfileDao,
    private val measurementDao: MeasurementDao,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutLogUiState())
    val state: StateFlow<WorkoutLogUiState> = _state.asStateFlow()

    private var goal = Goal.HYPERTROPHY
    private var age = TrainingAge.INTERMEDIATE
    private var weak: Set<MuscleGroup> = emptySet()

    init {
        viewModelScope.launch {
            profileDao.get()?.let { p ->
                goal = runCatching { Goal.valueOf(p.goal) }.getOrDefault(Goal.HYPERTROPHY)
                age = runCatching { TrainingAge.valueOf(p.trainingAge) }.getOrDefault(TrainingAge.INTERMEDIATE)
            }
            weak = coach.aiWeakTrainingGroups(goal).toSet()
            val pending = coach.pendingLog.also { coach.pendingLog = null }
            _state.value = _state.value.copy(
                weightKg = measurementDao.latestWeightKg() ?: 75.0,
                strength = pending?.sport?.let { it == Sport.STRENGTH } ?: true,
                title = pending?.title ?: "Workout",
                sport = pending?.sport?.takeIf { it != Sport.STRENGTH } ?: Sport.RUNNING,
                minutes = pending?.minutes ?: 45,
                intensity = pending?.intensity ?: Intensity.MODERATE,
            )
            pending?.exercises?.forEach { addExercise(it.name, it.group) }
            refresh()
        }
    }

    fun setStrength(strength: Boolean) { _state.value = _state.value.copy(strength = strength) }
    fun setSport(sport: Sport) { _state.value = _state.value.copy(sport = sport, title = sport.label) }
    fun setMinutes(minutes: Int) { _state.value = _state.value.copy(minutes = minutes.coerceIn(1, 600)) }
    fun setIntensity(intensity: Intensity) { _state.value = _state.value.copy(intensity = intensity) }
    fun setDistance(km: Double?) { _state.value = _state.value.copy(distanceKm = km) }

    /** Adds an exercise with its prescription and today's suggestion from the last session. */
    fun addExercise(name: String, knownGroup: MuscleGroup? = null) {
        if (_state.value.exercises.any { it.name == name }) return
        viewModelScope.launch {
            val library = ExerciseLibrary.ALL.firstOrNull { it.name == name }
            val machine = EquipmentCatalog.ALL.firstOrNull { it.exercise == name }
            val group = knownGroup ?: library?.primary ?: machine?.group
            val compound = library?.compound ?: machine?.compound ?: false
            val lowerBody = group in LOWER
            val p = Coaching.prescribe(goal, age, compound, group in weak)
            val last = coach.lastSession(name)
            val entry = ExerciseEntry(name, group, p, Coaching.next(last, p, compound, lowerBody), last, group in weak)
            _state.value = _state.value.copy(exercises = _state.value.exercises + entry)
        }
    }

    fun logSet(exercise: ExerciseEntry, weightKg: Double, reps: Int, rir: Int?) {
        if (reps <= 0) return
        viewModelScope.launch {
            coach.logSet(exercise.name, exercise.group, LoggedSet(weightKg, reps, rir))
            refresh()
        }
    }

    fun deleteSet(set: LoggedSetEntity) {
        viewModelScope.launch { coach.deleteSet(set); refresh() }
    }

    /** Closes a strength session: its duration goes to the activity log for calories. */
    fun finishStrength() {
        viewModelScope.launch {
            val s = _state.value
            val kcal = coach.logSession(Sport.STRENGTH, s.title, s.minutes, Intensity.MODERATE, null)
            _state.value = s.copy(
                message = "Saved: ${s.todaysSets.size} sets, about $kcal kcal. Your nutrition plan and " +
                    "this week's volume now include it.",
            )
            refresh()
        }
    }

    fun saveSport() {
        viewModelScope.launch {
            val s = _state.value
            val kcal = coach.logSession(s.sport, s.title.ifBlank { s.sport.label }, s.minutes, s.intensity, s.distanceKm)
            _state.value = s.copy(message = "Saved ${s.sport.label.lowercase()}: ${s.minutes} min, about $kcal kcal above rest.")
            refresh()
        }
    }

    fun deleteSession(session: ActivitySessionEntity) {
        viewModelScope.launch { coach.deleteSession(session); refresh() }
    }

    private suspend fun refresh() {
        _state.value = _state.value.copy(
            todaysSets = coach.todaysSets(),
            recent = coach.recentSessions(14),
        )
    }

    private companion object {
        val LOWER = setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)
    }
}
