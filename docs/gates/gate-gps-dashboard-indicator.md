# Gate: GPS Lock Indicator on Dashboard

**Branch:** `codex/gps-dashboard-indicator`
**Base:** `main`
**Dependency:** must be dispatched AFTER `codex/gps-location-quality` (PR #61) is merged.
`RideForegroundService.kt` will conflict with that PR if rebased before merge.

---

## Context

The dashboard has no signal to the user that GPS is (or isn't) capturing their route. When GPS
is locked and recording clean points the user should see a green indicator; when searching or
accuracy is poor, orange. This is especially useful for diagnosing "why is my map empty" after a
ride.

`RideForegroundService.startLocationUpdates()` (after PR #61 merges) already filters
`loc.accuracy > 30f`. We extend it to also publish the lock state to `RideServiceRepository`,
then wire that through to the dashboard UI.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/service/RideServiceRepository.kt
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt
app/src/main/kotlin/com/zwheel/app/ui/DashboardState.kt
app/src/main/kotlin/com/zwheel/app/ui/DashboardViewModel.kt
app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt
```

Do NOT touch any other file.

---

## Implementation spec

### 1. `RideServiceRepository.kt` — expose `gpsLocked` flow

Add after `_tripDistanceMeters`:

```kotlin
private val _gpsLocked = MutableStateFlow(false)
val gpsLocked: StateFlow<Boolean> = _gpsLocked.asStateFlow()
```

Add update function after `updateTripDistance`:

```kotlin
internal fun updateGpsLock(locked: Boolean) {
    _gpsLocked.value = locked
}
```

### 2. `RideForegroundService.kt` — publish GPS lock state

In `startLocationUpdates()`, the callback currently reads (after PR #61):

```kotlin
override fun onLocationResult(result: LocationResult) {
    val loc = result.lastLocation ?: return
    if (loc.accuracy > 30f) return
    lastLatitude = loc.latitude
    lastLongitude = loc.longitude
    lastAltitude = loc.altitude
}
```

Change to publish lock state:

```kotlin
override fun onLocationResult(result: LocationResult) {
    val loc = result.lastLocation ?: return
    if (loc.accuracy > 30f) {
        rideServiceRepository.updateGpsLock(false)
        return
    }
    lastLatitude = loc.latitude
    lastLongitude = loc.longitude
    lastAltitude = loc.altitude
    rideServiceRepository.updateGpsLock(true)
}
```

Note: the fully-qualified class names for LocationResult may already be unqualified imports after
PR #61 — read the file and use whatever import style is already in use.

### 3. `DashboardState.kt` — add `gpsLocked` field

Add to `DashboardUiState`:

```kotlin
val gpsLocked: Boolean = false,
```

Add `gpsLocked: Boolean = false` parameter to `toDashboardUiState()` and pass it through:

```kotlin
fun BoardState.toDashboardUiState(
    prefs: UserPreferences,
    topSpeedMetersPerSecond: Double?,
    estimatedRangeKilometers: Double?,
    tripDistanceMeters: Double = 0.0,
    gpsLocked: Boolean = false,         // ← add this
): DashboardUiState {
```

In the returned `DashboardUiState(...)` add:

```kotlin
gpsLocked = gpsLocked,
```

Also update `mockDashboardState()` to include `gpsLocked = true` so previews look sensible.

### 4. `DashboardViewModel.kt` — add `gpsLocked` to combine

Change the 3-flow combine to 4-flow:

```kotlin
val uiState: StateFlow<DashboardUiState> = combine(
    rideServiceRepository.boardState,
    settingsRepository.preferences,
    rideServiceRepository.tripDistanceMeters,
    rideServiceRepository.gpsLocked,
) { boardState, prefs, tripDistanceMeters, gpsLocked ->
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
        gpsLocked = gpsLocked,
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
    initialValue = emptyDashboardState(),
)
```

### 5. `ZWheelAppScreen.kt` — GPS badge in TripStatsCard

`TripStatsCard` currently shows three `SmallStat` items in a row (DISTANCE, USED, REGEN).
Add a GPS lock row below that row:

```kotlin
@Composable
private fun TripStatsCard(state: DashboardUiState) {
    DashboardCard(color = Color(0xff00a7c8), contentColor = Color(0xff061016)) {
        Label("TRIP STATS")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallStat("DISTANCE", "%.2f MI".format(state.tripMiles))
            SmallStat("USED", "%.2f AH".format(state.tripAmpHours))
            SmallStat("REGEN", "%.2f AH".format(state.regenAmpHours))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val gpsColor = if (state.gpsLocked) Color(0xff007a3d) else Color(0xffb45309)
            Text(
                text = if (state.gpsLocked) "GPS LOCKED" else "GPS SEARCHING",
                color = gpsColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
        }
    }
}
```

Add `import androidx.compose.ui.Alignment` if not already present.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass cleanly.

Commit message: `feat(ui): GPS lock indicator on dashboard trip stats card`
