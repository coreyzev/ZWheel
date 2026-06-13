package com.zwheel.core.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RpmBasedTest {
    private val calculator = RpmBased()

    @Test
    fun `M1 idle sample calculates corrected speed from rpm and diameter`() {
        assertEquals(
            0.444,
            calculator.correctedMetersPerSecond(
                rpm = 29.0,
                firmwareSpeedMetersPerSecond = null,
                diameterInches = 11.5,
            ) ?: error("Expected corrected speed"),
            0.001,
        )
    }

    @Test
    fun `returns null when rpm is null`() {
        assertNull(
            calculator.correctedMetersPerSecond(
                rpm = null,
                firmwareSpeedMetersPerSecond = null,
                diameterInches = 11.5,
            ),
        )
    }

    @Test
    fun `returns null when diameter is below safety range`() {
        assertNull(
            calculator.correctedMetersPerSecond(
                rpm = 29.0,
                firmwareSpeedMetersPerSecond = null,
                diameterInches = 7.9,
            ),
        )
    }

    @Test
    fun `returns null when diameter is above safety range`() {
        assertNull(
            calculator.correctedMetersPerSecond(
                rpm = 29.0,
                firmwareSpeedMetersPerSecond = null,
                diameterInches = 16.1,
            ),
        )
    }
}
