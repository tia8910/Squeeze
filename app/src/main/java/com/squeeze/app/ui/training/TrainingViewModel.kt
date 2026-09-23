package com.squeeze.app.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.BodyCompositionRepository
import com.squeeze.app.data.CoachRepository
import com.squeeze.app.data.PendingLog
import com.squeeze.app.data.VolumeRow
import com.squeeze.core.workout.Discipline
import com.squeeze.core.workout.HybridWeek
import com.squeeze.core.workout.PlannedSession
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.ProfileDao
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Goal
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.model.UnitSystem
import com.squeeze.core.program.CompositionFeedback
import com.squeeze.core.program.Equipment
import com.squeeze.core.program.Mesocycle
import com.squeeze.core.program.ProgramGenerator
import com.squeeze.core.program.TrainingConstraints
import com.squeeze.core.program.VolumeAdjustment
import com.squeeze.core.program.WeakPoint
import com.squeeze.core.program.WeakPointAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainingUiState(
    val profileMissing: Boolean = false,
    val daysPerWeek: Int = 4,
    val goal: Goal = Goal.HYPERTROPHY,
    val trainingAge: TrainingAge = TrainingAge.INTERMEDIATE,
    val equipment: Set<Equipment> = Equipment.entries.toSet(),
    val mesocycle: Mesocycle? = null,
    /** Why the composition trend changed the prescription, if it did. */
    val adjustmentRationale: String? = null,
    val selectedWeek: Int = 0,
    /**
     * Lagging body parts read from the latest scan, worst first.
     *
     * The programme is built with these as priority groups, so this list is not decoration —
     * it is the explanation for why the block looks the way it does.
     */
    val weakPoints: List<WeakPoint> = emptyList(),
    /** The sports the user trains; the week is built across all of them. */
    val disciplines: Set<Discipline> = emptySet(),
    /** The combined week, once created. */
    val week: HybridWeek? = null,
    /** Sets logged this week per muscle group against the plan. */
    val volume: List<VolumeRow> = emptyList(),
    /** Planned exercise calories a day, for the link to nutrition. */
    val plannedKcalPerDay: Int? = null,
)

/**
 * Generates a training block and keeps it in step with the composition trend.
 *
 * The generator itself is a deterministic rules engine in `:core` with its own tests; this
 * class only supplies the inputs and surfaces the result. That split is why a change to
 * training logic is verified without an emulator.
 */
