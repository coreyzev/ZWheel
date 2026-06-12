package com.zwheel.app.ui.ble

import android.util.Log
import com.zwheel.core.ports.GattIo
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.debug.BleDebugRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TELEMETRY_PROBE_TIMEOUT_MS = 2_000L

internal class BleDebugSessionLogger(
    private val io: GattIo,
    private val recorder: BleDebugRecorder,
    private val selectedDeviceId: () -> String?,
) {
    fun startDumpJobs(scope: CoroutineScope, appendLog: (String) -> Unit): List<Job> =
        dumpCharacteristics.map { characteristic ->
            scope.launch {
                io.notifications(characteristic)
                    .take(20)
                    .collect { value ->
                        recorder.record(
                            type = "notification",
                            deviceId = selectedDeviceId(),
                            characteristicUuid = characteristic.uuid.toString(),
                            characteristicName = characteristic.debugName(),
                            rawValueHex = value.toRawHexString(),
                        )
                        appendLog("${characteristic.shortName()} ${value.toHexString()}")
                    }
            }
        }

    suspend fun boardMetadataLine(): String {
        val hardware = readHex(OwUuids.HARDWARE_REVISION)
        val firmware = readHex(OwUuids.FIRMWARE_REVISION)
        val rideMode = readHex(OwUuids.RIDE_MODE)
        return "Meta hw=${hardware.displayValue()} fw=${firmware.displayValue()} ride=${rideMode.displayValue()}"
    }

    suspend fun probeTelemetryLine(): String = coroutineScope {
        val results = telemetryProbeCharacteristics.map { probe ->
            async {
                val value = runCatching {
                    withTimeoutOrNull(TELEMETRY_PROBE_TIMEOUT_MS) {
                        io.notifications(probe.characteristicId).first()
                    }
                }.onFailure { error ->
                    Log.d(TAG, "Probe ${probe.name} failed", error)
                }.getOrNull()
                recorder.record(
                    type = "telemetry_probe",
                    deviceId = selectedDeviceId(),
                    characteristicUuid = probe.characteristicId.uuid.toString(),
                    characteristicName = probe.characteristicId.debugName(),
                    rawValueHex = value?.toRawHexString(),
                    status = value?.let { "ok" } ?: "timeout",
                )
                probe.name to value?.toCompactDisplay()
            }
        }.awaitAll()

        results.joinToString(
            separator = " ",
            prefix = "Probe ",
        ) { (name, value) -> "$name=${value ?: "--"}" }
    }

    private suspend fun readHex(characteristicId: GattCharacteristicId): String? =
        runCatching { io.read(characteristicId) }
            .onSuccess { value ->
                recorder.record(
                    type = "metadata_read",
                    deviceId = selectedDeviceId(),
                    characteristicUuid = characteristicId.uuid.toString(),
                    characteristicName = characteristicId.debugName(),
                    rawValueHex = value.toRawHexString(),
                    status = "ok",
                )
            }
            .onFailure { error ->
                recorder.record(
                    type = "metadata_read",
                    deviceId = selectedDeviceId(),
                    characteristicUuid = characteristicId.uuid.toString(),
                    characteristicName = characteristicId.debugName(),
                    status = error.shortMessage(),
                )
                Log.d(TAG, "Read ${characteristicId.shortName()} failed", error)
            }
            .getOrNull()
            ?.toCompactDisplay()

    private companion object {
        const val TAG = "BleDebug"
    }
}
