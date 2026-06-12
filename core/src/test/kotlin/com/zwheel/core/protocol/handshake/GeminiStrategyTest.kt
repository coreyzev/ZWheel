package com.zwheel.core.protocol.handshake

import com.zwheel.core.ports.GattIo
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeminiStrategyTest {
    // 0x1071 = 4209 — Corey's XR board hardware revision from the M1 capture fixture.
    private val firmwareRevisionBytes = byteArrayOf(0x10, 0x71)

    @Test
    fun `unlock reads firmware revision writes it back as trigger then writes calculated response`() = runBlocking {
        // Gemini flow per OWCE OWBoard.cs L861-876:
        //   1. Subscribe to UART_READ notifications.
        //   2. Read FIRMWARE_REVISION and write it back unchanged — this write event tells the
        //      board to emit its challenge. The board ignores the written value.
        //   3. Receive the CRX challenge on UART_READ.
        //   4. Compute MD5 response and write to UART_WRITE.
        val challenge = "43:52:58:7f:9e:5c:14:df:42:e2:62:82:62:62:62:62:62:77:f6:9c".strategyHexBytes()
        val io = FakeGattIo(
            reads = mapOf(OwUuids.FIRMWARE_REVISION to firmwareRevisionBytes),
            notifications = mapOf(OwUuids.UART_READ to challenge),
        )

        val result = GeminiStrategy().unlock(io)

        assertEquals(true, result.unlocked)
        assertEquals(listOf(OwUuids.UART_READ), io.notificationsSeen)
        assertEquals(
            listOf(OwUuids.FIRMWARE_REVISION, OwUuids.UART_WRITE),
            io.writesSeen.map { it.characteristicId },
        )
        // Firmware trigger: same bytes as read — value is irrelevant to the board, but we must
        // write back exactly what we read.
        assertEquals(
            firmwareRevisionBytes.toHexString(),
            io.writesSeen[0].value.toHexString(),
        )
        // MD5 challenge response.
        assertEquals(
            "43:52:58:d8:82:11:d1:26:96:5f:9f:aa:72:fc:de:92:f3:25:3d:20",
            io.writesSeen[1].value.toHexString(),
        )
    }
}

private class FakeGattIo(
    private val reads: Map<GattCharacteristicId, ByteArray> = emptyMap(),
    private val notifications: Map<GattCharacteristicId, ByteArray> = emptyMap(),
) : GattIo {
    val notificationsSeen = mutableListOf<GattCharacteristicId>()
    val writesSeen = mutableListOf<WriteRecord>()

    override suspend fun read(characteristicId: GattCharacteristicId): ByteArray =
        checkNotNull(reads[characteristicId]) { "No fake read value for $characteristicId" }

    override suspend fun write(characteristicId: GattCharacteristicId, value: ByteArray) {
        writesSeen += WriteRecord(characteristicId, value)
    }

    override fun notifications(characteristicId: GattCharacteristicId): Flow<ByteArray> {
        notificationsSeen += characteristicId
        return flowOf(checkNotNull(notifications[characteristicId]) { "No fake notification for $characteristicId" })
    }
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
