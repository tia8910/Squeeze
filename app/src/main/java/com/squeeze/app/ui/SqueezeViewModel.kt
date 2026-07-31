package com.squeeze.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.ads.AdGate
import com.squeeze.app.data.BodyCompositionRepository
import com.squeeze.app.data.db.ProfileDao
import com.squeeze.app.data.db.ProfileEntity
import com.squeeze.app.data.settings.SecuritySettings
import com.squeeze.core.bodycomp.PersonalCalibration
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SqueezeUiState(
    val profile: Profile? = null,
    val bodyFatTrend: List<TrendPoint> = emptyList(),
    val leanMassTrend: List<TrendPoint> = emptyList(),
    val repeatability: RepeatabilityScore? = null,
    val calibration: PersonalCalibration = PersonalCalibration.none(),
    val loading: Boolean = true,
)

@HiltViewModel
class SqueezeViewModel @Inject constructor(
    private val repository: BodyCompositionRepository,
    private val profileDao: ProfileDao,
    private val securitySettings: SecuritySettings,
    /** Exposed so composables can consult ad policy without reaching for a singleton. */
    val adGate: AdGate,
) : ViewModel() {

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
    fun updateProfile(heightCm: Double?, birthYear: Int?, sex: Sex?) {
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
            val profile = profileDao.get()?.toDomain()
            if (profile == null) {
                // No profile yet means onboarding has not run; there is nothing to compute
                // because every equation needs height and sex at minimum.
                _state.value = SqueezeUiState(loading = false)
                return@launch
            }

            val snapshot = repository.snapshot(profile)
            _state.value = SqueezeUiState(
                profile = profile,
                bodyFatTrend = snapshot.bodyFatTrend,
                leanMassTrend = snapshot.leanMassTrend,
                repeatability = snapshot.repeatability,
                calibration = snapshot.calibration,
                loading = false,
            )
        }
    }
}

private fun com.squeeze.app.data.db.ProfileEntity.toDomain() = Profile(
    heightCm = heightCm,
    birthYear = birthYear,
    sex = Sex.valueOf(sex),
    trainingAge = TrainingAge.valueOf(trainingAge),
    goal = Goal.valueOf(goal),
    unitSystem = UnitSystem.valueOf(unitSystem),
)
