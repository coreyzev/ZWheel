package com.zwheel.core.ports

import com.zwheel.core.model.RideDataPoint
import com.zwheel.core.model.RideSession
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.HandshakeResult
import com.zwheel.core.protocol.KeepAliveAction
import kotlinx.coroutines.flow.Flow

interface BleTransport {
    suspend fun scan(): Flow<ScanResult>
    suspend fun connect(deviceId: String)
    suspend fun disconnect()
    suspend fun read(characteristicId: GattCharacteristicId): ByteArray
    suspend fun write(characteristicId: GattCharacteristicId, value: ByteArray)
    fun notifications(characteristicId: GattCharacteristicId): Flow<ByteArray>
}

interface GattIo {
    suspend fun read(characteristicId: GattCharacteristicId): ByteArray
    suspend fun write(characteristicId: GattCharacteristicId, value: ByteArray)
    fun notifications(characteristicId: GattCharacteristicId): Flow<ByteArray>
}

interface HandshakeStrategy {
    suspend fun unlock(io: GattIo): HandshakeResult
    fun keepAlive(): Flow<KeepAliveAction>
}

interface Clock {
    fun nowEpochMillis(): Long
}

interface RideStorage {
    suspend fun startSession(session: RideSession)
    suspend fun appendPoint(point: RideDataPoint)
    suspend fun finishSession(sessionId: String, endEpochMillis: Long)
    suspend fun openSession(): RideSession?
}

data class ScanResult(
    val deviceId: String,
    val displayName: String?,
    val rssi: Int?,
)
