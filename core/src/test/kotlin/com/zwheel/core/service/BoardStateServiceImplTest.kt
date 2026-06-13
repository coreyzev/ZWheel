package com.zwheel.core.service

import app.cash.turbine.test
import com.zwheel.core.model.BoardState
import com.zwheel.core.ports.BleTransport
import com.zwheel.core.ports.Clock
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

private class FakeBleTransport : BleTransport {
    private val flows = mutableMapOf<GattCharacteristicId, MutableSharedFlow<ByteArray>>()
    fun flowFor(id: GattCharacteristicId) =
        flows.getOrPut(id) { MutableSharedFlow(extraBufferCapacity = 64) }
    suspend fun emit(id: GattCharacteristicId, bytes: ByteArray) = flowFor(id).emit(bytes)
    override fun notifications(characteristicId: GattCharacteristicId): Flow<ByteArray> =
        flowFor(characteristicId)
    override suspend fun scan(): Flow<ScanResult> = emptyFlow()
    override suspend fun connect(deviceId: String) {}
    override suspend fun disconnect() {}
    override suspend fun read(characteristicId: GattCharacteristicId): ByteArray = ByteArray(0)
    override suspend fun write(characteristicId: GattCharacteristicId, value: ByteArray) {}
}

private class FakeClock(var currentMillis: Long = 0L) : Clock {
    override fun nowEpochMillis() = currentMillis
}

@OptIn(ExperimentalCoroutinesApi::class)
class BoardStateServiceImplTest {
    @Test
    fun `uses RpmBased when RPM arrives`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBleTransport()
        val clock = FakeClock()
        val service = BoardStateServiceImpl(
            transport = transport,
            clock = clock,
            diameterInches = 11.5,
            stockDiameterInches = 11.5,
        )

        service.start(backgroundScope)

        service.state.test {
            transport.emit(OwUuids.RPM, byteArrayOf(0x00, 0x1D))

            val state = awaitState { it.speedMetersPerSecondCorrected != null }
            assertEquals(0.444, state.speedMetersPerSecondCorrected!!, 0.01)
        }
    }

    @Test
    fun `batteryPercent updates on notification`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBleTransport()
        val clock = FakeClock()
        val service = BoardStateServiceImpl(
            transport = transport,
            clock = clock,
            diameterInches = 11.5,
            stockDiameterInches = 11.5,
        )

        service.start(backgroundScope)

        service.state.test {
            transport.emit(OwUuids.BATTERY_PERCENT, byteArrayOf(0x00, 0x60))

            val state = awaitState { it.batteryPercent == 96 }
            assertEquals(96, state.batteryPercent)
        }
    }

    @Test
    fun `malformed payload is skipped, no crash`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBleTransport()
        val clock = FakeClock()
        val service = BoardStateServiceImpl(
            transport = transport,
            clock = clock,
            diameterInches = 11.5,
            stockDiameterInches = 11.5,
        )

        service.start(backgroundScope)
        transport.emit(OwUuids.BATTERY_PERCENT, byteArrayOf(0x42))
        runCurrent()

        assertNull(service.state.value.batteryPercent)
    }

    @Test
    fun `odometer delta produces non-null raw speed`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeBleTransport()
        val clock = FakeClock()
        val service = BoardStateServiceImpl(
            transport = transport,
            clock = clock,
            diameterInches = 11.5,
            stockDiameterInches = 11.5,
        )

        service.start(backgroundScope)

        service.state.test {
            clock.currentMillis = 0L
            transport.emit(OwUuids.ODOMETER, byteArrayOf(0x00, 0x00))
            clock.currentMillis = 1_000L
            transport.emit(OwUuids.ODOMETER, byteArrayOf(0x00, 0x01))

            val state = awaitState { it.speedMetersPerSecondRaw != null }
            assertNotNull(state.speedMetersPerSecondRaw)
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<BoardState>.awaitState(
        predicate: (BoardState) -> Boolean,
    ): BoardState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
