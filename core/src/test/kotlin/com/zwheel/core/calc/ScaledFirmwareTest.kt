package com.zwheel.core.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScaledFirmwareTest {
    private val calculator = ScaledFirmware(stockDiameterInches = 11.5)

    @Test
    fun `scales firmware speed up by diameter ratio`() {
        assertEquals(
            10.87,
            calculator.correctedMetersPerSecond(
                rpm = null,
                firmwareSpeedMetersPerSecond = 10.0,
                diameterInches = 12.5,
            ) ?: error("Expected corrected speed"),
            0.001,
        )
    }

    @Test
    fun `returns null when firmware speed is null`() {
        assertNull(
            calculator.correctedMetersPerSecond(
                rpm = null,
                firmwareSpeedMetersPerSecond = null,
                diameterInches = 12.5,
            ),
        )
    }

    @Test
    fun `returns null when diameter is out of range`() {
        assertNull(
            calculator.correctedMetersPerSecond(
                rpm = null,
                firmwareSpeedMetersPerSecond = 10.0,
                diameterInches = 16.1,
            ),
        )
    }
}
