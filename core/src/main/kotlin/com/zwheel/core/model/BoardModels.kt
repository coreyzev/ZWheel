package com.zwheel.core.model

enum class BoardType(
    val displayName: String,
    val stockTireDiameterInches: Double,
) {
    ONEWHEEL_V1("Onewheel V1", 11.5),
    PLUS("Onewheel+", 11.5),
    XR("Onewheel+ XR", 11.5),
    PINT("Onewheel Pint", 10.5),
    PINT_X("Onewheel Pint X", 10.5),
    UNKNOWN("Unknown stock Onewheel", 11.5),
}

enum class ConnectionState {
    IDLE,
    SCANNING,
    CONNECTING,
    DISCOVERING_SERVICES,
    HANDSHAKING,
    SUBSCRIBED,
    DEGRADED,
    RECONNECTING,
    DISCONNECTED,
}

enum class RideMode {
    CLASSIC,
    CRUZ,
    CUSTOM,
    DELIRIUM,
    ELEVATED,
    EXTREME,
    MISSION,
    SEQUOIA,
    UNKNOWN,
}

data class BoardIdentity(
    val boardId: String,
    val name: String,
    val type: BoardType,
    val serialNumber: String? = null,
    val firmwareRevision: String? = null,
    val hardwareRevision: String? = null,
)

data class BoardState(
    val identity: BoardIdentity? = null,
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val speedMetersPerSecondCorrected: Double? = null,
    val speedMetersPerSecondRaw: Double? = null,
    val rpm: Double? = null,
    val batteryPercent: Int? = null,
    val packVoltage: Double? = null,
    val cellVoltages: List<Double> = emptyList(),
    val amps: Double? = null,
    val tripAmpHours: Double? = null,
    val tripRegenAmpHours: Double? = null,
    val pitchDegrees: Double? = null,
    val rollDegrees: Double? = null,
    val yawDegrees: Double? = null,
    val controllerTempCelsius: Double? = null,
    val motorTempCelsius: Double? = null,
    val batteryTempCelsius: Int? = null,
    val safetyHeadroom: Int? = null,
    val statusError: Int? = null,
    val rideMode: RideMode = RideMode.UNKNOWN,
    val lightsOn: Boolean? = null,
)

data class RideSession(
    val id: String,
    val boardId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long? = null,
    val maxSpeedMetersPerSecondCorrected: Double = 0.0,
    val distanceMetersCorrected: Double = 0.0,
    val distanceMetersRaw: Double = 0.0,
    val wattHoursUsed: Double? = null,
    val notes: String? = null,
)

data class RideDataPoint(
    val sessionId: String,
    val epochMillis: Long,
    val speedMetersPerSecondCorrected: Double?,
    val speedMetersPerSecondRaw: Double?,
    val rpm: Double?,
    val batteryPercent: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val amps: Double?,
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
    val controllerTempCelsius: Double?,
    val motorTempCelsius: Double?,
)

data class WatchPayload(
    val speedMetersPerSecondCorrected: Double?,
    val topSpeedMetersPerSecond: Double,
    val batteryPercent: Int?,
    val estimatedRangeMeters: Double?,
    val speedUnit: SpeedUnit,
    val isRiding: Boolean,
    val connectionState: ConnectionState,
)

enum class SpeedUnit {
    MPH,
    KPH,
}

enum class TemperatureUnit {
    FAHRENHEIT,
    CELSIUS,
}
