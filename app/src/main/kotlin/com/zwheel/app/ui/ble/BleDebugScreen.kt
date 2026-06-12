package com.zwheel.app.ui.ble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zwheel.app.ble.KableBleTransport
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.handshake.GeminiStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

@Composable
fun BleDebugScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val transport = remember { KableBleTransport() }
    val devices = remember { mutableStateListOf<ScanResult>() }
    val logLines = remember { mutableStateListOf("Idle") }
    val selectedDevice = remember { mutableStateOf<ScanResult?>(null) }
    val scanJob = remember { mutableStateOf<Job?>(null) }
    val dumpJobs = remember { mutableStateListOf<Job>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("BLE DEBUG", fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scanJob.value?.cancel()
                    devices.clear()
                    logLines.append("Scanning for Onewheel service")
                    scanJob.value = scope.launch {
                        runCatching {
                            transport.scan(OwUuids.ONEWHEEL_SERVICE).collect { result ->
                                if (devices.none { it.deviceId == result.deviceId }) {
                                    devices += result
                                }
                                selectedDevice.value = selectedDevice.value ?: result
                                logLines.append("Found ${result.label()}")
                            }
                        }.onFailure { error -> logLines.append("Scan failed: ${error.message}") }
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
