package com.zwheel.core.service

import com.zwheel.core.calc.RpmBased
import com.zwheel.core.calc.ScaledFirmware
import com.zwheel.core.calc.SpeedCalculator
import com.zwheel.core.model.BoardState
import com.zwheel.core.ports.BleTransport
import com.zwheel.core.ports.BoardStateService
import com.zwheel.core.ports.Clock
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.Parsers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BoardStateServiceImpl(
    private val transport: BleTransport,
    private val clock: Clock,
    private val diameterInches: Double,
    private val stockDiameterInches: Double,
) : BoardStateService {

    private val _state = MutableStateFlow(BoardState())
    override val state: StateFlow<BoardState> = _state.asStateFlow()

    suspend fun start(scope: CoroutineScope) {
        scope.launch { collectAmps() }
        scope.launch { collectPackVoltage() }
        scope.launch { collectBatteryPercent() }
        scope.launch { collectTemperature() }
        scope.launch { collectRideMode() }
        scope.launch { collectOdometer() }
        scope.launch {
            val calculator = selectCalculator()
            collectRpm(calculator)
        }
    }

    private suspend fun selectCalculator(): SpeedCalculator {
        val firstRpm = withTimeoutOrNull(3_000L) {
            transport.notifications(OwUuids.RPM).first()
        }
        return if (firstRpm != null) RpmBased() else ScaledFirmware(stockDiameterInches)
    }

    private suspend fun collectRpm(calculator: SpeedCalculator) {
        transport.notifications(OwUuids.RPM).collect { bytes ->
            try {
                val rpm = Parsers.rpm(bytes).toDouble()
                val speed = calculator.correctedMetersPerSecond(rpm, null, diameterInches)
                _state.update { it.copy(rpm = rpm, speedMetersPerSecondCorrected = speed) }
            } catch (e: IllegalArgumentException) {
                println("[BoardStateServiceImpl] RPM: ${e.message}")
            }
        }
    }

    private suspend fun collectAmps() {
        transport.notifications(OwUuids.AMPS).collect { bytes ->
            try {
                _state.update { it.copy(amps = Parsers.amps(bytes)) }
            } catch (e: IllegalArgumentException) {
                println("[BoardStateServiceImpl] AMPS: ${e.message}")
            }
        }
    }

    private suspend fun collectPackVoltage() {
        transport.notifications(OwUuids.PACK_VOLTAGE).collect { bytes ->
            try {
                _state.update { it.copy(packVoltage = Parsers.packVoltage(bytes)) }
            } catch (e: IllegalArgumentException) {
                println("[BoardStateServiceImpl] PACK_VOLTAGE: ${e.message}")
            }
        }
    }

    private suspend fun collectBatteryPercent() {
        transport.notifications(OwUuids.BATTERY_PERCENT).collect { bytes ->
            try {
                _state.update { it.copy(batteryPercent = Parsers.batteryPercent(bytes)) }
            } catch (e: IllegalArgumentException) {
                println("[BoardStateServiceImpl] BATTERY_PERCENT: ${e.message}")
            }
        }
    }

    private suspend fun collectTemperature() {
        transport.notifications(OwUuids.TEMPERATURE).collect { bytes ->
            try {
                val (ctrl, motor) = Parsers.temperatures(bytes)
                _state.update {
                    it.copy(
                        controllerTempCelsius = ctrl.toDouble(),
                        motorTempCelsius = motor.toDouble(),
                    )
                }
            } catch (e: IllegalArgumentException) {
                println("[BoardStateServiceImpl] TEMPERATURE: ${e.message}")
            }
        }
    }

    private suspend fun collectRideMode() {
        transport.notifications(OwUuids.RIDE_MODE).collect { bytes ->
            try {
                _state.update { it.copy(rideMode = Parsers.rideMode(bytes)) }
            } catch (e: IllegalArgumentException) {
                println("[BoardStateServiceImpl] RIDE_MODE: ${e.message}")
            }
        }
    }

    private suspend fun collectOdometer() {
        var prevTicks: Int? = null
        var prevMillis: Long? = null
        transport.notifications(OwUuids.ODOMETER).collect { bytes ->
            try {
                val newTicks = Parsers.unsignedInt16(bytes)
                val newMillis = clock.nowEpochMillis()
                val prev = prevTicks
                val prevMs = prevMillis
                if (prev != null && prevMs != null) {
                    val deltaTicks = newTicks - prev
                    val deltaMs = newMillis - prevMs
                    if (deltaTicks > 0 && deltaMs > 0) {
                        val rawSpeed = (deltaTicks / 42.0) * 1609.344 / (deltaMs / 1000.0)
                        _state.update { it.copy(speedMetersPerSecondRaw = rawSpeed) }
                    }
                }
                prevTicks = newTicks
                prevMillis = newMillis
            } catch (e: IllegalArgumentException) {
                println("[BoardStateServiceImpl] ODOMETER: ${e.message}")
            }
        }
    }
}
