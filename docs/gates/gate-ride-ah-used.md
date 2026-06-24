# Gate: Persist Ah Used to Ride History

## Problem

`RideRecorder.endCurrentSession()` never sets `wattHoursUsed` on the session.
`BoardState.tripAmpHours` is received correctly (visible on the dashboard as
"DRAW · 0.07 Ah") but is never written to the DB, so the ride detail screen
always shows "Ah USED  --".

## Fix

`RideRecorder` already receives a `BoardState` on every tick. Track the last
non-null `tripAmpHours` value and store it at session close using the same
nominal 63 V conversion that `RideDetailViewModel` already uses when dividing
back out (`watts / 63.0 → Ah`).

No DB schema change is required — `RideSession.wattHoursUsed: Double?` exists
and is already wired through `RideRepository`, `RideEntities`, and the DAO.

---

## File to modify

### `app/src/main/kotlin/com/zwheel/app/service/RideRecorder.kt`

**Add field** after `private var distanceMetersCorrected`:

```kotlin
private var lastTripAmpHours: Double? = null
```

**In `onTick()`**, after updating `distanceMetersCorrected` (around line 54),
add:

```kotlin
state.tripAmpHours?.let { lastTripAmpHours = it }
```

**In `endCurrentSession()`**, add `wattHoursUsed` to the `session.copy(...)`:

```kotlin
repository.updateSession(
    session.copy(
        endEpochMillis = clock.nowEpochMillis(),
        maxSpeedMetersPerSecondCorrected = maxSpeedMetersPerSecondCorrected,
        distanceMetersCorrected = distanceMetersCorrected,
        wattHoursUsed = lastTripAmpHours?.let { it * 63.0 },
    ),
)
```

**In `endCurrentSession()`**, reset the field alongside the other resets:

```kotlin
lastTripAmpHours = null
```

---

## Compile check

```
gradle :app:compileDebugKotlin
```

Must pass with zero errors.

## Commit

```
fix(ride): persist Ah used from board tripAmpHours to ride history
```

No Co-Authored-By line.
