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
    /** Foods ticked on the page, saved when the user builds their meals. */
    val favourites: Set<String> = emptySet(),
    val favouritesDirty: Boolean = false,
    val selectedDay: Int = 0,
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
            _state.value = _state.value.copy(
                loading = false,
                context = coach.nutritionPlan(),
                favourites = coach.favouriteFoods(),
                favouritesDirty = false,
            )
        }
    }

    fun toggleFood(name: String) {
        val current = _state.value.favourites
        _state.value = _state.value.copy(
            favourites = if (name in current) current - name else current + name,
            favouritesDirty = true,
        )
    }

    /** Ticks or clears a whole group at once. */
    fun toggleGroup(names: List<String>) {
        val current = _state.value.favourites
        val all = names.all { it in current }
        _state.value = _state.value.copy(
            favourites = if (all) current - names.toSet() else current + names,
            favouritesDirty = true,
        )
    }

    /** Saves the favourites and rebuilds the week of meals from them. */
    fun buildMeals() {
        viewModelScope.launch {
            coach.saveFavouriteFoods(_state.value.favourites)
            refresh()
        }
    }

    fun selectDay(index: Int) {
        _state.value = _state.value.copy(selectedDay = index)
    }

    fun showTrainingDay(training: Boolean) {
        _state.value = _state.value.copy(showTrainingDay = training)
    }
}
