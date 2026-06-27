# Gate C — Issue #156: AudioAlertsService, PhoneAudioPlayer, WearAlertDispatcher

## Depends on
Gates A and B must be merged first.

**Already exists in `core/alerts/`:**
- `AlertType` (SPEED, HEADROOM)
- `AlertOutput` (AUTO, WATCH, PHONE)
- `AlertConfig` (data class with enabled, type, threshold, output, hysteresis, cooldownMs)
- `AlertMonitor` (stateful state machine with `evaluate(config, speedMps, headroom, nowMs)` and `reset()`)

**Already exists in `app/data/settings/UserPreferences`:**
```kotlin
val audioAlertsEnabled: Boolean
val audioAlertType: AlertType
val audioAlertThresholdMph: Int   // stored in mph
val audioAlertThresholdHeadroom: Int
val audioAlertOutput: AlertOutput
```

**Reference: `app/service/HomeAssistantSync.kt`** — the pattern this service follows:
- Constructor-injected with `SettingsRepository` + `StateFlow<BoardState>`
- Exposes a `start(scope: CoroutineScope)` method
- Started from `RideForegroundService.onCreate()`

## Files to create

### `app/src/main/kotlin/com/zwheel/app/service/PhoneAudioPlayer.kt`
```kotlin
package com.zwheel.app.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.zwheel.core.alerts.AlertType

internal class PhoneAudioPlayer(private val context: Context) {
    fun play(type: AlertType) {
        val toneType = when (type) {
            AlertType.SPEED -> ToneGenerator.TONE_PROP_BEEP
            AlertType.HEADROOM -> ToneGenerator.TONE_PROP_BEEP2
        }
        runCatching {
            val gen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            gen.startTone(toneType, 500)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { gen.release() } }, 600)
        }.onFailure { e ->
            android.util.Log.w("PhoneAudioPlayer", "Failed to play alert tone: ${e.message}")
        }
    }
}
```

### `app/src/main/kotlin/com/zwheel/app/service/WearAlertDispatcher.kt`
```kotlin
package com.zwheel.app.service

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.zwheel.core.alerts.AlertType

internal const val WEAR_ALERT_PATH = "/zwheel/alert"

internal class WearAlertDispatcher(private val context: Context) {
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    fun fire(type: AlertType) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, WEAR_ALERT_PATH, type.name.toByteArray())
            }
        }
    }
}
```

### `app/src/main/kotlin/com/zwheel/app/service/AudioAlertsService.kt`
```kotlin
package com.zwheel.app.service

import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.core.alerts.AlertConfig
import com.zwheel.core.alerts.AlertMonitor
import com.zwheel.core.alerts.AlertOutput
import com.zwheel.core.model.BoardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val MPH_TO_MPS = 0.44704

internal class AudioAlertsService(
    private val settingsRepository: SettingsRepository,
    private val boardStateFlow: StateFlow<BoardState>,
    private val wearDispatcher: WearAlertDispatcher,
    private val phonePlayer: PhoneAudioPlayer,
) {
    private val monitor = AlertMonitor()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(settingsRepository.preferences, boardStateFlow) { prefs, state ->
                Pair(prefs, state)
            }.collect { (prefs, state) ->
                if (!prefs.audioAlertsEnabled) {
                    monitor.reset()
                    return@collect
                }
                val config = AlertConfig(
                    enabled = true,
                    type = prefs.audioAlertType,
                    threshold = when (prefs.audioAlertType) {
                        com.zwheel.core.alerts.AlertType.SPEED ->
                            prefs.audioAlertThresholdMph * MPH_TO_MPS
                        com.zwheel.core.alerts.AlertType.HEADROOM ->
                            prefs.audioAlertThresholdHeadroom.toDouble()
                    },
                    output = prefs.audioAlertOutput,
                    hysteresis = when (prefs.audioAlertType) {
                        com.zwheel.core.alerts.AlertType.SPEED -> 0.894  // ~2 mph
                        com.zwheel.core.alerts.AlertType.HEADROOM -> 2.0
                    },
                )
                val nowMs = System.currentTimeMillis()
                if (monitor.evaluate(config, state.speedMetersPerSecondCorrected, state.safetyHeadroom, nowMs)) {
                    dispatch(config)
                }
            }
        }
    }

    private fun dispatch(config: AlertConfig) {
        when (config.output) {
            AlertOutput.PHONE -> phonePlayer.play(config.type)
            AlertOutput.WATCH -> wearDispatcher.fire(config.type)
            AlertOutput.AUTO -> {
                // Try watch; fall back to phone if no nodes connected.
                com.google.android.gms.wearable.Wearable
                    // We can't easily call getNodeClient here without context.
                    // Delegate to wearDispatcher which handles node lookup, but
                    // we also need the phone fallback. Use a flag approach:
                    // wearDispatcher.fireWithFallback handles this.
                    .let { wearDispatcher.fireAutoWithFallback(config.type, phonePlayer) }
            }
        }
    }
}
```

