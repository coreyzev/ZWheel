package com.zwheel.core.protocol

import com.zwheel.core.model.RideMode

object Parsers {
    fun rpm(value: ByteArray): Int = value.uint16BigEndian()

    fun amps(value: ByteArray): Int = value.uint16BigEndian()

    fun packVoltage(value: ByteArray): Double = value.uint16BigEndian() / 10.0

    // M1 captured raw two-byte temperature values. Scale and byte order need warmer-board verification.
    fun temperatures(value: ByteArray): Pair<Int, Int> {
        val bytes = value.requireSize(2)
        return Pair(bytes[0].toInt() and 0xff, bytes[1].toInt() and 0xff)
    }

    fun batteryPercent(value: ByteArray): Int = value.uint16BigEndian()

    fun rideMode(value: ByteArray): RideMode = when (value.uint16BigEndian()) {
        0 -> RideMode.CUSTOM
        else -> RideMode.UNKNOWN
    }

    fun hardwareRevision(value: ByteArray): Int = value.uint16BigEndian()

    fun firmwareRevision(value: ByteArray): Int = value.uint16BigEndian()

    private fun ByteArray.uint16BigEndian(): Int {
        val bytes = requireSize(2)
        return ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)
    }

    private fun ByteArray.requireSize(expected: Int): ByteArray {
        require(size == expected) { "Expected $expected byte(s), got $size." }
        return this
    }
}
