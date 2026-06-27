package com.zwheel.app.data.settings

import com.zwheel.core.alerts.AlertOutput
import com.zwheel.core.alerts.AlertTone
import com.zwheel.core.alerts.AlertType
import com.zwheel.core.model.BoardType
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit

data class UserPreferences(
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val tireDiameterInches: Double = 11.5,
    val hasCustomTireDiameter: Boolean = false,
    val lastConnectedDeviceId: String? = null,
    val lastConnectedBoardType: BoardType? = null,
    val lastConnectedTireDiameterInches: Double? = null,
    val lastConnectedBoardName: String? = null,
    val lastConnectedSerial: String? = null,
    val lastConnectedBatterySerial: String? = null,
    val lastConnectedHardwareRev: String? = null,
    val lastConnectedFirmwareRev: String? = null,
    val lastConnectedLifetimeMiles: Int? = null,
    val lastConnectedLifetimeAmpHours: Double? = null,
    val lastConnectedCellCount: Int? = null,
    val hasRequestedBatteryOptimization: Boolean = false,
    val hasAttemptedLocationPermission: Boolean = false,
    val haUrl: String = "",
    val haToken: String = "",
    val customBoardName: String? = null,
    val bleDebugPassword: String = "",
    val audioAlertsEnabled: Boolean = false,
    val audioAlertType: AlertType = AlertType.HEADROOM,
    /** Speed threshold stored in mph. Convert to m/s when building AlertConfig. */
    val audioAlertThresholdMph: Int = 16,
    /** Headroom threshold (raw firmware integer). Alert when safetyHeadroom <= this value. */
    val audioAlertThresholdHeadroom: Int = 0,
    val audioAlertOutput: AlertOutput = AlertOutput.WATCH,
    val audioAlertTone: AlertTone = AlertTone.SHORT_BEEP,
)
