package com.zwheel.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OwUuidsTest {
    @Test
    fun `writable allowlist contains only unlock ride mode and lights`() {
        assertEquals(
            setOf(
                OwUuids.UART_WRITE,
                OwUuids.RIDE_MODE,
                OwUuids.LIGHTS,
            ),
            OwUuids.writableAllowlist,
        )
    }
}
