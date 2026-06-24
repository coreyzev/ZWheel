package com.zwheel.app.ble

import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.core.model.BoardIdentity
import com.zwheel.core.model.BoardState
import com.zwheel.core.ports.Clock
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.BoardTypeDetector
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
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    private var connectJob: Job? = null
    private var scanJob: Job? = null
    private var stateMirrorJob: Job? = null
    private var keepAliveJob: Job? = null

    val connectionState: StateFlow<ConnectionState> = transport.connectionState

    private val _devices = MutableStateFlow<List<ScanResult>>(emptyList())
    val devices: StateFlow<List<ScanResult>> = _devices.asStateFlow()

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    private val _staleTelemetry = MutableStateFlow(false)
    val staleTelemetry: StateFlow<Boolean> = _staleTelemetry.asStateFlow()
    private var staleTelemetryJob: Job? = null

    private val _lastErrorCode = MutableStateFlow<Int?>(null)
    val lastErrorCode: StateFlow<Int?> = _lastErrorCode.asStateFlow()

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
        _staleTelemetry.value = false
        _lastErrorCode.value = null
        staleTelemetryJob?.cancel()
        connectJob?.cancelAndJoin()
        connectJob = coroutineContext[Job]
        val scanResult = _devices.value.firstOrNull { it.deviceKey() == deviceId.lowercase() }
        _rssi.value = scanResult?.rssi
        stopScan()
        stateMirrorJob?.cancel()
        keepAliveJob?.cancel()
        transport.connect(deviceId)
        val handshakeStrategy = GeminiStrategy(
            debugRecorder = recorder,
            debugDeviceId = { deviceId },
        )
        // The board won't re-issue the Gemini challenge without a power cycle.
        // If unlock fails (timeout or otherwise), disconnect the transport so the
        // connection state returns to Disconnected — prevents the dashboard from
        // showing indefinitely with zero data.
        val unlockResult = try {
            handshakeStrategy.unlock(transport)
        } catch (e: Exception) {
            runCatching { transport.disconnect() }
            throw e
        }
        check(unlockResult.unlocked) { "Board unlock failed: ${unlockResult.strategyName}" }

        val hwBytes = transport.read(OwUuids.HARDWARE_REVISION)
        val fwBytes = transport.read(OwUuids.FIRMWARE_REVISION)
        val hwRev = Parsers.hardwareRevision(hwBytes)
        val fwRev = Parsers.firmwareRevision(fwBytes)
        val boardType = scanResult?.displayName?.let { BoardTypeDetector.detectFromBleName(it) }
            ?: BoardTypeDetector.detect(hwRev)
        val serialNumber = runCatching {
            Parsers.serialNumber(transport.read(OwUuids.SERIAL_NUMBER))
        }.getOrNull()
        val batterySerialNumber = runCatching {
            Parsers.batterySerialNumber(transport.read(OwUuids.BATTERY_SERIAL))
        }.getOrNull()
        val lifetimeMiles = runCatching {
            Parsers.lifetimeOdometer(transport.read(OwUuids.LIFETIME_ODOMETER))
        }.getOrNull()
        val lifetimeAmpHours = runCatching {
            Parsers.lifetimeAmpHours(transport.read(OwUuids.LIFETIME_AMP_HOURS))
        }.getOrNull()
        val boardCustomName = runCatching {
            Parsers.customName(transport.read(OwUuids.CUSTOM_NAME))
        }.getOrNull()
        val identity = BoardIdentity(
            boardId = deviceId,
            name = scanResult?.displayName ?: boardType.displayName,
            type = boardType,
            serialNumber = serialNumber,
            batterySerialNumber = batterySerialNumber,
            firmwareRevision = fwRev.toString(),
            hardwareRevision = hwRev.toString(),
            lifetimeMiles = lifetimeMiles,
            lifetimeAmpHours = lifetimeAmpHours,
        )

        // Persist identity now, inside connect(), so settings are always saved before
        // disconnect() clears boardState — avoids the race in persistConnectedIdentity().
        settingsRepository.saveLastConnectedBoardType(identity.type)
        settingsRepository.saveLastConnectedIdentityDetails(
            name = identity.name,
            serial = identity.serialNumber,
            batterySerial = identity.batterySerialNumber,
            hardwareRev = identity.hardwareRevision,
            firmwareRev = identity.firmwareRevision,
        )

        val currentCustomName = settingsRepository.preferences.first().customBoardName
        if (currentCustomName == null && boardCustomName != null) {
            settingsRepository.setCustomBoardName(boardCustomName)
        }

        val savedPrefs = settingsRepository.preferences.first()
        val tireDiameter = savedPrefs.lastConnectedTireDiameterInches
            ?: boardType.stockTireDiameterInches
        val service = BoardStateServiceImpl(
            transport = transport,
            clock = clock,
            boardType = boardType,
            diameterInches = tireDiameter,
            stockDiameterInches = boardType.stockTireDiameterInches,
            boardIdentity = identity,
        )
        service.start(scope)
        startStaleTelemetryWatcher()
        scope.launch {
            runCatching {
                transport.notifications(OwUuids.LAST_ERROR_CODE).collect { bytes ->
                    val code = Parsers.lastErrorCode(bytes)
                    if (code != null) _lastErrorCode.value = code
                }
            }
        }
        stateMirrorJob = scope.launch {
            service.state.collect { state ->
                _boardState.value = state
            }
        }
        startKeepAlive(handshakeStrategy, deviceId)
    }

    fun disconnect() {
        stopScan()
        stateMirrorJob?.cancel()
        stateMirrorJob = null
        staleTelemetryJob?.cancel()
        staleTelemetryJob = null
        _staleTelemetry.value = false
        keepAliveJob?.cancel()
        keepAliveJob = null
        scope.coroutineContext.cancelChildren()
        _boardState.value = BoardState()
        _rssi.value = null
        scope.launch {
            runCatching { transport.disconnect() }
        }
    }

    fun refreshLifetimeStats() {
        scope.launch {
            val currentIdentity = _boardState.value.identity ?: return@launch
            val newMiles = runCatching {
                Parsers.lifetimeOdometer(transport.read(OwUuids.LIFETIME_ODOMETER))
            }.getOrNull()
            val newAh = runCatching {
                Parsers.lifetimeAmpHours(transport.read(OwUuids.LIFETIME_AMP_HOURS))
            }.getOrNull()
            if (newMiles != null || newAh != null) {
                _boardState.update { state ->
                    state.copy(
                        identity = state.identity?.copy(
                            lifetimeMiles = newMiles ?: currentIdentity.lifetimeMiles,
                            lifetimeAmpHours = newAh ?: currentIdentity.lifetimeAmpHours,
                        ),
                    )
                }
            }
        }
    }

    private fun startKeepAlive(strategy: GeminiStrategy, deviceId: String) {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launchGeminiKeepAlive(strategy, deviceId, transport, recorder)
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

    private fun startStaleTelemetryWatcher() {
        staleTelemetryJob?.cancel()
        staleTelemetryJob = scope.launch {
            var hadNonZeroVoltage = false
            var pendingStaleJob: Job? = null
            _boardState.collect { state ->
                if (connectionState.value != ConnectionState.Connected) return@collect
                if ((state.packVoltage ?: 0.0) > 0.0) {
                    hadNonZeroVoltage = true
                    pendingStaleJob?.cancel()
                    pendingStaleJob = null
                    _staleTelemetry.value = false
                } else if (hadNonZeroVoltage && pendingStaleJob?.isActive != true) {
                    pendingStaleJob = launch {
                        delay(500L)
                        _staleTelemetry.value = true
                    }
                }
            }
        }
    }
}
