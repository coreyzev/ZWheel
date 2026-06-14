# Gate: Wire Trip and Regen Amp-Hours into BoardState

**Branch:** `codex/trip-energy`
**Base:** `main`
**One concern:** `OwUuids.TRIP_TOTAL_AMP_HOURS` and `TRIP_REGEN_AMP_HOURS` are defined but
never subscribed. Add fields to `BoardState`, board-type-aware parsers, collectors, and tests.

---

## Context from OWCE (`OWBoard.cs` lines 1211–1229)

```csharp
case TripAmpHoursUUID:
    if (BoardType == OWBoardType.V1)
        TripAmpHours = (float)value * 0.00009f;
    else
        TripAmpHours = (float)value * 0.00018f;
    break;
case TripRegenAmpHoursUUID:
    if (BoardType == OWBoardType.V1)
        TripRegenAmpHours = (float)value * 0.00009f;
    else
        TripRegenAmpHours = (float)value * 0.00018f;
    break;
```

`value` is uint16 big-endian. Scale factor is board-type dependent.
Corey's XR uses the non-V1 scale: `* 0.00018`.

---

## Allowed files (touch ONLY these)

```
core/src/main/kotlin/com/zwheel/core/model/BoardModels.kt               ← add 2 fields to BoardState
core/src/main/kotlin/com/zwheel/core/protocol/Parsers.kt                ← add 2 parser functions
core/src/main/kotlin/com/zwheel/core/service/BoardStateServiceImpl.kt   ← add 2 collectors
core/src/test/kotlin/com/zwheel/core/protocol/ParsersTest.kt            ← add 4 parser tests
```

---

## Implementation spec

### 1. `BoardModels.kt` — add 2 fields to `BoardState`

Add after `amps`:

```kotlin
val tripAmpHours: Double? = null,
val tripRegenAmpHours: Double? = null,
```

### 2. `Parsers.kt` — add 2 functions

Add after the `amps` function:

```kotlin
fun tripAmpHours(value: ByteArray, boardType: BoardType): Double {
    val scale = if (boardType == BoardType.ONEWHEEL_V1) 0.00009 else 0.00018
    return value.uint16BigEndian() * scale
}

fun tripRegenAmpHours(value: ByteArray, boardType: BoardType): Double {
    val scale = if (boardType == BoardType.ONEWHEEL_V1) 0.00009 else 0.00018
    return value.uint16BigEndian() * scale
}
```

`uint16BigEndian()` is a private extension already inside `Parsers`. `BoardType` is already
imported in `Parsers.kt` — do not add a duplicate import.

### 3. `BoardStateServiceImpl.kt` — add 2 collectors

```kotlin
private suspend fun collectTripAmpHours() {
    transport.notifications(OwUuids.TRIP_TOTAL_AMP_HOURS).collect { bytes ->
        try {
            _state.update { it.copy(tripAmpHours = Parsers.tripAmpHours(bytes, boardType)) }
        } catch (e: IllegalArgumentException) {
            println("[BoardStateServiceImpl] TRIP_AMP_HOURS: ${e.message}")
        }
    }
}

private suspend fun collectTripRegenAmpHours() {
    transport.notifications(OwUuids.TRIP_REGEN_AMP_HOURS).collect { bytes ->
        try {
            _state.update { it.copy(tripRegenAmpHours = Parsers.tripRegenAmpHours(bytes, boardType)) }
        } catch (e: IllegalArgumentException) {
            println("[BoardStateServiceImpl] TRIP_REGEN_AMP_HOURS: ${e.message}")
        }
    }
}
```

In `start()`, add:
```kotlin
scope.launch { collectTripAmpHours() }
scope.launch { collectTripRegenAmpHours() }
```

### 4. `ParsersTest.kt` — add 4 tests

```kotlin
@Test
fun `tripAmpHours XR scale 0x0064 = 100 * 0_00018`() {
    // 0x0064 = 100, 100 * 0.00018 = 0.018 Ah
    assertEquals(0.018, Parsers.tripAmpHours(hex("0064"), BoardType.XR), 1e-9)
}

@Test
fun `tripAmpHours V1 scale 0x0064 = 100 * 0_00009`() {
    assertEquals(0.009, Parsers.tripAmpHours(hex("0064"), BoardType.ONEWHEEL_V1), 1e-9)
}

@Test
fun `tripRegenAmpHours XR scale 0x0032 = 50 * 0_00018`() {
    // 0x0032 = 50, 50 * 0.00018 = 0.009 Ah
    assertEquals(0.009, Parsers.tripRegenAmpHours(hex("0032"), BoardType.XR), 1e-9)
}

@Test
fun `tripRegenAmpHours zero returns 0`() {
    assertEquals(0.0, Parsers.tripRegenAmpHours(hex("0000"), BoardType.XR), 1e-9)
}
```

Note: `assertEquals(Double, Double, delta)` is the JUnit 5 overload for floating-point comparison.
Use the three-argument form shown above.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :core:test --tests "com.zwheel.core.protocol.ParsersTest" && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

All tests must pass.

Commit message: `feat(telemetry): wire trip and regen amp-hours into BoardState`
