# Gate: Validate Corrected Speed and Distance Accumulation (issue #27)

**Branch:** `codex/issue27-speed-distance-tests`
**Base:** `main`

---

## Context

`RideRecorder` accumulates `distanceMetersCorrected` each tick and exposes
`tripDistanceMeters: StateFlow<Double>`. The existing `RideRecorderTest` only tests session
lifecycle (start/stop), not the actual distance and speed accumulation. This gate adds the
missing coverage.

`RpmBased.correctedMetersPerSecond()` already has basic tests in `RpmBasedTest.kt` but is
missing the corrected-diameter case that proves the tire-diameter calibration works.

---

## Allowed files (touch ONLY these)

```
app/src/test/kotlin/com/zwheel/app/service/RideRecorderTest.kt
core/src/test/kotlin/com/zwheel/core/calc/RpmBasedTest.kt
```

Do NOT touch any other file.

---

## Implementation spec

### 1. `RideRecorderTest.kt` — add accumulation and GPS tests

The existing `FakeRideDao` already captures inserted points in a `points` list.
The existing `startRiding()` helper runs 3 ticks at 1.0 m/s.

Add these tests:

```kotlin
@Test
fun `distance accumulates per tick at corrected speed`() = runBlocking {
    val dao = FakeRideDao()
    val recorder = RideRecorder(RideRepository(dao), FakeClock())
    startRiding(recorder)  // 3 ticks at 1.0 m/s — also starts session

    // 3 more ticks at 2.0 m/s — each adds 2.0 m
    val fast = boardStateAt(speedMps = 2.0)
    repeat(3) { recorder.onTick(fast) }

    // total: 3*1.0 + 3*2.0 = 9.0 m
    assertEquals(9.0, recorder.tripDistanceMeters.value, 0.001)
}

@Test
fun `tripDistanceMeters resets after endCurrentSession`() = runBlocking {
    val dao = FakeRideDao()
    val recorder = RideRecorder(RideRepository(dao), FakeClock())
    startRiding(recorder)
    recorder.endCurrentSession()

    assertEquals(0.0, recorder.tripDistanceMeters.value, 0.001)
}

@Test
fun `max speed is tracked and saved on session end`() = runBlocking {
    val dao = FakeRideDao()
    val recorder = RideRecorder(RideRepository(dao), FakeClock())
    startRiding(recorder)  // 3 ticks at 1.0 m/s

    recorder.onTick(boardStateAt(speedMps = 8.5))
    recorder.onTick(boardStateAt(speedMps = 5.0))
    recorder.endCurrentSession()

    assertEquals(8.5, dao.updatedSession?.maxSpeedMetersPerSecondCorrected ?: 0.0, 0.001)
}

@Test
fun `distance is saved to session on end`() = runBlocking {
    val dao = FakeRideDao()
    val recorder = RideRecorder(RideRepository(dao), FakeClock())
    startRiding(recorder)           // 3 ticks at 1.0 m/s = 3.0 m
    recorder.endCurrentSession()

    assertEquals(3.0, dao.updatedSession?.distanceMetersCorrected ?: 0.0, 0.001)
}

@Test
fun `GPS coordinates are stored in data points`() = runBlocking {
    val dao = FakeRideDao()
    val recorder = RideRecorder(RideRepository(dao), FakeClock())

    val riding = boardStateAt(speedMps = 1.0)
    repeat(2) { recorder.onTick(riding, latitude = null, longitude = null) }
    recorder.onTick(riding, latitude = 37.7749, longitude = -122.4194)

    // session starts on 3rd tick — that tick's point should have the GPS coords
    val points = dao.insertedPoints()
    val gpsPoint = points.firstOrNull { it.latitude != null }
    assertNotNull(gpsPoint, "expected at least one point with GPS coords")
    assertEquals(37.7749, gpsPoint!!.latitude ?: 0.0, 0.0001)
    assertEquals(-122.4194, gpsPoint.longitude ?: 0.0, 0.0001)
}
```

The `FakeRideDao` needs a way to expose all inserted points. Add to `FakeRideDao`:
```kotlin
fun insertedPoints(): List<RideDataPointEntity> = points.toList()
```

The existing `startRiding` helper is:
```kotlin
private suspend fun startRiding(recorder: RideRecorder) {
    val riding = boardStateAt(speedMps = 1.0)
    repeat(3) { recorder.onTick(riding) }
}
```

Add `import org.junit.jupiter.api.Assertions.assertNotNull` at the top if not already present.

### 2. `RpmBasedTest.kt` — add corrected-diameter test

Add one test proving the diameter correction scales speed proportionally:

```kotlin
@Test
fun `larger diameter produces proportionally higher speed at same RPM`() {
    val smallWheel = calculator.correctedMetersPerSecond(
        rpm = 500.0,
        firmwareSpeedMetersPerSecond = null,
        diameterInches = 10.5,
    )!!
    val largeWheel = calculator.correctedMetersPerSecond(
        rpm = 500.0,
        firmwareSpeedMetersPerSecond = null,
        diameterInches = 11.5,
    )!!
    // ratio should equal diameter ratio: 11.5 / 10.5 ≈ 1.0952
    assertEquals(11.5 / 10.5, largeWheel / smallWheel, 0.001)
}
```

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:test :core:test
```

All tests must pass. The new tests must appear in the output and pass.

Commit message: `test(m2): validate corrected speed, distance accumulation, and GPS passthrough`

Closes #27
