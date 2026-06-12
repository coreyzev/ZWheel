package com.zwheel.app.ui.ble

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val pendingScan = remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        bleScanPermissions()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantResults ->
        val granted = requiredPermissions.all { permission ->
            grantResults[permission] == true || hasPermission(context, permission)
        }
        if (granted) {
            permissionDenied.value = null
            if (pendingScan.value) {
                pendingScan.value = false
                startScan(scope, transport, devices, selectedDevice, scanJob, logLines)
            }
        } else {
            pendingScan.value = false
            permissionDenied.value = buildPermissionDeniedMessage()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAllRequiredPermissions(context, requiredPermissions)) {
            permissionDenied.value = buildPermissionDeniedMessage()
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
            Button(
                onClick = {
                    if (hasAllRequiredPermissions(context, requiredPermissions)) {
                        permissionDenied.value = null
                        startScan(scope, transport, devices, selectedDevice, scanJob, logLines)
                    } else {
                        pendingScan.value = true
                        permissionDenied.value = buildPermissionDeniedMessage()
                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                    }
                },
            ) {
                Text("Scan")
            }
            Button(
                enabled = selectedDevice.value != null,
                onClick = {
                    val device = selectedDevice.value ?: return@Button
                    scanJob.value?.cancel()
                    logLines.append("Connecting ${device.label()}")
                    scope.launch {
                        runCatching {
                            transport.connect(device.deviceId)
                            logLines.append("Connected, sending Gemini unlock")
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
                onClick = {
                    scanJob.value?.cancel()
                    dumpJobs.forEach(Job::cancel)
                    dumpJobs.clear()
                    scope.launch {
                        runCatching { transport.disconnect() }
                            .onSuccess { logLines.append("Disconnected") }
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

private fun hasPermission(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasAllRequiredPermissions(
    context: android.content.Context,
    permissions: List<String>,
): Boolean = permissions.all { hasPermission(context, it) }

private fun buildPermissionDeniedMessage(): String =
    "Bluetooth scan permissions are required before scanning so ZWheel can find the board and keep the ride connection alive."

private fun bleScanPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
