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

    fun cellVoltage(value: ByteArray, firmwareMajor: Int): Pair<Int, Double> {
        return if (firmwareMajor >= 4141) {
            val raw = value.uint16BigEndian()
            val cellIndex = (raw shr 12) and 0xF
            val voltage = (raw and 0xFFF) * 0.0011
            Pair(cellIndex, voltage)
        } else {
            val bytes = value.requireSize(2)
            val cellIndex = bytes[1].toInt() and 0xFF
            val voltage = (bytes[0].toInt() and 0xFF) * 0.02
            Pair(cellIndex, voltage)
        }
    }

    fun pitch(value: ByteArray): Double = 0.1 * (1800 - value.uint16BigEndian())

    fun roll(value: ByteArray): Double = 0.1 * (1800 - value.uint16BigEndian())

    fun yaw(value: ByteArray): Double = 0.1 * (1800 - value.uint16BigEndian())

    // M1 captured raw two-byte temperature values. Scale and byte order need warmer-board verification.
    fun temperatures(value: ByteArray): Pair<Int, Int> {
        val bytes = value.requireSize(2)
        return Pair(bytes[0].toInt() and 0xff, bytes[1].toInt() and 0xff)
    }

    fun batteryPercent(value: ByteArray): Int = value.uint16BigEndian().coerceIn(0, 100)

    fun rideMode(value: ByteArray, boardType: BoardType): RideMode {
        return when (boardType) {
            BoardType.ONEWHEEL_V1 -> when (value.uint16BigEndian()) {
                1 -> RideMode.CLASSIC
                2 -> RideMode.EXTREME
                3 -> RideMode.ELEVATED
                else -> RideMode.UNKNOWN
            }
            BoardType.PLUS, BoardType.XR -> when (value.uint16BigEndian()) {
                4 -> RideMode.SEQUOIA
                5 -> RideMode.CRUZ
                6 -> RideMode.MISSION
                7 -> RideMode.ELEVATED
                8 -> RideMode.DELIRIUM
                9 -> RideMode.CUSTOM
                else -> RideMode.UNKNOWN
            }
            BoardType.PINT, BoardType.PINT_X -> when (value.uint16BigEndian()) {
                5 -> RideMode.SEQUOIA // Redwood on Pint - closest enum
                6 -> RideMode.MISSION // Pacific - closest enum
                7 -> RideMode.ELEVATED
                8 -> RideMode.CUSTOM // Skyline - closest enum
                else -> RideMode.UNKNOWN
            }
            BoardType.UNKNOWN -> RideMode.UNKNOWN
        }
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
