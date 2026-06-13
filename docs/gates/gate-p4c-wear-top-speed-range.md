# Gate: Phase 4c — Wire TopSpeedTracker and RangeEstimator into Wear sync

**Branch:** `codex/p4c-wear-top-speed-range`
**Depends on:** P4a merged (WearDataLayerRepository exists in phone app)
**One concern:** Replace the `TODO(p4)` stubs in `WearDataLayerRepository.toWatchPayload()` with real values from `DefaultTopSpeedTracker` and `DefaultRangeEstimator`.

---

## Problem

`WearDataLayerRepository.toWatchPayload()` currently sends:
```kotlin
topSpeedMetersPerSecond = 0.0, // TODO(p4): wire TopSpeedTracker into service
estimatedRangeMeters = null,   // TODO(p4): wire RangeEstimator into service
```

The watch always shows "0 mph" top speed and "0.0 mi" range. Both calculators exist in `core/` already.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/service/RideServiceRepository.kt    ← add topSpeedMps StateFlow
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt    ← add DefaultTopSpeedTracker, update topSpeed each tick
app/src/main/kotlin/com/zwheel/app/wear/WearDataLayerRepository.kt     ← replace TODO stubs with real values
```

---

## Implementation spec

### 1. `RideServiceRepository.kt` — add `topSpeedMetersPerSecond`

Add alongside the existing `boardState`/`connectionState`/`isRiding` flows:

```kotlin
private val _topSpeedMetersPerSecond = MutableStateFlow(0.0)
val topSpeedMetersPerSecond: StateFlow<Double> = _topSpeedMetersPerSecond.asStateFlow()

internal fun updateTopSpeed(speedMps: Double) {
    _topSpeedMetersPerSecond.value = speedMps
}
```

### 2. `RideForegroundService.kt` — add DefaultTopSpeedTracker

Add a field:
```kotlin
private val topSpeedTracker = DefaultTopSpeedTracker()
```

In `mirrorBoardStateAndUpdateNotification()`, after calling `rideServiceRepository.updateBoardState(state)`, add:
```kotlin
topSpeedTracker.consume(state.speedMetersPerSecondCorrected)
rideServiceRepository.updateTopSpeed(topSpeedTracker.currentTripMaxMetersPerSecond ?: 0.0)
```

Add the import:
```kotlin
import com.zwheel.core.calc.DefaultTopSpeedTracker
```

### 3. `WearDataLayerRepository.kt` — replace TODO stubs

In `toWatchPayload()` (the private top-level function), replace:

```kotlin
topSpeedMetersPerSecond = 0.0, // TODO(p4): wire TopSpeedTracker into service
estimatedRangeMeters = null,   // TODO(p4): wire RangeEstimator into service
```

With:

```kotlin
topSpeedMetersPerSecond = rideServiceRepository.topSpeedMetersPerSecond.value,
estimatedRangeMeters = DefaultRangeEstimator.estimateKilometersRemaining(
    batteryPct = boardState.batteryPercent,
    boardType = boardState.identity?.type ?: BoardType.UNKNOWN,
)?.let { it * 1000.0 }, // km → meters
```

But wait — `toWatchPayload()` is currently a top-level private function, not a member of
`WearDataLayerRepository`. It doesn't have access to `rideServiceRepository`. You need to
either:

**Option A** (preferred — minimal change): Move `toWatchPayload()` inside `startSync()`'s
`combine` lambda by inlining the logic, or

**Option B**: Pass `topSpeedMps` and `estimatedRangeMeters` as parameters to `toWatchPayload()`.

Use Option B — it's cleaner. Update the function signature:

```kotlin
private fun toWatchPayload(
    boardState: BoardState,
    connectionState: ConnectionState,
    isRiding: Boolean,
    speedUnit: SpeedUnit,
    topSpeedMetersPerSecond: Double,
    estimatedRangeMeters: Double?,
): WatchPayload { ... }
```

Update the `combine` call in `startSync()` to include `rideServiceRepository.topSpeedMetersPerSecond`
as a 5th flow. Since `combine` with 5 flows needs the 5-argument overload:

```kotlin
combine(
    rideServiceRepository.boardState,
    rideServiceRepository.connectionState,
    rideServiceRepository.isRiding,
    settingsRepository.preferences,
    rideServiceRepository.topSpeedMetersPerSecond,
) { boardState, connectionState, isRiding, prefs, topSpeedMps ->
    val estimatedRangeMeters = DefaultRangeEstimator.estimateKilometersRemaining(
        batteryPct = boardState.batteryPercent,
        boardType = boardState.identity?.type ?: BoardType.UNKNOWN,
    )?.let { it * 1000.0 }
    toWatchPayload(boardState, connectionState, isRiding, prefs.speedUnit, topSpeedMps, estimatedRangeMeters)
}
```

Add these imports to `WearDataLayerRepository.kt`:
```kotlin
import com.zwheel.core.calc.DefaultRangeEstimator
import com.zwheel.core.model.BoardType
```

---

## Constraints

- `core/` is untouched.
- `DefaultTopSpeedTracker` is in `com.zwheel.core.calc` — check the import path.
- `DefaultRangeEstimator` is an `object` in `com.zwheel.core.calc` — call it directly, no injection needed.
- `combine` with 5 flows: Kotlin coroutines supports this directly with a 5-argument lambda overload.
- The `TODO(p4)` comments must be removed.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin --no-daemon
```

Must pass. Fix any errors.