Wait — the AUTO path needs to check connected nodes. Refactor `WearAlertDispatcher` to handle this cleanly. Replace `WearAlertDispatcher` contents with:

```kotlin
package com.zwheel.app.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.zwheel.core.alerts.AlertType

internal const val WEAR_ALERT_PATH = "/zwheel/alert"

internal class WearAlertDispatcher(private val context: Context) {
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    /** Send alert to all connected watch nodes. Returns immediately; delivery is async. */
    fun fire(type: AlertType) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, WEAR_ALERT_PATH, type.name.toByteArray())
            }
        }.addOnFailureListener { e ->
            Log.w("WearAlertDispatcher", "Could not query nodes: ${e.message}")
        }
    }

    /**
     * AUTO mode: send to watch if any node is reachable, otherwise play on phone.
     */
    fun fireAutoWithFallback(type: AlertType, fallback: PhoneAudioPlayer) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isNotEmpty()) {
                    nodes.forEach { node ->
                        messageClient.sendMessage(node.id, WEAR_ALERT_PATH, type.name.toByteArray())
                    }
                } else {
                    fallback.play(type)
                }
            }
            .addOnFailureListener {
                fallback.play(type)
            }
    }
}
```

And simplify `AudioAlertsService.dispatch()`:
```kotlin
private fun dispatch(config: AlertConfig) {
    when (config.output) {
        AlertOutput.PHONE -> phonePlayer.play(config.type)
        AlertOutput.WATCH -> wearDispatcher.fire(config.type)
        AlertOutput.AUTO -> wearDispatcher.fireAutoWithFallback(config.type, phonePlayer)
    }
}
```

Write `AudioAlertsService.kt` with this clean dispatch, no dangling `.let { }` block.

## File to modify

### `app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt`

In `onCreate()`, add a line after the `HomeAssistantSync(...)` line:

```kotlin
AudioAlertsService(
    settingsRepository = settingsRepository,
    boardStateFlow = connectionManager.boardState,
    wearDispatcher = WearAlertDispatcher(this),
    phonePlayer = PhoneAudioPlayer(this),
).start(lifecycleScope)
```

The existing `onCreate()` body (do not change anything else):
```kotlin
override fun onCreate() {
    super.onCreate()
    notifications.createChannel()
    lifecycleScope.launch { settingsRepository.migrateHaTokenIfNeeded() }
    trackSpeedUnitPreference()
    observeBoardForNotificationAndWakelock()
    startRideRecorderTicker()
    observeUnexpectedDisconnect()
    wearDataLayerRepository.startSync(lifecycleScope)
    locationTracker.start()
    HomeAssistantSync(settingsRepository, connectionManager.boardState).start(lifecycleScope)
    // ADD THIS LINE:
    AudioAlertsService(
        settingsRepository = settingsRepository,
        boardStateFlow = connectionManager.boardState,
        wearDispatcher = WearAlertDispatcher(this),
        phonePlayer = PhoneAudioPlayer(this),
    ).start(lifecycleScope)
}
```

## Compile and verify
Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin`
Must compile clean. Fix any errors before committing.

## Commit
`feat(alerts): AudioAlertsService, PhoneAudioPlayer, WearAlertDispatcher — issue #156`

Do not modify any other files.
