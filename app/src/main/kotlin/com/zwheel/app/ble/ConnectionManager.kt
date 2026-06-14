package com.zwheel.app.ble

import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.core.model.BoardIdentity
import com.zwheel.core.model.BoardState
import com.zwheel.core.ports.Clock
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.BoardTypeDetector
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.KeepAliveAction
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.Parsers
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
    private var keepAliveJob: Job? = null

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
        keepAliveJob?.cancel()
        transport.connect(deviceId)
        val handshakeStrategy = GeminiStrategy(
            debugRecorder = recorder,
            debugDeviceId = { deviceId },
        )
        val unlockResult = handshakeStrategy.unlock(transport)
        check(unlockResult.unlocked) { "Board unlock failed: ${unlockResult.strategyName}" }
        startKeepAlive(handshakeStrategy, deviceId)

        val hwBytes = transport.read(OwUuids.HARDWARE_REVISION)
        val fwBytes = transport.read(OwUuids.FIRMWARE_REVISION)
        val hwRev = Parsers.hardwareRevision(hwBytes)
        val fwRev = Parsers.firmwareRevision(fwBytes)
        val boardType = BoardTypeDetector.detect(hwRev)
        val identity = BoardIdentity(
            boardId = deviceId,
            name = boardType.displayName,
            type = boardType,
            firmwareRevision = fwRev.toString(),
            hardwareRevision = hwRev.toString(),
        )

        val savedDiameter = settingsRepository.preferences.first().tireDiameterInches
        val tireDiameter = if (savedDiameter != UserPreferences().tireDiameterInches) {
            savedDiameter
        } else {
            boardType.stockTireDiameterInches
        }
        val service = BoardStateServiceImpl(
            transport = transport,
            clock = clock,
            boardType = boardType,
            diameterInches = tireDiameter,
            stockDiameterInches = boardType.stockTireDiameterInches,
            boardIdentity = identity,
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
        keepAliveJob?.cancel()
        keepAliveJob = null
        scope.coroutineContext.cancelChildren()
        _boardState.value = BoardState()
        scope.launch {
            runCatching { transport.disconnect() }
        }
    }

    private fun startKeepAlive(strategy: GeminiStrategy, deviceId: String) {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            strategy.keepAlive().collect { action ->
                try {
                    executeKeepAliveAction(action, deviceId)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    recordKeepAlive(action, deviceId, status = "error:${error.shortMessage()}")
                    throw CancellationException("Gemini keep-alive failed", error)
                }
            }
        }
    }

    private suspend fun executeKeepAliveAction(action: KeepAliveAction, deviceId: String) {
        when (action) {
            is KeepAliveAction.Write -> {
                recordKeepAlive(action, deviceId, status = "before")
                transport.write(action.characteristicId, action.value)
                recordKeepAlive(action, deviceId, status = "after")
            }
        }
    }

    private fun recordKeepAlive(action: KeepAliveAction, deviceId: String, status: String) {
        when (action) {
            is KeepAliveAction.Write -> recorder.record(
                type = "gemini_keep_alive_write",
                deviceId = deviceId,
                characteristicUuid = action.characteristicId.uuid.toString(),
                characteristicName = action.characteristicId.debugName(),
                rawValueHex = action.value.toRawHexString(),
                status = status,
            )
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

    private fun GattCharacteristicId.debugName(): String =
        when (this) {
            OwUuids.FIRMWARE_REVISION -> "firmware_revision"
            OwUuids.UART_WRITE -> "uart_write"
            else -> uuid.toString().substring(startIndex = 4, endIndex = 8)
        }

    private fun ByteArray.toRawHexString(): String =
        joinToString(separator = "") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }

    private fun Throwable.shortMessage(): String =
        message ?: this::class.simpleName ?: "unknown"
}
