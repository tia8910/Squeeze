package com.squeeze.app.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.CoachRepository
import com.squeeze.app.data.NutritionContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NutritionUiState(
    val loading: Boolean = true,
    /** Null when there is no profile or no logged weight yet. */
    val context: NutritionContext? = null,
    val showTrainingDay: Boolean = true,
)

/**
 * Surfaces the nutrition plan [CoachRepository] builds from the rest of the app.
 *
 * Recomputed every time the page opens rather than cached, because every input — a new
 * weight, a new scan, a regenerated programme, an edited goal — lives on another screen, and
 * a plan that lagged behind them would be the one part of the app that had not heard.
 */
@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val coach: CoachRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NutritionUiState())
    val state: StateFlow<NutritionUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = false, context = coach.nutritionPlan())
        }
    }

    fun showTrainingDay(training: Boolean) {
        _state.value = _state.value.copy(showTrainingDay = training)
    }
}
