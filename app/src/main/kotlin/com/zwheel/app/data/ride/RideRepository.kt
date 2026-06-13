package com.zwheel.app.data.ride

import com.zwheel.core.model.RideDataPoint
import com.zwheel.core.model.RideSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RideRepository @Inject constructor(private val dao: RideDao) {

    suspend fun startSession(session: RideSession) = dao.insertSession(session.toEntity())

    suspend fun updateSession(session: RideSession) = dao.updateSession(session.toEntity())

    suspend fun getOpenSession(): RideSession? = dao.getOpenSession()?.toModel()

    fun getAllSessions(): Flow<List<RideSession>> =
        dao.getAllSessions().map { list -> list.map { it.toModel() } }

    suspend fun insertPoint(point: RideDataPoint) = dao.insertPoint(point.toEntity())

    fun getPointsForSession(sessionId: String): Flow<List<RideDataPoint>> =
        dao.getPointsForSession(sessionId).map { list -> list.map { it.toModel() } }
}

private fun RideSession.toEntity() = RideSessionEntity(
    id = id,
    boardId = boardId,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    maxSpeedMetersPerSecondCorrected = maxSpeedMetersPerSecondCorrected,
    distanceMetersCorrected = distanceMetersCorrected,
    distanceMetersRaw = distanceMetersRaw,
    wattHoursUsed = wattHoursUsed,
    notes = notes,
)

private fun RideSessionEntity.toModel() = RideSession(
    id = id,
    boardId = boardId,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    maxSpeedMetersPerSecondCorrected = maxSpeedMetersPerSecondCorrected,
    distanceMetersCorrected = distanceMetersCorrected,
    distanceMetersRaw = distanceMetersRaw,
    wattHoursUsed = wattHoursUsed,
    notes = notes,
)

private fun RideDataPoint.toEntity() = RideDataPointEntity(
    sessionId = sessionId,
    epochMillis = epochMillis,
    speedMetersPerSecondCorrected = speedMetersPerSecondCorrected,
    speedMetersPerSecondRaw = speedMetersPerSecondRaw,
    rpm = rpm,
    batteryPercent = batteryPercent,
    latitude = latitude,
    longitude = longitude,
    amps = amps,
    pitchDegrees = pitchDegrees,
    rollDegrees = rollDegrees,
    controllerTempCelsius = controllerTempCelsius,
    motorTempCelsius = motorTempCelsius,
)

private fun RideDataPointEntity.toModel() = RideDataPoint(
    sessionId = sessionId,
    epochMillis = epochMillis,
    speedMetersPerSecondCorrected = speedMetersPerSecondCorrected,
    speedMetersPerSecondRaw = speedMetersPerSecondRaw,
    rpm = rpm,
    batteryPercent = batteryPercent,
    latitude = latitude,
    longitude = longitude,
    amps = amps,
    pitchDegrees = pitchDegrees,
    rollDegrees = rollDegrees,
    controllerTempCelsius = controllerTempCelsius,
    motorTempCelsius = motorTempCelsius,
)
