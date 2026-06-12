package com.zwheel.app.ui.ble

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.ble.KableBleTransport
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.handshake.GeminiStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SCAN_DURATION_MS = 30_000L

@Composable
fun BleDebugScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val transport = remember { KableBleTransport() }
    val devices = remember { mutableStateListOf<ScanResult>() }
    val logLines = remember { mutableStateListOf("Idle") }
    val selectedDevice = remember { mutableStateOf<ScanResult?>(null) }
    val scanJob = remember { mutableStateOf<Job?>(null) }
    val dumpJobs = remember { mutableStateListOf<Job>() }
    val permissionDenied = remember { mutableStateOf<String?>(null) }
    val permissionsGranted = remember { mutableStateOf(false) }
    val permanentlyDenied = remember { mutableStateOf(false) }
    val permissionRequestAttempted = remember { mutableStateOf(false) }
    val connectionState by transport.connectionState.collectAsState()

    val requiredPermissions = remember { bleScanPermissions() }
    fun refreshPermissionState(updatePermanentDenial: Boolean) {
        permissionsGranted.value = hasAllRequiredPermissions(context, requiredPermissions)
        permissionDenied.value = if (permissionsGranted.value) null else buildPermissionDeniedMessage()
        if (updatePermanentDenial) {
            permanentlyDenied.value = hasPermanentlyDeniedPermission(
                context = context,
                permissions = requiredPermissions,
                requestAttempted = permissionRequestAttempted.value,
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantResults ->
        permissionRequestAttempted.value = true
        val granted = requiredPermissions.all { permission ->
            grantResults[permission] == true || hasPermission(context, permission)
        }
        permissionsGranted.value = granted
        permissionDenied.value = if (granted) null else buildPermissionDeniedMessage()
        permanentlyDenied.value = hasPermanentlyDeniedPermission(
            context = context,
            permissions = requiredPermissions,
            requestAttempted = permissionRequestAttempted.value,
        )
    }

    LaunchedEffect(Unit) {
        refreshPermissionState(updatePermanentDenial = false)
    }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            ConnectionState.Scanning -> logLines.append("Scanning...")
            ConnectionState.Connected -> logLines.append("Connected")
            ConnectionState.Disconnected -> logLines.append("Disconnected")
            ConnectionState.Idle -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("BLE DEBUG", fontWeight = FontWeight.Black)
        permissionDenied.value?.let { message ->
            Text(message)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (permissionsGranted.value) {
                Button(
                    enabled = connectionState != ConnectionState.Scanning &&
                        connectionState != ConnectionState.Connected,
                    onClick = {
                        refreshPermissionState(updatePermanentDenial = false)
                        if (permissionsGranted.value) {
                            startScan(scope, transport, devices, selectedDevice, scanJob, logLines)
                        }
                    },
                ) {
                    Text("Scan")
                }
            } else {
                Button(
                    onClick = { permissionLauncher.launch(requiredPermissions.toTypedArray()) },
                ) {
                    Text("Grant permissions")
                }
                if (permanentlyDenied.value) {
                    Button(
                        onClick = { context.openAppSettings() },
                    ) {
                        Text("Open settings")
                    }
                }
            }
            Button(
                enabled = selectedDevice.value != null &&
                    connectionState != ConnectionState.Scanning &&
                    connectionState != ConnectionState.Connected,
                onClick = {
                    val device = selectedDevice.value ?: return@Button
                    scanJob.value?.cancel()
                    logLines.append("Connecting ${device.label()}")
                    scope.launch {
                        runCatching {
                            transport.connect(device.deviceId)
                            logLines.append("Sending Gemini unlock")
                            val result = GeminiStrategy().unlock(transport)
                            logLines.append("Unlock ${result.strategyName}: ${result.unlocked}")
                            startDumpJobs(scope, transport, dumpJobs, logLines)
                        }.onFailure { error -> logLines.append("Connect/unlock failed: ${error.message}") }
                    }
                },
            ) {
                Text("Connect + Unlock")
            }
            Button(
                enabled = connectionState == ConnectionState.Connected,
                onClick = {
                    scanJob.value?.cancel()
                    dumpJobs.forEach(Job::cancel)
                    dumpJobs.clear()
                    scope.launch {
                        runCatching { transport.disconnect() }
                            .onFailure { error -> logLines.append("Disconnect failed: ${error.message}") }
                    }
                },
            ) {
                Text("Disconnect")
            }
        }
        if (devices.isNotEmpty()) {
            Text("Selected: ${selectedDevice.value?.label().orEmpty()}")
        }
        HorizontalDivider()
        logLines.takeLast(12).forEach { line ->
            Text(text = line)
        }
    }
}

private fun startScan(
    scope: CoroutineScope,
    transport: KableBleTransport,
    devices: MutableList<ScanResult>,
    selectedDevice: androidx.compose.runtime.MutableState<ScanResult?>,
    scanJob: androidx.compose.runtime.MutableState<Job?>,
    logLines: MutableList<String>,
) {
    scanJob.value?.cancel()
    devices.clear()
    selectedDevice.value = null
    logLines.append("Scanning 30s: service UUID first, ow name fallback after 10s")
    scanJob.value = scope.launch {
        runCatching {
            withTimeoutOrNull(SCAN_DURATION_MS) {
                transport.scan().collect { result ->
                    if (devices.none { it.deviceId == result.deviceId }) {
                        devices += result
                    }
                    selectedDevice.value = selectedDevice.value ?: result
                    logLines.append("Found ${result.label()}")
                }
            } ?: logLines.append("Scan stopped after 30s")
        }.onFailure { error -> logLines.append("Scan failed: ${error.message}") }
    }
}

private fun startDumpJobs(
    scope: CoroutineScope,
    transport: KableBleTransport,
    jobs: MutableList<Job>,
    logLines: MutableList<String>,
) {
    jobs.forEach(Job::cancel)
    jobs.clear()
    dumpCharacteristics.forEach { characteristic ->
        jobs += scope.launch {
            transport.notifications(characteristic)
                .take(20)
                .collect { value -> logLines.append("${characteristic.shortName()} ${value.toHexString()}") }
        }
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

private fun MutableList<String>.append(line: String) {
    add(line)
    if (size > 80) removeAt(0)
}

private fun ScanResult.label(): String =
    listOfNotNull(displayName, deviceId, rssi?.let { "$it dBm" }).joinToString("  ")

private fun GattCharacteristicId.shortName(): String =
    uuid.toString().substring(startIndex = 4, endIndex = 8)

private fun ByteArray.toHexString(): String =
    joinToString(":") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }

private fun buildPermissionDeniedMessage(): String =
    "Bluetooth scan permissions are required before scanning so ZWheel can find the board and keep the ride connection alive."
