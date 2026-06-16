package com.zwheel.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.ble.ConnectionManager
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.service.RideServiceController
import com.zwheel.app.service.RideServiceRepository
import com.zwheel.core.calc.RangeEstimator
import com.zwheel.core.model.BoardType
import com.zwheel.core.ports.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val rideServiceRepository: RideServiceRepository,
    private val rideServiceController: RideServiceController,
    settingsRepository: SettingsRepository,
    private val rangeEstimator: RangeEstimator,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = combine(
        connectionManager.boardState,
        settingsRepository.preferences,
        rideServiceRepository.tripDistanceMeters,
        rideServiceRepository.gpsLocked,
        rideServiceRepository.topSpeedMetersPerSecond,
    ) { boardState, prefs, tripDistanceMeters, gpsLocked, topSpeedMps ->
        val boardType = boardState.identity?.type ?: BoardType.XR
        val estimatedRange = rangeEstimator.estimateKilometersRemaining(
            batteryPct = boardState.batteryPercent,
            boardType = boardType,
            calibration = null,
        )
        boardState.toDashboardUiState(
            prefs = prefs,
            topSpeedMetersPerSecond = topSpeedMps,
            estimatedRangeKilometers = estimatedRange,
            tripDistanceMeters = tripDistanceMeters,
            gpsLocked = gpsLocked,
        )
    }.combine(connectionManager.rssi) { state, rssi ->
        state.copy(rssi = rssi)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = emptyDashboardState(),
    )

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    // Scan is still UI-driven; scan results come from ConnectionManager until scan
    // is moved into the service in a future gate.
    val devices: StateFlow<List<ScanResult>> = connectionManager.devices

    fun scan() {
        connectionManager.scan()
    }

    fun connect(deviceId: String) {
        rideServiceController.connect(deviceId)
    }

    fun disconnect() {
        rideServiceController.disconnect()
    }
}
