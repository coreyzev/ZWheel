# Gate: Issue #13 — Correct Ride Mode Enum and Parser for XR

**Branch:** `codex/issue13-ride-mode-enum`
**Base:** `main`
**Closes:** #13
**One concern:** `Parsers.rideMode()` currently maps 0 → CUSTOM (wrong) and ignores all named
modes. OWCE source confirms: for Plus/XR boards, 0 = Unknown, 4–9 = named modes. Fix the enum,
the parser signature, the service call site, and the affected test.

---

## Context from OWCE source (`OWBoard.cs` `RideModeString` property)

| Value | V1        | Plus / XR  | Pint / PintX |
|-------|-----------|------------|--------------|
| 0     | Unknown   | Unknown    | Unknown      |
| 1     | Classic   | —          | —            |
| 2     | Extreme   | —          | —            |
| 3     | Elevated  | —          | —            |
| 4     | —         | Sequoia    | —            |
| 5     | —         | Cruz       | Redwood      |
| 6     | —         | Mission    | Pacific      |
| 7     | —         | Elevated   | Elevated     |
| 8     | —         | Delirium   | Skyline      |
| 9     | —         | Custom     | —            |

Corey's M1 capture rawValueHex `0000` = 0 → correctly maps to UNKNOWN (not CUSTOM as the
current parser asserts).

`Parsers.amps()` already accepts `boardType: BoardType` — follow the same pattern here.

---

## Allowed files (touch ONLY these)

```
core/src/main/kotlin/com/zwheel/core/model/BoardModels.kt               ← add CRUZ to RideMode
core/src/main/kotlin/com/zwheel/core/protocol/Parsers.kt                ← fix rideMode(bytes, boardType)
core/src/main/kotlin/com/zwheel/core/service/BoardStateServiceImpl.kt   ← pass boardType in collectRideMode()
core/src/test/kotlin/com/zwheel/core/protocol/ParsersTest.kt            ← fix wrong test, add new ones
```

Do NOT touch any UI files or any other file.

---

## Implementation spec

### 1. `BoardModels.kt` — add CRUZ

In `RideMode`, add `CRUZ` between `SEQUOIA` and `MISSION` (alphabetical within the Plus/XR group):

```kotlin
enum class RideMode {
    CLASSIC,
    CRUZ,
    CUSTOM,
    DELIRIUM,
    ELEVATED,
    EXTREME,
    MISSION,
    SEQUOIA,
    UNKNOWN,
}
```

### 2. `Parsers.kt` — add boardType parameter, fix mapping

Replace:
```kotlin
fun rideMode(value: ByteArray): RideMode = when (value.uint16BigEndian()) {
    0 -> RideMode.CUSTOM
    else -> RideMode.UNKNOWN
}
```

With:
```kotlin
fun rideMode(value: ByteArray, boardType: BoardType): RideMode {
    return when (boardType) {
        BoardType.ONEWHEEL_V1 -> when (value.uint16BigEndian()) {
            1 -> RideMode.CLASSIC
            2 -> RideMode.EXTREME
            3 -> RideMode.ELEVATED
            else -> RideMode.UNKNOWN
        }
        BoardType.PLUS, BoardType.XR -> when (value.uint16BigEndian()) {
            4 -> RideMode.SEQUOIA
            5 -> RideMode.CRUZ
            6 -> RideMode.MISSION
            7 -> RideMode.ELEVATED
            8 -> RideMode.DELIRIUM
            9 -> RideMode.CUSTOM
            else -> RideMode.UNKNOWN
        }
        BoardType.PINT, BoardType.PINT_X -> when (value.uint16BigEndian()) {
            5 -> RideMode.SEQUOIA  // Redwood on Pint — closest enum
            6 -> RideMode.MISSION  // Pacific — closest enum
            7 -> RideMode.ELEVATED
            8 -> RideMode.CUSTOM   // Skyline — closest enum
            else -> RideMode.UNKNOWN
        }
        BoardType.UNKNOWN -> RideMode.UNKNOWN
    }
}
```

Note: Pint mode names (Redwood, Pacific, Skyline) don't have dedicated enum values — map to the
nearest semantically similar value. This is fine for now; #13 is XR-focused.

Add `import com.zwheel.core.model.BoardType` if not already present in Parsers.kt. Check the
imports at the top of the file first — `BoardType` is already imported.

### 3. `BoardStateServiceImpl.kt` — pass boardType to collectRideMode()

In `collectRideMode()`, change:
```kotlin
_state.update { it.copy(rideMode = Parsers.rideMode(bytes)) }
```
to:
```kotlin
_state.update { it.copy(rideMode = Parsers.rideMode(bytes, boardType)) }
```

`boardType` is already a constructor field — no other changes needed.

### 4. `ParsersTest.kt` — fix wrong test, add new tests

Replace the existing wrong test:
```kotlin
@Test
fun `ride mode parses uint16 big endian M1 custom sample`() {
    // Source: core/src/test/resources/xr4209-success-handshake-telemetry.jsonl,
    // e659f302 ride_mode metadata_read rawValueHex 0000 from the M1 XR 4209 success capture.
    assertEquals(RideMode.CUSTOM, Parsers.rideMode(hex("0000")))
}
```

With:
```kotlin
@Test
fun `ride mode 0 maps to UNKNOWN for XR`() {
    // Source: xr4209-success-handshake-telemetry.jsonl rawValueHex 0000.
    // 0 = no active mode / board not riding. Corrects prior wrong CUSTOM mapping.
    assertEquals(RideMode.UNKNOWN, Parsers.rideMode(hex("0000"), BoardType.XR))
}

@Test
fun `ride mode 9 maps to CUSTOM for XR`() {
    assertEquals(RideMode.CUSTOM, Parsers.rideMode(hex("0009"), BoardType.XR))
}

@Test
fun `ride mode 8 maps to DELIRIUM for XR`() {
    assertEquals(RideMode.DELIRIUM, Parsers.rideMode(hex("0008"), BoardType.XR))
}

@Test
fun `ride mode 6 maps to MISSION for XR`() {
    assertEquals(RideMode.MISSION, Parsers.rideMode(hex("0006"), BoardType.XR))
}

@Test
fun `ride mode 5 maps to CRUZ for XR`() {
    assertEquals(RideMode.CRUZ, Parsers.rideMode(hex("0005"), BoardType.XR))
}

@Test
fun `ride mode 4 maps to SEQUOIA for XR`() {
    assertEquals(RideMode.SEQUOIA, Parsers.rideMode(hex("0004"), BoardType.XR))
}

@Test
fun `ride mode 1 maps to CLASSIC for V1`() {
    assertEquals(RideMode.CLASSIC, Parsers.rideMode(hex("0001"), BoardType.ONEWHEEL_V1))
}
```

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :core:test --tests "com.zwheel.core.protocol.ParsersTest" && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

All tests must pass. The old test is replaced, not kept alongside — there must be no test that
asserts `rideMode(hex("0000")) == CUSTOM`.

Commit message: `fix(telemetry): correct ride mode enum and parser for XR using OWCE reference (#13)`
