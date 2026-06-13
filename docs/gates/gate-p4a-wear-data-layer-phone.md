# Gate: Phase 4a — Wear Data Layer (Phone Side)

**Branch:** `codex/p4a-wear-data-layer`
**Depends on:** Phase 3 merged to main (`RideForegroundService` available)
**One concern:** Push live `WatchPayload` to the Wear OS Data Layer from the phone.

**Architecture spec in:** `docs/adr/ADR-007-wear-os-data-layer.md` — read it.

---

## Allowed files

```
app/src/main/kotlin/com/zwheel/app/wear/WearDataLayerRepository.kt   ← new
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt  ← add startSync call
app/src/main/kotlin/com/zwheel/app/service/RideServiceRepository.kt  ← add isRiding StateFlow
app/build.gradle.kts                                                  ← add play-services-wearable
gradle/libs.versions.toml                                             ← add version + lib entry
```

---

## Implementation spec

### 1. `gradle/libs.versions.toml`

Add:
```toml
playServicesWearable = "18.2.0"
```

Add to `[libraries]`:
```toml
play-services-wearable = { module = "com.google.android.gms:play-services-wearable", version.ref = "playServicesWearable" }
```

### 2. `app/build.gradle.kts`

Add:
```kotlin
implementation(libs.play.services.wearable)
```

### 3. `RideServiceRepository.kt`

Add `isRiding` field so the watch knows whether a session is active:
```kotlin
private val _isRiding = MutableStateFlow(false)
val isRiding: StateFlow<Boolean> = _isRiding.asStateFlow()

internal fun updateIsRiding(riding: Boolean) { _isRiding.value = riding }
```

`RideRecorder` should call `rideServiceRepository.updateIsRiding(true/false)` when it
starts/ends a session — but `RideRecorder` does not have access to the repository.
**Instead:** have `RideForegroundService` call `rideServiceRepository.updateIsRiding()`
by observing the `rideRecorder`'s session state via a callback:

Add a `var onSessionChanged: ((Boolean) -> Unit)? = null` callback to `RideRecorder`,
called in `startSession()` and `endCurrentSession()`. The service sets this callback and
forwards to `rideServiceRepository.updateIsRiding()`.

### 4. `WearDataLayerRepository.kt`

Implement exactly as specified in ADR-007 §5. Key points:
- `@Singleton`, `@Inject constructor`
- `startSync(scope: CoroutineScope)` combines `boardState`, `connectionState`, `isRiding`,
  `preferences` into a `WatchPayload` and calls `putPayload` on changes
- Deduplicate: skip if `payload == lastSentPayload`
- `putPayload` builds a `PutDataMapRequest` for `/zwheel/state` with `setUrgent()` and
  calls `dataClient.putDataItem()`

`WatchPayload` requires `isRiding: Boolean`. Read the current `isRiding` from
`rideServiceRepository.isRiding.value` in the combine.

The helper extension `BoardState.toWatchPayload()` should be a private function inside
`WearDataLayerRepository.kt`:
```kotlin
private fun toWatchPayload(
    boardState: BoardState,
    connectionState: ConnectionState,
    prefs: UserPreferences,
    isRiding: Boolean,
): WatchPayload = WatchPayload(
    speedMetersPerSecondCorrected = boardState.speedMetersPerSecondCorrected,
    topSpeedMetersPerSecond = 0.0, // TODO(p4): wire TopSpeedTracker into service
    batteryPercent = boardState.batteryPercent,
    estimatedRangeMeters = null,   // TODO(p4): wire RangeEstimator into service
    speedUnit = prefs.speedUnit,
    isRiding = isRiding,
    connectionState = com.zwheel.core.model.ConnectionState.valueOf(connectionState.name.uppercase()),
)
```

Note: `WatchPayload.connectionState` uses the **core** `ConnectionState` enum (not the
app-layer one). Map by name: the core enum values are all-caps, the app-layer ones are
PascalCase — use `.name.uppercase()` to convert.

If the core `ConnectionState` doesn't have a matching value, map to `DISCONNECTED`.

### 5. `RideForegroundService.kt`

After injecting `WearDataLayerRepository`:
```kotlin
@Inject lateinit var wearDataLayerRepository: WearDataLayerRepository
```

In `onCreate()`, after `startRideRecorderTicker()`:
```kotlin
wearDataLayerRepository.startSync(lifecycleScope)
```

Also wire the `rideRecorder.onSessionChanged` callback:
```kotlin
recorder.onSessionChanged = { isRiding ->
    rideServiceRepository.updateIsRiding(isRiding)
}
```
(Do this inside `startRideRecorderTicker()` after creating the recorder.)

---

## Constraints

- `core/` untouched.
- `WearDataLayerRepository` must be `@Singleton` so only one sync loop runs.
- Do NOT wrap `dataClient.putDataItem()` in a try/catch — let it fail fast during
  development; error handling can be added in P4b.
- The `play-services-wearable` dependency is allowed (ADR-007 accepted).

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin :app:kspDebugKotlin
```

Both must pass. Fix any errors before reporting done.
