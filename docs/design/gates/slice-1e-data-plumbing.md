# Gate: Slice 1e — Serial numbers + live average speed

You are wiring three pieces of live telemetry that the redesigned ZWheel UI shows but the data layer
does not yet provide: board serial number (already partially plumbed), battery serial number (missing
entirely), and live average speed (hardcoded `0.0`). Read ONLY this gate file. Do not read any design
docs or other gates.

**Out of scope — do NOT touch:**
- `tripAmpHours` is always `0.0`; that is pre-existing bug #113 tracked separately.
- `CUSTOM_NAME` / editable board name — local-only, already done.
- No new BLE UUIDs (both `SERIAL_NUMBER` e659f301 and `BATTERY_SERIAL` e659f306 already exist in
  `OwUuids`). No new writable characteristic.

---

## Part 1 — `core/` changes (pure Kotlin, no Android imports)

### 1a. `Parsers.kt` — add two new parser functions

File: `core/src/main/kotlin/com/zwheel/core/protocol/Parsers.kt`

Add immediately after `firmwareRevision`:

```kotlin
fun serialNumber(value: ByteArray): String = value.uint16BigEndian().toString()

fun batterySerialNumber(value: ByteArray): String = value.uint16BigEndian().toString()
```

Both return `String`. The protocol encodes these as unsigned 16-bit big-endian integers (same
wire format as `hardwareRevision` / `firmwareRevision`). The golden captures show values 18694
(`0x4906`) and 22136 (`0x5678`) — both fit in `UShort`, so `uint16BigEndian()` (which returns
`Int` in `0..65535`) converted to decimal string is correct. Do NOT apply any scale or clamp.
`uint16BigEndian()` is already a private extension on `ByteArray` — these new functions follow the
same single-expression pattern as `hardwareRevision` and `firmwareRevision`.

### 1b. `BoardModels.kt` — add `batterySerialNumber` field

File: `core/src/main/kotlin/com/zwheel/core/model/BoardModels.kt`

`BoardIdentity` currently has:
```kotlin
data class BoardIdentity(
    val boardId: String,
    val name: String,
    val type: BoardType,
    val serialNumber: String? = null,
    val firmwareRevision: String? = null,
    val hardwareRevision: String? = null,
)
```

Add one field with a default so all existing call sites compile without changes:
```kotlin
    val batterySerialNumber: String? = null,
```

Place it after `serialNumber` (before `firmwareRevision`). The final order:
`boardId`, `name`, `type`, `serialNumber`, `batterySerialNumber`, `firmwareRevision`, `hardwareRevision`.

---

## Part 2 — `app/` changes

### 2a. `ConnectionManager.kt` — read serial + battery serial during connect

File: `app/src/main/kotlin/com/zwheel/app/ble/ConnectionManager.kt`

The `connect()` function currently reads `HARDWARE_REVISION` and `FIRMWARE_REVISION` and builds a
`BoardIdentity`. After the existing two reads (lines 117–120 in the current file), add reads for the
two serial characteristics. Each read must be wrapped in `runCatching` so a board that does not
expose the characteristic degrades gracefully to `null` rather than crashing the connect flow.

Replace the block that builds `identity` — currently:
```kotlin
val hwBytes = transport.read(OwUuids.HARDWARE_REVISION)
val fwBytes = transport.read(OwUuids.FIRMWARE_REVISION)
val hwRev = Parsers.hardwareRevision(hwBytes)
val fwRev = Parsers.firmwareRevision(fwBytes)
val boardType = BoardTypeDetector.detect(hwRev)
val identity = BoardIdentity(
    boardId = deviceId,
    name = boardType.displayName,
    type = boardType,
    firmwareRevision = fwRev.toString(),
    hardwareRevision = hwRev.toString(),
)
```

