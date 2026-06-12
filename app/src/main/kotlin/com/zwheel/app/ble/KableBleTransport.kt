package com.zwheel.app.ble

import android.bluetooth.le.ScanSettings
import com.juul.kable.Advertisement
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.toIdentifier
import com.zwheel.core.ports.BleTransport
import com.zwheel.core.ports.GattIo
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@OptIn(ObsoleteKableApi::class)
class KableBleTransport : BleTransport, GattIo {
    private val advertisements = mutableMapOf<String, Advertisement>()
    private var peripheral: com.juul.kable.Peripheral? = null

    override suspend fun scan(serviceUuid: UUID): Flow<ScanResult> {
        val scanner = Scanner {
            filters {
                match {
                    services = listOf(serviceUuid)
                }
            }
            scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            preConflate = true
        }

        return scanner.advertisements
            .onEach { advertisement -> advertisements[advertisement.identifier.toString()] = advertisement }
            .map { advertisement ->
                ScanResult(
                    deviceId = advertisement.identifier.toString(),
                    displayName = advertisement.name ?: advertisement.peripheralName,
                    rssi = advertisement.rssi.takeUnless { it == Int.MIN_VALUE },
                )
            }
    }

    override suspend fun connect(deviceId: String) {
        val advertisement = advertisements[deviceId]
        peripheral = if (advertisement != null) {
            Peripheral(advertisement)
        } else {
            Peripheral(deviceId.toIdentifier())
        }
        currentPeripheral().connect()
    }

    override suspend fun disconnect() {
        peripheral?.disconnect()
        peripheral = null
    }

    override suspend fun read(characteristicId: GattCharacteristicId): ByteArray =
        currentPeripheral().read(characteristicId.toKableCharacteristic())

    override suspend fun write(characteristicId: GattCharacteristicId, value: ByteArray) {
        require(characteristicId in OwUuids.writableAllowlist) {
            "Refusing write to non-allowlisted Onewheel characteristic ${characteristicId.uuid}"
        }
        currentPeripheral().write(characteristicId.toKableCharacteristic(), value, WriteType.WithResponse)
    }

    override fun notifications(characteristicId: GattCharacteristicId): Flow<ByteArray> =
        currentPeripheral().observe(characteristicId.toKableCharacteristic())

    private fun currentPeripheral(): com.juul.kable.Peripheral =
        checkNotNull(peripheral) { "No active BLE peripheral" }

    private fun GattCharacteristicId.toKableCharacteristic() =
        characteristicOf(OwUuids.ONEWHEEL_SERVICE.toString(), uuid.toString())
}
