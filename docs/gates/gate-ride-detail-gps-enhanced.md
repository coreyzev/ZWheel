# Gate: GPS Distance Comparison + Speed-Colored Route (issues #66 + #67)

**Branch:** `codex/ride-detail-gps-enhanced`
**Base:** `main`

---

## Context

Two improvements to the ride detail GPS experience, combined because they touch the same files:

**#66 — GPS vs board distance:** Compute Haversine distance between consecutive stored GPS
points and show it alongside the board-derived distance on the ride detail screen. Useful for
understanding the divergence between the two measurement methods.

**#67 — Speed-colored polyline:** Color each GPS route segment by `speedMetersPerSecondCorrected`
at that point instead of drawing one flat-color polyline. Blue = slow, green = medium,
orange = fast, red = very fast.

Both require passing speed alongside lat/lon in `gpsPoints`. The type changes from
`List<Pair<Double, Double>>` to `List<Triple<Double, Double, Double?>>` (lat, lon, speedMps).

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailViewModel.kt
app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailScreen.kt
```

Do NOT touch any other file.

---

## Implementation spec

### 1. `RideDetailViewModel.kt`

#### Change `RideDetailUiState`

Add `gpsDistanceLabel`:
```kotlin
data class RideDetailUiState(
    val dateLabel: String,
    val boardId: String,
    val durationLabel: String,
    val distanceLabel: String,
    val topSpeedLabel: String,
    val avgSpeedLabel: String,
    val gpsPoints: List<Triple<Double, Double, Double?>> = emptyList(),  // ← was Pair, now Triple(lat,lon,speedMps)
    val gpsDistanceLabel: String = "--",                                  // ← add this
)
```

#### Update `init` block — include speed, compute Haversine distance

Change the GPS point mapping from `Pair` to `Triple` (add `p.speedMetersPerSecondCorrected`):

```kotlin
val gpsPoints = points.mapNotNull { p ->
    val lat = p.latitude ?: return@mapNotNull null
    val lon = p.longitude ?: return@mapNotNull null
    Triple(lat, lon, p.speedMetersPerSecondCorrected)
}
```

After building `gpsPoints`, compute GPS Haversine distance:

```kotlin
var gpsMeters = 0.0
for (i in 0 until gpsPoints.size - 1) {
    val (lat1, lon1, _) = gpsPoints[i]
    val (lat2, lon2, _) = gpsPoints[i + 1]
    gpsMeters += haversineMeters(lat1, lon1, lat2, lon2)
}
val gpsDistanceLabel = if (gpsPoints.size < 2) {
    "--"
} else if (speedUnit == SpeedUnit.MPH) {
    "%.2f mi (GPS)".format(gpsMeters / 1_609.344)
} else {
    "%.2f km (GPS)".format(gpsMeters / 1_000.0)
}
```

Pass both to `toUiState`:
```kotlin
_state.value = session.toUiState(speedUnit, gpsPoints, gpsDistanceLabel)
```

#### Update `toUiState` signature

```kotlin
private fun RideSession.toUiState(
    speedUnit: SpeedUnit,
    gpsPoints: List<Triple<Double, Double, Double?>> = emptyList(),
    gpsDistanceLabel: String = "--",
): RideDetailUiState {
```

Add both to the returned `RideDetailUiState(...)`:
```kotlin
gpsPoints = gpsPoints,
gpsDistanceLabel = gpsDistanceLabel,
```

#### Add Haversine helper at the bottom of the file (outside the class)

```kotlin
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dp / 2) * Math.sin(dp / 2) +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
```

---

### 2. `RideDetailScreen.kt`

#### Add GPS distance row

After `DetailRow(label = "Avg Speed", value = s.avgSpeedLabel)` and before the GPS map block,
add:

```kotlin
if (s.gpsDistanceLabel != "--") {
    DetailRow(label = "GPS Distance", value = s.gpsDistanceLabel)
}
```

#### Update `geoPoints` to extract only lat/lon from the Triple

Find the existing line:
```kotlin
val geoPoints = remember(s.gpsPoints) {
    s.gpsPoints.map { (lat, lon) -> GeoPoint(lat, lon) }
}
```

Change to:
```kotlin
val geoPoints = remember(s.gpsPoints) {
    s.gpsPoints.map { (lat, lon, _) -> GeoPoint(lat, lon) }
}
```

#### Replace the single-color polyline with per-segment speed-colored segments

Find the existing `AndroidView` `update` lambda. It currently contains:
```kotlin
mv.overlays.clear()
val polyline = Polyline().apply { setPoints(geoPoints) }
mv.overlays.add(polyline)
val midpoint = geoPoints[geoPoints.size / 2]
mv.controller.setZoom(15.5)
mv.controller.setCenter(midpoint)
```

Replace the body of the `update` lambda with:
```kotlin
mv.overlays.clear()
for (i in 0 until s.gpsPoints.size - 1) {
    val (lat1, lon1, spd) = s.gpsPoints[i]
    val (lat2, lon2, _)   = s.gpsPoints[i + 1]
    val segment = Polyline().apply {
        setPoints(listOf(GeoPoint(lat1, lon1), GeoPoint(lat2, lon2)))
        outlinePaint.color = speedColor(spd)
        outlinePaint.strokeWidth = 10f
    }
    mv.overlays.add(segment)
}
val midpoint = geoPoints[geoPoints.size / 2]
mv.controller.setZoom(15.5)
mv.controller.setCenter(midpoint)
```

#### Add `speedColor` helper at the bottom of the file

```kotlin
private fun speedColor(speedMps: Double?): Int {
    val s = speedMps ?: return android.graphics.Color.LTGRAY
    return when {
        s < 2.0  -> android.graphics.Color.rgb(80,  120, 220)   // slow   — blue
        s < 5.0  -> android.graphics.Color.rgb(50,  190,  80)   // medium — green
        s < 8.0  -> android.graphics.Color.rgb(255, 160,  20)   // fast   — orange
        else     -> android.graphics.Color.rgb(220,  50,  50)   // v.fast — red
    }
}
```

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass cleanly.

Commit message: `feat(ui): speed-colored GPS route and GPS vs board distance on ride detail`
