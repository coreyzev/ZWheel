package com.zwheel.app.service

import com.zwheel.app.data.ride.RideDao
import com.zwheel.app.data.ride.RideDataPointEntity
import com.zwheel.app.data.ride.RideRepository
import com.zwheel.app.data.ride.RideSessionEntity
import com.zwheel.core.model.BoardState
import com.zwheel.core.ports.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RideRecorderTest {
    @Test
    fun `session starts after 3 seconds above speed threshold`() = runBlocking {
        val dao = FakeRideDao()
        val recorder = RideRecorder(RideRepository(dao), FakeClock())
        var isRiding = false
        recorder.onSessionChanged = { isRiding = it }

        val riding = boardStateAt(speedMps = 1.0)
        repeat(2) { recorder.onTick(riding) }
        assertNull(dao.openSession, "no session before threshold")

        recorder.onTick(riding)
        assertNotNull(dao.openSession, "session starts at tick 3")
        assertEquals(true, isRiding)
    }

    @Test
    fun `long pause does not end the session`() = runBlocking {
        val dao = FakeRideDao()
        val recorder = RideRecorder(RideRepository(dao), FakeClock())
        startRiding(recorder)

        val stopped = boardStateAt(speedMps = 0.0)
        repeat(120) { recorder.onTick(stopped) }

        assertNotNull(dao.openSession, "session survives 120 ticks at zero speed")
        assertNull(dao.openSession?.endEpochMillis, "session not ended")
    }

    @Test
    fun `endCurrentSession closes the session`() = runBlocking {
        val dao = FakeRideDao()
        val recorder = RideRecorder(RideRepository(dao), FakeClock())
        var isRiding: Boolean? = null
        recorder.onSessionChanged = { isRiding = it }

        startRiding(recorder)
        recorder.endCurrentSession()

        assertNotNull(dao.updatedSession, "session was updated with end time")
        assertNotNull(dao.updatedSession?.endEpochMillis)
        assertEquals(false, isRiding)
    }

    @Test
    fun `speed threshold reset restarts start counter`() = runBlocking {
        val dao = FakeRideDao()
        val recorder = RideRecorder(RideRepository(dao), FakeClock())

        val riding = boardStateAt(speedMps = 1.0)
        val stopped = boardStateAt(speedMps = 0.0)

        repeat(2) { recorder.onTick(riding) }
        recorder.onTick(stopped)
        repeat(3) { recorder.onTick(riding) }

        assertNotNull(dao.openSession, "session starts after fresh 3-tick run")
    }

    @Test
    fun `distance accumulates per tick at corrected speed`() = runBlocking {
        val dao = FakeRideDao()
        val recorder = RideRecorder(RideRepository(dao), FakeClock())
        startRiding(recorder)

        val fast = boardStateAt(speedMps = 2.0)
        repeat(3) { recorder.onTick(fast) }

        // startRiding: tick 3 opens session and accumulates 1.0m; 3 ticks at 2.0m/s = 6.0m; total = 7.0m
        assertEquals(7.0, recorder.tripDistanceMeters.value, 0.001)
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
        startRiding(recorder)

        recorder.onTick(boardStateAt(speedMps = 8.5))
        recorder.onTick(boardStateAt(speedMps = 5.0))
        recorder.endCurrentSession()

        assertEquals(8.5, dao.updatedSession?.maxSpeedMetersPerSecondCorrected ?: 0.0, 0.001)
    }

    @Test
    fun `distance is saved to session on end`() = runBlocking {
        val dao = FakeRideDao()
        val recorder = RideRecorder(RideRepository(dao), FakeClock())
        startRiding(recorder)
        recorder.endCurrentSession()

        // startRiding: only tick 3 (session-open tick) accumulates distance = 1.0m
        assertEquals(1.0, dao.updatedSession?.distanceMetersCorrected ?: 0.0, 0.001)
    }

    @Test
    fun `GPS coordinates are stored in data points`() = runBlocking {
        val dao = FakeRideDao()
        val recorder = RideRecorder(RideRepository(dao), FakeClock())

        val riding = boardStateAt(speedMps = 1.0)
        repeat(2) { recorder.onTick(riding, latitude = null, longitude = null) }
        recorder.onTick(riding, latitude = 37.7749, longitude = -122.4194)

        val points = dao.insertedPoints()
        val gpsPoint = points.firstOrNull { it.latitude != null }
        assertNotNull(gpsPoint, "expected at least one point with GPS coords")
        assertEquals(37.7749, gpsPoint!!.latitude ?: 0.0, 0.0001)
        assertEquals(-122.4194, gpsPoint.longitude ?: 0.0, 0.0001)
    }

    private suspend fun startRiding(recorder: RideRecorder) {
        val riding = boardStateAt(speedMps = 1.0)
        repeat(3) { recorder.onTick(riding) }
    }

    private fun boardStateAt(speedMps: Double) = BoardState(
        speedMetersPerSecondCorrected = speedMps,
    )
}

private class FakeRideDao : RideDao {
    var openSession: RideSessionEntity? = null
    var updatedSession: RideSessionEntity? = null
    private val points = mutableListOf<RideDataPointEntity>()

    override suspend fun insertSession(session: RideSessionEntity) {
        openSession = session
    }

    override suspend fun updateSession(session: RideSessionEntity) {
        updatedSession = session
        openSession = session
    }

    override suspend fun getSession(id: String): RideSessionEntity? =
        openSession?.takeIf { it.id == id }

    override suspend fun getOpenSession(): RideSessionEntity? =
        openSession?.takeIf { it.endEpochMillis == null }

    override fun getAllSessions(): Flow<List<RideSessionEntity>> =
        flowOf(listOfNotNull(openSession))

    override suspend fun insertPoint(point: RideDataPointEntity) {
        points += point
    }

    fun insertedPoints(): List<RideDataPointEntity> = points.toList()

    override fun getPointsForSession(sessionId: String): Flow<List<RideDataPointEntity>> =
        flowOf(points.filter { it.sessionId == sessionId })
}

private class FakeClock : Clock {
    private var millis = 1_000_000L

    override fun nowEpochMillis(): Long = millis++
}