With:
```kotlin
val hwBytes = transport.read(OwUuids.HARDWARE_REVISION)
val fwBytes = transport.read(OwUuids.FIRMWARE_REVISION)
val hwRev = Parsers.hardwareRevision(hwBytes)
val fwRev = Parsers.firmwareRevision(fwBytes)
val boardType = BoardTypeDetector.detect(hwRev)
val serialNumber = runCatching {
    Parsers.serialNumber(transport.read(OwUuids.SERIAL_NUMBER))
}.getOrNull()
val batterySerialNumber = runCatching {
    Parsers.batterySerialNumber(transport.read(OwUuids.BATTERY_SERIAL))
}.getOrNull()
val identity = BoardIdentity(
    boardId = deviceId,
    name = boardType.displayName,
    type = boardType,
    serialNumber = serialNumber,
    batterySerialNumber = batterySerialNumber,
    firmwareRevision = fwRev.toString(),
    hardwareRevision = hwRev.toString(),
)
```

No other changes to `ConnectionManager`. The two reads are sequential one-shots (same as the hw/fw
reads); no subscription, no notify. The `runCatching` wraps the entire `transport.read(…)` +
`Parsers.…(…)` chain, so a missing characteristic OR a malformed response both degrade to `null`.

### 2b. `DeviceInfoDisclosure.kt` — wire Battery serial row

File: `app/src/main/kotlin/com/zwheel/app/ui/settings/DeviceInfoDisclosure.kt`

The component already builds a `rows` list. The "Serial" row already reads `identity?.serialNumber ?: "—"`.
The "Battery serial" row is currently hardcoded `"—"` with a TODO comment. Replace that one entry:

Old:
```kotlin
// TODO(battery-serial): BoardIdentity does not yet carry batterySerialNumber.
// Wire OwUuids.BATTERY_SERIAL through BLE and BoardIdentity in a future gate.
"Battery serial" to "—",
```

New:
```kotlin
"Battery serial" to (identity?.batterySerialNumber ?: "—"),
```

No other changes to this file.

### 2c. `RideServiceRepository.kt` — add elapsed time + avgSpeed flow

File: `app/src/main/kotlin/com/zwheel/app/service/RideServiceRepository.kt`

The repository is the shared data bus between the foreground service and the ViewModel. It currently
has `isRiding`, `tripDistanceMeters`, `gpsLocked`, and `topSpeedMetersPerSecond`. Add elapsed-time
tracking and derive average speed here.

Add the following to the class body (below `updateGpsLock`):

```kotlin
private val _rideStartEpochMillis = MutableStateFlow<Long?>(null)

/** Milliseconds since the current session started, or null if not riding. */
private val _rideElapsedMillis = MutableStateFlow<Long>(0L)
val rideElapsedMillis: StateFlow<Long> = _rideElapsedMillis.asStateFlow()

/**
 * Live average speed in m/s: tripDistanceMeters / elapsed seconds.
 * Returns 0.0 when not riding or elapsed < 1 s to avoid division by zero.
 */
val avgSpeedMetersPerSecond: StateFlow<Double> = combine(
    _tripDistanceMeters,
    _rideElapsedMillis,
) { dist, elapsedMs ->
    val elapsedSec = elapsedMs / 1_000.0
    if (elapsedSec < 1.0) 0.0 else dist / elapsedSec
}.stateIn(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    started = SharingStarted.Eagerly,
    initialValue = 0.0,
)

internal fun markRideStarted(epochMillis: Long) {
    _rideStartEpochMillis.value = epochMillis
    _rideElapsedMillis.value = 0L
}

internal fun tickElapsed(nowEpochMillis: Long) {
    val start = _rideStartEpochMillis.value ?: return
    _rideElapsedMillis.value = (nowEpochMillis - start).coerceAtLeast(0L)
}

internal fun markRideStopped() {
    _rideStartEpochMillis.value = null
    _rideElapsedMillis.value = 0L
}
```

Add the necessary imports at the top of the file:
```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
```

**Why a CoroutineScope inside the repository:** `RideServiceRepository` is a `@Singleton` with no
lifecycle scope. A `SupervisorJob() + Dispatchers.Default` scope scoped to the singleton lifetime is
the correct minimal pattern here; the alternative (injecting a scope) would require larger changes.

### 2d. `RideForegroundService.kt` — call the new elapsed-time hooks

File: `app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt`

The foreground service owns `RideRecorder` and already calls
`rideServiceRepository.updateIsRiding(isRiding)` via `recorder.onSessionChanged`. Extend
`startRideRecorderTicker()` to drive the new hooks.

In `startRideRecorderTicker()`, replace the `recorder.onSessionChanged` lambda:

