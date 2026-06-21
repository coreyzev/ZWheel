package com.zwheel.app.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Singleton
class RideServiceRepository @Inject constructor() {

    private val _isRiding = MutableStateFlow(false)
    val isRiding: StateFlow<Boolean> = _isRiding.asStateFlow()

    private val _tripDistanceMeters = MutableStateFlow(0.0)
    val tripDistanceMeters: StateFlow<Double> = _tripDistanceMeters.asStateFlow()

    private val _gpsLocked = MutableStateFlow(false)
    val gpsLocked: StateFlow<Boolean> = _gpsLocked.asStateFlow()

    private val _topSpeedMetersPerSecond = MutableStateFlow(0.0)
    val topSpeedMetersPerSecond: StateFlow<Double> = _topSpeedMetersPerSecond.asStateFlow()

    private val _rideStartEpochMillis = MutableStateFlow<Long?>(null)

    /** Milliseconds since the current session started, or null if not riding. */
    private val _rideElapsedMillis = MutableStateFlow(0L)
    val rideElapsedMillis: StateFlow<Long> = _rideElapsedMillis.asStateFlow()

    /**
     * Live average speed in m/s: tripDistanceMeters / elapsed seconds.
     * Returns 0.0 when not riding or elapsed < 1 s to avoid division by zero.
     */
    val avgSpeedMetersPerSecond: StateFlow<Double> = combine(
        _tripDistanceMeters,
        _rideElapsedMillis,
    ) { dist, elapsedMs ->
        val elapsedSec = elapsedMs / 1_000.0
        if (elapsedSec < 1.0) 0.0 else dist / elapsedSec
    }.stateIn(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        started = SharingStarted.Eagerly,
        initialValue = 0.0,
    )

    internal fun updateTopSpeed(speedMps: Double) {
        _topSpeedMetersPerSecond.value = speedMps
    }

    internal fun updateIsRiding(riding: Boolean) {
        _isRiding.value = riding
    }

    internal fun updateTripDistance(meters: Double) {
        _tripDistanceMeters.value = meters
    }

    internal fun updateGpsLock(locked: Boolean) {
        _gpsLocked.value = locked
    }

    internal fun markRideStarted(epochMillis: Long) {
        _rideStartEpochMillis.value = epochMillis
        _rideElapsedMillis.value = 0L
    }

    internal fun tickElapsed(nowEpochMillis: Long) {
        val start = _rideStartEpochMillis.value ?: return
        _rideElapsedMillis.value = (nowEpochMillis - start).coerceAtLeast(0L)
    }

    internal fun markRideStopped() {
        _rideStartEpochMillis.value = null
        _rideElapsedMillis.value = 0L
    }
}
