# Gate: Wire Safety Headroom, Status Error, and Battery Temperature

**Branch:** `codex/safety-status-batt-temp`
**Base:** `main`
**One concern:** Three useful characteristics are defined in `OwUuids` but never subscribed.
Add fields to `BoardState`, parsers, collectors, and tests for all three in one pass.

---

## Context from OWCE (`OWBoard.cs`)

| Characteristic       | UUID suffix | OWCE parsing                             | Notes                  |
|----------------------|-------------|------------------------------------------|------------------------|
| SAFETY_HEADROOM      | e659f317    | `SafetyHeadroom = value` (raw uint16)    | 0–100 pushback %       |
| STATUS_ERROR         | e659f30f    | `StatusError = value` (raw uint16)       | 0 = no error           |
| BATTERY_TEMPERATURE  | e659f315    | XR: `data[0]` (first byte, raw Celsius)  | V1/Plus use `data[1]`  |

All three UUIDs are already defined in `OwUuids`. BATTERY_TEMPERATURE encoding is board-type
dependent — pass `boardType` to match the amps parser pattern.

---

## Allowed files (touch ONLY these)

```
core/src/main/kotlin/com/zwheel/core/model/BoardModels.kt               ← add 3 fields to BoardState
core/src/main/kotlin/com/zwheel/core/protocol/Parsers.kt                ← add 3 parser functions
core/src/main/kotlin/com/zwheel/core/service/BoardStateServiceImpl.kt   ← add 3 collectors
core/src/test/kotlin/com/zwheel/core/protocol/ParsersTest.kt            ← add 4 parser tests
```

---

## Implementation spec

### 1. `BoardModels.kt` — add 3 fields to `BoardState`

Add after `motorTempCelsius`:

```kotlin
val batteryTempCelsius: Int? = null,
val safetyHeadroom: Int? = null,
val statusError: Int? = null,
```

### 2. `Parsers.kt` — add 3 parser functions

Add after the existing temperature functions:

```kotlin
fun batteryTemperature(value: ByteArray, boardType: BoardType): Int {
    val bytes = value.requireSize(2)
    return when (boardType) {
        BoardType.ONEWHEEL_V1, BoardType.PLUS -> bytes[1].toInt() and 0xFF
        else -> bytes[0].toInt() and 0xFF
    }
}

fun safetyHeadroom(value: ByteArray): Int = value.uint16BigEndian()

fun statusError(value: ByteArray): Int = value.uint16BigEndian()
```

`requireSize` and `uint16BigEndian` are private extensions already inside `Parsers` — these
functions are inside the same object and can call them directly.

Ensure `import com.zwheel.core.model.BoardType` is at the top of `Parsers.kt` — it is already
present (added for `rideMode`). Do not add a duplicate import.

### 3. `BoardStateServiceImpl.kt` — add 3 collectors, wire into `start()`

Add these private suspend functions:

```kotlin
private suspend fun collectBatteryTemperature() {
    transport.notifications(OwUuids.BATTERY_TEMPERATURE).collect { bytes ->
        try {
            _state.update { it.copy(batteryTempCelsius = Parsers.batteryTemperature(bytes, boardType)) }
        } catch (e: IllegalArgumentException) {
            println("[BoardStateServiceImpl] BATTERY_TEMPERATURE: ${e.message}")
        }
    }
}

private suspend fun collectSafetyHeadroom() {
    transport.notifications(OwUuids.SAFETY_HEADROOM).collect { bytes ->
        try {
            _state.update { it.copy(safetyHeadroom = Parsers.safetyHeadroom(bytes)) }
        } catch (e: IllegalArgumentException) {
            println("[BoardStateServiceImpl] SAFETY_HEADROOM: ${e.message}")
        }
    }
}

private suspend fun collectStatusError() {
    transport.notifications(OwUuids.STATUS_ERROR).collect { bytes ->
        try {
            _state.update { it.copy(statusError = Parsers.statusError(bytes)) }
        } catch (e: IllegalArgumentException) {
            println("[BoardStateServiceImpl] STATUS_ERROR: ${e.message}")
        }
    }
}
```

In `start()`, add after existing launches:

```kotlin
scope.launch { collectBatteryTemperature() }
scope.launch { collectSafetyHeadroom() }
scope.launch { collectStatusError() }
```

### 4. `ParsersTest.kt` — add 4 tests

```kotlin
@Test
fun `batteryTemperature XR reads first byte`() {
    // XR: data[0]=0x1E=30 → 30°C
    assertEquals(30, Parsers.batteryTemperature(hex("1e00"), BoardType.XR))
}

@Test
fun `batteryTemperature V1 reads second byte`() {
    // V1: data[1]=0x1E=30 → 30°C
    assertEquals(30, Parsers.batteryTemperature(hex("001e"), BoardType.ONEWHEEL_V1))
}

@Test
fun `safetyHeadroom parses raw uint16`() {
    // 0x0064 = 100 → full pushback headroom
    assertEquals(100, Parsers.safetyHeadroom(hex("0064")))
}

@Test
fun `statusError parses raw uint16`() {
    // 0x0000 = no error
    assertEquals(0, Parsers.statusError(hex("0000")))
}
```

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :core:test --tests "com.zwheel.core.protocol.ParsersTest" && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

All tests must pass.

Commit message: `feat(telemetry): wire safety headroom, status error, and battery temperature (#)`
