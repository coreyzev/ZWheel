# Gate: GPS Map Empty State on Ride Detail

**Branch:** `codex/gps-map-empty-state`
**Base:** `main`
**Dependency:** must be dispatched AFTER `codex/gps-route-map` (PR #62) is merged.
Both gates modify `RideDetailScreen.kt` and will conflict if rebased before merge.

---

## Context

After PR #62, `RideDetailScreen` shows an OSMDroid map when `s.gpsPoints.isNotEmpty()`.
When a ride has no GPS data (denied permission, indoor ride, old session recorded before the
GPS feature), the map section is silently absent with no explanation. Add a clear empty state.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailScreen.kt
```

Do NOT touch any other file.

---

## Implementation spec

### `RideDetailScreen.kt` — add empty state branch

After PR #62, the screen contains a block like:

```kotlin
if (s.gpsPoints.isNotEmpty()) {
    // ... OSMDroid AndroidView ...
}
```

Change it to include an `else` branch:

```kotlin
if (s.gpsPoints.isNotEmpty()) {
    // ... existing OSMDroid AndroidView block unchanged ...
} else {
    Text(
        text = "No GPS data recorded for this ride",
        fontSize = 13.sp,
        color = Color(0xff888888),
    )
}
```

No other changes.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass cleanly.

Commit message: `feat(ui): show empty state when no GPS data recorded for ride`
