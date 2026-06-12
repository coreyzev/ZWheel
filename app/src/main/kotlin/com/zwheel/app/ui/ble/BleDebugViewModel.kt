package com.zwheel.app.ui.ble

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.ble.KableBleTransport
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.debug.BleDebugRecorder
import com.zwheel.core.protocol.handshake.GeminiStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SCAN_DURATION_MS = 30_000L
private const val DEVICE_STALE_MS = 5_000L
private const val DEVICE_PRUNE_INTERVAL_MS = 1_000L

class BleDebugViewModel : ViewModel() {
    private val transport = KableBleTransport()
    private val recorder = BleDebugRecorder()
    private val deviceLastSeen = mutableMapOf<String, Long>()
    private var scanJob: Job? = null
    private val dumpJobs = mutableListOf<Job>()

    private val _devices = MutableStateFlow<List<ScanResult>>(emptyList())
    val devices: StateFlow<List<ScanResult>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<ScanResult?>(null)
    val selectedDevice: StateFlow<ScanResult?> = _selectedDevice.asStateFlow()

    private val sessionLogger = BleDebugSessionLogger(
        io = transport,
        recorder = recorder,
        selectedDeviceId = { _selectedDevice.value?.deviceId },
    )

    private val _logLines = MutableStateFlow(listOf("Idle"))
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    private val _exportStatus = MutableStateFlow("No export yet")
    val exportStatus: StateFlow<String> = _exportStatus.asStateFlow()

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
                    ConnectionState.Disconnected -> {
                        recorder.record(type = "disconnect", status = "transport disconnected")
                        appendLog("Disconnected")
                    }
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
        recorder.record(
            type = "selected_device",
            deviceId = device.deviceId,
            displayName = device.displayName,
            rssi = device.rssi,
        )
        recorder.record(
            type = "connect_start",
            deviceId = device.deviceId,
            displayName = device.displayName,
            rssi = device.rssi,
        )
        appendLog("Connecting ${device.label()}")
        viewModelScope.launch {
            try {
                transport.connect(device.deviceId)
                recorder.record(type = "gatt_ready", deviceId = device.deviceId, status = "connected")
                appendLog("GATT ready")
                appendLog(sessionLogger.boardMetadataLine())
                recorder.record(type = "gemini_wait", deviceId = device.deviceId, status = "uart notification 5s")
                appendLog("Gemini wait UART 5s")
                val result = GeminiStrategy(
                    debugRecorder = recorder,
                    debugDeviceId = { _selectedDevice.value?.deviceId },
                ).unlock(transport)
                recorder.record(
                    type = "gemini_result",
                    deviceId = device.deviceId,
                    status = "${result.strategyName}:${result.unlocked}",
                )
                appendLog("Unlock ${result.strategyName}: ${result.unlocked}")
                if (result.unlocked) {
                    appendLog("Connected")
                    startDumpJobs()
                }
            } catch (error: Throwable) {
                recorder.record(
                    type = "connect_failure",
                    deviceId = device.deviceId,
                    status = error.shortMessage(),
                )
                appendLog("Connect/unlock failed: ${error.shortMessage()}")
                if (error is TimeoutCancellationException) {
                    appendLog(sessionLogger.probeTelemetryLine())
                    appendLog("OWCE trigger likely needed")
                }
            }
        }
    }

    fun onDisconnectClicked() {
        scanJob?.cancel()
        dumpJobs.forEach(Job::cancel)
        dumpJobs.clear()
        recorder.record(type = "disconnect_requested", deviceId = _selectedDevice.value?.deviceId)
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

    fun exportJsonLines(): String = recorder.toJsonLines()

    fun onExportStatus(status: String) {
        _exportStatus.value = status
        appendLog(status)
    }

    private fun startScan() {
        scanJob?.cancel()
        _devices.value = emptyList()
        deviceLastSeen.clear()
        _selectedDevice.value = null
        recorder.record(type = "scan_start", status = "service UUID first, ow name fallback after 10s")
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
                } ?: run {
                    recorder.record(type = "scan_stop", status = "timeout 30s")
                    appendLog("Scan stopped after 30s")
                }
            }.onFailure { error ->
                if (error !is CancellationException) {
                    recorder.record(type = "scan_failure", status = error.shortMessage())
                    appendLog("Scan failed: ${error.shortMessage()}")
                }
            }
        }
    }

    private fun onScanResult(result: ScanResult) {
        recorder.record(
            type = "scan_discovery",
            deviceId = result.deviceId,
            displayName = result.displayName,
            rssi = result.rssi,
        )
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
        dumpJobs += sessionLogger.startDumpJobs(viewModelScope, ::appendLog)
    }

    private fun appendLog(line: String) {
        Log.d(TAG, line)
        _logLines.value = (_logLines.value + line).takeLast(MAX_LOG_LINES)
    }

    override fun onCleared() {
        dumpJobs.forEach(Job::cancel)
        scanJob?.cancel()
        viewModelScope.launch { transport.disconnect() }
        super.onCleared()
    }

    private companion object {
        const val TAG = "BleDebug"
        const val MAX_LOG_LINES = 80
    }
}
