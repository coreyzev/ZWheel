package com.zwheel.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

sealed interface HaPushResult {
    data object Success : HaPushResult
    data object AuthFailed : HaPushResult
    data object Unreachable : HaPushResult
    data object BadUrl : HaPushResult
}

internal fun haBody(pct: Int, friendlyName: String): String =
    """{"state":"$pct","attributes":{"unit_of_measurement":"%","device_class":"battery","friendly_name":"$friendlyName"}}"""

internal fun haEntitySlug(serialNumber: String): String =
    "zwheel_${serialNumber.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}_battery"

internal fun haEndpoint(baseUrl: String, entitySlug: String): String =
    "${baseUrl.trimEnd('/')}/api/states/sensor.$entitySlug"

internal object HomeAssistantPusher {

    suspend fun push(
        haUrl: String,
        haToken: String,
        batteryPercent: Int,
        entitySlug: String,
        friendlyName: String,
    ): HaPushResult {
        val url = haUrl.trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return HaPushResult.BadUrl
        }
        val body = haBody(batteryPercent, friendlyName)
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(haEndpoint(url, entitySlug))
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $haToken")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code = connection.responseCode
                connection.disconnect()
                when {
                    code in 200..299 -> HaPushResult.Success
                    code == 401 || code == 403 -> HaPushResult.AuthFailed
                    else -> HaPushResult.Unreachable
                }
            } catch (_: MalformedURLException) {
                HaPushResult.BadUrl
            } catch (_: Exception) {
                HaPushResult.Unreachable
            }
        }
    }

    // Verify credentials without writing any sensor data — GET /api/ returns 200
    // when the token is valid and HA is reachable, 401/403 on bad token.
    suspend fun test(haUrl: String, haToken: String): HaPushResult {
        val url = haUrl.trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return HaPushResult.BadUrl
        }
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL("$url/api/").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $haToken")
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                val code = connection.responseCode
                connection.disconnect()
                when {
                    code in 200..299 -> HaPushResult.Success
                    code == 401 || code == 403 -> HaPushResult.AuthFailed
                    else -> HaPushResult.Unreachable
                }
            } catch (_: MalformedURLException) {
                HaPushResult.BadUrl
            } catch (_: Exception) {
                HaPushResult.Unreachable
            }
        }
    }

    // DELETE the legacy sensor.onewheel_battery entity left by old builds.
    // HA's REST states API doesn't expose a delete button in the UI for entities
    // without a unique_id, so we have to do it programmatically.
    suspend fun clearLegacySensor(haUrl: String, haToken: String): HaPushResult {
        val url = haUrl.trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return HaPushResult.BadUrl
        }
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL("$url/api/states/sensor.onewheel_battery")
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "DELETE"
                connection.setRequestProperty("Authorization", "Bearer $haToken")
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                val code = connection.responseCode
                connection.disconnect()
                when {
                    code in 200..299 -> HaPushResult.Success
                    code == 401 || code == 403 -> HaPushResult.AuthFailed
                    else -> HaPushResult.Unreachable
                }
            } catch (_: MalformedURLException) {
                HaPushResult.BadUrl
            } catch (_: Exception) {
                HaPushResult.Unreachable
            }
        }
    }
}
