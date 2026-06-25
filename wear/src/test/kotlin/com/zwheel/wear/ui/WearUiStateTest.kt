package com.zwheel.wear.ui

import com.zwheel.core.model.ConnectionState
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.WatchPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WearUiStateTest {

    // ── WearDashboardUiState.activeFace() ──────────────────────────────────

    private fun connected(
        pushback: Boolean = false,
        errorCode: Int? = null,
    ) = WearDashboardUiState(
        speedDisplay = "15",
        speedDecimalDisplay = "3",
        speedUnitLabel = "MPH",
        topSpeedDisplay = "18.2",
        batteryPercent = 72,
        batteryDisplay = "72%",
        rangeDisplay = "8.5",
        connectionLabel = "SUBSCRIBED",
        pushbackApproaching = pushback,
        isConnected = true,
        lastErrorCode = errorCode,
    )

    private fun disconnected() = WearDashboardUiState(
        speedDisplay = "--",
        speedDecimalDisplay = "-",
        speedUnitLabel = "MPH",
        topSpeedDisplay = "--",
        batteryPercent = 0,
        batteryDisplay = "--%",
        rangeDisplay = "--",
        connectionLabel = "DISCONNECTED",
        pushbackApproaching = false,
        isConnected = false,
    )

    @Test
    fun `connected board normal ride shows ACTIVE face`() {
        assertEquals(WearDashboardUiState.Face.ACTIVE, connected().activeFace(isAmbient = false))
    }

    @Test
    fun `connected board ambient mode shows AMBIENT face`() {
        assertEquals(WearDashboardUiState.Face.AMBIENT, connected().activeFace(isAmbient = true))
    }

    @Test
    fun `connected board approaching pushback shows CAUTION face`() {
        assertEquals(
            WearDashboardUiState.Face.CAUTION,
            connected(pushback = true).activeFace(isAmbient = false),
        )
    }

    @Test
    fun `error code present shows ERROR face regardless of connection`() {
        assertEquals(
            WearDashboardUiState.Face.ERROR,
            connected(errorCode = 23).activeFace(isAmbient = false),
        )
    }

    @Test
    fun `null payload renders empty state — DISCONNECTED face`() {
        val state = null?.toUiState() ?: WearDashboardUiState.empty()
        assertEquals(WearDashboardUiState.Face.DISCONNECTED, state.activeFace(isAmbient = false))
    }

    @Test
    fun `disconnected state shows DISCONNECTED face`() {
        assertEquals(
            WearDashboardUiState.Face.DISCONNECTED,
            disconnected().activeFace(isAmbient = false),
        )
    }

    // ── WatchPayload.toUiState() — connected board shows ACTIVE ───────────

    private fun connectedPayload(
        connectionState: ConnectionState = ConnectionState.SUBSCRIBED,
        safetyHeadroom: Int? = 5,
        errorCode: Int? = null,
    ) = WatchPayload(
        speedMetersPerSecondCorrected = 6.7,
        topSpeedMetersPerSecond = 9.2,
        batteryPercent = 72,
        estimatedRangeMeters = 9000.0,
        speedUnit = SpeedUnit.MPH,
        isRiding = true,
        connectionState = connectionState,
        safetyHeadroom = safetyHeadroom,
        lastErrorCode = errorCode,
    )

    @Test
    fun `SUBSCRIBED payload converts to ACTIVE face`() {
        val face = connectedPayload(ConnectionState.SUBSCRIBED).toUiState().activeFace(false)
        assertEquals(WearDashboardUiState.Face.ACTIVE, face)
    }

    @Test
    fun `DEGRADED payload converts to ACTIVE face`() {
        val face = connectedPayload(ConnectionState.DEGRADED).toUiState().activeFace(false)
        assertEquals(WearDashboardUiState.Face.ACTIVE, face)
    }

    @Test
    fun `SCANNING payload converts to DISCONNECTED face`() {
        val face = connectedPayload(ConnectionState.SCANNING).toUiState().activeFace(false)
        assertEquals(WearDashboardUiState.Face.DISCONNECTED, face)
    }

    @Test
    fun `DISCONNECTED payload converts to DISCONNECTED face`() {
        val face = connectedPayload(ConnectionState.DISCONNECTED).toUiState().activeFace(false)
        assertEquals(WearDashboardUiState.Face.DISCONNECTED, face)
    }

    @Test
    fun `safety headroom zero triggers CAUTION face`() {
        val face = connectedPayload(safetyHeadroom = 0).toUiState().activeFace(false)
        assertEquals(WearDashboardUiState.Face.CAUTION, face)
    }

    @Test
    fun `safety headroom negative triggers CAUTION face`() {
        val face = connectedPayload(safetyHeadroom = -1).toUiState().activeFace(false)
        assertEquals(WearDashboardUiState.Face.CAUTION, face)
    }

    @Test
    fun `null safety headroom does not trigger CAUTION`() {
        val face = connectedPayload(safetyHeadroom = null).toUiState().activeFace(false)
        assertEquals(WearDashboardUiState.Face.ACTIVE, face)
    }

    @Test
    fun `speed converts from mps to mph correctly`() {
        val state = connectedPayload().toUiState()
        // 6.7 m/s * 2.23694 ≈ 14.99 mph → integer part 14, decimal part 9
        assertEquals("14", state.speedDisplay)
        assertEquals("9", state.speedDecimalDisplay)
        assertEquals("MPH", state.speedUnitLabel)
    }

    @Test
    fun `battery and range display are formatted`() {
        val state = connectedPayload().toUiState()
        assertEquals("72%", state.batteryDisplay)
        // 9000 m * 0.000621371 ≈ 5.6 miles
        assertEquals("5.6", state.rangeDisplay)
    }
}
