package com.zwheel.app.wear

import com.zwheel.core.model.ConnectionState
import com.zwheel.core.model.KEY_BATTERY_PCT
import com.zwheel.core.model.KEY_CONNECTION_STATE
import com.zwheel.core.model.KEY_ESTIMATED_RANGE_M
import com.zwheel.core.model.KEY_IS_RIDING
import com.zwheel.core.model.KEY_SPEED_MPS_CORRECTED
import com.zwheel.core.model.KEY_SPEED_UNIT
import com.zwheel.core.model.KEY_TOP_SPEED_MPS
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.WatchPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WearPayloadEncodeTest {

    private fun payload(
        speedMps: Double? = 5.0,
        topSpeedMps: Double = 10.0,
        batteryPct: Int? = 80,
        rangeM: Double? = 15000.0,
        speedUnit: SpeedUnit = SpeedUnit.MPH,
        isRiding: Boolean = true,
        connectionState: ConnectionState = ConnectionState.SUBSCRIBED,
    ) = WatchPayload(
        speedMetersPerSecondCorrected = speedMps,
        topSpeedMetersPerSecond = topSpeedMps,
        batteryPercent = batteryPct,
        estimatedRangeMeters = rangeM,
        speedUnit = speedUnit,
        isRiding = isRiding,
        connectionState = connectionState,
    )

    @Test
    fun `speed is encoded as float`() {
        val entries = payload(speedMps = 3.5).toDataEntries()
        assertEquals(3.5f, entries[KEY_SPEED_MPS_CORRECTED] as Float, 0.001f)
    }

    @Test
    fun `null speed encodes as sentinel -1`() {
        val entries = payload(speedMps = null).toDataEntries()
        assertEquals(-1f, entries[KEY_SPEED_MPS_CORRECTED] as Float, 0.001f)
    }

    @Test
    fun `top speed is encoded as float`() {
        val entries = payload(topSpeedMps = 12.3).toDataEntries()
        assertEquals(12.3f, entries[KEY_TOP_SPEED_MPS] as Float, 0.001f)
    }

    @Test
    fun `battery is encoded as int`() {
        val entries = payload(batteryPct = 57).toDataEntries()
        assertEquals(57, entries[KEY_BATTERY_PCT] as Int)
    }

    @Test
    fun `null battery encodes as sentinel -1`() {
        val entries = payload(batteryPct = null).toDataEntries()
        assertEquals(-1, entries[KEY_BATTERY_PCT] as Int)
    }

    @Test
    fun `null range encodes as sentinel -1`() {
        val entries = payload(rangeM = null).toDataEntries()
        assertEquals(-1f, entries[KEY_ESTIMATED_RANGE_M] as Float, 0.001f)
    }

    @Test
    fun `range is encoded as float`() {
        val entries = payload(rangeM = 8000.0).toDataEntries()
        assertEquals(8000f, entries[KEY_ESTIMATED_RANGE_M] as Float, 0.5f)
    }

    @Test
    fun `speed unit is encoded as enum name`() {
        assertEquals("KPH", payload(speedUnit = SpeedUnit.KPH).toDataEntries()[KEY_SPEED_UNIT])
        assertEquals("MPH", payload(speedUnit = SpeedUnit.MPH).toDataEntries()[KEY_SPEED_UNIT])
    }

    @Test
    fun `is_riding is encoded as boolean`() {
        assertEquals(true, payload(isRiding = true).toDataEntries()[KEY_IS_RIDING])
        assertEquals(false, payload(isRiding = false).toDataEntries()[KEY_IS_RIDING])
    }

    @Test
    fun `connection state is encoded as enum name`() {
        assertEquals(
            "SUBSCRIBED",
            payload(connectionState = ConnectionState.SUBSCRIBED).toDataEntries()[KEY_CONNECTION_STATE],
        )
        assertEquals(
            "DISCONNECTED",
            payload(connectionState = ConnectionState.DISCONNECTED).toDataEntries()[KEY_CONNECTION_STATE],
        )
    }
}
