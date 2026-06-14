package com.zwheel.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal object HomeAssistantPusher {

    suspend fun push(haUrl: String, haToken: String, batteryPercent: Int) {
        val url = haUrl.trimEnd('/')
        val body = """{"state":"$batteryPercent","attributes":{"unit_of_measurement":"%","device_class":"battery","friendly_name":"Onewheel Battery"}}"""
        withContext(Dispatchers.IO) {
            try {
                val connection = URL("$url/api/states/sensor.onewheel_battery")
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $haToken")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.outputStream.use { it.write(body.toByteArray()) }
                connection.responseCode
                connection.disconnect()
            } catch (_: Exception) {
                // HA unreachable; retry on next battery percentage change.
            }
        }
    }
}
