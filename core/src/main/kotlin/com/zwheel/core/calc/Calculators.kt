package com.zwheel.core.calc

import com.zwheel.core.model.BoardType
import kotlin.math.PI

interface SpeedCalculator {
    fun correctedSpeedMetersPerSecond(input: SpeedInput): CorrectedSpeed
}

interface RangeEstimator {
    fun estimateRangeMeters(input: RangeInput): RangeEstimate
}

interface TopSpeedTracker {
    fun update(currentTopMetersPerSecond: Double, candidateMetersPerSecond: Double): Double
}

data class SpeedInput(
    val boardType: BoardType,
    val tireOuterDiameterInches: Double,
    val rpm: Double?,
    val firmwareSpeedMetersPerSecond: Double?,
)

data class RangeInput(
    val batteryPercent: Int?,
    val learnedMetersPerPercent: Double?,
    val fallbackMetersPerPercent: Double,
)

data class CorrectedSpeed(
    val metersPerSecond: Double,
    val rawMetersPerSecond: Double?,
    val source: SpeedSource,
    val isUncorrected: Boolean,
)

data class RangeEstimate(
    val meters: Double?,
    val source: RangeSource,
)

enum class SpeedSource {
    RPM,
    SCALED_FIRMWARE,
    RAW_FIRMWARE_UNCORRECTED,
    UNAVAILABLE,
}

enum class RangeSource {
    LEARNED_FROM_RIDES,
    BOARD_DEFAULT,
    UNAVAILABLE,
}

class RpmBasedSpeedCalculator : SpeedCalculator {
    override fun correctedSpeedMetersPerSecond(input: SpeedInput): CorrectedSpeed {
        val rpm = input.rpm
        if (rpm == null) {
            return CorrectedSpeed(
                metersPerSecond = input.firmwareSpeedMetersPerSecond ?: 0.0,
                rawMetersPerSecond = input.firmwareSpeedMetersPerSecond,
                source = if (input.firmwareSpeedMetersPerSecond == null) {
                    SpeedSource.UNAVAILABLE
                } else {
                    SpeedSource.RAW_FIRMWARE_UNCORRECTED
                },
                isUncorrected = true,
            )
        }

        val diameterMeters = input.tireOuterDiameterInches * INCHES_TO_METERS
        val metersPerMinute = rpm * PI * diameterMeters
        return CorrectedSpeed(
            metersPerSecond = metersPerMinute / SECONDS_PER_MINUTE,
            rawMetersPerSecond = input.firmwareSpeedMetersPerSecond,
            source = SpeedSource.RPM,
            isUncorrected = false,
        )
    }

    private companion object {
        const val INCHES_TO_METERS = 0.0254
        const val SECONDS_PER_MINUTE = 60.0
    }
}

class ScaledFirmwareSpeedCalculator : SpeedCalculator {
    override fun correctedSpeedMetersPerSecond(input: SpeedInput): CorrectedSpeed {
        val raw = input.firmwareSpeedMetersPerSecond
        if (raw == null) {
            return CorrectedSpeed(
                metersPerSecond = 0.0,
                rawMetersPerSecond = null,
                source = SpeedSource.UNAVAILABLE,
                isUncorrected = true,
            )
        }

        val stockDiameter = input.boardType.stockTireDiameterInches
        if (stockDiameter <= 0.0 || input.tireOuterDiameterInches <= 0.0) {
            return CorrectedSpeed(
                metersPerSecond = raw,
                rawMetersPerSecond = raw,
                source = SpeedSource.RAW_FIRMWARE_UNCORRECTED,
                isUncorrected = true,
            )
        }

        return CorrectedSpeed(
            metersPerSecond = raw * (input.tireOuterDiameterInches / stockDiameter),
            rawMetersPerSecond = raw,
            source = SpeedSource.SCALED_FIRMWARE,
            isUncorrected = false,
        )
    }
}
