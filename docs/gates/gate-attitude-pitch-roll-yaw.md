# Gate: Wire Pitch / Roll / Yaw Notifications into BoardState

**Branch:** `codex/attitude-pitch-roll-yaw`
**Base:** `main`
**One concern:** `BoardState.pitchDegrees`, `rollDegrees`, `yawDegrees` are already defined as
nullable fields, and `OwUuids.PITCH/ROLL/YAW` are already defined. `BoardStateServiceImpl`
just never subscribes to them. Wire them up.

---

## Context

OWCE formula (`OWBoard.cs` lines 1155–1162):
```csharp
case PitchUUID:
    Pitch = 0.1f * (1800 - value);   // value = uint16 big-endian
case RollUUID:
    Roll  = 0.1f * (1800 - value);
case YawUUID:
    Yaw   = 0.1f * (1800 - value);
```

`value` is a uint16. The reference frame: 1800 = 0° (level). Decreasing value = positive angle
(forward lean for pitch). The formula works as signed arithmetic since normal riding angles
stay well within ±180° of the 1800 reference.

ZWheel's `uint16BigEndian()` returns `Int`, so `1800 - bytes.uint16BigEndian()` is signed Int
arithmetic — negative angles work correctly.

---

## Allowed files (touch ONLY these)

```
core/src/main/kotlin/com/zwheel/core/protocol/Parsers.kt               ← add pitch(), roll(), yaw()
core/src/main/kotlin/com/zwheel/core/service/BoardStateServiceImpl.kt  ← add 3 collectors, call in start()
core/src/test/kotlin/com/zwheel/core/protocol/ParsersTest.kt           ← add 3 parser tests
```

Do NOT touch `BoardModels.kt` (fields already exist), `OwUuids.kt` (UUIDs already defined),
or any UI file.

---

## Implementation spec

### 1. `Parsers.kt` — add three functions

Add after `packVoltage`:

```kotlin
fun pitch(value: ByteArray): Double = 0.1 * (1800 - value.uint16BigEndian())
fun roll(value: ByteArray): Double  = 0.1 * (1800 - value.uint16BigEndian())
fun yaw(value: ByteArray): Double   = 0.1 * (1800 - value.uint16BigEndian())
```

`uint16BigEndian()` is a private extension already inside the `Parsers` object — these
functions are inside the same object and can call it.

### 2. `BoardStateServiceImpl.kt` — add 3 collectors and call them in `start()`

Add these three private suspend functions:

```kotlin
private suspend fun collectPitch() {
    transport.notifications(OwUuids.PITCH).collect { bytes ->
        try {
            _state.update { it.copy(pitchDegrees = Parsers.pitch(bytes)) }
        } catch (e: IllegalArgumentException) {
            println("[BoardStateServiceImpl] PITCH: ${e.message}")
        }
    }
}

private suspend fun collectRoll() {
    transport.notifications(OwUuids.ROLL).collect { bytes ->
        try {
            _state.update { it.copy(rollDegrees = Parsers.roll(bytes)) }
        } catch (e: IllegalArgumentException) {
            println("[BoardStateServiceImpl] ROLL: ${e.message}")
        }
    }
}

private suspend fun collectYaw() {
    transport.notifications(OwUuids.YAW).collect { bytes ->
        try {
            _state.update { it.copy(yawDegrees = Parsers.yaw(bytes)) }
        } catch (e: IllegalArgumentException) {
            println("[BoardStateServiceImpl] YAW: ${e.message}")
        }
    }
}
```

In `start()`, add after the existing launches:

```kotlin
scope.launch { collectPitch() }
scope.launch { collectRoll() }
scope.launch { collectYaw() }
```

### 3. `ParsersTest.kt` — add 3 tests

```kotlin
@Test
fun `pitch at level returns 0 degrees`() {
    // value=1800 (0x0708): 0.1*(1800-1800)=0.0
    assertEquals(0.0, Parsers.pitch(hex("0708")))
}

@Test
fun `pitch forward lean returns positive degrees`() {
    // value=1710 (0x06AE): 0.1*(1800-1710)=9.0
    assertEquals(9.0, Parsers.pitch(hex("06ae")))
}

@Test
fun `pitch backward lean returns negative degrees`() {
    // value=1890 (0x0762): 0.1*(1800-1890)=-9.0
    assertEquals(-9.0, Parsers.pitch(hex("0762")))
}
```

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :core:test --tests "com.zwheel.core.protocol.ParsersTest" && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

All tests must pass.

Commit message: `feat(telemetry): wire pitch, roll, and yaw notifications into BoardState`
