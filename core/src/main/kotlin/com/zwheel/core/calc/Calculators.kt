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

class RpmBased : SpeedCalculator {
    override fun correctedMetersPerSecond(
        rpm: Double?,
        firmwareSpeedMetersPerSecond: Double?,
        diameterInches: Double,
    ): Double? {
        if (rpm == null) return null
        if (diameterInches !in 8.0..16.0) return null

        val diameterMeters = diameterInches * 0.0254
        return rpm * Math.PI * diameterMeters / 60.0
    }
}

class ScaledFirmware(
    private val stockDiameterInches: Double,
) : SpeedCalculator {
    override fun correctedMetersPerSecond(
        rpm: Double?,
        firmwareSpeedMetersPerSecond: Double?,
        diameterInches: Double,
    ): Double? {
        if (firmwareSpeedMetersPerSecond == null) return null
        if (diameterInches !in 8.0..16.0) return null

        return firmwareSpeedMetersPerSecond * (diameterInches / stockDiameterInches)
    }
}

class DefaultTopSpeedTracker : TopSpeedTracker {
    private var max: Double? = null

    override val currentTripMaxMetersPerSecond: Double?
        get() = max

    override fun consume(speedMetersPerSecond: Double?) {
        if (speedMetersPerSecond == null) return

        val currentMax = max
        if (currentMax == null || speedMetersPerSecond > currentMax) {
            max = speedMetersPerSecond
        }
    }

    internal fun reset() {
        max = null
    }
}

class DefaultRangeEstimator : RangeEstimator {
    override fun estimateKilometersRemaining(
        batteryPct: Int?,
        boardType: BoardType,
        calibration: RangeCalibration?,
    ): Double? {
        if (batteryPct == null) return null

        val kilometersPerBatteryPercent =
            calibration?.kilometersPerBatteryPercent ?: boardType.defaultKilometersPerBatteryPercent
        return batteryPct * kilometersPerBatteryPercent
    }

    private val BoardType.defaultKilometersPerBatteryPercent: Double
        get() = when (this) {
            BoardType.XR -> 0.16
            BoardType.PLUS -> 0.14
            BoardType.PINT -> 0.10
            BoardType.PINT_X -> 0.12
            BoardType.ONEWHEEL_V1 -> 0.12
            BoardType.UNKNOWN -> 0.10
        }
}
