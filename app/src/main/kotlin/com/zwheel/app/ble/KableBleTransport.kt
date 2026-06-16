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
import com.zwheel.core.protocol.debug.BleDebugRecorder
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeout

@OptIn(ObsoleteKableApi::class)
class KableBleTransport : BleTransport, GattIo {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val advertisements = mutableMapOf<String, Advertisement>()
    private val _connectionState = MutableStateFlow(ConnectionState.Idle)
    private var peripheral: com.juul.kable.Peripheral? = null
    private var activeDeviceId: String? = null

    private val sharedFlows = mutableMapOf<GattCharacteristicId, Flow<ByteArray>>()
    private var debugRecorder: BleDebugRecorder? = null

    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun setDebugRecorder(recorder: BleDebugRecorder) {
        this.debugRecorder = recorder
    }

    override suspend fun scan(): Flow<ScanResult> {
        advertisements.clear()
        val primary = scanner(serviceUuid = OwUuids.ONEWHEEL_SERVICE)
            .advertisements
            .map { advertisement -> advertisement.toScanResult() }

        val fallback = flow {
            delay(NAME_FALLBACK_DELAY_MS)
            emitAll(
                scanner(serviceUuid = null)
                    .advertisements
                    .filter { advertisement -> advertisement.onewheelName() != null }
                    .map { advertisement -> advertisement.toScanResult(displayName = advertisement.onewheelName()) },
            )
        }

        return merge(primary, fallback)
            .onStart { _connectionState.value = ConnectionState.Scanning }
            .onCompletion {
                if (_connectionState.value == ConnectionState.Scanning) {
                    _connectionState.value = ConnectionState.Idle
                }
            }
    }

    private fun scanner(serviceUuid: UUID?): Scanner<Advertisement> = Scanner {
        if (serviceUuid != null) {
            filters {
                match {
                    services = listOf(serviceUuid)
                }
            }
        }
        scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        preConflate = true
    }

    override suspend fun connect(deviceId: String) {
        synchronized(sharedFlows) {
            sharedFlows.clear()
        }
        val advertisement = advertisements[deviceId]
        activeDeviceId = deviceId
        peripheral = if (advertisement != null) {
            Peripheral(advertisement)
        } else {
            Peripheral(deviceId.toIdentifier())
        }
        try {
            currentPeripheral().connect()
            verifyOnewheelService()
            _connectionState.value = ConnectionState.Connected
        } catch (error: Throwable) {
            disconnect()
            throw error
        }
    }

    override suspend fun disconnect() {
        synchronized(sharedFlows) {
            sharedFlows.clear()
        }
        peripheral?.disconnect()
        peripheral = null
        activeDeviceId = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun read(characteristicId: GattCharacteristicId): ByteArray =
        currentPeripheral().read(characteristicId.toKableCharacteristic())

    override suspend fun write(characteristicId: GattCharacteristicId, value: ByteArray) {
        require(characteristicId in OwUuids.writableAllowlist) {
            "Refusing write to non-allowlisted Onewheel characteristic ${characteristicId.uuid}"
        }
        currentPeripheral().write(characteristicId.toKableCharacteristic(), value, WriteType.WithResponse)
    }

    override fun notifications(characteristicId: GattCharacteristicId): Flow<ByteArray> = synchronized(sharedFlows) {
        sharedFlows.getOrPut(characteristicId) {
            currentPeripheral()
                .observe(characteristicId.toKableCharacteristic())
                .onEach { bytes ->
                    debugRecorder?.record(
                        type = "notification",
                        deviceId = activeDeviceId,
                        characteristicUuid = characteristicId.uuid.toString(),
                        characteristicName = characteristicId.debugName(),
                        rawValueHex = bytes.toRawHexString(),
                    )
                }
                .shareIn(
                    scope = scope,
                    started = SharingStarted.WhileSubscribed(),
                    replay = 1,
                )
        }
    }

    private fun currentPeripheral(): com.juul.kable.Peripheral =
        checkNotNull(peripheral) { "No active BLE peripheral" }

    private suspend fun verifyOnewheelService() {
        val services = withTimeout(SERVICE_DISCOVERY_TIMEOUT_MS) {
            currentPeripheral().services.first { discoveredServices -> !discoveredServices.isNullOrEmpty() }
        }
        require(services?.any { service -> service.serviceUuid == OwUuids.ONEWHEEL_SERVICE } == true) {
            "Connected device does not expose the Onewheel service ${OwUuids.ONEWHEEL_SERVICE}"
        }
    }

    private fun GattCharacteristicId.toKableCharacteristic() =
        characteristicOf(OwUuids.ONEWHEEL_SERVICE.toString(), uuid.toString())

    private fun Advertisement.onewheelName(): String? =
        listOfNotNull(name, peripheralName)
            .firstOrNull { deviceName -> deviceName.startsWith(ONEWHEEL_NAME_PREFIX, ignoreCase = true) }

    private fun Advertisement.toScanResult(displayName: String? = name ?: peripheralName): ScanResult {
        advertisements[identifier.toString()] = this
        return ScanResult(
            deviceId = identifier.toString(),
            displayName = displayName,
            rssi = rssi.takeUnless { it == Int.MIN_VALUE },
        )
    }

    private companion object {
        const val ONEWHEEL_NAME_PREFIX = "ow"
        const val NAME_FALLBACK_DELAY_MS = 10_000L
        const val SERVICE_DISCOVERY_TIMEOUT_MS = 10_000L
    }
}
