# Gate: dashboard-5bugs

Fix five dashboard bugs. Touch only the files listed below. Run `:app:compileDebugKotlin`, fix all errors, then commit.

---

## Bug 1 — "MPH\nUNCORRECTED" embedded in unit label

**File:** `app/src/main/kotlin/com/zwheel/app/ui/DashboardState.kt`

Change line:
```kotlin
speedUnitLabel = if (isSpeedCorrected) prefs.speedUnit.name else "${prefs.speedUnit.name}\nUNCORRECTED",
```
To:
```kotlin
speedUnitLabel = prefs.speedUnit.name,
```

---

## Bug 2 — Show "UNCORRECTED" as a small dimmed pill (safety rule §3 requires an uncorrected badge)

**File:** `app/src/main/kotlin/com/zwheel/app/ui/dashboard/SpeedSlab.kt`

The current code block:
```kotlin
if (state.isSpeedCorrected) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = c.screenBg,
        border = BorderStroke(1.dp, c.borderLime),
    ) {
        Text(
            text = "TIRE-CORRECTED",
            color = c.lime,
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
```

Replace with:
```kotlin
if (state.isSpeedCorrected) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = c.screenBg,
        border = BorderStroke(1.dp, c.borderLime),
    ) {
        Text(
            text = "TIRE-CORRECTED",
            color = c.lime,
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
} else {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = c.screenBg,
        border = BorderStroke(1.dp, c.border),
    ) {
        Text(
            text = "UNCORRECTED",
            color = c.textDim,
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
```

---

## Bug 3 — MODE tile text wraps ("UNKNOW N")

**File:** `app/src/main/kotlin/com/zwheel/app/ui/dashboard/StatRow.kt`

In `ModeTile`, the Text showing `state.rideMode` needs overflow protection. Change:
```kotlin
Text(
    text = state.rideMode,
    color = c.lime,
    style = TextStyle(
        fontFamily = SairaFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
)
```
To:
```kotlin
Text(
    text = state.rideMode,
    color = c.lime,
    maxLines = 1,
    softWrap = false,
    overflow = TextOverflow.Ellipsis,
    style = TextStyle(
        fontFamily = SairaFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
)
```

Add the `androidx.compose.ui.text.style.TextOverflow` import if not already present.

---

## Bug 4 — MODE shows "UNKNOWN" when board is in an unmapped ride mode

**File:** `app/src/main/kotlin/com/zwheel/app/ui/DashboardState.kt`

In `BoardState.toDashboardUiState(...)`, change:
```kotlin
rideMode = rideMode.name,
```
To:
```kotlin
rideMode = if (rideMode == RideMode.UNKNOWN) "--" else rideMode.name,
```

---

## Bug 5 — Battery % always shows 0% (and ride mode never populates)

BLE GATT does not push the current value when you subscribe to notifications — the board
only sends a notification when the value *changes*. For stable characteristics like battery
level and ride mode, we must do an initial `read()` to populate the current value, then
let notifications handle subsequent changes.

**File:** `core/src/main/kotlin/com/zwheel/core/service/BoardStateServiceImpl.kt`

### 5a — collectBatteryPercent

Replace:
```kotlin
private suspend fun collectBatteryPercent() {
    transport.notifications(OwUuids.BATTERY_PERCENT).collect { bytes ->
        try {
            _state.update { it.copy(batteryPercent = Parsers.batteryPercent(bytes)) }
        } catch (e: Exception) {
            println("[BoardStateServiceImpl] BATTERY_PERCENT: ${e.message}")
        }
    }
}
```
With:
```kotlin
private suspend fun collectBatteryPercent() {
    try {
        val initial = transport.read(OwUuids.BATTERY_PERCENT)
        _state.update { it.copy(batteryPercent = Parsers.batteryPercent(initial)) }
    } catch (e: Exception) {
        println("[BoardStateServiceImpl] BATTERY_PERCENT initial read: ${e.message}")
    }
    try {
        transport.notifications(OwUuids.BATTERY_PERCENT).collect { bytes ->
            try {
                _state.update { it.copy(batteryPercent = Parsers.batteryPercent(bytes)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] BATTERY_PERCENT parse: ${e.message}")
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("[BoardStateServiceImpl] BATTERY_PERCENT subscribe failed: ${e.message}")
    }
}
```

### 5b — collectRideMode

Replace:
```kotlin
private suspend fun collectRideMode() {
    transport.notifications(OwUuids.RIDE_MODE).collect { bytes ->
        try {
            _state.update { it.copy(rideMode = Parsers.rideMode(bytes, boardType)) }
        } catch (e: Exception) {
            println("[BoardStateServiceImpl] RIDE_MODE: ${e.message}")
        }
    }
}
```
With:
```kotlin
private suspend fun collectRideMode() {
    try {
        val initial = transport.read(OwUuids.RIDE_MODE)
        _state.update { it.copy(rideMode = Parsers.rideMode(initial, boardType)) }
    } catch (e: Exception) {
        println("[BoardStateServiceImpl] RIDE_MODE initial read: ${e.message}")
    }
    try {
        transport.notifications(OwUuids.RIDE_MODE).collect { bytes ->
            try {
                _state.update { it.copy(rideMode = Parsers.rideMode(bytes, boardType)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] RIDE_MODE parse: ${e.message}")
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("[BoardStateServiceImpl] RIDE_MODE subscribe failed: ${e.message}")
    }
}
```

The `CancellationException` import is already used elsewhere in the project (`kotlinx.coroutines.CancellationException`).

---

## Compile and commit

Run:
```
./gradlew :app:compileDebugKotlin
```
Fix any errors. Then commit with message:
```
fix(dashboard): uncorrected pill, mode overflow, battery%/ride-mode initial read
```
