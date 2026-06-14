# Gate: Phase 4b — Wear Data Layer (Watch Side, revised)

**Branch:** `codex/p4b-wear-data-layer-watch`
**Depends on:** Phase 4a merged to main (phone pushes `/zwheel/state` DataItems)
**One concern:** Wire the existing watch UI to real live data from the Wear OS Data Layer.

**Architecture spec:** `docs/adr/ADR-007-wear-os-data-layer.md`

---

## Context — existing wear module

The wear module already has a polished Compose UI. Do NOT rewrite it from scratch.
Read these files carefully before touching anything:

```
wear/src/main/kotlin/com/zwheel/wear/MainActivity.kt          ← ComponentActivity, sets content to ZWheelWearScreen()
wear/src/main/kotlin/com/zwheel/wear/ZWheelWearApp.kt         ← @HiltAndroidApp Application
wear/src/main/kotlin/com/zwheel/wear/ui/ZWheelWearScreen.kt   ← full UI with WearDashboardUiState (currently mock)
wear/src/main/AndroidManifest.xml                             ← has MainActivity declared
wear/build.gradle.kts                                          ← missing play-services-wearable dep
```

---

## Allowed files (touch ONLY these)

```
wear/src/main/kotlin/com/zwheel/wear/WearDataLayerRepository.kt   ← new
wear/src/main/kotlin/com/zwheel/wear/MainViewModel.kt             ← new
wear/src/main/kotlin/com/zwheel/wear/ui/ZWheelWearScreen.kt       ← update to accept ViewModel
wear/src/main/kotlin/com/zwheel/wear/MainActivity.kt              ← add onResume/onPause registration
wear/build.gradle.kts                                              ← add play-services-wearable dep
gradle/libs.versions.toml                                          ← only if play-services-wearable entry is missing
```

---

## Implementation spec

### 1. `WearDataLayerRepository.kt` (new)

```kotlin
package com.zwheel.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.zwheel.core.model.ConnectionState
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.WatchPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class WearDataLayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DataClient.OnDataChangedListener {

    private val _payload = MutableStateFlow<WatchPayload?>(null)
    val payload: StateFlow<WatchPayload?> = _payload.asStateFlow()

    private val dataClient: DataClient = Wearable.getDataClient(context)

    fun register() { dataClient.addListener(this) }
    fun unregister() { dataClient.removeListener(this) }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/zwheel/state"
            ) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val speedRaw = dataMap.getFloat("speed_mps_corrected")
                val batteryRaw = dataMap.getInt("battery_pct")
                val rangeRaw = dataMap.getFloat("estimated_range_m")
                val connState = try {
                    ConnectionState.valueOf(
                        dataMap.getString("connection_state") ?: "DISCONNECTED"
                    )
                } catch (e: IllegalArgumentException) {
                    ConnectionState.DISCONNECTED
                }
                _payload.value = WatchPayload(
                    speedMetersPerSecondCorrected = if (speedRaw < 0) null else speedRaw.toDouble(),
                    topSpeedMetersPerSecond = dataMap.getFloat("top_speed_mps").toDouble(),
                    batteryPercent = if (batteryRaw < 0) null else batteryRaw,
                    estimatedRangeMeters = if (rangeRaw < 0) null else rangeRaw.toDouble(),
                    speedUnit = try {
                        SpeedUnit.valueOf(dataMap.getString("speed_unit") ?: "MPH")
                    } catch (e: Exception) {
                        SpeedUnit.MPH
                    },
                    isRiding = dataMap.getBoolean("is_riding"),
                    connectionState = connState,
                )
            }
        }
    }
}
```

### 2. `MainViewModel.kt` (new)

```kotlin
package com.zwheel.wear

import androidx.lifecycle.ViewModel
import com.zwheel.core.model.WatchPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class MainViewModel @Inject constructor(
    val repository: WearDataLayerRepository,
) : ViewModel() {
    val payload: StateFlow<WatchPayload?> = repository.payload
}
```

Note: `register()` / `unregister()` are called by `MainActivity` in `onResume`/`onPause`,
not here. The ViewModel just exposes the flow.

### 3. `ZWheelWearScreen.kt` — update to accept ViewModel

The existing `ZWheelWearScreen()` composable currently creates mock data with
`mockWearDashboardStateFlow()`. Replace it to accept a `MainViewModel` and use real data.

The existing private `WearDashboardUiState` data class and all the rendering composables
(`WearDashboard`, `BatteryRing`, `WearStat`) should stay unchanged. Only change the top-level
`ZWheelWearScreen` function.

```kotlin
@Composable
fun ZWheelWearScreen(viewModel: MainViewModel = hiltViewModel()) {
    val payload by viewModel.payload.collectAsStateWithLifecycle()
    val state = payload.toUiState()
    WearDashboard(state = state)
}
```

Add a private extension function `WatchPayload?.toUiState(): WearDashboardUiState`:

```kotlin
private fun WatchPayload?.toUiState(): WearDashboardUiState {
    if (this == null) {
        return WearDashboardUiState(
            speedMph = 0.0,
            topSpeedMph = 0.0,
            batteryPercent = 0,
            rangeMiles = 0.0,
            connectionLabel = "DISCONNECTED",
        )
    }
    val speedMph = (speedMetersPerSecondCorrected ?: 0.0) * 2.23694
    val topMph = topSpeedMetersPerSecond * 2.23694
    val rangeMiles = (estimatedRangeMeters ?: 0.0) / 1609.344
    return WearDashboardUiState(
        speedMph = speedMph,
        topSpeedMph = topMph,
        batteryPercent = batteryPercent ?: 0,
        rangeMiles = rangeMiles,
        connectionLabel = connectionState.name,
    )
}
```

Remove the `mockWearDashboardStateFlow()` function and `remember { }` block — they're no
longer needed.

You need these new imports:
```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwheel.core.model.WatchPayload
```

### 4. `MainActivity.kt` — lifecycle-aware registration

Add `onResume` and `onPause` overrides that delegate to the repository:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var wearDataLayerRepository: WearDataLayerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZWheelWearScreen() }
    }

    override fun onResume() {
        super.onResume()
        wearDataLayerRepository.register()
    }

    override fun onPause() {
        super.onPause()
        wearDataLayerRepository.unregister()
    }
}
```

### 5. `wear/build.gradle.kts` — add deps

Add to the `dependencies` block:
```kotlin
implementation(libs.play.services.wearable)
implementation(libs.androidx.hilt.navigation.compose)
implementation(libs.androidx.lifecycle.runtime.compose)
```

Check `gradle/libs.versions.toml` — if `play-services-wearable` lib alias is already
declared (it was added in P4a), do not add it again. If missing, add:
```
playServicesWearable = "18.2.0"          # under [versions]
play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }   # under [libraries]
```

`androidx.hilt.navigation.compose` and `androidx.lifecycle.runtime.compose` are already
declared from the phone module — just reference them in wear's build.gradle.kts.

---

## Constraints

- `core/` must be untouched.
- Wear Compose uses `androidx.wear.compose.material` (already imported) — do NOT switch to Material3.
- The Hilt entry-point annotation `@AndroidEntryPoint` is already on `MainActivity` — verify before adding it.
- No `WearableListenerService` background service — activity-level listener is sufficient for now.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :wear:compileDebugKotlin :wear:kspDebugKotlin --no-daemon
```

Must pass. Fix any errors before reporting done.
