package com.zwheel.core.protocol

import com.zwheel.core.model.BoardType
import com.zwheel.core.model.RideMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParsersTest {
    @Test
    fun `rpm parses uint16 big endian M1 sample`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f30b rpm notification rawValueHex 001d from the M1 XR 4209 success capture.
        assertEquals(29, Parsers.rpm(hex("001d")))
    }

    @Test
    fun `amps parses signed XR current with OWCE scale`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f312 amps notification rawValueHex 01ef from the M1 XR 4209 success capture.
        assertEquals(0.99, Parsers.amps(hex("01ef"), BoardType.XR))
    }

    @Test
    fun `amps preserves signed regen with OWCE scale`() {
        assertEquals(-0.512, Parsers.amps(hex("ff00"), BoardType.XR))
    }

    @Test
    fun `amps uses Plus current scale from OWCE`() {
        assertEquals(0.891, Parsers.amps(hex("01ef"), BoardType.PLUS))
    }

    @Test
    fun `pack voltage parses tenths of volts M1 sample`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f316 pack_voltage notification rawValueHex 0266 from the M1 XR 4209 success capture.
        assertEquals(61.4, Parsers.packVoltage(hex("0266")))
    }

    @Test
    fun `temperatures parse raw two byte M1 sample`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f310 temperature notification rawValueHex 1918 from the M1 XR 4209 success capture.
        // Scale and byte order are unverified until a warmer-board capture.
        assertEquals(Pair(25, 24), Parsers.temperatures(hex("1918")))
    }

    @Test
    fun `battery percent parses uint16 big endian M1 sample`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f303 battery_percent notification rawValueHex 0060 from the M1 XR 4209 success capture.
        assertEquals(96, Parsers.batteryPercent(hex("0060")))
    }

    @Test
    fun `battery percent clamps above display range`() {
        assertEquals(100, Parsers.batteryPercent(hex("00ff")))
    }

    @Test
    fun `ride mode parses uint16 big endian M1 custom sample`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f302 ride_mode metadata_read rawValueHex 0000 from the M1 XR 4209 success capture.
        assertEquals(RideMode.CUSTOM, Parsers.rideMode(hex("0000")))
    }

    @Test
    fun `hardware revision parses uint16 big endian M1 sample`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f318 hardware_revision metadata_read rawValueHex 1071 from the M1 XR 4209 success capture.
        assertEquals(4209, Parsers.hardwareRevision(hex("1071")))
    }

    @Test
    fun `firmware revision parses uint16 big endian M1 sample`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f311 firmware_revision metadata_read rawValueHex 1026 from the M1 XR 4209 success capture.
        assertEquals(4134, Parsers.firmwareRevision(hex("1026")))
    }

    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0) { "Hex string must contain complete bytes." }
        return value.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
