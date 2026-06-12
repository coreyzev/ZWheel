package com.zwheel.core.protocol

import java.util.UUID

@JvmInline
value class GattCharacteristicId(val uuid: UUID)

object OwUuids {
    /** Primary Onewheel BLE service UUID documented by UWP-Onewheel and OWCE. */
    val ONEWHEEL_SERVICE: UUID = UUID.fromString("e659f300-ea98-11e3-ac10-0800200c9a66")

    /** Serial number characteristic documented by OWCE board metadata reads. */
    val SERIAL_NUMBER = GattCharacteristicId(UUID.fromString("e659f301-ea98-11e3-ac10-0800200c9a66"))

    /** Ride mode read/write characteristic from OWCE/pOnewheel community maps. */
    val RIDE_MODE = GattCharacteristicId(UUID.fromString("e659f302-ea98-11e3-ac10-0800200c9a66"))

    /** Battery percentage telemetry characteristic from the community GATT map. */
    val BATTERY_PERCENT = GattCharacteristicId(UUID.fromString("e659f303-ea98-11e3-ac10-0800200c9a66"))

    /** Battery serial characteristic documented by OWCE/pOnewheel community maps. */
    val BATTERY_SERIAL = GattCharacteristicId(UUID.fromString("e659f306-ea98-11e3-ac10-0800200c9a66"))

    /** Pitch telemetry characteristic from OWCE/pOnewheel community maps. */
    val PITCH = GattCharacteristicId(UUID.fromString("e659f307-ea98-11e3-ac10-0800200c9a66"))

    /** Roll telemetry characteristic from OWCE/pOnewheel community maps. */
    val ROLL = GattCharacteristicId(UUID.fromString("e659f308-ea98-11e3-ac10-0800200c9a66"))

    /** Yaw telemetry characteristic from OWCE/pOnewheel community maps. */
    val YAW = GattCharacteristicId(UUID.fromString("e659f309-ea98-11e3-ac10-0800200c9a66"))

    /** Odometer/tire revolution telemetry characteristic from OWCE/pOnewheel community maps. */
    val ODOMETER = GattCharacteristicId(UUID.fromString("e659f30a-ea98-11e3-ac10-0800200c9a66"))

    /** RPM telemetry characteristic used for corrected speed on XR-era boards. */
    val RPM = GattCharacteristicId(UUID.fromString("e659f30b-ea98-11e3-ac10-0800200c9a66"))

    /** Lighting mode read/write characteristic from OWCE/pOnewheel community maps. */
    val LIGHTS = GattCharacteristicId(UUID.fromString("e659f30c-ea98-11e3-ac10-0800200c9a66"))

    /** Front lights characteristic documented by OWCE/pOnewheel community maps. */
    val LIGHTS_FRONT = GattCharacteristicId(UUID.fromString("e659f30d-ea98-11e3-ac10-0800200c9a66"))

    /** Back lights characteristic documented by OWCE/pOnewheel community maps. */
    val LIGHTS_BACK = GattCharacteristicId(UUID.fromString("e659f30e-ea98-11e3-ac10-0800200c9a66"))

    /** Status/error characteristic documented by OWCE/pOnewheel community maps. */
    val STATUS_ERROR = GattCharacteristicId(UUID.fromString("e659f30f-ea98-11e3-ac10-0800200c9a66"))

    /** Controller/motor temperature telemetry characteristic from OWCE parsing references. */
    val TEMPERATURE = GattCharacteristicId(UUID.fromString("e659f310-ea98-11e3-ac10-0800200c9a66"))

    /** Firmware revision characteristic documented by OWCE and UWP-Onewheel references. */
    val FIRMWARE_REVISION = GattCharacteristicId(UUID.fromString("e659f311-ea98-11e3-ac10-0800200c9a66"))

    /** Current/amps telemetry characteristic from OWCE parsing references. */
    val AMPS = GattCharacteristicId(UUID.fromString("e659f312-ea98-11e3-ac10-0800200c9a66"))

