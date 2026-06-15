package com.zwheel.app.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
}
