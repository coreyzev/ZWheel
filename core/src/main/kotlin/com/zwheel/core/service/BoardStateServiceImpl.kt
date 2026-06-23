package com.zwheel.core.service

import com.zwheel.core.calc.RpmBased
import com.zwheel.core.model.BoardIdentity
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.BoardType
import com.zwheel.core.ports.BleTransport
import com.zwheel.core.ports.BoardStateService
import com.zwheel.core.ports.Clock
import com.zwheel.core.protocol.OwUuids
import com.zwheel.core.protocol.Parsers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BoardStateServiceImpl(
    private val transport: BleTransport,
    private val clock: Clock,
    private val boardType: BoardType,
    private val diameterInches: Double,
    private val stockDiameterInches: Double,
    private val boardIdentity: BoardIdentity? = null,
) : BoardStateService {

    private val _state = MutableStateFlow(BoardState())
    override val state: StateFlow<BoardState> = _state.asStateFlow()

    suspend fun start(scope: CoroutineScope) {
        _state.update { it.copy(identity = boardIdentity) }
        scope.launch { collectAmps() }
        scope.launch { collectTripAmpHours() }
        scope.launch { collectTripRegenAmpHours() }
        scope.launch { collectPackVoltage() }
        scope.launch { collectBatteryPercent() }
        scope.launch { collectTemperature() }
        scope.launch { collectBatteryTemperature() }
        scope.launch { collectSafetyHeadroom() }
        scope.launch { collectStatusError() }
        scope.launch { collectRideMode() }
        scope.launch { collectOdometer() }
        scope.launch { collectRpm() }
        scope.launch { collectCellVoltages() }
        scope.launch { collectPitch() }
        scope.launch { collectRoll() }
        scope.launch { collectYaw() }
    }

    private suspend fun collectRpm() {
        val calculator = RpmBased()
        transport.notifications(OwUuids.RPM).collect { bytes ->
            try {
                val rpm = Parsers.rpm(bytes).toDouble()
                val speed = calculator.correctedMetersPerSecond(rpm, null, diameterInches)
                _state.update { it.copy(rpm = rpm, speedMetersPerSecondCorrected = speed) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] RPM: ${e.message}")
            }
        }
    }

    private suspend fun collectAmps() {
        transport.notifications(OwUuids.AMPS).collect { bytes ->
            try {
                _state.update { it.copy(amps = Parsers.amps(bytes, boardType)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] AMPS: ${e.message}")
            }
        }
    }

    private suspend fun collectTripAmpHours() {
        transport.notifications(OwUuids.TRIP_TOTAL_AMP_HOURS).collect { bytes ->
            try {
                _state.update { it.copy(tripAmpHours = Parsers.tripAmpHours(bytes, boardType)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] TRIP_AMP_HOURS: ${e.message}")
            }
        }
    }

    private suspend fun collectTripRegenAmpHours() {
        transport.notifications(OwUuids.TRIP_REGEN_AMP_HOURS).collect { bytes ->
            try {
                _state.update { it.copy(tripRegenAmpHours = Parsers.tripRegenAmpHours(bytes, boardType)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] TRIP_REGEN_AMP_HOURS: ${e.message}")
            }
        }
    }

    private suspend fun collectPackVoltage() {
        transport.notifications(OwUuids.PACK_VOLTAGE).collect { bytes ->
            try {
                _state.update { it.copy(packVoltage = Parsers.packVoltage(bytes)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] PACK_VOLTAGE: ${e.message}")
            }
        }
    }

    private suspend fun collectCellVoltages() {
        val firmwareMajor = boardIdentity?.firmwareRevision?.toIntOrNull() ?: return
        val cellMap = mutableMapOf<Int, Double>()
        transport.notifications(OwUuids.CELL_VOLTAGES).collect { bytes ->
            try {
                val (cellIndex, voltage) = Parsers.cellVoltage(bytes, firmwareMajor)
                if (cellIndex < 15) {
                    cellMap[cellIndex] = voltage
                    _state.update { state ->
                        val sorted = cellMap.entries.sortedBy { it.key }.map { it.value }
                        state.copy(cellVoltages = sorted)
                    }
                }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] CELL_VOLTAGES: ${e.message}")
            }
        }
    }

    private suspend fun collectPitch() {
        transport.notifications(OwUuids.PITCH).collect { bytes ->
            try {
                _state.update { it.copy(pitchDegrees = Parsers.pitch(bytes)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] PITCH: ${e.message}")
            }
        }
    }

    private suspend fun collectRoll() {
        transport.notifications(OwUuids.ROLL).collect { bytes ->
            try {
                _state.update { it.copy(rollDegrees = Parsers.roll(bytes)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] ROLL: ${e.message}")
            }
        }
    }

    private suspend fun collectYaw() {
        transport.notifications(OwUuids.YAW).collect { bytes ->
            try {
                _state.update { it.copy(yawDegrees = Parsers.yaw(bytes)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] YAW: ${e.message}")
            }
        }
    }

    private suspend fun collectBatteryPercent() {
        try {
            val initial = transport.read(OwUuids.BATTERY_PERCENT)
            _state.update { it.copy(batteryPercent = Parsers.batteryPercent(initial)) }
        } catch (e: Exception) {
            println("[BoardStateServiceImpl] BATTERY_PERCENT initial read: ${e.message}")
        }
        try {
            transport.notifications(OwUuids.BATTERY_PERCENT).collect { bytes ->
                try {
                    _state.update { it.copy(batteryPercent = Parsers.batteryPercent(bytes)) }
                } catch (e: Exception) {
                    println("[BoardStateServiceImpl] BATTERY_PERCENT parse: ${e.message}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[BoardStateServiceImpl] BATTERY_PERCENT subscribe failed: ${e.message}")
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
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] TEMPERATURE: ${e.message}")
            }
        }
    }

    private suspend fun collectBatteryTemperature() {
        transport.notifications(OwUuids.BATTERY_TEMPERATURE).collect { bytes ->
            try {
                _state.update { it.copy(batteryTempCelsius = Parsers.batteryTemperature(bytes, boardType)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] BATTERY_TEMPERATURE: ${e.message}")
            }
        }
    }

    private suspend fun collectSafetyHeadroom() {
        transport.notifications(OwUuids.SAFETY_HEADROOM).collect { bytes ->
            try {
                _state.update { it.copy(safetyHeadroom = Parsers.safetyHeadroom(bytes)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] SAFETY_HEADROOM: ${e.message}")
            }
        }
    }

    private suspend fun collectStatusError() {
        transport.notifications(OwUuids.STATUS_ERROR).collect { bytes ->
            try {
                _state.update { it.copy(statusError = Parsers.statusError(bytes)) }
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] STATUS_ERROR: ${e.message}")
            }
        }
    }

    private suspend fun collectRideMode() {
        try {
            val initial = transport.read(OwUuids.RIDE_MODE)
            _state.update { it.copy(rideMode = Parsers.rideMode(initial, boardType)) }
        } catch (e: Exception) {
            println("[BoardStateServiceImpl] RIDE_MODE initial read: ${e.message}")
        }
        try {
            transport.notifications(OwUuids.RIDE_MODE).collect { bytes ->
                try {
                    _state.update { it.copy(rideMode = Parsers.rideMode(bytes, boardType)) }
                } catch (e: Exception) {
                    println("[BoardStateServiceImpl] RIDE_MODE parse: ${e.message}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[BoardStateServiceImpl] RIDE_MODE subscribe failed: ${e.message}")
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
            } catch (e: Exception) {
                println("[BoardStateServiceImpl] ODOMETER: ${e.message}")
            }
        }
    }
}
