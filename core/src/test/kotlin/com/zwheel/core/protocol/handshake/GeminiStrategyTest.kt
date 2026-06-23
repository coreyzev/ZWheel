package com.zwheel.core.protocol.handshake

import com.zwheel.core.ports.GattIo
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.KeepAliveAction
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.debug.BleDebugRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
        val recorder = BleDebugRecorder(salt = "test-salt")
        val io = FakeGattIo(
            reads = mapOf(OwUuids.FIRMWARE_REVISION to firmwareRevisionBytes),
            notifications = mapOf(OwUuids.UART_READ to listOf(challenge)),
        )

        val result = GeminiStrategy(
            debugRecorder = recorder,
            debugDeviceId = { "AA:BB:CC:DD:EE:FF" },
        ).unlock(io)

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

        val debugEvents = recorder.snapshot()
        assertEquals(
            listOf(
                "gemini_raw_notification:fragment:4352587f9e5c14df42e26282626262626277f69c",
                "gemini_challenge_assembled:ok:4352587f9e5c14df42e26282626262626277f69c",
                "gemini_trigger_write:before:1071",
                "gemini_trigger_write:after:1071",
            ),
            debugEvents.map { event -> "${event.type}:${event.status}:${event.rawValueHex}" },
        )
    }

    @Test
    fun `unlock discards stray bytes and assembles fragmented M1 challenge before writing response`() = runBlocking {
        val legacyAck = "01:02:03".strategyHexBytes()
        val fragments = listOf(
            "43",
            "52:58",
            "ff:6e:9d:ff",
            "da:3f",
            "e7:e7",
            "57",
            "17:17",
            "17:17",
            "17:83",
            "82:1e",
        ).map { it.strategyHexBytes() }
        val recorder = BleDebugRecorder(salt = "test-salt")
        val io = FakeGattIo(
            reads = mapOf(OwUuids.FIRMWARE_REVISION to firmwareRevisionBytes),
            notifications = mapOf(OwUuids.UART_READ to listOf(legacyAck) + fragments),
        )

        val result = GeminiStrategy(
            debugRecorder = recorder,
            debugDeviceId = { "AA:BB:CC:DD:EE:FF" },
        ).unlock(io)

        assertEquals(true, result.unlocked)
        assertEquals(
            "43:52:58:1c:ea:39:a9:50:3d:1a:9c:93:f6:40:ec:81:e3:9d:d9:2b",
            io.writesSeen.last().value.toHexString(),
        )

        val debugEvents = recorder.snapshot()
        assertEquals(
            listOf(
                "010203",
                "43",
                "5258",
                "ff6e9dff",
                "da3f",
                "e7e7",
                "57",
                "1717",
                "1717",
                "1783",
                "821e",
            ),
            debugEvents
                .filter { event -> event.type == "gemini_raw_notification" }
                .map { event -> event.rawValueHex },
        )
        assertEquals(
            "435258ff6e9dffda3fe7e757171717171783821e",
            debugEvents.single { event -> event.type == "gemini_challenge_assembled" }.rawValueHex,
        )
    }

    @Test
    fun `keepAlive is empty before unlock succeeds`() = runTest {
        val actions = GeminiStrategy().keepAlive().toList()

        assertEquals(emptyList<KeepAliveAction>(), actions)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `keepAlive emits immediate and periodic firmware revision write actions after unlock`() = runTest {
        val firmwareRevision = byteArrayOf(0x10, 0x71)
        val challenge = "43:52:58:7f:9e:5c:14:df:42:e2:62:82:62:62:62:62:62:77:f6:9c".strategyHexBytes()
        val io = FakeGattIo(
            reads = mapOf(OwUuids.FIRMWARE_REVISION to firmwareRevision),
            notifications = mapOf(OwUuids.UART_READ to listOf(challenge)),
        )
        val strategy = GeminiStrategy()

        strategy.unlock(io)
        firmwareRevision[0] = 0x00

        val actions = mutableListOf<KeepAliveAction>()
        val job = launch {
            strategy.keepAlive().take(2).toList(actions)
        }

        runCurrent()
        assertEquals(1, actions.size)
        assertFirmwareKeepAlive("10:71", actions.single())

        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(1, actions.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, actions.size)
        actions.forEach { action -> assertFirmwareKeepAlive("10:71", action) }
        job.cancel()
    }
}

private class FakeGattIo(
    private val reads: Map<GattCharacteristicId, ByteArray> = emptyMap(),
    private val notifications: Map<GattCharacteristicId, List<ByteArray>> = emptyMap(),
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
        val values = checkNotNull(notifications[characteristicId]) {
            "No fake notification for $characteristicId"
        }
        return flow {
            // The fake emits the first value immediately so tests can cover pre-trigger stray
            // UART_READ traffic. Real hardware normally emits the challenge after the trigger.
            emit(values.first())
            while (writesSeen.none { it.characteristicId == OwUuids.FIRMWARE_REVISION }) {
                delay(1)
            }
            values.drop(1).forEach { value -> emit(value) }
        }
    }
}

private data class WriteRecord(
    val characteristicId: GattCharacteristicId,
    val value: ByteArray,
)

private fun assertFirmwareKeepAlive(expectedHex: String, action: KeepAliveAction) {
    val write = action as KeepAliveAction.Write
    assertEquals(OwUuids.FIRMWARE_REVISION, write.characteristicId)
    assertEquals(expectedHex, write.value.toHexString())
}

private fun String.strategyHexBytes(): ByteArray =
    split(":")
        .map { it.toInt(radix = 16).toByte() }
        .toByteArray()

private fun ByteArray.toHexString(): String =
    joinToString(":") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }
