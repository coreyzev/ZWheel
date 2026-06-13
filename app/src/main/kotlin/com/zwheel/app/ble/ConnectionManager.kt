package com.zwheel.app.ble

import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.BoardType
import com.zwheel.core.ports.Clock
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.debug.BleDebugRecorder
import com.zwheel.core.protocol.handshake.GeminiStrategy
import com.zwheel.core.service.BoardStateServiceImpl
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SCAN_DURATION_MS = 30_000L
private const val DEVICE_STALE_MS = 5_000L
private const val DEVICE_PRUNE_INTERVAL_MS = 1_000L

@Singleton
class ConnectionManager @Inject constructor(
    private val transport: KableBleTransport,
    private val clock: Clock,
    private val settingsRepository: SettingsRepository,
    private val recorder: BleDebugRecorder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val deviceLastSeen = mutableMapOf<String, Long>()

    init {
        transport.setDebugRecorder(recorder)
    }
    private var scanJob: Job? = null
    private var stateMirrorJob: Job? = null

    val connectionState: StateFlow<ConnectionState> = transport.connectionState

    private val _devices = MutableStateFlow<List<ScanResult>>(emptyList())
    val devices: StateFlow<List<ScanResult>> = _devices.asStateFlow()

    private val _boardState = MutableStateFlow(BoardState())
    val boardState: StateFlow<BoardState> = _boardState.asStateFlow()

    fun scan() {
        if (connectionState.value == ConnectionState.Scanning ||
            connectionState.value == ConnectionState.Connected
        ) {
            return
        }
        scanJob?.cancel()
        _devices.value = emptyList()
        deviceLastSeen.clear()
        scanJob = scope.launch {
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
                }
            }.onFailure { error ->
                if (error !is CancellationException) {
                    _devices.value = emptyList()
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    suspend fun connect(deviceId: String) {
        stopScan()
        stateMirrorJob?.cancel()
        transport.connect(deviceId)
        val unlockResult = GeminiStrategy().unlock(transport)
        check(unlockResult.unlocked) { "Board unlock failed: ${unlockResult.strategyName}" }

        val boardType = BoardType.XR
        val tireDiameter = settingsRepository.preferences.first().tireDiameterInches
        val service = BoardStateServiceImpl(
            transport = transport,
            clock = clock,
            boardType = boardType,
            diameterInches = tireDiameter,
            stockDiameterInches = boardType.stockTireDiameterInches,
        )
        service.start(scope)
        stateMirrorJob = scope.launch {
            service.state.collect { state ->
                _boardState.value = state
            }
        }
    }

    fun disconnect() {
        stopScan()
        stateMirrorJob?.cancel()
        stateMirrorJob = null
        scope.coroutineContext.cancelChildren()
        _boardState.value = BoardState()
        scope.launch {
            runCatching { transport.disconnect() }
        }
    }

    private fun onScanResult(result: ScanResult) {
        val key = result.deviceKey()
        deviceLastSeen[key] = System.currentTimeMillis()
        val currentDevices = _devices.value
        val index = currentDevices.indexOfFirst { it.deviceKey() == key }
        _devices.value = if (index == -1) {
            currentDevices + result
        } else {
            currentDevices.toMutableList().also { devices ->
                devices[index] = result
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
    }

    private fun ScanResult.deviceKey(): String = deviceId.lowercase()
}
