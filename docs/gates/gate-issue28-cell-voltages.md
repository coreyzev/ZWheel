# Gate: Issue #28 — Parse and Collect OWCE-Style Cell Voltages

**Branch:** `codex/issue28-cell-voltages`
**Base:** `main`
**Closes:** #28
**One concern:** Add a two-path cell voltage parser (FW < 4141 vs FW ≥ 4141) and wire it into
`BoardStateServiceImpl` so `BoardState.cellVoltages` is populated from live BLE notifications.

---

## Context

`BoardState.cellVoltages: List<Double>` exists but is always empty because:
1. `Parsers.kt` has only a TODO comment where the parser should be.
2. `BoardStateServiceImpl.start()` never subscribes to `OwUuids.CELL_VOLTAGES`.

The board sends one cell reading per notification on UUID `e659f31b` (CELL_VOLTAGES). The
encoding depends on firmware revision:

| Firmware | Encoding |
|----------|----------|
| < 4141   | `bytes[1]` = cell index (0–14), `bytes[0] * 0.02` = volts |
| ≥ 4141   | uint16 big-endian: top 4 bits = cell index, bottom 12 bits × 0.0011 = volts |

Corey's XR FW 4134 uses the old (< 4141) encoding. XR has 15 cells (indices 0–14).
Cell index ≥ 15 should be silently discarded in the service (not the parser).

The firmware revision is already read and passed as `boardIdentity.firmwareRevision` (a String
like `"4134"`) before `BoardStateServiceImpl.start()` is called. See `ConnectionManager.kt`
lines ~110–136.

---

## Allowed files (touch ONLY these)

```
core/src/main/kotlin/com/zwheel/core/protocol/Parsers.kt               ← add cellVoltage()
core/src/test/kotlin/com/zwheel/core/protocol/ParsersTest.kt           ← add 4 parser tests
core/src/main/kotlin/com/zwheel/core/service/BoardStateServiceImpl.kt  ← add collectCellVoltages()
```

Do NOT touch any UI files, `BoardModels.kt`, `OwUuids.kt`, `ConnectionManager.kt`, or
any other file.

---

## Implementation spec

### 1. `Parsers.kt` — add `cellVoltage()`

Insert after the existing `packVoltage` function and remove the TODO comment:

```kotlin
fun cellVoltage(value: ByteArray, firmwareMajor: Int): Pair<Int, Double> {
    return if (firmwareMajor >= 4141) {
        val raw = value.uint16BigEndian()
        val cellIndex = (raw shr 12) and 0xF
        val voltage = (raw and 0xFFF) * 0.0011
        Pair(cellIndex, voltage)
    } else {
        val bytes = value.requireSize(2)
        val cellIndex = bytes[1].toInt() and 0xFF
        val voltage = (bytes[0].toInt() and 0xFF) * 0.02
        Pair(cellIndex, voltage)
    }
}
```

`uint16BigEndian()` and `requireSize()` are private extensions already in `Parsers` — they
are accessible from this new function since it's inside the same object.

### 2. `ParsersTest.kt` — add 4 tests

Append these tests inside `ParsersTest`:

```kotlin
@Test
fun `cellVoltage old encoding parses cell index from byte 1 and voltage from byte 0`() {
    // FW 4134 (< 4141): bytes[0]=200 → 200*0.02=4.0V, bytes[1]=0x00 → cell 0
    assertEquals(Pair(0, 4.0), Parsers.cellVoltage(hex("c800"), 4134))
}

@Test
fun `cellVoltage old encoding parses non-zero cell index`() {
    // FW 4134: bytes[0]=202 → 4.04V, bytes[1]=0x07 → cell 7
    assertEquals(Pair(7, 4.04), Parsers.cellVoltage(hex("ca07"), 4134))
}

@Test
fun `cellVoltage new encoding extracts top 4 bits as cell index`() {
    // FW 4141: uint16=0x3E34, top nibble=3 → cell 3, raw=0xE34=3636, 3636*0.0011=3.9996V
    assertEquals(Pair(3, 3.9996), Parsers.cellVoltage(hex("3e34"), 4141))
}

@Test
fun `cellVoltage new encoding cell 0`() {
    // FW 4141: uint16=0x0E34, top nibble=0 → cell 0, raw=3636, 3.9996V
    assertEquals(Pair(0, 3.9996), Parsers.cellVoltage(hex("0e34"), 4141))
}
```

### 3. `BoardStateServiceImpl.kt` — add `collectCellVoltages()`

Add this private suspend function:

```kotlin
private suspend fun collectCellVoltages() {
    val firmwareMajor = boardIdentity?.firmwareRevision?.toIntOrNull() ?: return
    val cellMap = mutableMapOf<Int, Double>()
    transport.notifications(OwUuids.CELL_VOLTAGES).collect { bytes ->
        try {
            val (cellIndex, voltage) = Parsers.cellVoltage(bytes, firmwareMajor)
            if (cellIndex < 15) {
                cellMap[cellIndex] = voltage
                _state.update { state ->
                    val sorted = cellMap.entries.sortedBy { it.key }.map { it.value }
                    state.copy(cellVoltages = sorted)
                }
            }
        } catch (e: IllegalArgumentException) {
            println("[BoardStateServiceImpl] CELL_VOLTAGES: ${e.message}")
        }
    }
}
```

Then in `start()`, add the launch call **after** the existing launches:

```kotlin
suspend fun start(scope: CoroutineScope) {
    _state.update { it.copy(identity = boardIdentity) }
    scope.launch { collectAmps() }
    scope.launch { collectPackVoltage() }
    scope.launch { collectBatteryPercent() }
    scope.launch { collectTemperature() }
    scope.launch { collectRideMode() }
    scope.launch { collectOdometer() }
    scope.launch { collectRpm() }
    scope.launch { collectCellVoltages() }   // ← add this line
}
```

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :core:test --tests "com.zwheel.core.protocol.ParsersTest"
```

All 4 new tests must pass. No other tests should break.

Then full compile check:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :core:compileDebugKotlin :app:compileDebugKotlin
```

Commit message: `feat(telemetry): parse and collect OWCE-style cell voltages for XR (#28)`
