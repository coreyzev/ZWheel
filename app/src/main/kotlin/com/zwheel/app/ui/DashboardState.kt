package com.zwheel.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DashboardUiState(
    val boardName: String,
    val connectionLabel: String,
    val rssi: Int,
    val firmwareLabel: String,
    val speedMph: Double,
    val topSpeedMph: Double,
    val estimatedRangeMiles: Double,
    val batteryPercent: Int,
    val packVoltage: Double,
    val amps: Double,
    val controllerTempF: Int,
    val cellVoltages: List<CellVoltageUiState>,
    val tripMiles: Double,
    val tripAmpHours: Double,
    val regenAmpHours: Double,
    val rideMode: String,
    val lightsLabel: String,
    val tireDiameterInches: Double,
)

data class CellVoltageUiState(
    val label: String,
    val volts: Double,
    val isLow: Boolean,
)

fun mockDashboardState(): DashboardUiState = DashboardUiState(
    boardName = "XR 4029",
    connectionLabel = "MOCK CONNECTED",
    rssi = -58,
    firmwareLabel = "FW GEMINI",
    speedMph = 14.8,
    topSpeedMph = 19.6,
    estimatedRangeMiles = 7.4,
    batteryPercent = 63,
    packVoltage = 54.2,
    amps = -8.4,
    controllerTempF = 96,
    cellVoltages = List(16) { index ->
        val volts = if (index == 7) 3.86 else 3.94 + (index % 3) * 0.01
        CellVoltageUiState(label = "C${index + 1}", volts = volts, isLow = volts < 3.9)
    },
    tripMiles = 3.42,
    tripAmpHours = 2.14,
    regenAmpHours = 0.31,
    rideMode = "MISSION",
    lightsLabel = "FRONT + BACK",
    tireDiameterInches = 10.5,
)

fun mockDashboardStateFlow(): StateFlow<DashboardUiState> = MutableStateFlow(mockDashboardState())