Old:
```kotlin
recorder.onSessionChanged = { isRiding ->
    rideServiceRepository.updateIsRiding(isRiding)
    if (!isRiding) {
        topSpeedTracker = DefaultTopSpeedTracker()
        rideServiceRepository.updateTopSpeed(0.0)
    }
}
```

New:
```kotlin
recorder.onSessionChanged = { isRiding ->
    rideServiceRepository.updateIsRiding(isRiding)
    if (isRiding) {
        rideServiceRepository.markRideStarted(clock.nowEpochMillis())
    } else {
        rideServiceRepository.markRideStopped()
        topSpeedTracker = DefaultTopSpeedTracker()
        rideServiceRepository.updateTopSpeed(0.0)
    }
}
```

Also, inside the ticker coroutine, call `tickElapsed` each second after `recorder.onTick`. The
existing ticker loop in `startRideRecorderTicker()` is:
```kotlin
lifecycleScope.launch {
    while (isActive) {
        delay(1_000L)
        runCatching {
            recorder.onTick(
                connectionManager.boardState.value,
                lastLatitude,
                lastLongitude,
                lastAltitude,
            )
        }
    }
}
```

Add one line after `recorder.onTick(…)` (still inside the `runCatching` block) to update elapsed
time on every tick:
```kotlin
rideServiceRepository.tickElapsed(clock.nowEpochMillis())
```

So the `runCatching` block becomes:
```kotlin
runCatching {
    recorder.onTick(
        connectionManager.boardState.value,
        lastLatitude,
        lastLongitude,
        lastAltitude,
    )
    rideServiceRepository.tickElapsed(clock.nowEpochMillis())
}
```

`clock` is already injected into `RideForegroundService` — no new dependency.

### 2e. `DashboardViewModel.kt` + `DashboardState.kt` — wire avgSpeedMph

**`DashboardViewModel.kt`** (`app/src/main/kotlin/com/zwheel/app/ui/DashboardViewModel.kt`)

The ViewModel already has a 5-flow `combine` chained with a `.combine(rssi)`. Adding a 7th source
to the inner `combine` would require refactoring the lambda. The cleanest approach that minimises
diff: add a second `.combine()` stage (matching the existing `.combine(connectionManager.rssi)`
pattern) to fold in `avgSpeedMetersPerSecond` from `RideServiceRepository`.

Replace the tail of the `uiState` declaration — after the inner combine, before `.stateIn` — with:

```kotlin
    }.combine(connectionManager.rssi) { state, rssi ->
        state.copy(rssi = rssi)
    }.combine(rideServiceRepository.avgSpeedMetersPerSecond) { state, avgSpeedMps ->
        state.copy(avgSpeedMph = avgSpeedMps.toDisplaySpeed(state))
    }.stateIn(
```

The `toDisplaySpeed` conversion must use the same m/s → display-unit logic as `topSpeedMph`.
Because `toDashboardUiState` is a top-level function in `DashboardState.kt` and the speed-unit pref
is not available at this late `.combine` stage, expose a small private helper in
`DashboardViewModel.kt`:

```kotlin
private fun Double.toDisplaySpeed(state: DashboardUiState): Double {
    // Re-use the same conversion path: the unit is already baked into state.speedUnitLabel.
    // We don't have prefs here, so inspect the label directly.
    return if (state.speedUnitLabel.startsWith("KPH")) {
        com.zwheel.core.calc.UnitConversions.metersPerSecondToKph(this)
    } else {
        com.zwheel.core.calc.UnitConversions.metersPerSecondToMph(this)
    }
}
```

**Alternative if the helper feels fragile:** pass `avgSpeedMetersPerSecond` into the 5-flow combine
as a 6th source by breaking the lambda into a named intermediate. Either approach is acceptable, but
the `.combine` chain extension is preferred to minimise the diff and match the existing `rssi`
pattern.

**`DashboardState.kt`** (`app/src/main/kotlin/com/zwheel/app/ui/DashboardState.kt`)

Remove the TODO comment and keep the `avgSpeedMph = 0.0` default in `toDashboardUiState` — the real
value is now injected by the ViewModel's `.combine` stage above, not by `toDashboardUiState`. The
comment currently reads:
```kotlin
// TODO(avg-speed): wire RideServiceRepository once rolling avg is tracked
avgSpeedMph = 0.0,
```

