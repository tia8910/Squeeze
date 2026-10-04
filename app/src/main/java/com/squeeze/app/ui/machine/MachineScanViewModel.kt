package com.squeeze.app.ui.machine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.CoachRepository
import com.squeeze.app.data.PendingLog
import com.squeeze.app.data.db.ProfileDao
import com.squeeze.app.scan.EquipmentRecognizer
import com.squeeze.app.scan.MachineMatch
import com.squeeze.app.scan.PhotoLoader
import com.squeeze.core.model.Goal
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.workout.Coaching
import com.squeeze.core.workout.EquipmentCatalog
import com.squeeze.core.workout.MachineExercise
import com.squeeze.core.workout.WorkoutSummary
import com.squeeze.app.data.db.LoggedSetEntity
import com.squeeze.core.workout.Intensity
import com.squeeze.core.workout.LoggedSet
import com.squeeze.core.workout.MachineGuide
import com.squeeze.core.workout.PlannedItem
import com.squeeze.core.workout.Prescription
import com.squeeze.core.workout.Suggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MachineScanUiState(
    val photo: Bitmap? = null,
    val analysing: Boolean = false,
    val match: MachineMatch? = null,
    val selected: MachineGuide? = null,
    val prescription: Prescription? = null,
    val suggestion: Suggestion? = null,
    val lastSets: List<LoggedSet> = emptyList(),
    val weakPoint: Boolean = false,
    val error: String? = null,
    /** Every exercise this machine is used for; the selected one is logged here. */
    val workouts: List<MachineExercise> = emptyList(),
    val exercise: MachineExercise? = null,
    /** Today's sets of the selected exercise. */
    val todaysSets: List<LoggedSetEntity> = emptyList(),
    /** Today's whole workout against last time, shown once anything is logged. */
    val summary: WorkoutSummary? = null,
    val saved: String? = null,
)

/**
 * Photograph a machine; the on-device model says what it is, and the coach says how to use it
 * for this user — sets and reps from their goal and experience, one more set if the AI scan
 * found the muscle lagging, and a starting weight from the last time they logged it.
 */
@HiltViewModel
class MachineScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recognizer: EquipmentRecognizer,
    private val coach: CoachRepository,
    private val profileDao: ProfileDao,
) : ViewModel() {

    private val _state = MutableStateFlow(MachineScanUiState())
    val state: StateFlow<MachineScanUiState> = _state.asStateFlow()

    fun onPhoto(photo: Bitmap) {
        _state.value = MachineScanUiState(photo = photo, analysing = true)
        viewModelScope.launch {
            val match = withContext(Dispatchers.Default) { recognizer.recognise(photo) }
            if (match == null) {
                _state.value = _state.value.copy(analysing = false, error = "The on-device model could not run on this phone.")
                return@launch
            }
            _state.value = _state.value.copy(analysing = false, match = match)
            // The best guess's guide is always shown — a coach who is 55% sure it is a lat
            // pulldown still tells you how to use it, and offers the other options.
            if (!match.noMachine) match.candidates.firstOrNull()?.let { select(it.first) }
        }
    }

    fun onPicked(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { PhotoLoader.load(context, uri) }
            if (bitmap == null) {
                _state.value = MachineScanUiState(error = "That photo could not be opened.")
            } else {
                onPhoto(bitmap)
            }
        }
    }

    fun select(guide: MachineGuide) {
        val workouts = EquipmentCatalog.workouts(guide)
        _state.value = _state.value.copy(selected = guide, workouts = workouts, saved = null)
        workouts.firstOrNull()?.let(::chooseExercise) ?: _state.value.let {
            _state.value = it.copy(exercise = null, prescription = null, suggestion = null, lastSets = emptyList())
        }
    }

    /** Picks which of the machine's exercises to do, with today's target from the log. */
    fun chooseExercise(exercise: MachineExercise) {
        viewModelScope.launch {
            val profile = profileDao.get()
            val goal = profile?.let { runCatching { Goal.valueOf(it.goal) }.getOrNull() } ?: Goal.HYPERTROPHY
            val age = profile?.let { runCatching { TrainingAge.valueOf(it.trainingAge) }.getOrNull() } ?: TrainingAge.INTERMEDIATE
            val weak = exercise.group in coach.aiWeakTrainingGroups(goal)
            val prescription = Coaching.prescribe(goal, age, exercise.compound, weak)
            val last = coach.lastSession(exercise.name)
            _state.value = _state.value.copy(
                exercise = exercise,
                prescription = prescription,
                suggestion = Coaching.next(last, prescription, exercise.compound, exercise.lowerBody),
                lastSets = last,
                weakPoint = weak,
            )
            refreshLog()
        }
    }

    /** Logs one set of the selected exercise, right here on the scanner screen. */
    fun logSet(weightKg: Double, reps: Int, rir: Int?) {
        val exercise = _state.value.exercise ?: return
        if (reps <= 0) return
        viewModelScope.launch {
            coach.logSet(exercise.name, exercise.group, LoggedSet(weightKg, reps, rir))
            refreshLog()
        }
    }

    fun deleteSet(set: LoggedSetEntity) {
        viewModelScope.launch { coach.deleteSet(set); refreshLog() }
    }

    /** Closes the session so its calories reach the nutrition plan. */
    fun finish(minutes: Int, intensity: Intensity = Intensity.MODERATE) {
        val guide = _state.value.selected ?: return
        viewModelScope.launch {
            val kcal = coach.logSession(guide.sport, guide.name, minutes, intensity, null)
            _state.value = _state.value.copy(saved = "Saved · $minutes min · about $kcal kcal. Your plan and progress are updated.")
            refreshLog()
        }
    }

    private suspend fun refreshLog() {
        val name = _state.value.exercise?.name
        _state.value = _state.value.copy(
            todaysSets = coach.todaysSets().filter { it.exerciseName == name },
            summary = coach.workoutSummary().takeIf { it.exercises.isNotEmpty() },
        )
    }

    /** Opens the log with this machine's exercise ready. */
    fun prepareLog() {
        val s = _state.value
        val guide = s.selected ?: return
        coach.pendingLog = PendingLog(
            sport = guide.sport,
            title = guide.name,
            minutes = if (guide.cardio) 30 else 45,
            intensity = Intensity.MODERATE,
            exercises = s.prescription?.let { p ->
                listOf(PlannedItem(guide.exercise, p.summary, guide.group, guide.compound, p, s.weakPoint))
            }.orEmpty(),
        )
    }
}
