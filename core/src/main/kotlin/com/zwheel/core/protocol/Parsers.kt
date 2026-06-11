package com.zwheel.core.protocol

object Parsers {
    fun unsignedByte(value: ByteArray): Int = value.requireSize(1)[0].toInt() and 0xff

    fun signedInt16BigEndian(value: ByteArray): Int {
        val bytes = value.requireSize(2)
        return ((bytes[0].toInt() shl 8) or (bytes[1].toInt() and 0xff)).toShort().toInt()
    }

    fun batteryPercent(value: ByteArray): Int = unsignedByte(value).coerceIn(0, 100)

    fun voltageTenths(value: ByteArray): Double = unsignedByte(value) / 10.0

    fun cellVoltagesMillivoltsBigEndian(value: ByteArray): List<Double> {
        require(value.size % 2 == 0) { "Cell voltage payload must contain 16-bit values." }
        return value.asIterable()
            .chunked(2)
            .map { pair ->
                val millivolts = ((pair[0].toInt() and 0xff) shl 8) or (pair[1].toInt() and 0xff)
                millivolts / 1000.0
            }
    }

    private fun ByteArray.requireSize(expected: Int): ByteArray {
        require(size == expected) { "Expected $expected byte(s), got $size." }
        return this
    }
}
