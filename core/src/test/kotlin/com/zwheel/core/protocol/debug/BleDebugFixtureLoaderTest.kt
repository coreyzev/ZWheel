package com.zwheel.core.protocol.debug

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BleDebugFixtureLoaderTest {
    @Test
    fun `loads m1 jsonl fixtures as non-empty event rows`() {
        val lines = BleDebugFixtureLoader.loadM1Fixture("sample_redacted_session.jsonl")

        assertEquals(3, lines.size)
        lines.forEach { line ->
            assertTrue(line.startsWith("{"))
            assertTrue(line.endsWith("}"))
            assertTrue(line.contains("\"schemaVersion\":\"m1-ble-debug-v1\""))
            assertTrue(line.contains("\"sessionId\":\"sample-session\""))
        }
    }
}
