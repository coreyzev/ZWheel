package com.zwheel.app.ble

import com.zwheel.core.ports.GattIo
import com.zwheel.core.protocol.KeepAliveAction
import com.zwheel.core.protocol.debug.BleDebugRecorder
import com.zwheel.core.protocol.handshake.GeminiStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun CoroutineScope.launchGeminiKeepAlive(
    strategy: GeminiStrategy,
    deviceId: String,
    transport: GattIo,
    recorder: BleDebugRecorder,
    onError: ((String) -> Unit)? = null,
): Job = launch {
    strategy.keepAlive().collect { action ->
        try {
            when (action) {
                is KeepAliveAction.Write -> {
                    recorder.recordKeepAlive(action, deviceId, "before")
                    transport.write(action.characteristicId, action.value)
                    recorder.recordKeepAlive(action, deviceId, "after")
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            recorder.recordKeepAlive(action, deviceId, "error:${error.shortMessage()}")
            onError?.invoke("Keep-alive failed: ${error.shortMessage()}")
            throw CancellationException("Gemini keep-alive failed", error)
        }
    }
}

private fun BleDebugRecorder.recordKeepAlive(
    action: KeepAliveAction,
    deviceId: String,
    status: String,
) {
    if (action !is KeepAliveAction.Write) return
    record(
        type = "gemini_keep_alive_write",
        deviceId = deviceId,
        characteristicUuid = action.characteristicId.uuid.toString(),
        characteristicName = action.characteristicId.debugName(),
        rawValueHex = action.value.toRawHexString(),
        status = status,
    )
}
