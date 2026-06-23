# Gate: Full-Screen Map Tap-Through from Ride Detail (issue #63)

**Branch:** `codex/map-fullscreen`
**Base:** `main`

---

## Context

The ride detail screen shows a 300dp OSMDroid map. Tapping it should navigate to a
full-screen map view. This gate adds:
1. A new `MapFullScreenScreen` composable and `MapFullScreenViewModel`
2. The nav route `rideDetail/{sessionId}/map`
3. A tap handler on the existing map card in `RideDetailScreen`

`gpsPoints` is `List<Triple<Double, Double, Double?>>` (lat, lon, speedMps) — must be
re-queried from Room in the new ViewModel (can't pass a list through nav args).

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/ui/history/MapFullScreenScreen.kt   ← NEW FILE
app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailScreen.kt
app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt
```

Do NOT touch any other file.

---

## Implementation spec

### 1. `MapFullScreenScreen.kt` — create new file

```kotlin
package com.zwheel.app.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zwheel.app.data.ride.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import javax.inject.Inject

@HiltViewModel
class MapFullScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RideRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _gpsPoints = MutableStateFlow<List<Triple<Double, Double, Double?>>>(emptyList())
    val gpsPoints: StateFlow<List<Triple<Double, Double, Double?>>> = _gpsPoints.asStateFlow()

    init {
        viewModelScope.launch {
            val points = repository.getPointsForSession(sessionId).first()
            _gpsPoints.value = points.mapNotNull { p ->
                val lat = p.latitude ?: return@mapNotNull null
                val lon = p.longitude ?: return@mapNotNull null
                Triple(lat, lon, p.speedMetersPerSecondCorrected)
            }
        }
    }
}

@Composable
fun MapFullScreenScreen(
    viewModel: MapFullScreenViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val gpsPoints by viewModel.gpsPoints.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (gpsPoints.isNotEmpty()) {
            val context = LocalContext.current
            val geoPoints = remember(gpsPoints) {
                gpsPoints.map { (lat, lon, _) -> GeoPoint(lat, lon) }
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
                    for (i in 0 until gpsPoints.size - 1) {
                        val (lat1, lon1, spd) = gpsPoints[i]
                        val (lat2, lon2, _) = gpsPoints[i + 1]
                        val segment = Polyline().apply {
                            setPoints(listOf(GeoPoint(lat1, lon1), GeoPoint(lat2, lon2)))
                            outlinePaint.color = speedColorFull(spd)
                            outlinePaint.strokeWidth = 12f
                        }
                        mv.overlays.add(segment)
                    }
                    val midpoint = geoPoints[geoPoints.size / 2]
                    mv.controller.setZoom(15.5)
                    mv.controller.setCenter(midpoint)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Text("← Back", color = Color.White)
        }
    }
}

private fun speedColorFull(speedMps: Double?): Int {
    val s = speedMps ?: return android.graphics.Color.LTGRAY
    return when {
        s < 2.0 -> android.graphics.Color.rgb(80, 120, 220)
        s < 5.0 -> android.graphics.Color.rgb(50, 190, 80)
        s < 8.0 -> android.graphics.Color.rgb(255, 160, 20)
        else    -> android.graphics.Color.rgb(220, 50, 50)
    }
}
```

### 2. `RideDetailScreen.kt` — make map card tappable

Add `onOpenMap: () -> Unit = {}` parameter to `RideDetailScreen`:

```kotlin
@Composable
fun RideDetailScreen(
    viewModel: RideDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenMap: () -> Unit = {},
) {
```

Wrap the existing `AndroidView` with a `androidx.compose.foundation.clickable` modifier OR wrap
the entire `if (s.gpsPoints.isNotEmpty()) { ... }` block in a `Box` with a clickable overlay.
The simplest approach: add `.clickable { onOpenMap() }` to the `AndroidView`'s modifier, after
`.height(300.dp)`:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .height(300.dp)
    .clickable { onOpenMap() },
```

Add `import androidx.compose.foundation.clickable` if not already present.

### 3. `ZWheelAppScreen.kt` — add nav route and wire onOpenMap

In the `NavHost`, find the existing `rideDetail/{sessionId}` route:

```kotlin
composable("rideDetail/{sessionId}") {
    RideDetailScreen(onBack = { navController.popBackStack() })
}
```

Add `onOpenMap` callback and a new route for the full-screen map:

```kotlin
composable("rideDetail/{sessionId}") {
    val sessionId = it.arguments?.getString("sessionId") ?: return@composable
    RideDetailScreen(
        onBack = { navController.popBackStack() },
        onOpenMap = { navController.navigate("mapFullScreen/$sessionId") },
    )
}
composable("mapFullScreen/{sessionId}") {
    MapFullScreenScreen(onBack = { navController.popBackStack() })
}
```

Add the import for `MapFullScreenScreen` if the file is in the same package
(`com.zwheel.app.ui.history`) — it may be auto-resolved, but add explicitly if the compiler
complains.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass cleanly.

Commit message: `feat(ui): full-screen map tap-through from ride detail (issue #63)`
