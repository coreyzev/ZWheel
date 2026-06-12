package com.zwheel.core.protocol.handshake

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GeminiChallengeResponseTest {
    @Test
    fun `calculates pOnewheel issue 86 scan 1 challenge response vector`() {
        /*
         * Gemini default challenge response, from pOnewheel issue #86 and the
         * COM8/UWP-Onewheel DefaultGeminiUnlock.cs reference:
         * 1. Require the 20-byte board challenge to begin with ASCII "CRX".
         * 2. Copy challenge bytes 3 through 18 inclusive. The twentieth byte from the
         *    issue capture is not included in the MD5 input.
         * 3. Append the 16-byte public Gemini password from pOnewheel issue #86.
         * 4. MD5 the resulting 32 bytes.
         * 5. Build response = "CRX" + 16-byte MD5 digest.
         * 6. Append one validation byte: XOR of the first 19 response bytes.
         *
         * This is the only complete public challenge/response pair found in the
         * cited sources that matches the UWP-Onewheel DefaultGeminiUnlock.cs
         * byte-level transform. pOnewheel issue #86 also includes a second
         * challenge capture next to a response, but that response does not match
         * the UWP transform for the adjacent challenge bytes, so it is not treated
         * as a known-good vector here.
         */
        val challenge = "43:52:58:7f:9e:5c:14:df:42:e2:62:82:62:62:62:62:62:77:f6:9c".hexBytes()
        val response = "43:52:58:d8:82:11:d1:26:96:5f:9f:aa:72:fc:de:92:f3:25:3d:20".hexBytes()

        assertArrayEquals(response, GeminiChallengeResponse.calculate(challenge))
    }

    @Test
    fun `calculates xor check byte after md5 digest`() {
        /*
         * UWP-Onewheel's DefaultGeminiUnlock.cs builds all Gemini responses as
         * "CRX" + MD5(challenge[3..18] + password) + check byte. The check byte is
         * not part of the MD5; it is the XOR fold of the first 19 response bytes.
         * This test uses the other public pOnewheel issue #86 challenge capture as
         * transform coverage without claiming its adjacent response text as a
         * verified known-good pair.
         */
        val response = GeminiChallengeResponse.calculate(
            "43:52:58:7f:8e:0c:4c:17:7a:22:a2:b2:32:e2:e2:e2:e2:f8:77:ca".hexBytes(),
        )

        val expectedCheckByte = response
            .dropLast(1)
            .fold(0) { acc, byte -> acc xor byte.toInt().and(0xff) }
            .toByte()
        assertEquals(expectedCheckByte, response.last())
    }

    @Test
    fun `rejects non gemini challenge prefix`() {
        /*
         * A Gemini challenge is framed with ASCII "CRX" before the 16 MD5 input
         * bytes. Anything else fails before hashing so the hardware path does not
         * send a guessed response to UART_WRITE.
         */
        val error = assertThrows(IllegalArgumentException::class.java) {
            GeminiChallengeResponse.calculate(
                "00:52:58:7f:9e:5c:14:df:42:e2:62:82:62:62:62:62:62:77:f6:9c".hexBytes(),
            )
        }

        assertEquals("Gemini challenge must start with CRX prefix", error.message)
    }
}

private fun String.hexBytes(): ByteArray =
    split(":")
        .map { it.toInt(radix = 16).toByte() }
        .toByteArray()
