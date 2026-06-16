# ADR-007: Wear OS Data Layer and Watch Payload

Status: Accepted

## Context

The Galaxy Watch 7 Classic (Wear OS 5) is the target watch. The app needs to push
live ride telemetry to the watch face and a companion tile. The watch cannot connect
to the Onewheel BLE characteristic directly — the phone is the only BLE host. The
Wear Data Layer API (`com.google.android.gms:play-services-wearable`) is the standard
IPC mechanism between phone and watch for Wear OS.

`WatchPayload` is already defined in `core/model/BoardModels.kt`.

## Decision

### 1. Sync mechanism

Use `DataClient.putDataItem()` from the Wear Data Layer (not `MessageClient` and not
`ChannelClient`). `DataItem` fits because:
- The data is small (< 100 KB per update).
- Wear OS deduplicates consecutive identical payloads automatically (saves battery).
- The watch app can read the latest value even if it was offline when the update arrived.

### 2. Data path

```
ConnectionManager (boardState StateFlow)
  → WearDataLayerRepository (phone, app module)  [reads ConnectionManager + RideServiceRepository]
    → DataClient.putDataItem("/zwheel/state")
      → Wear OS Data Layer
        → WearDataLayerRepository (watch, wear module)
          → StateFlow<WatchPayload>
            → MainActivity / ZWheelWearScreen (renders)
```

### 3. `/zwheel/state` payload encoding

Encode `WatchPayload` as a `DataMap` (key → primitive):
- `"speed_mps_corrected"` → `Float` (null → `-1f` sentinel)
- `"top_speed_mps"` → `Float`
- `"battery_pct"` → `Int` (null → `-1` sentinel)
- `"estimated_range_m"` → `Float` (null → `-1f` sentinel)
- `"speed_unit"` → `String` ("MPH" | "KPH")
- `"is_riding"` → `Boolean`
- `"connection_state"` → `String` (the `ConnectionState.name` value)

Do NOT encode cell voltages or full `BoardState` — the watch only needs the fields in
`WatchPayload`. The watch face renders: speed, top speed, battery, range, ride state.

### 4. Update cadence

- Push on every new `boardState` emission from `ConnectionManager` (which is already
  bounded by BLE notification rate, typically 1–2 Hz).
- Deduplicate: compare the new `WatchPayload` to the last-sent payload; skip `putDataItem`
  if unchanged (avoids spurious wakeups on the watch).
- On disconnect: push a final payload with `isRiding = false`, `connectionState = "DISCONNECTED"`.

### 5. Phone-side implementation

New file: `app/src/main/kotlin/com/zwheel/app/wear/WearDataLayerRepository.kt`

```kotlin
@Singleton
class WearDataLayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val rideServiceRepository: RideServiceRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private var lastSentPayload: WatchPayload? = null

    fun startSync(scope: CoroutineScope) {
        scope.launch {
            combine(
                connectionManager.boardState,
                connectionManager.connectionState,
                settingsRepository.preferences,
            ) { boardState, connectionState, prefs ->
                boardState.toWatchPayload(connectionState, prefs)
            }.collect { payload ->
                if (payload != lastSentPayload) {
                    putPayload(payload)
                    lastSentPayload = payload
                }
            }
        }
    }

    private fun putPayload(payload: WatchPayload) {
        val dataMap = PutDataMapRequest.create("/zwheel/state").apply {
            dataMap.putFloat("speed_mps_corrected", payload.speedMetersPerSecondCorrected?.toFloat() ?: -1f)
            dataMap.putFloat("top_speed_mps", payload.topSpeedMetersPerSecond.toFloat())
            dataMap.putInt("battery_pct", payload.batteryPercent ?: -1)
            dataMap.putFloat("estimated_range_m", payload.estimatedRangeMeters?.toFloat() ?: -1f)
            dataMap.putString("speed_unit", payload.speedUnit.name)
            dataMap.putBoolean("is_riding", payload.isRiding)
            dataMap.putString("connection_state", payload.connectionState.name)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataMap)
    }
}
```

Call `startSync(lifecycleScope)` from `RideForegroundService.onCreate()`.

### 6. Watch-side implementation

New file: `wear/src/main/kotlin/com/zwheel/wear/WearDataLayerRepository.kt`

Implement `WearableListenerService` subclass (or use `DataClient.addListener()`) to
receive updates, parse the `DataMap`, and emit to a `StateFlow<WatchPayload?>`. Watch UI
(Compose for Wear OS) collects this state flow.

### 7. Dependency

Add to `app/build.gradle.kts` and `wear/build.gradle.kts`:
```kotlin
implementation("com.google.android.gms:play-services-wearable:18.2.0")
```

Add to `wear/build.gradle.kts`:
```kotlin
implementation("androidx.wear:wear:1.3.0")
implementation("androidx.wear.compose:compose-material:1.4.0")
```

(Check Compose BOM compatibility before pinning versions.)

### 8. Offline fallback

If the watch is out of range, `DataClient.putDataItem()` queues the update locally; Wear
OS delivers it when reconnected. No special retry logic needed.

## Consequences

- Phone app gains a new Hilt singleton `WearDataLayerRepository`. It is started by
  `RideForegroundService` so sync is tied to the BLE connection lifetime.
- Watch app needs a `WearableListenerService` in its manifest (`android:exported="true"`
  with the `com.google.android.gms.wearable.DATA_CHANGED` intent filter).
- The data path `/zwheel/state` must match exactly between phone and watch.
- `play-services-wearable` introduces a Google Play Services dependency; this is
  acceptable for the Wear OS use case.
- `WatchPayload.isRiding` should be `true` when `RideRecorder` has an open session.
  Gate P4 wires this: `RideServiceRepository` gains an `isRiding: StateFlow<Boolean>`
  updated by `RideRecorder` when a session starts/ends.
- Actual rendering is currently done via `MainActivity` and `ZWheelWearScreen` (Compose), 
  not `WatchFaceService` / `TileService` (those are not yet implemented).

## Gates

- **P4a:** `WearDataLayerRepository` (phone) + `startSync()` in `RideForegroundService`
- **P4b:** `WearDataLayerRepository` (watch) + `WatchFaceService` Compose rendering
- **P4c:** Watch tile showing speed/battery

## Delivery

The Wear OS app is a standalone application (`standalone=true` in `AndroidManifest.xml`).
It is distributed independently of the phone app:
- **Production:** Published to the Google Play Store.
- **Development:** Sideloaded via `adb`.

Delivery via the legacy `wearApp()` micro-APK embedding is NOT used, as it is
deprecated and unsupported on Wear OS 3+ (e.g., Galaxy Watch 7).
