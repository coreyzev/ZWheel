package com.zwheel.core.calc

import com.zwheel.core.model.BoardType

interface SpeedCalculator {
    fun correctedMetersPerSecond(
        rpm: Double?,
        firmwareSpeedMetersPerSecond: Double?,
        diameterInches: Double,
    ): Double?
}

interface TopSpeedTracker {
    val currentTripMaxMetersPerSecond: Double?

    fun consume(speedMetersPerSecond: Double?)
}

interface RangeEstimator {
    fun estimateKilometersRemaining(
        batteryPct: Int?,
        boardType: BoardType,
        calibration: RangeCalibration? = null,
    ): Double?
}

data class RangeCalibration(
    val kilometersPerBatteryPercent: Double,
)
