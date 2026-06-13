# Gate: M3 — GPS Location Capture in Ride Recording

**Branch:** `codex/m3-gps-location`
**One concern:** Capture GPS latitude/longitude in `RideDataPoint` during active ride sessions. The manifest already has `ACCESS_FINE_LOCATION` and `FOREGROUND_SERVICE_LOCATION`; the service is declared with `foregroundServiceType="connectedDevice|location"`.

---

## Problem

`RideDataPoint.latitude` and `RideDataPoint.longitude` are always `null` (see `// TODO(m3): wire FusedLocationProviderClient` in `RideRecorder.kt`). Ride routes cannot be replayed or mapped.

---

## Allowed files (touch ONLY these)

```
gradle/libs.versions.toml                                                       ← add play-services-location
app/build.gradle.kts                                                            ← add implementation dep
app/src/main/kotlin/com/zwheel/app/service/RideRecorder.kt                     ← add lat/lng params to onTick
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt            ← request location updates, pass to onTick
```

---

## Implementation spec

### 1. `gradle/libs.versions.toml`

Under `[versions]`:
```
playServicesLocation = "21.3.0"
```

Under `[libraries]`:
```
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
```

### 2. `app/build.gradle.kts`

Add to dependencies:
```kotlin
implementation(libs.play.services.location)
```

### 3. `RideRecorder.kt` — update `onTick` signature

Change:
```kotlin
suspend fun onTick(state: BoardState) {
```
To:
```kotlin
suspend fun onTick(state: BoardState, latitude: Double? = null, longitude: Double? = null) {
```

In the `insertPoint(...)` call, replace the `// TODO(m3)` lines:
```kotlin
latitude = null,
longitude = null,
```
With:
```kotlin
latitude = latitude,
longitude = longitude,
```

Remove the `// TODO(m3): wire FusedLocationProviderClient` comment.

### 4. `RideForegroundService.kt` — request location and pass to `onTick`

Add two volatile fields after the existing fields:
```kotlin
@Volatile private var lastLatitude: Double? = null
@Volatile private var lastLongitude: Double? = null
```

In `onCreate()`, after `wearDataLayerRepository.startSync(lifecycleScope)`, call:
```kotlin
startLocationUpdates()
```

Add the `startLocationUpdates()` method:
```kotlin
private fun startLocationUpdates() {
    val fusedClient = com.google.android.gms.location.LocationServices
        .getFusedLocationProviderClient(this)
    val request = com.google.android.gms.location.LocationRequest.Builder(
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
        5_000L,
    ).setMinUpdateIntervalMillis(2_000L).build()
    val callback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
            val loc = result.lastLocation ?: return
            lastLatitude = loc.latitude
            lastLongitude = loc.longitude
        }
    }
    try {
        fusedClient.requestLocationUpdates(
            request,
            callback,
            android.os.Looper.getMainLooper(),
        )
    } catch (e: SecurityException) {
        // ACCESS_FINE_LOCATION not granted; lat/lng remain null
    }
}
```

In `startRideRecorderTicker()`, update the `onTick` call:
```kotlin
recorder.onTick(rideServiceRepository.boardState.value, lastLatitude, lastLongitude)
```

Add import:
```kotlin
import android.annotation.SuppressLint
```

No `@SuppressLint("MissingPermission")` needed because the try/catch on `SecurityException` handles the missing permission case gracefully.

---

## Constraints

- Do NOT add a runtime permission request — `ACCESS_FINE_LOCATION` is already handled by the BLE scan flow in the UI. The service silently skips location if not granted.
- The `FusedLocationProviderClient` is NOT injected via Hilt — use `LocationServices.getFusedLocationProviderClient(context)` directly in the service (avoids a Play Services Hilt module).
- `@Volatile` on `lastLatitude`/`lastLongitude` is required since they are written from the main looper callback and read from the `lifecycleScope` coroutine dispatcher.
- Location updates are intentionally NOT stopped in `onDestroy()` for simplicity — the `FusedLocationProviderClient` will auto-stop when the process dies. If you want to stop them cleanly, store the callback reference and call `fusedClient.removeLocationUpdates(callback)` in `onDestroy()`.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin --no-daemon
```

Must pass. Fix any errors.
