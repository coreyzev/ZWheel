# Gate: Push Battery Percentage to Home Assistant

**Branch:** `codex/ha-battery-push`
**Base:** `main`

---

## Context

When a board is charging on a smart outlet, the user wants Home Assistant to cut power at 90%
(not 100%) to preserve battery longevity. The phone bridges BLE → Wi-Fi → HA.

**Data path:** Board → BLE → ZWheel app → Wi-Fi → HA REST API → smart outlet off

`batteryPercent` is already in `BoardState` flowing every second. No BLE changes needed.
INTERNET permission is already in the manifest. No new library dependency needed —
`java.net.HttpURLConnection` handles the POST.

HA treats a `POST /api/states/sensor.onewheel_battery` as a sensor immediately, no custom
component required.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/data/settings/UserPreferences.kt
app/src/main/kotlin/com/zwheel/app/data/settings/SettingsRepository.kt
app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsScreen.kt
app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsViewModel.kt
app/src/main/kotlin/com/zwheel/app/service/HomeAssistantPusher.kt   ← NEW FILE (create it)
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt
```

Do NOT touch any other file.

---

## Implementation spec

### 1. `UserPreferences.kt` — add HA fields

```kotlin
data class UserPreferences(
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val tireDiameterInches: Double = 11.5,
    val lastConnectedDeviceId: String? = null,
    val hasRequestedBatteryOptimization: Boolean = false,
    val haUrl: String = "",      // ← add: e.g. "http://homeassistant.local:8123"
    val haToken: String = "",    // ← add: HA long-lived access token
)
```

### 2. `SettingsRepository.kt` — persist HA fields

Add two keys in the companion object:
```kotlin
val HA_URL   = stringPreferencesKey("ha_url")
val HA_TOKEN = stringPreferencesKey("ha_token")
```

In `preferences` map:
```kotlin
haUrl   = preferences[HA_URL]   ?: "",
haToken = preferences[HA_TOKEN] ?: "",
```

Add two save functions:
```kotlin
suspend fun setHaUrl(url: String) {
    dataStore.edit { it[HA_URL] = url.trim() }
}

suspend fun setHaToken(token: String) {
    dataStore.edit { it[HA_TOKEN] = token.trim() }
}
```

### 3. `SettingsViewModel.kt` — add save methods

```kotlin
fun setHaUrl(url: String) {
    viewModelScope.launch { repo.setHaUrl(url) }
}

fun setHaToken(token: String) {
    viewModelScope.launch { repo.setHaToken(token) }
}
```

### 4. `SettingsScreen.kt` — add HA config section

In `SettingsScreen()`, add `onHaUrlChanged` and `onHaTokenChanged` callbacks wired to the
ViewModel, then pass them down to `SettingsContent`.

In `SettingsContent()`, after `TireDiameterControl(...)`, add a call to:
```kotlin
HomeAssistantSection(
    haUrl = preferences.haUrl,
    haToken = preferences.haToken,
    onUrlChanged = onHaUrlChanged,
    onTokenChanged = onHaTokenChanged,
)
```

Add the composable:
```kotlin
@Composable
private fun HomeAssistantSection(
    haUrl: String,
    haToken: String,
    onUrlChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("HOME ASSISTANT")
        androidx.compose.material3.OutlinedTextField(
            value = haUrl,
            onValueChange = onUrlChanged,
            label = { Text("Server URL") },
            placeholder = { Text("http://homeassistant.local:8123") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        androidx.compose.material3.OutlinedTextField(
            value = haToken,
            onValueChange = onTokenChanged,
            label = { Text("Long-lived access token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(
            text = "When configured, battery % is pushed to HA as sensor.onewheel_battery while connected.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Color(0xff777777),
        )
    }
}
```

Add `import androidx.compose.ui.Modifier` and `import androidx.compose.foundation.layout.fillMaxWidth`
if not already present (both likely are — check before adding).

### 5. `HomeAssistantPusher.kt` — create new file

```kotlin
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
                connection.responseCode  // trigger the request
                connection.disconnect()
            } catch (_: Exception) {
                // HA unreachable — fail silently, will retry on next change
            }
        }
    }
}
```

### 6. `RideForegroundService.kt` — launch HA sync coroutine

In `onCreate()`, after `startLocationUpdates()`, add:
```kotlin
startHomeAssistantSync()
```

Add the private function:
```kotlin
private fun startHomeAssistantSync() {
    lifecycleScope.launch {
        var lastPushedPercent: Int? = null
        kotlinx.coroutines.flow.combine(
            settingsRepository.preferences,
            rideServiceRepository.boardState,
        ) { prefs, boardState -> Pair(prefs, boardState) }
        .collect { (prefs, boardState) ->
            val url   = prefs.haUrl.takeIf   { it.isNotBlank() } ?: return@collect
            val token = prefs.haToken.takeIf { it.isNotBlank() } ?: return@collect
            val pct   = boardState.batteryPercent                 ?: return@collect
            if (pct == lastPushedPercent) return@collect
            lastPushedPercent = pct
            HomeAssistantPusher.push(url, token, pct)
        }
    }
}
```

If `combine` is already imported for flows (check existing imports), use the short form.
Otherwise use the fully-qualified `kotlinx.coroutines.flow.combine`.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass cleanly.

Commit message: `feat(ha): push battery percentage to Home Assistant while connected`
