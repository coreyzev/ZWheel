package com.zwheel.app.data.ride

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class RideDaoTest {

    private lateinit var db: ZWheelDatabase
    private lateinit var dao: RideDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ZWheelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.rideDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert session and retrieve by id`() = runBlocking {
        val session = session("s1")
        dao.insertSession(session)
        assertEquals(session, dao.getSession("s1"))
    }

    @Test
    fun `getOpenSession returns null when no open session`() = runBlocking {
        assertNull(dao.getOpenSession())
    }

    @Test
    fun `getOpenSession returns session with null endEpochMillis`() = runBlocking {
        dao.insertSession(session("s2", endEpochMillis = null))
        assertEquals("s2", dao.getOpenSession()?.id)
    }

    @Test
    fun `getOpenSession returns null after session is closed`() = runBlocking {
        val open = session("s3", endEpochMillis = null)
        dao.insertSession(open)
        dao.updateSession(open.copy(endEpochMillis = 9000L))
        assertNull(dao.getOpenSession())
    }

    @Test
    fun `insertPoint and retrieve points for session`() = runBlocking {
        dao.insertSession(session("s4"))
        val point = RideDataPointEntity(
            sessionId = "s4",
            epochMillis = 1000L,
            speedMetersPerSecondCorrected = 5.0,
            speedMetersPerSecondRaw = 4.8,
            rpm = 120.0,
            batteryPercent = 80,
            latitude = null,
            longitude = null,
            altitude = null,
            amps = 2.0,
            pitchDegrees = 1.0,
            rollDegrees = 0.5,
            controllerTempCelsius = 30.0,
            motorTempCelsius = 35.0,
        )
        dao.insertPoint(point)
        val points = dao.getPointsForSession("s4").first()
        assertEquals(1, points.size)
        assertEquals(1000L, points[0].epochMillis)
        assertEquals(5.0, points[0].speedMetersPerSecondCorrected)
    }

    @Test
    fun `getAllSessions returns sessions newest first`() = runBlocking {
        dao.insertSession(session("older", startEpochMillis = 1000L))
        dao.insertSession(session("newer", startEpochMillis = 2000L))
        val sessions = dao.getAllSessions().first()
        assertEquals("newer", sessions[0].id)
        assertEquals("older", sessions[1].id)
    }

    private fun session(
        id: String,
        startEpochMillis: Long = 1000L,
        endEpochMillis: Long? = 5000L,
    ) = RideSessionEntity(
        id = id,
        boardId = "board-1",
        startEpochMillis = startEpochMillis,
        endEpochMillis = endEpochMillis,
        maxSpeedMetersPerSecondCorrected = 0.0,
        distanceMetersCorrected = 0.0,
        distanceMetersRaw = 0.0,
        wattHoursUsed = null,
        notes = null,
    )
}
