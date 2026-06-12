package com.zwheel.core.protocol.debug

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BleDebugEventTest {
    @Test
    fun `serializes compact jsonl with escaped values`() {
        val recorder = BleDebugRecorder(
            salt = "test-salt",
            sessionId = "session-1",
            startEpochMs = 1_000,
        )

        recorder.record(
            type = "scan_discovery",
            deviceId = "AA:BB:CC:DD:EE:FF",
            displayName = "Corey \"XR\"",
            rssi = -61,
            nowEpochMs = 1_250,
        )

        assertEquals(
            "{\"schemaVersion\":\"m1-ble-debug-v1\",\"sessionId\":\"session-1\"," +
                "\"offsetMs\":250,\"type\":\"scan_discovery\"," +
                "\"deviceHash\":\"43dd4e017b914ac62dee6b57\"," +
                "\"displayName\":\"Corey \\\"XR\\\"\",\"rssi\":-61}",
            recorder.toJsonLines(),
        )
    }

    @Test
    fun `redacts raw stable device ids`() {
        val recorder = BleDebugRecorder(salt = "test-salt", sessionId = "session-1", startEpochMs = 0)

        recorder.record(type = "selected_device", deviceId = "AA:BB:CC:DD:EE:FF")

        val json = recorder.toJsonLines()
        assertFalse(json.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(json.contains("\"deviceHash\":\"43dd4e017b914ac62dee6b57\""))
    }
}
