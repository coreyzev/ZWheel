package com.zwheel.app.ui.ble

import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids

internal val dumpCharacteristics = listOf(
    OwUuids.BATTERY_PERCENT,
    OwUuids.BATTERY_TEMPERATURE,
    OwUuids.RPM,
    OwUuids.PACK_VOLTAGE,
    OwUuids.CELL_VOLTAGES,
    OwUuids.AMPS,
    OwUuids.TRIP_TOTAL_AMP_HOURS,
    OwUuids.TRIP_REGEN_AMP_HOURS,
    OwUuids.TEMPERATURE,
    OwUuids.SAFETY_HEADROOM,
    OwUuids.STATUS_ERROR,
    OwUuids.LAST_ERROR_CODE,  // investigation: emits constant notifications, not clear it's error codes
    OwUuids.PITCH,
    OwUuids.ROLL,
    OwUuids.YAW,
    OwUuids.ODOMETER,
    OwUuids.RIDE_MODE,
)

internal val telemetryProbeCharacteristics = listOf(
    ProbeCharacteristic("bat", OwUuids.BATTERY_PERCENT),
    ProbeCharacteristic("bat_tmp", OwUuids.BATTERY_TEMPERATURE),
    ProbeCharacteristic("rpm", OwUuids.RPM),
    ProbeCharacteristic("v", OwUuids.PACK_VOLTAGE),
    ProbeCharacteristic("a", OwUuids.AMPS),
    ProbeCharacteristic("ah", OwUuids.TRIP_TOTAL_AMP_HOURS),
    ProbeCharacteristic("regen", OwUuids.TRIP_REGEN_AMP_HOURS),
    ProbeCharacteristic("tmp", OwUuids.TEMPERATURE),
    ProbeCharacteristic("safe", OwUuids.SAFETY_HEADROOM),
    ProbeCharacteristic("err", OwUuids.STATUS_ERROR),
    ProbeCharacteristic("last_err", OwUuids.LAST_ERROR_CODE),
    ProbeCharacteristic("pitch", OwUuids.PITCH),
    ProbeCharacteristic("odo", OwUuids.ODOMETER),
    ProbeCharacteristic("lt_odo", OwUuids.LIFETIME_ODOMETER),
    ProbeCharacteristic("lt_ah", OwUuids.LIFETIME_AMP_HOURS),
    ProbeCharacteristic("lights", OwUuids.LIGHTS),
    ProbeCharacteristic("lights_f", OwUuids.LIGHTS_FRONT),
    ProbeCharacteristic("lights_b", OwUuids.LIGHTS_BACK),
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

