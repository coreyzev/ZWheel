package com.zwheel.core.protocol.handshake

import com.zwheel.core.ports.GattIo
import com.zwheel.core.ports.HandshakeStrategy
import com.zwheel.core.protocol.HandshakeResult
import com.zwheel.core.protocol.KeepAliveAction
import com.zwheel.core.protocol.OwUuids
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

private const val CHALLENGE_TIMEOUT_MS = 5_000L

class NoneStrategy : HandshakeStrategy {
    override suspend fun unlock(io: GattIo): HandshakeResult =
        HandshakeResult(
            unlocked = true,
            strategyName = "none",
            message = "No board unlock required",
        )

    override fun keepAlive(): Flow<KeepAliveAction> = emptyFlow()
}

class GeminiStrategy : HandshakeStrategy {
    override suspend fun unlock(io: GattIo): HandshakeResult {
        val challenge = withTimeout(CHALLENGE_TIMEOUT_MS) { io.notifications(OwUuids.UART_READ).first() }
        val response = GeminiChallengeResponse.calculate(challenge)
        io.write(OwUuids.UART_WRITE, response)
        return HandshakeResult(
            unlocked = true,
            strategyName = "gemini",
            message = "Gemini challenge response sent",
        )
    }

    override fun keepAlive(): Flow<KeepAliveAction> = emptyFlow()
}

object GeminiChallengeResponse {
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

    fun hasChallengePrefix(value: ByteArray): Boolean =
        value.size >= challengePrefix.size &&
            value[0] == challengePrefix[0] &&
            value[1] == challengePrefix[1] &&
            value[2] == challengePrefix[2]

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
