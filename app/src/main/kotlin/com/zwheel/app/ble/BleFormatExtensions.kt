package com.zwheel.app.ble

import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids

internal fun ScanResult.deviceKey(): String = deviceId.lowercase()

internal fun GattCharacteristicId.debugName(): String =
    when (this) {
        OwUuids.BATTERY_PERCENT -> "battery_percent"
        OwUuids.BATTERY_TEMPERATURE -> "battery_temperature"
        OwUuids.RPM -> "rpm"
        OwUuids.PACK_VOLTAGE -> "pack_voltage"
        OwUuids.CELL_VOLTAGES -> "cell_voltages"
        OwUuids.AMPS -> "amps"
        OwUuids.TRIP_TOTAL_AMP_HOURS -> "trip_amp_hours"
        OwUuids.TRIP_REGEN_AMP_HOURS -> "trip_regen_amp_hours"
        OwUuids.TEMPERATURE -> "controller_motor_temp"
        OwUuids.SAFETY_HEADROOM -> "safety_headroom"
        OwUuids.STATUS_ERROR -> "status_error"
        OwUuids.PITCH -> "pitch"
        OwUuids.ROLL -> "roll"
        OwUuids.YAW -> "yaw"
        OwUuids.RIDE_MODE -> "ride_mode"
        OwUuids.ODOMETER -> "odometer"
        OwUuids.HARDWARE_REVISION -> "hardware_revision"
        OwUuids.FIRMWARE_REVISION -> "firmware_revision"
        OwUuids.UART_WRITE -> "uart_write"
        OwUuids.UART_READ -> "uart_read"
        else -> uuid.toString().substring(startIndex = 4, endIndex = 8)
    }

internal fun ByteArray.toRawHexString(): String =
    joinToString(separator = "") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }

internal fun Throwable.shortMessage(): String =
    message ?: this::class.simpleName ?: "unknown"
