package com.zwheel.app.service

import com.zwheel.app.data.ride.RideRepository
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.RideDataPoint
import com.zwheel.core.model.RideSession
import com.zwheel.core.ports.Clock
import java.util.UUID

private const val START_SPEED_THRESHOLD_METERS_PER_SECOND = 0.67
private const val STOP_SPEED_THRESHOLD_METERS_PER_SECOND = 0.5
private const val START_THRESHOLD_SECONDS = 3
private const val STOP_THRESHOLD_SECONDS = 90

internal class RideRecorder(
    private val repository: RideRepository,
    private val clock: Clock,
) {
    private var currentSessionId: String? = null
    private var currentSession: RideSession? = null
    private var speedAboveThresholdCounterSeconds: Int = 0
    private var speedBelowThresholdCounterSeconds: Int = 0
    private var maxSpeedMetersPerSecondCorrected: Double = 0.0
    private var distanceMetersCorrected: Double = 0.0

    suspend fun onTick(state: BoardState) {
        val speedMetersPerSecond = state.speedMetersPerSecondCorrected ?: 0.0

        if (speedMetersPerSecond > START_SPEED_THRESHOLD_METERS_PER_SECOND) {
            speedAboveThresholdCounterSeconds++
        } else {
            speedAboveThresholdCounterSeconds = 0
        }

        if (speedMetersPerSecond < STOP_SPEED_THRESHOLD_METERS_PER_SECOND) {
            speedBelowThresholdCounterSeconds++
        } else {
            speedBelowThresholdCounterSeconds = 0
        }

        if (
            speedAboveThresholdCounterSeconds >= START_THRESHOLD_SECONDS &&
            currentSessionId == null
        ) {
            startSession(state)
        }

        val sessionId = currentSessionId ?: return
        val correctedSpeed = state.speedMetersPerSecondCorrected
        distanceMetersCorrected += (correctedSpeed ?: 0.0) * 1.0
        maxSpeedMetersPerSecondCorrected = maxOf(
            maxSpeedMetersPerSecondCorrected,
            correctedSpeed ?: 0.0,
        )

        repository.insertPoint(
            RideDataPoint(
                sessionId = sessionId,
                epochMillis = clock.nowEpochMillis(),
                speedMetersPerSecondCorrected = state.speedMetersPerSecondCorrected,
                speedMetersPerSecondRaw = state.speedMetersPerSecondRaw,
                rpm = state.rpm,
                batteryPercent = state.batteryPercent,
                // TODO(m3): wire FusedLocationProviderClient
                latitude = null,
                longitude = null,
                amps = state.amps,
                pitchDegrees = state.pitchDegrees,
                rollDegrees = state.rollDegrees,
                controllerTempCelsius = state.controllerTempCelsius,
                motorTempCelsius = state.motorTempCelsius,
            ),
        )

        if (
            speedBelowThresholdCounterSeconds >= STOP_THRESHOLD_SECONDS &&
            currentSessionId != null
        ) {
            endCurrentSession()
        }
    }

    suspend fun endCurrentSession() {
        val session = currentSession ?: return
        repository.updateSession(
            session.copy(
                endEpochMillis = clock.nowEpochMillis(),
                maxSpeedMetersPerSecondCorrected = maxSpeedMetersPerSecondCorrected,
                distanceMetersCorrected = distanceMetersCorrected,
            ),
        )
        currentSessionId = null
        currentSession = null
        speedAboveThresholdCounterSeconds = 0
        speedBelowThresholdCounterSeconds = 0
        maxSpeedMetersPerSecondCorrected = 0.0
        distanceMetersCorrected = 0.0
    }

    private suspend fun startSession(state: BoardState) {
        val session = RideSession(
            id = UUID.randomUUID().toString(),
            boardId = state.identity?.boardId ?: "unknown",
            startEpochMillis = clock.nowEpochMillis(),
        )
        repository.startSession(session)
        currentSessionId = session.id
        currentSession = session
        speedBelowThresholdCounterSeconds = 0
        maxSpeedMetersPerSecondCorrected = 0.0
        distanceMetersCorrected = 0.0
    }
}
