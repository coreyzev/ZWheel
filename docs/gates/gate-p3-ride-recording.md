# Gate: Phase 3c — Ride Recording (Auto Start/Stop + Data Points)

**Branch:** `codex/p3-ride-recording`
**Depends on:** `codex/p3-foreground-service` merged first (needs `RideForegroundService` and `RideRepository`)
**One concern:** Auto start/stop ride sessions and record `RideDataPoint` rows at 1 Hz while riding.

---

## Context

`RideForegroundService` (P3b) owns the BLE connection and emits `BoardState` via `RideServiceRepository`. This gate adds the ride lifecycle logic inside the service: when to start a session, how to record points, when to auto-end.

---

## Allowed files

```
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt  ← add ride logic
app/src/main/kotlin/com/zwheel/app/service/RideRecorder.kt           ← new: encapsulates recording state machine
```

---

## Ride lifecycle state machine

The `RideRecorder` class manages ride state inside the service:

```kotlin
internal class RideRecorder(
    private val repository: RideRepository,
    private val clock: Clock,
) {
    private var currentSessionId: String? = null
    private var speedAboveThresholdCounterSeconds: Int = 0
    private var speedBelowThresholdCounterSeconds: Int = 0

    suspend fun onTick(state: BoardState) { /* see logic below */ }
    suspend fun endCurrentSession() { /* close open session */ }
}
```

### Auto-start trigger
Speed > 1.5 mph (0.67 m/s) for at least 3 consecutive seconds:
- Increment `speedAboveThresholdCounterSeconds` each tick when speed > 0.67 m/s
- Reset to 0 when speed drops below threshold
- When counter reaches 3 and `currentSessionId == null`: start a new session

### Auto-end trigger
Speed < 0.5 m/s for 90 consecutive seconds:
- Increment `speedBelowThresholdCounterSeconds` each tick when speed < 0.5 m/s
- Reset to 0 when speed rises above threshold (but don't reset start counter)
- When counter reaches 90 and `currentSessionId != null`: end the session

### Data point recording (1 Hz, while session active)
Every tick (called once per second from the service), when `currentSessionId != null`, insert a `RideDataPoint`:

```kotlin
RideDataPoint(
    sessionId = currentSessionId,
    epochMillis = clock.nowEpochMillis(),
    speedMetersPerSecondCorrected = state.speedMetersPerSecondCorrected,
    speedMetersPerSecondRaw = state.speedMetersPerSecondRaw,
    rpm = state.rpm,
    batteryPercent = state.batteryPercent,
    latitude = null,   // GPS deferred — leave null for now; TODO(m3): wire fused location
    longitude = null,
    amps = state.amps,
    pitchDegrees = state.pitchDegrees,
    rollDegrees = state.rollDegrees,
    controllerTempCelsius = state.controllerTempCelsius,
    motorTempCelsius = state.motorTempCelsius,
)
```

### Session fields
On start:
```kotlin
RideSession(
    id = java.util.UUID.randomUUID().toString(),
    boardId = state.identity?.boardId ?: "unknown",
    startEpochMillis = clock.nowEpochMillis(),
)
```

On end: call `repository.updateSession(currentSession.copy(endEpochMillis = clock.nowEpochMillis()))`. Also update `maxSpeedMetersPerSecondCorrected` and `distanceMetersCorrected` (simple sum of speed * 1s intervals).

---

## Integration in `RideForegroundService`

Add a 1-second ticker in the service's `lifecycleScope`:

```kotlin
lifecycleScope.launch {
    val recorder = RideRecorder(rideRepository, clock)
    while (isActive) {
        delay(1_000L)
        recorder.onTick(rideServiceRepository.boardState.value)
    }
}
```

On `onDestroy`, call `recorder.endCurrentSession()` if a session is open.

---

## Constraints

- `RideRecorder` has no Android dependencies — it's a pure Kotlin class. `Clock` is injected from `core/ports`.
- GPS is intentionally deferred (latitude/longitude = null). Leave a `// TODO(m3): wire FusedLocationProviderClient` comment.
- Distance calculation: `distanceMetersCorrected += (correctedSpeedMs ?: 0.0) * 1.0` per tick while session is active.
- No UI changes in this gate. Session data is persisted; history screen is a separate gate.

---

## Verification

```bash
./gradlew :app:compileDebugKotlin
```

Must pass. Fix any errors before reporting done.
