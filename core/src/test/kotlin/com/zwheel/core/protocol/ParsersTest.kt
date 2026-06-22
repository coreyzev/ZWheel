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
    fun `tripAmpHours XR scale 0x0064 = 100 * 0_00018`() {
        // 0x0064 = 100, 100 * 0.00018 = 0.018 Ah
        assertEquals(0.018, Parsers.tripAmpHours(hex("0064"), BoardType.XR), 1e-9)
    }

    @Test
    fun `tripAmpHours XR 4-byte zero-padded payload same result`() {
        // Gemini firmware may zero-pad trip counters to 4 bytes; last 2 bytes carry the value.
        assertEquals(0.018, Parsers.tripAmpHours(hex("00000064"), BoardType.XR), 1e-9)
    }

    @Test
    fun `tripAmpHours V1 scale 0x0064 = 100 * 0_00009`() {
        assertEquals(0.009, Parsers.tripAmpHours(hex("0064"), BoardType.ONEWHEEL_V1), 1e-9)
    }

    @Test
    fun `tripRegenAmpHours XR scale 0x0032 = 50 * 0_00018`() {
        // 0x0032 = 50, 50 * 0.00018 = 0.009 Ah
        assertEquals(0.009, Parsers.tripRegenAmpHours(hex("0032"), BoardType.XR), 1e-9)
    }

    @Test
    fun `tripRegenAmpHours zero returns 0`() {
        assertEquals(0.0, Parsers.tripRegenAmpHours(hex("0000"), BoardType.XR), 1e-9)
    }

    @Test
    fun `tripRegenAmpHours XR 4-byte zero-padded payload same result`() {
        assertEquals(0.009, Parsers.tripRegenAmpHours(hex("00000032"), BoardType.XR), 1e-9)
    }

    @Test
    fun `pack voltage parses tenths of volts M1 sample`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f316 pack_voltage notification rawValueHex 0266 from the M1 XR 4209 success capture.
        assertEquals(61.4, Parsers.packVoltage(hex("0266")))
    }

    @Test
    fun `cellVoltage old encoding parses cell index from byte 1 and voltage from byte 0`() {
        // FW 4134 (< 4141): bytes[0]=200 -> 200*0.02=4.0V, bytes[1]=0x00 -> cell 0
        assertEquals(Pair(0, 4.0), Parsers.cellVoltage(hex("c800"), 4134))
    }

    @Test
    fun `cellVoltage old encoding parses non-zero cell index`() {
        // FW 4134: bytes[0]=202 -> 4.04V, bytes[1]=0x07 -> cell 7
        assertEquals(Pair(7, 4.04), Parsers.cellVoltage(hex("ca07"), 4134))
    }

    @Test
    fun `cellVoltage new encoding extracts top 4 bits as cell index`() {
        // FW 4141: uint16=0x3E34, top nibble=3 -> cell 3, raw=0xE34=3636, 3636*0.0011=3.9996V
        assertEquals(Pair(3, 3.9996), Parsers.cellVoltage(hex("3e34"), 4141))
    }

    @Test
    fun `cellVoltage new encoding cell 0`() {
        // FW 4141: uint16=0x0E34, top nibble=0 -> cell 0, raw=3636, 3.9996V
        assertEquals(Pair(0, 3.9996), Parsers.cellVoltage(hex("0e34"), 4141))
    }

    @Test
    fun `pitch at level returns 0 degrees`() {
        // value=1800 (0x0708): 0.1*(1800-1800)=0.0
        assertEquals(0.0, Parsers.pitch(hex("0708")))
    }

    @Test
    fun `pitch forward lean returns positive degrees`() {
        // value=1710 (0x06AE): 0.1*(1800-1710)=9.0
        assertEquals(9.0, Parsers.pitch(hex("06ae")))
    }

    @Test
    fun `pitch backward lean returns negative degrees`() {
        // value=1890 (0x0762): 0.1*(1800-1890)=-9.0
        assertEquals(-9.0, Parsers.pitch(hex("0762")))
    }

    @Test
    fun `temperatures parse raw two byte M1 sample`() {
        // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
        // e659f310 temperature notification rawValueHex 1918 from the M1 XR 4209 success capture.
        // Scale and byte order are unverified until a warmer-board capture.
        assertEquals(Pair(25, 24), Parsers.temperatures(hex("1918")))
    }

    @Test
    fun `batteryTemperature XR reads first byte`() {
        // XR: data[0]=0x1E=30 -> 30 C
        assertEquals(30, Parsers.batteryTemperature(hex("1e00"), BoardType.XR))
    }

    @Test
    fun `batteryTemperature V1 reads second byte`() {
        // V1: data[1]=0x1E=30 -> 30 C
        assertEquals(30, Parsers.batteryTemperature(hex("001e"), BoardType.ONEWHEEL_V1))
    }

    @Test
    fun `safetyHeadroom parses raw uint16`() {
        // 0x0064 = 100 -> full pushback headroom
        assertEquals(100, Parsers.safetyHeadroom(hex("0064")))
    }

    @Test
    fun `statusError parses raw uint16`() {
        // 0x0000 = no error
        assertEquals(0, Parsers.statusError(hex("0000")))
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
    fun `ride mode 0 maps to UNKNOWN for XR`() {
        // Source: xr4209-success-handshake-telemetry.jsonl rawValueHex 0000.
        // 0 = no active mode / board not riding. Corrects prior wrong CUSTOM mapping.
        assertEquals(RideMode.UNKNOWN, Parsers.rideMode(hex("0000"), BoardType.XR))
    }

    @Test
    fun `ride mode 9 maps to CUSTOM for XR`() {
        assertEquals(RideMode.CUSTOM, Parsers.rideMode(hex("0009"), BoardType.XR))
    }

    @Test
    fun `ride mode 8 maps to DELIRIUM for XR`() {
        assertEquals(RideMode.DELIRIUM, Parsers.rideMode(hex("0008"), BoardType.XR))
    }

    @Test
    fun `ride mode 6 maps to MISSION for XR`() {
        assertEquals(RideMode.MISSION, Parsers.rideMode(hex("0006"), BoardType.XR))
    }

    @Test
    fun `ride mode 5 maps to CRUZ for XR`() {
        assertEquals(RideMode.CRUZ, Parsers.rideMode(hex("0005"), BoardType.XR))
    }

    @Test
    fun `ride mode 4 maps to SEQUOIA for XR`() {
        assertEquals(RideMode.SEQUOIA, Parsers.rideMode(hex("0004"), BoardType.XR))
    }

    @Test
    fun `ride mode 1 maps to CLASSIC for V1`() {
        assertEquals(RideMode.CLASSIC, Parsers.rideMode(hex("0001"), BoardType.ONEWHEEL_V1))
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

    @Test
    fun `serialNumber parses uint16 big endian golden 18694`() {
        // 18694 = 0x4906
        assertEquals("18694", Parsers.serialNumber(hex("4906")))
    }

    @Test
    fun `batterySerialNumber parses uint16 big endian golden 22136`() {
        // 22136 = 0x5678
        assertEquals("22136", Parsers.batterySerialNumber(hex("5678")))
    }

    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0) { "Hex string must contain complete bytes." }
        return value.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
