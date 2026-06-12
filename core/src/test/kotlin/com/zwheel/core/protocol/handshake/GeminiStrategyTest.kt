package com.zwheel.core.protocol.handshake

import com.zwheel.core.ports.GattIo
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeminiStrategyTest {
    @Test
    fun `unlock reads uart challenge and writes only uart response`() = runBlocking {
        val challenge = "43:52:58:7f:9e:5c:14:df:42:e2:62:82:62:62:62:62:62:77:f6:9c".strategyHexBytes()
        val io = FakeGattIo(reads = mapOf(OwUuids.UART_READ to challenge))

        val result = GeminiStrategy().unlock(io)

        assertEquals(true, result.unlocked)
        assertEquals(listOf(OwUuids.UART_READ), io.readsSeen)
        assertEquals(listOf(OwUuids.UART_WRITE), io.writesSeen.map { it.characteristicId })
        assertEquals(
            "43:52:58:d8:82:11:d1:26:96:5f:9f:aa:72:fc:de:92:f3:25:3d:20",
            io.writesSeen.single().value.toHexString(),
        )
    }
}

private class FakeGattIo(
    private val reads: Map<GattCharacteristicId, ByteArray>,
) : GattIo {
    val readsSeen = mutableListOf<GattCharacteristicId>()
    val writesSeen = mutableListOf<WriteRecord>()

    override suspend fun read(characteristicId: GattCharacteristicId): ByteArray {
        readsSeen += characteristicId
        return checkNotNull(reads[characteristicId]) { "No fake read for $characteristicId" }
    }

    override suspend fun write(characteristicId: GattCharacteristicId, value: ByteArray) {
        writesSeen += WriteRecord(characteristicId, value)
    }

    override fun notifications(characteristicId: GattCharacteristicId): Flow<ByteArray> = emptyFlow()
}

private data class WriteRecord(
    val characteristicId: GattCharacteristicId,
    val value: ByteArray,
)

private fun String.strategyHexBytes(): ByteArray =
    split(":")
        .map { it.toInt(radix = 16).toByte() }
        .toByteArray()

private fun ByteArray.toHexString(): String =
    joinToString(":") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }
