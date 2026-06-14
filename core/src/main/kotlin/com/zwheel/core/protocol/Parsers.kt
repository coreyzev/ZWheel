package com.zwheel.core.protocol

import com.zwheel.core.model.BoardType
import com.zwheel.core.model.RideMode

object Parsers {
    fun rpm(value: ByteArray): Int = value.uint16BigEndian()

    fun amps(value: ByteArray, boardType: BoardType): Double {
        val scale = when (boardType) {
            BoardType.ONEWHEEL_V1 -> 0.0009
            BoardType.PLUS -> 0.0018
            BoardType.XR,
            BoardType.PINT,
            BoardType.PINT_X -> 0.002
            BoardType.UNKNOWN -> throw IllegalArgumentException(
                "Cannot parse amps for unknown board type.",
            )
        }
        return value.int16BigEndian() * scale
    }

    fun packVoltage(value: ByteArray): Double = value.uint16BigEndian() / 10.0

    fun pitch(value: ByteArray): Double = 0.1 * (1800 - value.uint16BigEndian())

    fun roll(value: ByteArray): Double = 0.1 * (1800 - value.uint16BigEndian())

    fun yaw(value: ByteArray): Double = 0.1 * (1800 - value.uint16BigEndian())

    // TODO(m3): implement e659f31b cell voltage parsing after a dedicated capture session.

    // M1 captured raw two-byte temperature values. Scale and byte order need warmer-board verification.
    fun temperatures(value: ByteArray): Pair<Int, Int> {
        val bytes = value.requireSize(2)
        return Pair(bytes[0].toInt() and 0xff, bytes[1].toInt() and 0xff)
    }

    fun batteryPercent(value: ByteArray): Int = value.uint16BigEndian().coerceIn(0, 100)

    fun rideMode(value: ByteArray): RideMode = when (value.uint16BigEndian()) {
        0 -> RideMode.CUSTOM
        else -> RideMode.UNKNOWN
    }

    fun unsignedInt16(value: ByteArray): Int = value.uint16BigEndian()

    fun hardwareRevision(value: ByteArray): Int = value.uint16BigEndian()

    fun firmwareRevision(value: ByteArray): Int = value.uint16BigEndian()

    private fun ByteArray.uint16BigEndian(): Int {
        val bytes = requireSize(2)
        return ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)
    }

    private fun ByteArray.int16BigEndian(): Int = uint16BigEndian().toShort().toInt()

    private fun ByteArray.requireSize(expected: Int): ByteArray {
        require(size == expected) { "Expected $expected byte(s), got $size." }
        return this
    }
}
