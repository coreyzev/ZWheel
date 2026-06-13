package com.zwheel.core.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnitConversionsTest {
    @Test
    fun `converts meters per second to miles per hour`() {
        assertEquals(22.369, UnitConversions.metersPerSecondToMph(10.0), 0.001)
    }

    @Test
    fun `converts meters per second to kilometers per hour`() {
        assertEquals(36.0, UnitConversions.metersPerSecondToKph(10.0), 0.001)
    }

    @Test
    fun `converts meters to miles`() {
        assertEquals(1.0, UnitConversions.metersToMiles(1609.344), 0.001)
    }

    @Test
    fun `converts kilometers to miles`() {
        assertEquals(1.0, UnitConversions.kilometersToMiles(1.609344), 0.001)
    }

    @Test
    fun `converts celsius to fahrenheit`() {
        assertEquals(32.0, UnitConversions.celsiusToFahrenheit(0.0), 0.001)
        assertEquals(212.0, UnitConversions.celsiusToFahrenheit(100.0), 0.001)
    }
}
