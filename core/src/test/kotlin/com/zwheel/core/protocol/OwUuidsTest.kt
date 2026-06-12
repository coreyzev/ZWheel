package com.zwheel.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OwUuidsTest {
    @Test
    fun `writable allowlist contains only unlock ride mode lights and firmware revision trigger`() {
        // FIRMWARE_REVISION is intentionally writable for the Gemini handshake trigger only:
        // GeminiStrategy reads the current value and writes it back unchanged so the board emits
        // its challenge. The board ignores the written value; the write event is the trigger.
        // Evidence: OWCE OWBoard.cs L861-876. Corey sign-off: 2026-06-12. See ADR-004.
        // Do NOT remove FIRMWARE_REVISION from this set without a new ADR and Corey's sign-off.
        assertEquals(
            setOf(
                OwUuids.UART_WRITE,
                OwUuids.RIDE_MODE,
                OwUuids.LIGHTS,
                OwUuids.FIRMWARE_REVISION,
            ),
            OwUuids.writableAllowlist,
        )
    }
}
