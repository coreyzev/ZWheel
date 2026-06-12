package com.zwheel.app.ui.ble

import android.content.Context

class BleDebugLogExporter(
    @Suppress("UNUSED_PARAMETER") context: Context,
) {
    val uploadSupported: Boolean = false
    val receiverLabel: String = "debug-only"

    suspend fun share(jsonLines: String): String =
        "BLE log export is debug-only (${jsonLines.lineSequence().count()} events buffered)"

    suspend fun pair(pairingPassword: String): String =
        "BLE upload is debug-only"

    suspend fun upload(jsonLines: String): String =
        "BLE upload is debug-only"
}
