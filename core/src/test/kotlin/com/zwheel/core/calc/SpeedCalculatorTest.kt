package com.zwheel.core.calc

import com.zwheel.core.model.BoardType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.PI

class SpeedCalculatorTest {
    private val rpmCalculator = RpmBasedSpeedCalculator()
    private val scaledCalculator = ScaledFirmwareSpeedCalculator()

    @Test
    fun `rpm calculator converts stock XR tire speed from first principles`() {
        val result = rpmCalculator.correctedSpeedMetersPerSecond(
            SpeedInput(
                boardType = BoardType.XR,
                tireOuterDiameterInches = 11.5,
                rpm = 300.0,
                firmwareSpeedMetersPerSecond = 4.2,
            ),
        )

        assertEquals(expectedMetersPerSecond(rpm = 300.0, diameterInches = 11.5), result.metersPerSecond, TOLERANCE)
        assertEquals(4.2, result.rawMetersPerSecond)
        assertEquals(SpeedSource.RPM, result.source)
        assertEquals(false, result.isUncorrected)
    }

    @Test
    fun `rpm calculator reflects smaller tire diameter correction`() {
        val stock = rpmCalculator.correctedSpeedMetersPerSecond(
            SpeedInput(BoardType.XR, tireOuterDiameterInches = 11.5, rpm = 400.0, firmwareSpeedMetersPerSecond = null),
        )
        val smallerTire = rpmCalculator.correctedSpeedMetersPerSecond(
            SpeedInput(BoardType.XR, tireOuterDiameterInches = 10.5, rpm = 400.0, firmwareSpeedMetersPerSecond = null),
        )

        assertEquals(expectedMetersPerSecond(rpm = 400.0, diameterInches = 11.5), stock.metersPerSecond, TOLERANCE)
        assertEquals(expectedMetersPerSecond(rpm = 400.0, diameterInches = 10.5), smallerTire.metersPerSecond, TOLERANCE)
        assertEquals(10.5 / 11.5, smallerTire.metersPerSecond / stock.metersPerSecond, TOLERANCE)
    }

    @Test
    fun `rpm calculator handles custom tire diameters across expected rider range`() {
        listOf(10.0, 10.5, 11.0, 11.5, 12.0).forEach { diameter ->
            val result = rpmCalculator.correctedSpeedMetersPerSecond(
                SpeedInput(BoardType.XR, tireOuterDiameterInches = diameter, rpm = 512.0, firmwareSpeedMetersPerSecond = null),
            )

            assertEquals(expectedMetersPerSecond(rpm = 512.0, diameterInches = diameter), result.metersPerSecond, TOLERANCE)
            assertEquals(SpeedSource.RPM, result.source)
            assertEquals(false, result.isUncorrected)
        }
    }

    @Test
    fun `rpm calculator falls back conservatively when rpm is unavailable`() {
        val result = rpmCalculator.correctedSpeedMetersPerSecond(
            SpeedInput(BoardType.XR, tireOuterDiameterInches = 10.5, rpm = null, firmwareSpeedMetersPerSecond = 5.0),
        )

        assertEquals(5.0, result.metersPerSecond)
        assertEquals(5.0, result.rawMetersPerSecond)
        assertEquals(SpeedSource.RAW_FIRMWARE_UNCORRECTED, result.source)
        assertEquals(true, result.isUncorrected)
    }

    @Test
    fun `scaled firmware calculator scales by configured diameter over stock diameter`() {
        val result = scaledCalculator.correctedSpeedMetersPerSecond(
            SpeedInput(BoardType.XR, tireOuterDiameterInches = 10.5, rpm = null, firmwareSpeedMetersPerSecond = 8.0),
        )

        assertEquals(8.0 * (10.5 / 11.5), result.metersPerSecond, TOLERANCE)
        assertEquals(8.0, result.rawMetersPerSecond)
        assertEquals(SpeedSource.SCALED_FIRMWARE, result.source)
        assertEquals(false, result.isUncorrected)
    }

    @Test
    fun `scaled firmware calculator fails conservative with invalid tire diameter`() {
        val result = scaledCalculator.correctedSpeedMetersPerSecond(
            SpeedInput(BoardType.XR, tireOuterDiameterInches = 0.0, rpm = null, firmwareSpeedMetersPerSecond = 8.0),
        )

        assertEquals(8.0, result.metersPerSecond)
        assertEquals(SpeedSource.RAW_FIRMWARE_UNCORRECTED, result.source)
        assertEquals(true, result.isUncorrected)
    }

    private fun expectedMetersPerSecond(rpm: Double, diameterInches: Double): Double {
        val diameterMeters = diameterInches * INCHES_TO_METERS
        return rpm * PI * diameterMeters / SECONDS_PER_MINUTE
    }

    private companion object {
        const val INCHES_TO_METERS = 0.0254
        const val SECONDS_PER_MINUTE = 60.0
        const val TOLERANCE = 0.000001
    }
}
