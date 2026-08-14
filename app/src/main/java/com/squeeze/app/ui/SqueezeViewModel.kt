package com.squeeze.app.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.BodyCompositionRepository
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.app.data.db.ProfileDao
import com.squeeze.app.data.db.ProfileEntity
import com.squeeze.app.data.photo.ScanPhotoStore
import com.squeeze.app.data.settings.SecuritySettings
import com.squeeze.app.data.settings.UiSettings
import com.squeeze.app.ui.theme.ThemeMode
import com.squeeze.core.bodycomp.CompositionAnalyser
import com.squeeze.core.bodycomp.CompositionPanel
import com.squeeze.core.bodycomp.GoalPlanner
import com.squeeze.core.bodycomp.GoalProgress
import com.squeeze.core.bodycomp.PersonalCalibration
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Goal
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.model.UnitSystem
import com.squeeze.core.trend.RepeatabilityScore
import com.squeeze.core.trend.TrendPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class SqueezeUiState(
    val profile: Profile? = null,
    /** Raw history, newest first, for the log and for delete. */
    val measurements: List<MeasurementEntity> = emptyList(),
    val bodyFatTrend: List<TrendPoint> = emptyList(),
    val weightTrend: List<TrendPoint> = emptyList(),
    val leanMassTrend: List<TrendPoint> = emptyList(),
    val repeatability: RepeatabilityScore? = null,
    val calibration: PersonalCalibration = PersonalCalibration.none(),
    /**
     * Progress against the user's dated goal, or null when they have not set one.
     *
     * Recomputed on refresh rather than stored: it is a pure function of the trend and the
     * target, and the day it is read on changes the answer.
     */
    val goalProgress: GoalProgress? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class SqueezeViewModel @Inject constructor(
    private val repository: BodyCompositionRepository,
    private val profileDao: ProfileDao,
    private val measurementDao: MeasurementDao,
    private val securitySettings: SecuritySettings,
    private val uiSettings: UiSettings,
    private val photoStore: ScanPhotoStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = uiSettings.themeMode
    val landingSeen: StateFlow<Boolean> = uiSettings.landingSeen

    fun setThemeMode(mode: ThemeMode) = uiSettings.setThemeMode(mode)

    /** Short cues on save, capture and celebration. */
    val soundEnabled: StateFlow<Boolean> = uiSettings.soundEnabled

    /** The looping motivational bed. */
    val ambientEnabled: StateFlow<Boolean> = uiSettings.ambientEnabled

    fun setSoundEnabled(enabled: Boolean) = uiSettings.setSoundEnabled(enabled)

    fun setAmbientEnabled(enabled: Boolean) = uiSettings.setAmbientEnabled(enabled)

    fun markLandingSeen() = uiSettings.markLandingSeen()

    /** Whether FLAG_SECURE is applied; see [SecuritySettings.blockScreenshots]. */
    val blockScreenshots: StateFlow<Boolean> = securitySettings.blockScreenshots

    fun setBlockScreenshots(enabled: Boolean) = securitySettings.setBlockScreenshots(enabled)

    /**
     * Persists the profile once it is complete enough to be usable.
     *
     * Partial input is ignored rather than stored with placeholders: a profile with a
     * defaulted height would silently mis-scale every photo scan, which is worse than
     * having no profile at all, because the failure would be invisible.
     */
    fun updateProfile(
        heightCm: Double?,
        birthYear: Int?,
        sex: Sex?,
        targetBodyFatPercent: Double? = null,
        targetEpochDay: Long? = null,
    ) {
        if (heightCm == null || birthYear == null || sex == null) return
        if (heightCm !in 100.0..250.0) return
        if (birthYear !in 1900..LocalDate.now().year) return

        viewModelScope.launch {
            val existing = profileDao.get()
            profileDao.upsert(
                ProfileEntity(
                    id = 1,
                    heightCm = heightCm,
                    birthYear = birthYear,
                    sex = sex.name,
                    trainingAge = existing?.trainingAge ?: TrainingAge.NOVICE.name,
                    goal = existing?.goal ?: Goal.HYPERTROPHY.name,
                    unitSystem = existing?.unitSystem ?: UnitSystem.METRIC.name,
                    // A goal supplied here wins; otherwise whatever is already stored
                    // survives, so editing height in Settings cannot silently clear it.
                    targetBodyFatPercent = targetBodyFatPercent
                        ?: existing?.targetBodyFatPercent,
                    targetEpochDay = targetEpochDay ?: existing?.targetEpochDay,
                ),
            )
            refresh()
        }
    }

    /**
     * Stores or clears the dated target.
     *
     * Both together, always. Storing one without the other would leave the planner with a
     * goal it cannot judge, which is exactly the state this feature exists to avoid.
     */
    /**
     * Stores the goal, its targets and its deadline.
     *
     * A deadline plus at least one target, or nothing at all. The rule used to be a deadline
     * plus a body fat figure, which made body fat the only goal the app could hold — someone
     * adding size had to invent a percentage to have any goal at all, and a recomposition
     * could not be expressed, because holding weight while the percentage falls needs two
     * numbers and there was only one field.
     */
    fun setGoal(
        goal: Goal,
        targetBodyFatPercent: Double?,
        targetWeightKg: Double?,
        targetEpochDay: Long?,
    ) {
        viewModelScope.launch {
            val existing = profileDao.get() ?: return@launch
            val complete = targetEpochDay != null &&
                (targetBodyFatPercent != null || targetWeightKg != null)
            profileDao.upsert(
                existing.copy(
                    goal = goal.name,
                    targetBodyFatPercent = if (complete) targetBodyFatPercent else null,
                    targetWeightKg = if (complete) targetWeightKg else null,
                    targetEpochDay = if (complete) targetEpochDay else null,
                ),
            )
            refresh()
        }
    }

    private val _state = MutableStateFlow(SqueezeUiState())
    val state: StateFlow<SqueezeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // History is loaded before the profile check: measurements a user entered
            // should be visible and deletable even while the profile is incomplete.
            val measurements = measurementDao.since(Long.MIN_VALUE).sortedByDescending { it.epochDay }

            val profile = profileDao.get()?.toDomain()
            if (profile == null) {
                _state.value = SqueezeUiState(measurements = measurements, loading = false)
                return@launch
            }

            val snapshot = repository.snapshot(profile)

            _state.value = SqueezeUiState(
                profile = profile,
                measurements = measurements,
                bodyFatTrend = snapshot.bodyFatTrend,
                weightTrend = snapshot.weightTrend,
                leanMassTrend = snapshot.leanMassTrend,
                repeatability = snapshot.repeatability,
                calibration = snapshot.calibration,
                goalProgress = goalProgress(profile, snapshot, measurements),
                loading = false,
            )
        }
    }

    /**
     * Judges the trend against the user's deadline.
     *
     * The rate handed over is the filter's own weekly slope, not the difference between the
     * last two readings. Two readings differ by measurement noise as much as by anything
     * real, and a plan built on that would swing between "on track" and "behind" week to
     * week while the body did nothing unusual.
     */
    private fun goalProgress(
        profile: Profile,
        snapshot: com.squeeze.app.data.CompositionSnapshot,
        measurements: List<MeasurementEntity>,
    ): GoalProgress? {
        val target = profile.targetOrNull() ?: return null
        val latest = snapshot.latest

        return GoalPlanner.evaluate(
            target = target,
            currentBodyFatPercent = latest?.level,
            currentWeightKg = measurements.firstNotNullOfOrNull { it.weightKg },
            // Only once the slope is distinguishable from noise. Before that the honest
            // answer is that there is no rate yet, which GoalPlanner reports as TOO_EARLY.
            actualRatePerWeek = latest?.takeIf { it.isChangeSignificant }?.weeklyChange,
            todayEpochDay = LocalDate.now().toEpochDay(),
            sex = profile.sex,
        )
    }

    /**
     * Derives the full analysis for one stored measurement.
     *
     * Only that entry's own numbers are used. The dashboard version of this took the most
     * recent entry carrying each field — a waist from Tuesday, a weight from Sunday — which
     * is a reasonable way to answer "where am I now" but describes a body that was never
     * measured, and it has no business being called the analysis *of* a record. Inside a
     * record, every figure has to be traceable to the measurements printed above it.
     *
     * The body-fat figure comes from the entry's own equations rather than from the trend's
     * current level, for the same reason: a row dated three weeks ago should say what that
     * day said, not what the filter believes today.
     */
    fun analysisFor(entity: MeasurementEntity): CompositionPanel? {
        val profile = _state.value.profile ?: return null

        return CompositionAnalyser.analyse(
            profile = profile,
            circumferences = Circumferences(
                neckCm = entity.neckCm,
                waistCm = entity.waistCm,
                hipCm = entity.hipCm,
                chestCm = entity.chestCm,
                thighCm = entity.thighCm,
                armCm = entity.armCm,
                calfCm = entity.calfCm,
            ),
            bodyFatPercent = repository.estimate(profile, entity)?.percent,
            weightKg = entity.weightKg,
            currentYear = LocalDate.now().year,
        )
    }

    /**
     * Removes one measurement and recomputes everything derived from it.
     *
     * Deletion is worth having on the dashboard because a single mis-entered reading — a
     * waist typed as 850 instead of 85.0 — visibly bends the trend, and the fix should be
     * one tap away from where the damage shows.
     */
    fun deleteMeasurement(measurement: MeasurementEntity) {
        viewModelScope.launch {
            measurementDao.delete(measurement)

            // The photograph goes with the row. Leaving it would mean the user deleted a
            // measurement and the most sensitive part of it quietly stayed on the device.
            measurement.photoId?.let { photoStore.delete(it) }

            refresh()
        }
    }

    /** Decrypts a stored scan photograph for the detail view. */
    suspend fun loadPhoto(photoId: String): Bitmap? =
        withContext(Dispatchers.IO) { photoStore.load(photoId) }
}

private fun com.squeeze.app.data.db.ProfileEntity.toDomain() = Profile(
    heightCm = heightCm,
    birthYear = birthYear,
    sex = Sex.valueOf(sex),
    trainingAge = TrainingAge.valueOf(trainingAge),
    goal = Goal.valueOf(goal),
    unitSystem = UnitSystem.valueOf(unitSystem),
    targetBodyFatPercent = targetBodyFatPercent,
    targetWeightKg = targetWeightKg,
    targetEpochDay = targetEpochDay,
)
