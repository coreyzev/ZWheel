# Gate: Issue #46 — Keep Ride Session Open Across Breaks

**Branch:** `codex/issue46-ride-breaks`
**Base:** `main`
**Closes:** #46
**One concern:** Remove idle-time auto-end from `RideRecorder` so ride sessions stay open during breaks and only end on disconnect or explicit stop.

---

## Context

`RideRecorder.onTick()` currently auto-ends a session after `STOP_THRESHOLD_SECONDS` (90) ticks
below `STOP_SPEED_THRESHOLD_METERS_PER_SECOND`. This splits one real ride into multiple sessions
whenever Corey pauses. The fix: remove idle-auto-end entirely. Session ends only when
`endCurrentSession()` is called explicitly (service destroy, future manual-stop UI).

`endCurrentSession()` is already called from `RideForegroundService.onDestroy()`. No change
needed there. Auto-start on riding is preserved.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/service/RideRecorder.kt      ← remove auto-end
app/src/test/kotlin/com/zwheel/app/service/RideRecorderTest.kt  ← NEW — add tests
```

Do NOT touch `RideForegroundService.kt`, `core/`, or any other file.

---

## Implementation spec

### 1. `RideRecorder.kt` — remove idle-auto-end

Delete:
- The constant `private const val STOP_THRESHOLD_SECONDS = 90`
- The field `private var speedBelowThresholdCounterSeconds: Int = 0`
- The two lines in `onTick` that increment/reset `speedBelowThresholdCounterSeconds`
- The entire auto-end block at the bottom of `onTick`:
  ```kotlin
  if (
      speedBelowThresholdCounterSeconds >= STOP_THRESHOLD_SECONDS &&
      currentSessionId != null
  ) {
      endCurrentSession()
  }
  ```
- The reset of `speedBelowThresholdCounterSeconds = 0` inside `endCurrentSession()`
  (it no longer exists)
- The reset of `speedBelowThresholdCounterSeconds = 0` inside `startSession()`
  (it no longer exists)

Keep everything else unchanged: `START_SPEED_THRESHOLD_METERS_PER_SECOND`,
`STOP_SPEED_THRESHOLD_METERS_PER_SECOND` (still needed — delete this one too,
it is only referenced by the removed block), `START_THRESHOLD_SECONDS`,
`speedAboveThresholdCounterSeconds`, `startSession()`, `endCurrentSession()`,
`onSessionChanged`, repository writes, `distanceMetersCorrected`,
`maxSpeedMetersPerSecondCorrected`.

Actually scan carefully: `STOP_SPEED_THRESHOLD_METERS_PER_SECOND` feeds
`speedBelowThresholdCounterSeconds` which is being removed. Delete it too.

After the edit `RideRecorder` should have NO reference to idle/stop threshold or
below-threshold counter.

### 2. `RideRecorderTest.kt` — NEW

Create `app/src/test/kotlin/com/zwheel/app/service/RideRecorderTest.kt`.

`RideRecorder` depends on `RideRepository` and `Clock`. Use simple fakes:

```kotlin
package com.zwheel.app.service

import com.zwheel.app.data.ride.RideRepository
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.RideDataPoint
import com.zwheel.core.model.RideSession
import com.zwheel.core.ports.Clock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RideRecorderTest {

    @Test
    fun `session starts after 3 seconds above speed threshold`() = runTest {
        val repo = FakeRideRepository()
        val clock = FakeClock()
        val recorder = RideRecorder(repo, clock)
        var isRiding = false
        recorder.onSessionChanged = { isRiding = it }

        val riding = boardStateAt(speedMps = 1.0)
        repeat(2) { recorder.onTick(riding) }
        assertNull(repo.currentSession, "no session before threshold")

        recorder.onTick(riding)
        assertNotNull(repo.currentSession, "session starts at tick 3")
        assertEquals(true, isRiding)
    }

    @Test
    fun `long pause does not end the session`() = runTest {
        val repo = FakeRideRepository()
        val recorder = RideRecorder(repo, FakeClock())
        startRiding(recorder)

        val stopped = boardStateAt(speedMps = 0.0)
        repeat(120) { recorder.onTick(stopped) }

        assertNotNull(repo.currentSession, "session survives 120 ticks at zero speed")
        assertNull(repo.currentSession?.endEpochMillis, "session not ended")
    }

    @Test
    fun `endCurrentSession closes the session`() = runTest {
        val repo = FakeRideRepository()
        val recorder = RideRecorder(repo, FakeClock())
        var isRiding: Boolean? = null
        recorder.onSessionChanged = { isRiding = it }

        startRiding(recorder)
        recorder.endCurrentSession()

        assertNotNull(repo.updatedSession, "session was updated with end time")
        assertNotNull(repo.updatedSession?.endEpochMillis)
        assertEquals(false, isRiding)
    }

    @Test
    fun `speed threshold reset restarts start counter`() = runTest {
        val repo = FakeRideRepository()
        val recorder = RideRecorder(repo, FakeClock())

        val riding = boardStateAt(speedMps = 1.0)
        val stopped = boardStateAt(speedMps = 0.0)

        repeat(2) { recorder.onTick(riding) }
        recorder.onTick(stopped)      // resets above-threshold counter
        repeat(3) { recorder.onTick(riding) }
        assertNotNull(repo.currentSession, "session starts after fresh 3-tick run")
    }

    // ---- helpers ----

    private suspend fun startRiding(recorder: RideRecorder) {
        val riding = boardStateAt(speedMps = 1.0)
        repeat(3) { recorder.onTick(riding) }
    }

    private fun boardStateAt(speedMps: Double) = BoardState(
        speedMetersPerSecondCorrected = speedMps,
    )
}

private class FakeRideRepository : RideRepository {
    var currentSession: RideSession? = null
    var updatedSession: RideSession? = null

    override suspend fun startSession(session: RideSession) {
        currentSession = session
    }

    override suspend fun updateSession(session: RideSession) {
        updatedSession = session
        currentSession = session
    }

    override suspend fun insertPoint(point: RideDataPoint) = Unit

    // Implement remaining abstract members with no-ops
    override fun getAllSessions() = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getSessionById(id: String): RideSession? = null
    override fun getPointsForSession(sessionId: String) = kotlinx.coroutines.flow.flowOf(emptyList<RideDataPoint>())
}

private class FakeClock : Clock {
    private var millis = 1_000_000L
    override fun nowEpochMillis(): Long = millis++
}
```

If `RideRepository` has a different interface shape (different method names/signatures),
adapt the fake to match what actually exists. Read `RideRepository.kt` and `RideDao.kt`
briefly to confirm the interface before writing the fake.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin :app:test --tests "com.zwheel.app.service.RideRecorderTest"
```

All 4 tests must pass. Fix any compilation errors before committing.

Commit message: `fix(recording): keep ride session open across breaks until disconnect (#46)`
