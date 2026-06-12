package com.zwheel.core.protocol.debug

import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

data class BleDebugEvent(
    val schemaVersion: String,
    val sessionId: String,
    val offsetMs: Long,
    val type: String,
    val deviceHash: String? = null,
    val displayName: String? = null,
    val rssi: Int? = null,
    val characteristicUuid: String? = null,
    val characteristicName: String? = null,
    val rawValueHex: String? = null,
    val status: String? = null,
)

class BleDebugRecorder(
    private val salt: String = UUID.randomUUID().toString(),
    private val sessionId: String = UUID.randomUUID().toString(),
    private val startEpochMs: Long = System.currentTimeMillis(),
) {
    private val events = Collections.synchronizedList(mutableListOf<BleDebugEvent>())

    fun record(
        type: String,
        deviceId: String? = null,
        displayName: String? = null,
        rssi: Int? = null,
        characteristicUuid: String? = null,
        characteristicName: String? = null,
        rawValueHex: String? = null,
        status: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        events += BleDebugEvent(
            schemaVersion = SCHEMA_VERSION,
            sessionId = sessionId,
            offsetMs = nowEpochMs - startEpochMs,
            type = type,
            deviceHash = deviceId?.let(::hashDeviceId),
            displayName = displayName,
            rssi = rssi,
            characteristicUuid = characteristicUuid,
            characteristicName = characteristicName,
            rawValueHex = rawValueHex,
            status = status,
        )
    }

    fun toJsonLines(): String = synchronized(events) {
        events.joinToString(separator = "\n") { it.toJson() }
    }

    fun snapshot(): List<BleDebugEvent> = synchronized(events) {
        events.toList()
    }

    private fun hashDeviceId(deviceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$deviceId".encodeToByteArray())
        return digest.take(12).joinToString(separator = "") { byte ->
            byte.toUByte().toString(radix = 16).padStart(2, '0')
        }
    }

    private companion object {
        const val SCHEMA_VERSION = "m1-ble-debug-v1"
    }
}

fun BleDebugEvent.toJson(): String = buildString {
    append('{')
    appendJsonField("schemaVersion", schemaVersion)
    append(',')
    appendJsonField("sessionId", sessionId)
    append(',')
    appendJsonField("offsetMs", offsetMs)
    append(',')
    appendJsonField("type", type)
    appendNullableJsonField("deviceHash", deviceHash)
    appendNullableJsonField("displayName", displayName)
    appendNullableJsonField("rssi", rssi)
    appendNullableJsonField("characteristicUuid", characteristicUuid)
    appendNullableJsonField("characteristicName", characteristicName)
    appendNullableJsonField("rawValueHex", rawValueHex)
    appendNullableJsonField("status", status)
    append('}')
}

private fun StringBuilder.appendJsonField(name: String, value: String) {
    append('"').append(name).append("\":\"").append(value.jsonEscaped()).append('"')
}

private fun StringBuilder.appendJsonField(name: String, value: Long) {
    append('"').append(name).append("\":").append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Int) {
    append('"').append(name).append("\":").append(value)
}

private fun StringBuilder.appendNullableJsonField(name: String, value: String?) {
    value ?: return
    append(',')
    appendJsonField(name, value)
}

private fun StringBuilder.appendNullableJsonField(name: String, value: Int?) {
    value ?: return
    append(',')
    appendJsonField(name, value)
}

private fun String.jsonEscaped(): String = buildString {
    this@jsonEscaped.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
