package com.zwheel.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParsersTest {
    @Test
    fun `battery percent parses unsigned byte and clamps to display range`() {
        assertEquals(87, Parsers.batteryPercent(byteArrayOf(0x57)))
        assertEquals(100, Parsers.batteryPercent(byteArrayOf(0xff.toByte())))
    }

    @Test
    fun `cell voltages parse millivolt big endian fixture`() {
        assertEquals(
            listOf(4.123, 4.101),
            Parsers.cellVoltagesMillivoltsBigEndian(
                byteArrayOf(0x10, 0x1b, 0x10, 0x05),
            ),
        )
    }
}
