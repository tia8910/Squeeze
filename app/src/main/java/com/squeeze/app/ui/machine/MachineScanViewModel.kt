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
        viewModelScope.launch {
            val profile = profileDao.get()
            val goal = profile?.let { runCatching { Goal.valueOf(it.goal) }.getOrNull() } ?: Goal.HYPERTROPHY
            val age = profile?.let { runCatching { TrainingAge.valueOf(it.trainingAge) }.getOrNull() } ?: TrainingAge.INTERMEDIATE
            val weak = guide.group != null && guide.group in coach.aiWeakTrainingGroups(goal)
            val prescription = if (guide.cardio) null else Coaching.prescribe(goal, age, guide.compound, weak)
            val last = if (guide.cardio) emptyList() else coach.lastSession(guide.exercise)
            _state.value = _state.value.copy(
                selected = guide,
                prescription = prescription,
                suggestion = prescription?.let { Coaching.next(last, it, guide.compound, guide.lowerBody) },
                lastSets = last,
                weakPoint = weak,
            )
        }
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