Strip the TODO comment; leave the `0.0` default unchanged (it is overwritten by the combine stage):
```kotlin
avgSpeedMph = 0.0,
```

The `mockDashboardState()` function already has `avgSpeedMph = 8.3` — no change needed there.

---

## Part 3 — Tests

### 3a. Parser unit tests

File: `core/src/test/kotlin/com/zwheel/core/protocol/ParsersTest.kt`

Append two tests following the exact style of the existing `hardwareRevision` / `firmwareRevision`
tests (JUnit 5, backtick names, `hex()` helper):

```kotlin
@Test
fun `serialNumber parses uint16 big endian golden 18694`() {
    // 18694 = 0x4906
    assertEquals("18694", Parsers.serialNumber(hex("4906")))
}

@Test
fun `batterySerialNumber parses uint16 big endian golden 22136`() {
    // 22136 = 0x5678
    assertEquals("22136", Parsers.batterySerialNumber(hex("5678")))
}
```

### 3b. Screenshot tests — re-render with populated values

**`SettingsScreenshotTest.kt`**
(`app/src/test/kotlin/com/zwheel/app/ui/screenshots/SettingsScreenshotTest.kt`)

The `mockBoardState()` at the bottom of this file already sets `serialNumber = "18694"`. Add
`batterySerialNumber = "22136"` to populate the Battery serial row:

Old:
```kotlin
private fun mockBoardState() = BoardState(
    identity = BoardIdentity(
        boardId = "TEST_001",
        name = "Pint X",
        type = BoardType.PINT_X,
        serialNumber = "18694",
        firmwareRevision = "4134",
        hardwareRevision = "4209",
    ),
    ...
)
```

New:
```kotlin
private fun mockBoardState() = BoardState(
    identity = BoardIdentity(
        boardId = "TEST_001",
        name = "Pint X",
        type = BoardType.PINT_X,
        serialNumber = "18694",
        batterySerialNumber = "22136",
        firmwareRevision = "4134",
        hardwareRevision = "4209",
    ),
    ...
)
```

**`DashboardScreenshotTest.kt`**
(`app/src/test/kotlin/com/zwheel/app/ui/screenshots/DashboardScreenshotTest.kt`)

`DashboardScreen` is rendered with `mockDashboardState()`, which already sets `avgSpeedMph = 8.3` —
no change required to make a non-zero AVG render. Verify the output screenshot at
`build/outputs/roborazzi/dashboard.png` shows a non-zero average speed gauge.

---

## Build & commit

Set env before every Gradle invocation:
```
export GRADLE_USER_HOME=/root/zwheel-wt/.gradle-codex
```
And pass JVM flag to every `./gradlew` call:
```
-Djava.io.tmpdir=/root/zwheel-wt/.tmp-codex
```

Build steps in order:
1. `./gradlew :core:test -Djava.io.tmpdir=/root/zwheel-wt/.tmp-codex` — parser tests must pass.
2. `./gradlew :app:compileDebugKotlin -Djava.io.tmpdir=/root/zwheel-wt/.tmp-codex` — full compile must succeed.
3. `./gradlew :app:recordRoborazziDebug -Djava.io.tmpdir=/root/zwheel-wt/.tmp-codex` — re-renders all screenshots. Confirm `settings.png` / `settings_device_info_expanded.png` show "22136" in Battery serial, and `dashboard.png` shows "8.3" (or similar non-zero value) in AVG.

Commits (Conventional Commits format, no Co-Authored-By):
1. `feat(ble): read serial + battery serial into board identity`
   — covers `Parsers.kt` (new functions), `BoardModels.kt` (new field), `ConnectionManager.kt` (new reads), `DeviceInfoDisclosure.kt` (unwire TODO).
2. `feat(ride): live average speed`
   — covers `RideServiceRepository.kt`, `RideForegroundService.kt`, `DashboardViewModel.kt`, `DashboardState.kt`.
3. `test(core): parser unit tests for serialNumber + batterySerialNumber; re-render screenshots`
   — covers `ParsersTest.kt`, `SettingsScreenshotTest.kt`, and any screenshot PNG artifacts.
