package com.zwheel.app.ui.ble

import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids

internal val dumpCharacteristics = listOf(
    OwUuids.BATTERY_PERCENT,
    OwUuids.RPM,
    OwUuids.PACK_VOLTAGE,
    OwUuids.AMPS,
    OwUuids.TEMPERATURE,
    OwUuids.RIDE_MODE,
)

internal val telemetryProbeCharacteristics = listOf(
    ProbeCharacteristic("bat", OwUuids.BATTERY_PERCENT),
    ProbeCharacteristic("rpm", OwUuids.RPM),
    ProbeCharacteristic("v", OwUuids.PACK_VOLTAGE),
    ProbeCharacteristic("a", OwUuids.AMPS),
    ProbeCharacteristic("tmp", OwUuids.TEMPERATURE),
    ProbeCharacteristic("mode", OwUuids.RIDE_MODE),
)

internal data class ProbeCharacteristic(
    val name: String,
    val characteristicId: GattCharacteristicId,
)

internal fun ScanResult.label(): String =
    listOfNotNull(displayName, deviceId, rssi?.let { "$it dBm" }).joinToString("  ")

internal fun GattCharacteristicId.shortName(): String =
    uuid.toString().substring(startIndex = 4, endIndex = 8)

internal fun ByteArray.toHexString(): String =
    joinToString(":") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }

internal fun ByteArray.toCompactDisplay(): String =
    if (size == 2) {
        val value = (get(0).toInt().and(0xff) shl 8) or get(1).toInt().and(0xff)
        "$value/${toHexString()}"
    } else {
        toHexString()
    }

internal fun String?.displayValue(): String = this ?: "--"

