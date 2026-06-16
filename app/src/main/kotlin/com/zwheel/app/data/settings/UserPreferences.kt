package com.zwheel.app.data.settings

import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit

data class UserPreferences(
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val tireDiameterInches: Double = 11.5,
    val hasCustomTireDiameter: Boolean = false,
    val lastConnectedDeviceId: String? = null,
    val hasRequestedBatteryOptimization: Boolean = false,
    val haUrl: String = "",
    val haToken: String = "",
)
