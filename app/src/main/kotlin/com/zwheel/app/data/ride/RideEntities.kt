package com.zwheel.app.data.ride

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ride_session")
data class RideSessionEntity(
    @PrimaryKey val id: String,
    val boardId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
    val maxSpeedMetersPerSecondCorrected: Double,
    val distanceMetersCorrected: Double,
    val distanceMetersRaw: Double,
    val wattHoursUsed: Double?,
    val notes: String?,
)

@Entity(tableName = "ride_point", indices = [Index("sessionId")])
data class RideDataPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val epochMillis: Long,
    val speedMetersPerSecondCorrected: Double?,
    val speedMetersPerSecondRaw: Double?,
    val rpm: Double?,
    val batteryPercent: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val amps: Double?,
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
    val controllerTempCelsius: Double?,
    val motorTempCelsius: Double?,
)
