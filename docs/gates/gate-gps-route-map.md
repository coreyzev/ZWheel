# Gate: GPS Route Map — OSMDroid Polyline on Ride Detail Screen

**Branch:** `codex/gps-route-map`
**Base:** `main`

---

## Context

`RideDataPoint` already stores `latitude` and `longitude` per second of a ride session, persisted
in Room table `ride_point`. `RideDetailViewModel` loads the session stats but never queries the
data points. This gate adds an OSMDroid map view to `RideDetailScreen` showing the GPS route as
a polyline.

**Library choice:** OSMDroid + OpenStreetMap tiles — completely free, no API key, no account.
Attribution is required by OSM tile usage policy; OSMDroid renders it automatically.

The `app/build.gradle.kts` currently has a `verifyNetworkPermissionScoping` task that prevents
`INTERNET` from being in the main manifest. Map tiles are a deliberate production feature, so this
gate intentionally adds `INTERNET` to the main manifest and updates the task to reflect that.

---

## Allowed files (touch ONLY these)

```
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailViewModel.kt
app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailScreen.kt
```

Do NOT touch any other file.

---

## Implementation spec

### 1. `gradle/libs.versions.toml` — add OSMDroid

In `[versions]` add:
```toml
osmdroid = "6.1.20"
```

In `[libraries]` add:
```toml
osmdroid = { group = "org.osmdroid", name = "osmdroid-android", version.ref = "osmdroid" }
```

### 2. `app/build.gradle.kts` — add dependency + update INTERNET guard

In the `dependencies { }` block add:
```kotlin
implementation(libs.osmdroid)
```

Find the `verifyNetworkPermissionScoping` task. It currently fails if `INTERNET` is in the main
manifest. Map tiles are an intentional production feature. Replace the main-manifest check:

**Before:**
```kotlin
check(!mainManifest.asFile.readText().contains(internetPermission)) {
    "INTERNET permission must not be declared in app/src/main."
}
```

**After:** remove that `check(...)` line entirely. Keep the check that INTERNET must be in the
debug manifest, and the check that INTERNET must NOT be in the release manifest (if it exists).
The release build gets INTERNET through the main manifest merger, so it no longer needs its own
declaration — the release-manifest check is still valid as-is (it checks the release overlay only).

### 3. `app/src/main/AndroidManifest.xml` — add INTERNET

Add before the existing `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" ...>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 4. `RideDetailViewModel.kt` — load GPS points

Add `gpsPoints: List<Pair<Double, Double>> = emptyList()` to `RideDetailUiState`:

```kotlin
data class RideDetailUiState(
    val dateLabel: String,
    val boardId: String,
    val durationLabel: String,
    val distanceLabel: String,
    val topSpeedLabel: String,
    val avgSpeedLabel: String,
    val gpsPoints: List<Pair<Double, Double>> = emptyList(),   // ← add this
)
```

In the `init` block, after loading the session, also query points. Change the entire `init`:

```kotlin
init {
    viewModelScope.launch {
        val session = repository.getSession(sessionId) ?: return@launch
        val speedUnit = prefs.preferences.first().speedUnit
        val points = repository.getPointsForSession(sessionId).first()
        val gpsPoints = points.mapNotNull { p ->
            val lat = p.latitude ?: return@mapNotNull null
            val lon = p.longitude ?: return@mapNotNull null
            Pair(lat, lon)
        }
        _state.value = session.toUiState(speedUnit, gpsPoints)
    }
}
```

Add `import kotlinx.coroutines.flow.first` if not already present.

Update `toUiState()` signature to accept and pass `gpsPoints`:

```kotlin
private fun RideSession.toUiState(
    speedUnit: SpeedUnit,
    gpsPoints: List<Pair<Double, Double>> = emptyList(),
): RideDetailUiState {
    // ... existing body unchanged ...
    return RideDetailUiState(
        dateLabel = ...,
        boardId = boardId,
        durationLabel = durationLabel,
        distanceLabel = distanceLabel,
        topSpeedLabel = topSpeedLabel,
        avgSpeedLabel = avgSpeedLabel,
        gpsPoints = gpsPoints,     // ← add this
    )
}
```

### 5. `RideDetailScreen.kt` — add OSMDroid map

Add the following imports:
```kotlin
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
```

After the last `DetailRow(...)` call (currently `DetailRow(label = "Avg Speed", ...)`), add the
map block. Only render it when there are GPS points:

```kotlin
if (s.gpsPoints.isNotEmpty()) {
    val context = LocalContext.current
    val geoPoints = remember(s.gpsPoints) {
        s.gpsPoints.map { (lat, lon) -> GeoPoint(lat, lon) }
    }
    val mapView = remember {
        MapView(context).apply {
            Configuration.getInstance().userAgentValue = context.packageName
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isClickable = true
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }
    AndroidView(
        factory = { mapView },
        update = { mv ->
            mv.overlays.clear()
            val polyline = Polyline().apply { setPoints(geoPoints) }
            mv.overlays.add(polyline)
            val midpoint = geoPoints[geoPoints.size / 2]
            mv.controller.setZoom(15.5)
            mv.controller.setCenter(midpoint)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    )
}
```

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass cleanly.

Commit message: `feat(ui): show GPS route polyline on ride detail screen via OSMDroid`
