package com.zwheel.app.ui.ble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.ble.KableBleTransport
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.handshake.GeminiStrategy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SCAN_DURATION_MS = 30_000L
private const val DEVICE_STALE_MS = 5_000L
private const val DEVICE_PRUNE_INTERVAL_MS = 1_000L

class BleDebugViewModel : ViewModel() {
    private val transport = KableBleTransport()
    private val deviceLastSeen = mutableMapOf<String, Long>()
    private var scanJob: Job? = null
    private val dumpJobs = mutableListOf<Job>()

    private val _devices = MutableStateFlow<List<ScanResult>>(emptyList())
    val devices: StateFlow<List<ScanResult>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<ScanResult?>(null)
    val selectedDevice: StateFlow<ScanResult?> = _selectedDevice.asStateFlow()

    private val _logLines = MutableStateFlow(listOf("Idle"))
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = transport.connectionState

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _permanentlyDenied = MutableStateFlow(false)
    val permanentlyDenied: StateFlow<Boolean> = _permanentlyDenied.asStateFlow()

    private var permissionsAttempted = false

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                when (state) {
                    ConnectionState.Scanning -> appendLog("Scanning...")
                    ConnectionState.Disconnected -> appendLog("Disconnected")
                    ConnectionState.Connected,
                    ConnectionState.Idle -> Unit
                }
            }
        }
    }

    fun onScanClicked() {
        if (!_permissionsGranted.value ||
            connectionState.value == ConnectionState.Scanning ||
            connectionState.value == ConnectionState.Connected
        ) {
            return
        }
        startScan()
    }

    fun onConnectClicked(deviceId: String) {
        val device = _devices.value.firstOrNull { it.deviceId == deviceId } ?: return
        scanJob?.cancel()
        appendLog("Connecting ${device.label()}")
        viewModelScope.launch {
            runCatching {
                transport.connect(device.deviceId)
                appendLog("Sending Gemini unlock")
                val result = GeminiStrategy().unlock(transport)
                appendLog("Unlock ${result.strategyName}: ${result.unlocked}")
                if (result.unlocked) {
                    appendLog("Connected")
                    startDumpJobs()
                }
            }.onFailure { error -> appendLog("Connect/unlock failed: ${error.message}") }
        }
    }

    fun onDisconnectClicked() {
        scanJob?.cancel()
        dumpJobs.forEach(Job::cancel)
        dumpJobs.clear()
        viewModelScope.launch {
            runCatching { transport.disconnect() }
                .onFailure { error -> appendLog("Disconnect failed: ${error.message}") }
        }
    }

    fun onPermissionsAttempted() {
        permissionsAttempted = true
    }

    fun onInitialPermissionCheck(granted: Boolean) {
        _permissionsGranted.value = granted
    }

    fun onPermissionsResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        _permissionsGranted.value = granted
        _permanentlyDenied.value = !granted && permissionsAttempted && permanentlyDenied
    }

    private fun startScan() {
        scanJob?.cancel()
        _devices.value = emptyList()
        deviceLastSeen.clear()
        _selectedDevice.value = null
        appendLog("Scanning 30s: service UUID first, ow name fallback after 10s")
        scanJob = viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(SCAN_DURATION_MS) {
                    val cleanupJob = launch {
                        while (true) {
                            delay(DEVICE_PRUNE_INTERVAL_MS)
                            pruneStaleDevices()
                        }
                    }
                    try {
                        transport.scan().collect(::onScanResult)
                    } finally {
                        cleanupJob.cancel()
                    }
                } ?: appendLog("Scan stopped after 30s")
            }.onFailure { error -> appendLog("Scan failed: ${error.message}") }
        }
    }

    private fun onScanResult(result: ScanResult) {
        val key = result.deviceKey()
        deviceLastSeen[key] = System.currentTimeMillis()
        val currentDevices = _devices.value
        val index = currentDevices.indexOfFirst { it.deviceKey() == key }
        if (index == -1) {
            _devices.value = currentDevices + result
            _selectedDevice.value = _selectedDevice.value ?: result
            appendLog("Found ${result.label()}")
        } else {
            _devices.value = currentDevices.toMutableList().also { devices ->
                devices[index] = result
            }
            if (_selectedDevice.value?.deviceKey() == key) {
                _selectedDevice.value = result
            }
        }
    }

    private fun pruneStaleDevices() {
        val cutoff = System.currentTimeMillis() - DEVICE_STALE_MS
        val staleDeviceKeys = deviceLastSeen
            .filterValues { lastSeen -> lastSeen < cutoff }
            .keys
            .toSet()
        if (staleDeviceKeys.isEmpty()) return

        deviceLastSeen.keys.removeAll(staleDeviceKeys)
        _devices.value = _devices.value.filterNot { result -> result.deviceKey() in staleDeviceKeys }
        if (_selectedDevice.value?.deviceKey() in staleDeviceKeys) {
            _selectedDevice.value = _devices.value.firstOrNull()
        }
    }

    private fun startDumpJobs() {
        dumpJobs.forEach(Job::cancel)
        dumpJobs.clear()
        dumpCharacteristics.forEach { characteristic ->
            dumpJobs += viewModelScope.launch {
                transport.notifications(characteristic)
                    .take(20)
                    .collect { value -> appendLog("${characteristic.shortName()} ${value.toHexString()}") }
            }
        }
    }

    private fun appendLog(line: String) {
        _logLines.value = (_logLines.value + line).takeLast(MAX_LOG_LINES)
    }

    override fun onCleared() {
        dumpJobs.forEach(Job::cancel)
        scanJob?.cancel()
        viewModelScope.launch { transport.disconnect() }
        super.onCleared()
    }

    private companion object {
        const val MAX_LOG_LINES = 80
    }
}

private val dumpCharacteristics = listOf(
    OwUuids.BATTERY_PERCENT,
    OwUuids.RPM,
    OwUuids.PACK_VOLTAGE,
    OwUuids.AMPS,
    OwUuids.TEMPERATURE,
    OwUuids.RIDE_MODE,
)

private fun ScanResult.deviceKey(): String = deviceId.lowercase()

private fun ScanResult.label(): String =
    listOfNotNull(displayName, deviceId, rssi?.let { "$it dBm" }).joinToString("  ")

private fun GattCharacteristicId.shortName(): String =
    uuid.toString().substring(startIndex = 4, endIndex = 8)

private fun ByteArray.toHexString(): String =
    joinToString(":") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }
