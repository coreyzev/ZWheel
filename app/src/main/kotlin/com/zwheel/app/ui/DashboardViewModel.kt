package com.zwheel.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.ble.ConnectionManager
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.core.calc.DefaultTopSpeedTracker
import com.zwheel.core.calc.RangeEstimator
import com.zwheel.core.model.BoardType
import com.zwheel.core.ports.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    settingsRepository: SettingsRepository,
    private val rangeEstimator: RangeEstimator,
) : ViewModel() {
    private val topSpeedTracker = DefaultTopSpeedTracker()

    val uiState: StateFlow<DashboardUiState> = combine(
        connectionManager.boardState,
        settingsRepository.preferences,
    ) { boardState, prefs ->
        val correctedSpeed = boardState.speedMetersPerSecondCorrected
        topSpeedTracker.consume(correctedSpeed)
        val boardType = boardState.identity?.type ?: BoardType.XR
        val estimatedRange = rangeEstimator.estimateKilometersRemaining(
            batteryPct = boardState.batteryPercent,
            boardType = boardType,
            calibration = null,
        )
        boardState.toDashboardUiState(
            prefs = prefs,
            topSpeedMetersPerSecond = topSpeedTracker.currentTripMaxMetersPerSecond,
            estimatedRangeKilometers = estimatedRange,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = emptyDashboardState(),
    )

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val devices: StateFlow<List<ScanResult>> = connectionManager.devices

    fun scan() {
        connectionManager.scan()
    }

    fun connect(deviceId: String) {
        viewModelScope.launch {
            connectionManager.connect(deviceId)
        }
    }

    fun disconnect() {
        connectionManager.disconnect()
    }
}