    /** Trip total amp-hours telemetry characteristic from OWCE/pOnewheel community maps. */
    val TRIP_TOTAL_AMP_HOURS = GattCharacteristicId(UUID.fromString("e659f313-ea98-11e3-ac10-0800200c9a66"))

    /** Trip regen amp-hours telemetry characteristic from OWCE/pOnewheel community maps. */
    val TRIP_REGEN_AMP_HOURS = GattCharacteristicId(UUID.fromString("e659f314-ea98-11e3-ac10-0800200c9a66"))

    /** Battery temperature telemetry characteristic from OWCE/pOnewheel community maps. */
    val BATTERY_TEMPERATURE = GattCharacteristicId(UUID.fromString("e659f315-ea98-11e3-ac10-0800200c9a66"))

    /** Pack voltage telemetry characteristic from OWCE parsing references. */
    val PACK_VOLTAGE = GattCharacteristicId(UUID.fromString("e659f316-ea98-11e3-ac10-0800200c9a66"))

    /** Safety headroom telemetry characteristic from OWCE/pOnewheel community maps. */
    val SAFETY_HEADROOM = GattCharacteristicId(UUID.fromString("e659f317-ea98-11e3-ac10-0800200c9a66"))

    /** Hardware revision characteristic documented by OWCE board metadata reads. */
    val HARDWARE_REVISION = GattCharacteristicId(UUID.fromString("e659f318-ea98-11e3-ac10-0800200c9a66"))

    /** Lifetime odometer characteristic documented by OWCE/pOnewheel community maps. */
    val LIFETIME_ODOMETER = GattCharacteristicId(UUID.fromString("e659f319-ea98-11e3-ac10-0800200c9a66"))

    /** Lifetime amp-hours characteristic documented by OWCE/pOnewheel community maps. */
    val LIFETIME_AMP_HOURS = GattCharacteristicId(UUID.fromString("e659f31a-ea98-11e3-ac10-0800200c9a66"))

    /** Per-cell voltage telemetry characteristic from OWCE parsing references. */
    val CELL_VOLTAGES = GattCharacteristicId(UUID.fromString("e659f31b-ea98-11e3-ac10-0800200c9a66"))

    /** Last error code characteristic documented by OWCE/pOnewheel community maps. */
    val LAST_ERROR_CODE = GattCharacteristicId(UUID.fromString("e659f31c-ea98-11e3-ac10-0800200c9a66"))

    /** Custom board name characteristic documented by OWCE/pOnewheel community maps. */
    val CUSTOM_NAME = GattCharacteristicId(UUID.fromString("e659f3fd-ea98-11e3-ac10-0800200c9a66"))

    /**
     * Gemini UART read characteristic described in pOnewheel issue #86 and UWP-Onewheel docs.
     * Notify-only — the board pushes the challenge as a notification; never call read() on this
     * characteristic (it has no READ property and will throw at the transport layer).
     */
    val UART_READ = GattCharacteristicId(UUID.fromString("e659f3fe-ea98-11e3-ac10-0800200c9a66"))

    /** Gemini UART write/unlock characteristic described in pOnewheel issue #86 and UWP-Onewheel docs. */
    val UART_WRITE = GattCharacteristicId(UUID.fromString("e659f3ff-ea98-11e3-ac10-0800200c9a66"))

    /**
     * Writable ONLY for Gemini handshake trigger: read the value then write it back unchanged
     * to signal the board to emit the challenge. This is NOT a firmware write path — the board
     * ignores the value and uses the write event as a trigger only.
     * Evidence: OWCE OWBoard.cs L861-876.
     */
    val writableAllowlist: Set<GattCharacteristicId> = setOf(
        UART_WRITE,
        RIDE_MODE,
        LIGHTS,
        FIRMWARE_REVISION,
    )
}
