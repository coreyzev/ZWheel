package com.zwheel.wear

import com.zwheel.core.model.ConnectionState
import com.zwheel.core.model.SpeedUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WearPayloadDecodeTest {

    private fun decode(
        speedRaw: Float = 5.0f,
        topSpeedRaw: Float = 10.0f,
        batteryRaw: Int = 80,
        rangeRaw: Float = 15000f,
        speedUnitStr: String? = "MPH",
        isRiding: Boolean = true,
        connStateStr: String? = "SUBSCRIBED",
        safetyHeadroomRaw: Int = -1,
    ) = decodeWatchPayload(
        speedRaw,
        topSpeedRaw,
        batteryRaw,
        rangeRaw,
        speedUnitStr,
        isRiding,
        connStateStr,
        safetyHeadroomRaw,
    )

    @Test
    fun `speed is decoded from float`() {
        assertEquals(3.5, decode(speedRaw = 3.5f).speedMetersPerSecondCorrected!!, 0.001)
    }

    @Test
    fun `sentinel -1 speed decodes to null`() {
        assertNull(decode(speedRaw = -1f).speedMetersPerSecondCorrected)
    }

    @Test
    fun `negative speed decodes to null`() {
        assertNull(decode(speedRaw = -0.5f).speedMetersPerSecondCorrected)
    }

    @Test
    fun `top speed is decoded from float`() {
        assertEquals(12.3, decode(topSpeedRaw = 12.3f).topSpeedMetersPerSecond, 0.001)
    }

    @Test
    fun `battery is decoded from int`() {
        assertEquals(57, decode(batteryRaw = 57).batteryPercent)
    }

    @Test
    fun `sentinel -1 battery decodes to null`() {
        assertNull(decode(batteryRaw = -1).batteryPercent)
    }

    @Test
    fun `range is decoded from float`() {
        assertEquals(8000.0, decode(rangeRaw = 8000f).estimatedRangeMeters!!, 0.5)
    }

    @Test
    fun `sentinel -1 range decodes to null`() {
        assertNull(decode(rangeRaw = -1f).estimatedRangeMeters)
    }

    @Test
    fun `speed unit MPH decodes correctly`() {
        assertEquals(SpeedUnit.MPH, decode(speedUnitStr = "MPH").speedUnit)
    }

    @Test
    fun `speed unit KPH decodes correctly`() {
        assertEquals(SpeedUnit.KPH, decode(speedUnitStr = "KPH").speedUnit)
    }

    @Test
    fun `unknown speed unit falls back to MPH`() {
        assertEquals(SpeedUnit.MPH, decode(speedUnitStr = "INVALID").speedUnit)
    }

    @Test
    fun `null speed unit falls back to MPH`() {
        assertEquals(SpeedUnit.MPH, decode(speedUnitStr = null).speedUnit)
    }

    @Test
    fun `connection state SUBSCRIBED decodes correctly`() {
        assertEquals(ConnectionState.SUBSCRIBED, decode(connStateStr = "SUBSCRIBED").connectionState)
    }

    @Test
    fun `connection state DISCONNECTED decodes correctly`() {
        assertEquals(ConnectionState.DISCONNECTED, decode(connStateStr = "DISCONNECTED").connectionState)
    }

    @Test
    fun `unknown connection state falls back to DISCONNECTED`() {
        assertEquals(ConnectionState.DISCONNECTED, decode(connStateStr = "GARBAGE").connectionState)
    }

    @Test
    fun `null connection state falls back to DISCONNECTED`() {
        assertEquals(ConnectionState.DISCONNECTED, decode(connStateStr = null).connectionState)
    }

    @Test
    fun `is_riding decoded correctly`() {
        assertEquals(true, decode(isRiding = true).isRiding)
        assertEquals(false, decode(isRiding = false).isRiding)
    }

    @Test
    fun `safety headroom sentinel -1 decodes to null`() {
        assertNull(decode(safetyHeadroomRaw = -1).safetyHeadroom)
    }

    @Test
    fun `safety headroom value decodes correctly`() {
        assertEquals(3, decode(safetyHeadroomRaw = 3).safetyHeadroom)
    }
}
