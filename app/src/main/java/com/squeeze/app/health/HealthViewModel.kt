package com.squeeze.app.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.core.health.ActivityInsights
import com.squeeze.core.health.DailyActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * @param today null until read, or when nothing is connected
 * @param week oldest first, for the steps strip; empty when not connected
 * @param baselineRestingHr the user's usual resting pulse, for the recovery note
 */
data class HealthUiState(
    val availability: HealthAvailability = HealthAvailability.NOT_SUPPORTED,
    val connected: Boolean = false,
    val grantedCount: Int = 0,
    val today: DailyActivity? = null,
    val week: List<DailyActivity> = emptyList(),
    val baselineRestingHr: Int? = null,
    val loading: Boolean = true,
)

/** Health Connect status and today's synced data, shared by Settings, Body and Fuel. */
@HiltViewModel
class HealthViewModel @Inject constructor(
    private val health: HealthConnectManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    val permissions: Set<String> get() = health.permissions

    fun permissionContract() = health.permissionContract()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val availability = health.availability()
            val granted = if (availability == HealthAvailability.AVAILABLE) health.granted() else emptySet()
            val connected = granted.any { it in health.permissions }
            val week = if (connected) health.recent(7) else emptyList()
            _state.value = HealthUiState(
                availability = availability,
                connected = connected,
                grantedCount = granted.count { it in health.permissions },
                today = week.lastOrNull(),
                week = week,
                baselineRestingHr = ActivityInsights.baseline(week.dropLast(1).map { it.restingHeartRate }),
                loading = false,
            )
        }
    }
}
