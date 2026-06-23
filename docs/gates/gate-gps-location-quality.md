# Gate: GPS Location Quality — Accuracy Filter, Altitude, Lifecycle Cleanup

**Branch:** `codex/gps-location-quality`
**Base:** `main`

---

## Context

`RideForegroundService.startLocationUpdates()` already collects GPS fixes via
`FusedLocationProviderClient` and stores `lastLatitude`/`lastLongitude`. Three problems:

1. **No accuracy filter** — cold GPS fixes (tunnel exit, startup) have `accuracy` of hundreds of
   metres and get recorded as clean points. We must reject fixes worse than 30 m.
2. **Altitude not captured** — it is free from `Location.altitude` and useful for elevation later.
3. **Callback never removed** — `FusedLocationProviderClient.removeLocationUpdates()` is never
   called on service destroy, leaking the callback.

This gate fixes all three and writes the new `altitude` column to Room (migration 1→2).

---

## Allowed files (touch ONLY these)

```
core/src/main/kotlin/com/zwheel/core/model/BoardModels.kt
app/src/main/kotlin/com/zwheel/app/data/ride/RideEntities.kt
app/src/main/kotlin/com/zwheel/app/data/ride/ZWheelDatabase.kt
app/src/main/kotlin/com/zwheel/app/data/ride/RideRepository.kt
app/src/main/kotlin/com/zwheel/app/service/RideRecorder.kt
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt
```

Do NOT touch any other file.

---

## Implementation spec

### 1. `BoardModels.kt` — add `altitude` to `RideDataPoint`

Add `altitude: Double? = null` after `longitude`:

```kotlin
data class RideDataPoint(
    val sessionId: String,
    val epochMillis: Long,
    val speedMetersPerSecondCorrected: Double?,
    val speedMetersPerSecondRaw: Double?,
    val rpm: Double?,
    val batteryPercent: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,      // ← add this
    val amps: Double?,
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
    val controllerTempCelsius: Double?,
    val motorTempCelsius: Double?,
)
```

### 2. `RideEntities.kt` — add `altitude` column

Add `val altitude: Double?` after `longitude` in `RideDataPointEntity`:

```kotlin
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
    val altitude: Double?,      // ← add this
    val amps: Double?,
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
    val controllerTempCelsius: Double?,
    val motorTempCelsius: Double?,
)
```

### 3. `ZWheelDatabase.kt` — version 2 + migration

Replace the file entirely:

```kotlin
package com.zwheel.app.data.ride

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ride_point ADD COLUMN altitude REAL")
    }
}

@Database(
    entities = [RideSessionEntity::class, RideDataPointEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ZWheelDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
}
```

Also find where `ZWheelDatabase` is built (look for `Room.databaseBuilder` — it is in a Hilt
module file, likely `app/src/main/kotlin/com/zwheel/app/di/` or similar). Add
`.addMigrations(MIGRATION_1_2)` to the builder chain. Read those files to find the right place;
do NOT guess — search first. Touch that file only if you find the builder there.

### 4. `RideRepository.kt` — map `altitude`

In `RideDataPoint.toEntity()` add:
```kotlin
altitude = altitude,
```

In `RideDataPointEntity.toModel()` add:
```kotlin
altitude = altitude,
```

### 5. `RideForegroundService.kt` — accuracy filter, altitude, lifecycle

Add three new fields near `lastLatitude`/`lastLongitude`:

```kotlin
@Volatile private var lastAltitude: Double? = null
private var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null
private var locationCallback: com.google.android.gms.location.LocationCallback? = null
```

Rewrite `startLocationUpdates()`:

```kotlin
private fun startLocationUpdates() {
    val client = com.google.android.gms.location.LocationServices
        .getFusedLocationProviderClient(this)
    fusedLocationClient = client
    val request = com.google.android.gms.location.LocationRequest.Builder(
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
        5_000L,
    ).setMinUpdateIntervalMillis(2_000L).build()
    val callback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
            val loc = result.lastLocation ?: return
            if (loc.accuracy > 30f) return   // reject imprecise fixes
            lastLatitude = loc.latitude
            lastLongitude = loc.longitude
            lastAltitude = loc.altitude
        }
    }
    locationCallback = callback
    try {
        client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
    } catch (e: SecurityException) {
        // ACCESS_FINE_LOCATION not granted; GPS remains null.
    }
}
```

In `onDestroy()`, before `super.onDestroy()`, add:

```kotlin
locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
```

In `startRideRecorderTicker()`, update the `onTick` call to pass altitude:

```kotlin
recorder.onTick(rideServiceRepository.boardState.value, lastLatitude, lastLongitude, lastAltitude)
```

### 6. `RideRecorder.kt` — add `altitude` param

Change `onTick` signature:

```kotlin
suspend fun onTick(
    state: BoardState,
    latitude: Double? = null,
    longitude: Double? = null,
    altitude: Double? = null,
) {
```

Pass altitude to `RideDataPoint`:

```kotlin
repository.insertPoint(
    RideDataPoint(
        sessionId = sessionId,
        epochMillis = clock.nowEpochMillis(),
        speedMetersPerSecondCorrected = state.speedMetersPerSecondCorrected,
        speedMetersPerSecondRaw = state.speedMetersPerSecondRaw,
        rpm = state.rpm,
        batteryPercent = state.batteryPercent,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,   // ← add this
        amps = state.amps,
        pitchDegrees = state.pitchDegrees,
        rollDegrees = state.rollDegrees,
        controllerTempCelsius = state.controllerTempCelsius,
        motorTempCelsius = state.motorTempCelsius,
    ),
)
```

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin :core:test
```

Must pass cleanly.

Commit message: `feat(gps): accuracy filter, altitude capture, and callback lifecycle cleanup`
