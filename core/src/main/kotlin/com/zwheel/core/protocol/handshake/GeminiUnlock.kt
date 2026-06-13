package com.zwheel.core.protocol.handshake

import com.zwheel.core.ports.GattIo
import com.zwheel.core.ports.HandshakeStrategy
import com.zwheel.core.protocol.HandshakeResult
import com.zwheel.core.protocol.KeepAliveAction
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.debug.BleDebugRecorder
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeout

private const val CHALLENGE_TIMEOUT_MS = 5_000L
private const val CHALLENGE_BYTES = 20

class NoneStrategy : HandshakeStrategy {
    override suspend fun unlock(io: GattIo): HandshakeResult =
        HandshakeResult(
            unlocked = true,
            strategyName = "none",
            message = "No board unlock required",
        )

    override fun keepAlive(): Flow<KeepAliveAction> = emptyFlow()
}

class GeminiStrategy(
    private val debugRecorder: BleDebugRecorder? = null,
    private val debugDeviceId: () -> String? = { null },
) : HandshakeStrategy {
    override suspend fun unlock(io: GattIo): HandshakeResult = coroutineScope {
        // Launch collection before the trigger write. Kable's observe() is a cold flow —
        // the GATT CCCD write (enabling notifications) only happens when the flow is collected,
        // not when notifications() is called. Without this, the board can emit the one-shot
        // CRX challenge between the trigger write and .first() starting collection, and we
        // time out. Evidence: OWCE OWBoard.cs L861-876.
        val subscriptionReady = CompletableDeferred<Unit>()
        val challengeAsync = async {
            var buffer = ByteArray(0)
            io.notifications(OwUuids.UART_READ)
                .onStart { subscriptionReady.complete(Unit) }
                .onEach(::recordRawNotification)
                .mapNotNull { value ->
                    buffer += value
                    assembledChallengeOrNull(buffer)?.also(::recordChallengeAssembled)
                }
                .first()
        }

        val challenge = withTimeout(CHALLENGE_TIMEOUT_MS) {
            subscriptionReady.await()

            // Read firmware revision and write it back unchanged; the board uses this write event
            // as a trigger to emit the Gemini challenge on UART_READ. The value itself is ignored.
            val firmwareRevision = io.read(OwUuids.FIRMWARE_REVISION)
            recordTriggerWrite(status = "before", firmwareRevision = firmwareRevision)
            io.write(OwUuids.FIRMWARE_REVISION, firmwareRevision)
            recordTriggerWrite(status = "after", firmwareRevision = firmwareRevision)

            challengeAsync.await()
        }
        val response = GeminiChallengeResponse.calculate(challenge)
        io.write(OwUuids.UART_WRITE, response)
        HandshakeResult(
            unlocked = true,
            strategyName = "gemini",
            message = "Gemini challenge response sent",
        )
    }

    override fun keepAlive(): Flow<KeepAliveAction> = emptyFlow()

    private fun recordRawNotification(value: ByteArray) {
        debugRecorder?.record(
            type = "gemini_raw_notification",
            deviceId = debugDeviceId(),
            characteristicUuid = OwUuids.UART_READ.uuid.toString(),
            characteristicName = "uart_read",
            rawValueHex = value.toRawHexString(),
            status = "fragment",
        )
    }

    private fun assembledChallengeOrNull(buffer: ByteArray): ByteArray? =
        GeminiChallengeResponse.challengePrefixOnset(buffer)?.let { onset ->
            if (buffer.size - onset >= CHALLENGE_BYTES) {
                buffer.copyOfRange(fromIndex = onset, toIndex = onset + CHALLENGE_BYTES)
            } else {
                null
            }
        }

    private fun recordChallengeAssembled(challenge: ByteArray) {
        debugRecorder?.record(
            type = "gemini_challenge_assembled",
            deviceId = debugDeviceId(),
            characteristicUuid = OwUuids.UART_READ.uuid.toString(),
            characteristicName = "uart_read",
            rawValueHex = challenge.toRawHexString(),
            status = "ok",
        )
    }

    private fun recordTriggerWrite(status: String, firmwareRevision: ByteArray) {
        debugRecorder?.record(
            type = "gemini_trigger_write",
            deviceId = debugDeviceId(),
            characteristicUuid = OwUuids.FIRMWARE_REVISION.uuid.toString(),
            characteristicName = "firmware_revision",
            rawValueHex = firmwareRevision.toRawHexString(),
            status = status,
        )
    }
}

object GeminiChallengeResponse {
    const val PREFIX_SIZE = 3

    private val challengePrefix = byteArrayOf(0x43, 0x52, 0x58)

    private val challengeResponsePassword = byteArrayOf(
        0xD9.toByte(),
        0x25,
        0x5F,
        0x0F,
        0x23,
        0x35,
        0x4E,
        0x19,
        0xBA.toByte(),
        0x73,
        0x9C.toByte(),
        0xCD.toByte(),
        0xC4.toByte(),
        0xA9.toByte(),
        0x17,
        0x65,
    )

    fun hasChallengePrefix(value: ByteArray, offset: Int = 0): Boolean =
        offset >= 0 &&
            value.size - offset >= PREFIX_SIZE &&
            value[offset] == challengePrefix[0] &&
            value[offset + 1] == challengePrefix[1] &&
            value[offset + 2] == challengePrefix[2]

    fun challengePrefixOnset(value: ByteArray): Int? =
        (0..value.size - PREFIX_SIZE)
            .firstOrNull { offset -> hasChallengePrefix(value, offset) }

    fun calculate(challenge: ByteArray): ByteArray {
        require(challenge.size >= 19) { "Gemini challenge must contain at least 19 bytes" }
        require(hasChallengePrefix(challenge)) {
            "Gemini challenge must start with CRX prefix"
        }

        val md5Input = ByteArray(32)
        challenge.copyInto(destination = md5Input, startIndex = 3, endIndex = 19)
        challengeResponsePassword.copyInto(destination = md5Input, destinationOffset = 16)

        val digest = MessageDigest.getInstance("MD5").digest(md5Input)
        val responseWithoutCheck = challengePrefix + digest
        val checkByte = responseWithoutCheck.fold(0) { acc, byte ->
            acc xor byte.toInt().and(0xff)
        }.toByte()

        return responseWithoutCheck + checkByte
    }
}

private fun ByteArray.toRawHexString(): String =
    joinToString(separator = "") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }
