package com.zwheel.app.ble

import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids

internal fun ScanResult.deviceKey(): String = deviceId.lowercase()

internal fun GattCharacteristicId.debugName(): String =
    when (this) {
        OwUuids.BATTERY_PERCENT -> "battery_percent"
        OwUuids.RPM -> "rpm"
        OwUuids.PACK_VOLTAGE -> "pack_voltage"
        OwUuids.AMPS -> "amps"
        OwUuids.TEMPERATURE -> "temperature"
        OwUuids.RIDE_MODE -> "ride_mode"
        OwUuids.HARDWARE_REVISION -> "hardware_revision"
        OwUuids.FIRMWARE_REVISION -> "firmware_revision"
        OwUuids.UART_WRITE -> "uart_write"
        else -> uuid.toString().substring(startIndex = 4, endIndex = 8)
    }

internal fun ByteArray.toRawHexString(): String =
    joinToString(separator = "") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }

internal fun Throwable.shortMessage(): String =
    message ?: this::class.simpleName ?: "unknown"