@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val measurementDao: MeasurementDao,
    private val repository: BodyCompositionRepository,
    private val generator: ProgramGenerator,
    private val coach: CoachRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrainingUiState())
    val state: StateFlow<TrainingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileDao.get()
            _state.value = _state.value.copy(
                profileMissing = profile == null,
                goal = profile?.let { Goal.valueOf(it.goal) } ?: Goal.HYPERTROPHY,
                trainingAge = profile?.let { TrainingAge.valueOf(it.trainingAge) }
                    ?: TrainingAge.INTERMEDIATE,
                daysPerWeek = profile?.trainingDaysPerWeek ?: _state.value.daysPerWeek,
                disciplines = coach.disciplines(),
            )
            refreshWeek()
        }
    }

    fun setDaysPerWeek(days: Int) {
        _state.value = _state.value.copy(daysPerWeek = days.coerceIn(1, 7))
    }

    fun setGoal(goal: Goal) {
        _state.value = _state.value.copy(goal = goal)
    }

    fun setTrainingAge(age: TrainingAge) {
        _state.value = _state.value.copy(trainingAge = age)
    }

    fun toggleEquipment(equipment: Equipment) {
        val current = _state.value.equipment
        val next = if (equipment in current) current - equipment else current + equipment
        // Never allow an empty set: the generator cannot prescribe anything at all, and a
        // constraint with no equipment throws rather than returning an empty programme.
        if (next.isNotEmpty()) _state.value = _state.value.copy(equipment = next)
    }

    fun toggleDiscipline(discipline: Discipline) {
        val current = _state.value.disciplines
        _state.value = _state.value.copy(
            disciplines = if (discipline in current) current - discipline else current + discipline,
        )
    }

    /**
     * Saves the choices and builds the week across every chosen sport. Saved first, because
     * the nutrition plan, the log and the dashboard all read them from the profile.
     */
    fun createWeek() {
        viewModelScope.launch {
            val current = _state.value
            val chosen = current.disciplines.ifEmpty { setOf(Discipline.GYM) }
            coach.saveTrainingChoices(current.goal, current.trainingAge, current.daysPerWeek)
            coach.saveDisciplines(chosen)
            _state.value = _state.value.copy(disciplines = chosen)
            refreshWeek()
        }
    }

    /** Re-reads the week and this week's logged volume, e.g. after returning from the log. */
    fun refreshWeek() {
        viewModelScope.launch {
            val week = coach.week()
            val weight = measurementDao.latestWeightKg() ?: 75.0
            _state.value = _state.value.copy(
                week = week,
                volume = coach.weeklyVolume(),
                plannedKcalPerDay = week?.netKcalPerDay(weight)?.toInt(),
            )
        }
    }

    /** Hands a planned session to the log screen. */
    fun startLog(session: PlannedSession) {
        coach.pendingLog = PendingLog(
            sport = session.discipline.sport,
            title = session.title,
            minutes = session.minutes,
            intensity = session.intensity,
            exercises = session.items.filter { it.prescription != null },
        )
    }

    fun selectWeek(index: Int) {
        _state.value = _state.value.copy(selectedWeek = index)
    }

    /**
     * Builds a block, adjusted by what the composition trend shows.
     *
     * This is the loop the whole app is built around: the volume prescribed depends on
     * whether lean mass is actually holding, not only on what the lifter chose.
     */
    fun generate() {
        viewModelScope.launch {
            val stored = profileDao.get()
            if (stored == null) {
                _state.value = _state.value.copy(profileMissing = true)
                return@launch
            }

            val current = _state.value
            val profile = Profile(
                heightCm = stored.heightCm,
                birthYear = stored.birthYear,
                sex = Sex.valueOf(stored.sex),
                trainingAge = current.trainingAge,
                goal = current.goal,
                unitSystem = UnitSystem.valueOf(stored.unitSystem),
            )

            // Proportions from the most recent measurement carrying each site. Ratios
            // survive the scan's scale error — both sides of a ratio come from one
            // photograph at one scale — which is what makes them safe to prescribe from
            // when the absolute centimetres may not be.
            val measurements = measurementDao.since(Long.MIN_VALUE)
                .sortedByDescending { it.epochDay }
            val circumferences = Circumferences(
                neckCm = measurements.firstNotNullOfOrNull { it.neckCm },
                waistCm = measurements.firstNotNullOfOrNull { it.waistCm },
                hipCm = measurements.firstNotNullOfOrNull { it.hipCm },
                chestCm = measurements.firstNotNullOfOrNull { it.chestCm },
                thighCm = measurements.firstNotNullOfOrNull { it.thighCm },
                armCm = measurements.firstNotNullOfOrNull { it.armCm },
                calfCm = measurements.firstNotNullOfOrNull { it.calfCm },
            )
            // Two independent reads of what is lagging: proportions from the tape or scan
            // girths, and the on-device AI's look at each group on the last scan. The AI's
            // go first when both flag a group — it saw the muscle, the ratio inferred it.
            val aiWeakPoints = coach.aiWeakPoints(current.goal)
            val weakPoints = (aiWeakPoints + WeakPointAnalysis.analyse(circumferences, profile.sex))
                .distinctBy { it.group }
                .take(WeakPointAnalysis.MAX_PRIORITIES)

            // Remembered, so the nutrition plan fuels the block that was actually built.
            coach.saveTrainingChoices(current.goal, current.trainingAge, current.daysPerWeek)

            val snapshot = repository.snapshot(profile)
            val adjustment: VolumeAdjustment = CompositionFeedback.evaluate(
                bodyFatTrend = snapshot.bodyFatTrend,
                leanMassTrend = snapshot.leanMassTrend,
                goal = current.goal,
            )

            val mesocycle = generator.generate(
                profile = profile,
                constraints = TrainingConstraints(
                    // The gym block's splits run 2–6 days; the combined week takes 1–7.
                    daysPerWeek = current.daysPerWeek.coerceIn(2, 6),
                    availableEquipment = current.equipment,
                    priorityGroups = WeakPointAnalysis.priorityGroups(weakPoints),
                ),
                adjustment = adjustment,
            )

            _state.value = current.copy(
                mesocycle = mesocycle,
                adjustmentRationale = adjustment.rationale,
                weakPoints = weakPoints,
                selectedWeek = 0,
                profileMissing = false,
            )
        }
    }
}
