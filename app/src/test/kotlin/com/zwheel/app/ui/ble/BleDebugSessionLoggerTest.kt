package com.zwheel.app.ui.ble

import com.zwheel.core.ports.GattIo
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.debug.BleDebugRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BleDebugSessionLoggerTest {
    @Test
    fun `dump jobs call appendLog for notifications`() = runBlocking {
        val io = FakeGattIo()
        val recorder = BleDebugRecorder()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val logs = mutableListOf<String>()
        val jobs = BleDebugSessionLogger(
            io = io,
            recorder = recorder,
            selectedDeviceId = { "device-1" },
        ).startDumpJobs(scope, appendLog = { logs.add(it) })

        repeat(25) { index ->
            io.emit(OwUuids.RPM, byteArrayOf(0x00, index.toByte()))
        }
        yield()

        assertEquals(25, logs.size)

        jobs.forEach { job -> job.cancelAndJoin() }
    }

    @Test
    fun `compact two byte display uses parser byte order`() {
        assertEquals("29/00:1d", byteArrayOf(0x00, 0x1d).toCompactDisplay())
    }
}

private class FakeGattIo : GattIo {
    private val flows = mutableMapOf<GattCharacteristicId, MutableSharedFlow<ByteArray>>()

    suspend fun emit(characteristicId: GattCharacteristicId, value: ByteArray) {
        flowFor(characteristicId).emit(value)
    }

    override suspend fun read(characteristicId: GattCharacteristicId): ByteArray = ByteArray(0)

    override suspend fun write(characteristicId: GattCharacteristicId, value: ByteArray) = Unit

    override fun notifications(characteristicId: GattCharacteristicId): Flow<ByteArray> =
        flowFor(characteristicId)

    private fun flowFor(characteristicId: GattCharacteristicId): MutableSharedFlow<ByteArray> =
        flows.getOrPut(characteristicId) { MutableSharedFlow(extraBufferCapacity = 64) }
}
