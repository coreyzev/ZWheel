package com.zwheel.app.ui.ble

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BleDebugLogExporter(
    private val context: Context,
) {
    // Keep this debug uploader available until release hardening. It is the fast path for
    // Corey to send M2/M3 BLE fixtures to the receiver without rebuilding tooling.
    val uploadSupported: Boolean = true

    suspend fun share(jsonLines: String): String = withContext(Dispatchers.IO) {
        val file = writeCacheFile(jsonLines)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/x-ndjson")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share BLE debug log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Share sheet opened: ${file.name}"
    }

    val receiverLabel: String = DEFAULT_RECEIVER_URL

    suspend fun pair(pairingPassword: String): String = withContext(Dispatchers.IO) {
        val body = "{\"pairingPassword\":\"${pairingPassword.jsonEscaped()}\"}"
        val response = post(
            url = "$DEFAULT_RECEIVER_URL/pair",
            contentType = "application/json",
            body = body.encodeToByteArray(),
        )
        val token = response.body.extractJsonString("uploadToken")
        prefs().edit()
            .putString(KEY_SERVER_URL, DEFAULT_RECEIVER_URL)
            .putString(KEY_UPLOAD_TOKEN, token)
            .apply()
        "Paired with ${DEFAULT_RECEIVER_URL.hostLabel()}"
    }

    suspend fun upload(jsonLines: String): String = withContext(Dispatchers.IO) {
        val serverUrl = requireNotNull(prefs().getString(KEY_SERVER_URL, null)) {
            "Pair upload first"
        }
        val token = requireNotNull(prefs().getString(KEY_UPLOAD_TOKEN, null)) {
            "Pair upload first"
        }
        val fileName = "zwheel-ble-${randomHex()}.jsonl"
        val body = ensureTrailingNewline(jsonLines).encodeToByteArray()
        val response = post(
            url = "${serverUrl.trimEnd('/')}/upload?filename=$fileName",
            contentType = "application/x-ndjson",
            body = body,
            bearerToken = token,
        )
        val uploadId = response.body.extractJsonString("uploadId")
        "Uploaded $uploadId"
    }

    private fun writeCacheFile(jsonLines: String): File {
        val dir = File(context.cacheDir, "ble-debug").also(File::mkdirs)
        val file = File(dir, "zwheel-ble-${randomHex()}.jsonl")
        file.writeText(ensureTrailingNewline(jsonLines))
        return file
    }

    private fun prefs() = context.getSharedPreferences("ble_debug_upload", Context.MODE_PRIVATE)

    private fun post(
        url: String,
        contentType: String,
        body: ByteArray,
        bearerToken: String? = null,
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setFixedLengthStreamingMode(body.size)  // stream body; lets server respond early with 413 cleanly
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json")
            bearerToken?.let { token -> setRequestProperty("Authorization", "Bearer $token") }
        }
        return connection.use {
            outputStream.use { stream -> stream.write(body) }
            val status = responseCode
            val responseBody = if (status in 200..299) {
                inputStream.bufferedReader().use { reader -> reader.readText() }
            } else {
                errorStream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            }
            if (status !in 200..299) {
                error("HTTP $status ${responseBody.take(120)}")
            }
            HttpResponse(responseBody)
        }
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_UPLOAD_TOKEN = "upload_token"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val DEFAULT_RECEIVER_URL = "http://116.203.200.55:8765"
    }
}

private data class HttpResponse(val body: String)

private fun HttpURLConnection.use(block: HttpURLConnection.() -> HttpResponse): HttpResponse =
    try {
        block()
    } finally {
        disconnect()
    }

private fun ensureTrailingNewline(value: String): String =
    if (value.endsWith("\n")) value else "$value\n"

private fun randomHex(): String {
    val bytes = ByteArray(8)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString(separator = "") { byte ->
        byte.toUByte().toString(radix = 16).padStart(2, '0')
    }
}

private fun String.extractJsonString(name: String): String {
    val pattern = """"$name"\s*:\s*"([^"]+)"""".toRegex()
    return requireNotNull(pattern.find(this)?.groupValues?.get(1)) {
        "Missing $name in response"
    }
}

private fun String.jsonEscaped(): String = buildString {
    this@jsonEscaped.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}

private fun String.hostLabel(): String =
    runCatching { URL(this).host }.getOrDefault(this)
