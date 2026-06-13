package com.zwheel.app.ui

import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.core.calc.UnitConversions
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DashboardUiState(
    val boardName: String,
    val connectionLabel: String,
    val rssi: Int,
    val firmwareLabel: String,
    val speedMph: Double,
    val speedUnitLabel: String,
    val topSpeedMph: Double,
    val estimatedRangeMiles: Double,
    val rangeUnitLabel: String,
    val batteryPercent: Int,
    val packVoltage: Double,
    val amps: Double,
    val controllerTempF: Int,
    val temperatureUnitLabel: String,
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
    speedUnitLabel = "MPH",
    topSpeedMph = 19.6,
    estimatedRangeMiles = 7.4,
    rangeUnitLabel = "MI",
    batteryPercent = 63,
    packVoltage = 54.2,
    amps = -8.4,
    controllerTempF = 96,
    temperatureUnitLabel = "F",
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

fun emptyDashboardState(): DashboardUiState =
    BoardState().toDashboardUiState(
        prefs = UserPreferences(),
        topSpeedMetersPerSecond = null,
        estimatedRangeKilometers = null,
    )

fun BoardState.toDashboardUiState(
    prefs: UserPreferences,
    topSpeedMetersPerSecond: Double?,
    estimatedRangeKilometers: Double?,
): DashboardUiState {
    val displaySpeedMetersPerSecond = speedMetersPerSecondCorrected
        ?: 0.0
    val speed = displaySpeedMetersPerSecond.toDisplaySpeed(prefs.speedUnit)
    val topSpeed = (topSpeedMetersPerSecond ?: 0.0).toDisplaySpeed(prefs.speedUnit)
    val range = when (prefs.speedUnit) {
        SpeedUnit.MPH -> UnitConversions.kilometersToMiles(estimatedRangeKilometers ?: 0.0)
        SpeedUnit.KPH -> estimatedRangeKilometers ?: 0.0
    }
    val controllerTemp = controllerTempCelsius?.toDisplayTemperature(prefs.temperatureUnit) ?: 0.0

    return DashboardUiState(
        boardName = identity?.name ?: "Onewheel",
        connectionLabel = connectionState.name,
        rssi = 0,
        firmwareLabel = identity?.firmwareRevision?.let { "FW $it" } ?: "FW --",
        speedMph = speed,
        speedUnitLabel = prefs.speedUnit.name,
        topSpeedMph = topSpeed,
        estimatedRangeMiles = range,
        rangeUnitLabel = if (prefs.speedUnit == SpeedUnit.MPH) "MI" else "KM",
        batteryPercent = batteryPercent ?: 0,
        packVoltage = packVoltage ?: 0.0,
        amps = amps ?: 0.0,
        controllerTempF = controllerTemp.toInt(),
        temperatureUnitLabel = if (prefs.temperatureUnit == TemperatureUnit.FAHRENHEIT) "F" else "C",
        cellVoltages = cellVoltages.mapIndexed { index, volts ->
            CellVoltageUiState(label = "C${index + 1}", volts = volts, isLow = volts < 3.9)
        },
        tripMiles = 0.0,
        tripAmpHours = 0.0,
        regenAmpHours = 0.0,
        rideMode = rideMode.name,
        lightsLabel = when (lightsOn) {
            true -> "ON"
            false -> "OFF"
            null -> "--"
        },
        tireDiameterInches = prefs.tireDiameterInches,
    )
}

private fun Double.toDisplaySpeed(unit: SpeedUnit): Double =
    when (unit) {
        SpeedUnit.MPH -> UnitConversions.metersPerSecondToMph(this)
        SpeedUnit.KPH -> UnitConversions.metersPerSecondToKph(this)
    }

private fun Double.toDisplayTemperature(unit: TemperatureUnit): Double =
    when (unit) {
        TemperatureUnit.FAHRENHEIT -> UnitConversions.celsiusToFahrenheit(this)
        TemperatureUnit.CELSIUS -> this
    }
