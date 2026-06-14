# Gate: Surface Live Trip Distance on Dashboard

**Branch:** `codex/live-trip-distance`
**Base:** `main`
**One concern:** `DashboardUiState.tripMiles` is hardcoded to 0.0 even though `RideRecorder`
accumulates `distanceMetersCorrected` on every tick. Expose it as a `StateFlow` and wire it
through to the dashboard.

---

## Context

`RideRecorder` already accumulates per-tick corrected distance:
```kotlin
distanceMetersCorrected += (correctedSpeed ?: 0.0) * 1.0
```
But `distanceMetersCorrected` is private and never published. `DashboardState.toDashboardUiState`
hardcodes `tripMiles = 0.0`. The UI card at `ZWheelAppScreen.kt:437` shows `"DISTANCE"` but
always reads 0.

Plan: add a `StateFlow<Double>` to `RideRecorder`, collect it in `RideForegroundService`, push to
`RideServiceRepository`, combine in `DashboardViewModel`, and use it in `DashboardState`.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/service/RideRecorder.kt          ← expose tripDistanceMeters flow
app/src/main/kotlin/com/zwheel/app/service/RideServiceRepository.kt ← add tripDistanceMeters field
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt ← collect and push to repo
app/src/main/kotlin/com/zwheel/app/ui/DashboardViewModel.kt         ← add tripDistance to combine
app/src/main/kotlin/com/zwheel/app/ui/DashboardState.kt             ← wire tripMiles from actual value
```

Do NOT touch any other file.

---

## Implementation spec

### 1. `RideRecorder.kt` — expose `tripDistanceMeters`

Add a `MutableStateFlow` field near the other private fields:
```kotlin
private val _tripDistanceMeters = MutableStateFlow(0.0)
val tripDistanceMeters: StateFlow<Double> = _tripDistanceMeters.asStateFlow()
```

Add the required import:
```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
```

In `onTick()`, after `distanceMetersCorrected += ...`:
```kotlin
_tripDistanceMeters.value = distanceMetersCorrected
```

In `startSession()`, reset:
```kotlin
_tripDistanceMeters.value = 0.0
```

In `endCurrentSession()`, reset after updating session:
```kotlin
_tripDistanceMeters.value = 0.0
```

### 2. `RideServiceRepository.kt` — add tripDistanceMeters

Add after `_isRiding`:
```kotlin
private val _tripDistanceMeters = MutableStateFlow(0.0)
val tripDistanceMeters: StateFlow<Double> = _tripDistanceMeters.asStateFlow()
```

Add an internal update function after `updateIsRiding`:
```kotlin
internal fun updateTripDistance(meters: Double) {
    _tripDistanceMeters.value = meters
}
```

### 3. `RideForegroundService.kt` — collect and push

In the service, find where the main tick coroutine is launched (the loop that calls
`recorder.onTick(state)` every second). After starting that coroutine, add:

```kotlin
scope.launch {
    recorder.tripDistanceMeters.collect { meters ->
        rideServiceRepository.updateTripDistance(meters)
    }
}
```

If `scope` is not directly available, use the coroutine scope already used in the service
(e.g., `lifecycleScope`). Read the file carefully to find the right scope.

### 4. `DashboardViewModel.kt` — add tripDistanceMeters to combine

Currently the `uiState` combines two flows: `rideServiceRepository.boardState` and
`settingsRepository.preferences`. Change to three-flow combine by adding
`rideServiceRepository.tripDistanceMeters`:

```kotlin
val uiState: StateFlow<DashboardUiState> = combine(
    rideServiceRepository.boardState,
    settingsRepository.preferences,
    rideServiceRepository.tripDistanceMeters,
) { boardState, prefs, tripDistanceMeters ->
    val correctedSpeed = boardState.speedMetersPerSecondCorrected
    topSpeedTracker.consume(correctedSpeed)
    val boardType = boardState.identity?.type ?: BoardType.XR
    val estimatedRange = rangeEstimator.estimateKilometersRemaining(
        batteryPct = boardState.batteryPercent,
        boardType = boardType,
        calibration = null,
    )
    boardState.toDashboardUiState(
        prefs = prefs,
        topSpeedMetersPerSecond = topSpeedTracker.currentTripMaxMetersPerSecond,
        estimatedRangeKilometers = estimatedRange,
        tripDistanceMeters = tripDistanceMeters,
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
    initialValue = emptyDashboardState(),
)
```

### 5. `DashboardState.kt` — wire `tripMiles` from actual distance

Add `tripDistanceMeters: Double = 0.0` as a parameter to `toDashboardUiState()`:

```kotlin
fun BoardState.toDashboardUiState(
    prefs: UserPreferences,
    topSpeedMetersPerSecond: Double?,
    estimatedRangeKilometers: Double?,
    tripDistanceMeters: Double = 0.0,   // ← add this
): DashboardUiState {
```

Change `tripMiles = 0.0` to use the actual distance, respecting the speed unit preference:

```kotlin
tripMiles = when (prefs.speedUnit) {
    SpeedUnit.MPH -> UnitConversions.metersToMiles(tripDistanceMeters)
    SpeedUnit.KPH -> UnitConversions.metersToKilometers(tripDistanceMeters)
},
```

Check whether `UnitConversions.metersToMiles()` and `metersToKilometers()` exist — if they do
not, use the inverse of the existing conversions or compute directly:
- miles = meters / 1609.344
- km = meters / 1000.0

Also update `rangeUnitLabel` logic if needed — the label already handles MPH/KPH, so `tripMiles`
now holds either miles or km depending on unit setting. The existing `rangeUnitLabel` field
already has the right label.

Add import if needed:
```kotlin
import com.zwheel.core.calc.UnitConversions
```
(it is likely already imported — check before adding).

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass cleanly. No test changes needed — the `RideRecorderTest` tests `startRiding` helper
which already exercises the field indirectly.

Commit message: `feat(ui): surface live trip distance on dashboard from RideRecorder`
