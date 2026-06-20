# Gate: Slice 3 — Ride History List + Ride Detail + Full-Screen Route Map

You are implementing the history screens of the ZWheel dark instrument-cluster redesign. Read
ONLY this file and `docs/design/spec.md` (sections §5–§8 plus Design Tokens / Typography /
Radii). Do not read any other gate files.

This slice is **app module only**. Do NOT touch anything under `core/`. Do not add new BLE UUIDs.
No new network calls in `app/main` sources — the only network traffic is the existing osmdroid
OSM tile fetch, which is already permitted.

This slice builds on the Slice 0–2 foundation. The following are already in place and must be
reused without redefinition:

- `ZWheelColors` data class + `ZWheelDarkColors` singleton + `LocalZWheelColors` CompositionLocal
  — access tokens via `val c = LocalZWheelColors.current`
- `SairaFamily` / `JetBrainsMonoFamily` + `Typography` in `Type.kt`
- Re-skinned `DashboardCard`, `Label`, `Metric`, `SmallStat` in `DashboardComponents.kt`
- `ZWheelColors.ramp(fraction: Float)` helper

---

## Scope (implement all sections, in order)

### 1. Extend `RideHistoryItem` and `RideHistoryViewModel`

**File:** `app/src/main/kotlin/com/zwheel/app/ui/history/RideHistoryViewModel.kt`

#### 1a. Thumbnail route points — data path decision

`RideRepository.getPointsForSession(sessionId)` returns a `Flow<List<RideDataPoint>>` via a Room
query ordered by `epochMillis ASC`. Each `RideDataPoint` carries `latitude`, `longitude`, and
`speedMetersPerSecondCorrected` (nullable). There is no pre-computed thumbnail; points must be
loaded per session.

**Loading strategy:** add a new suspend function to `RideHistoryViewModel` that batch-loads
thumbnail points for all sessions **once**, driven by `combine` on `repository.getAllSessions()`.
For each session call `repository.getPointsForSession(sessionId).first()` inside a
`coroutineScope { }` using `async` so sessions load in parallel, then downsample the result to
at most **50 points** using `sampleEvenly(points, 50)` (see helper below). Store the result
as a `Map<String, List<Pair<Double, Double>>>` — session ID → normalized coordinate list — in
a `StateFlow` alongside `sessions`.

**Why this approach:** the DAO query for points is indexed on `sessionId` and fast; 50 points
per thumbnail is sufficient to render a recognizable route shape and cheap to hold in memory.
Loading all sessions' points at list-build time (rather than lazily per row) means no per-scroll
jank. For lists longer than ~20 rides the batch is still fast; if profiling later shows memory
pressure, move to a `LazyMap` keyed by visible session IDs — add a `// TODO(perf): lazy thumbnail
loading if history list grows > 50 sessions` comment.

Add these helpers inside `RideHistoryViewModel.kt` (private, file-level):

```kotlin
/** Returns up to [max] evenly spaced elements from [list]. */
private fun <T> sampleEvenly(list: List<T>, max: Int): List<T> {
    if (list.size <= max) return list
    val step = list.size.toDouble() / max
    return List(max) { i -> list[(i * step).toInt()] }
}

/**
 * Normalize a list of (lat, lon) pairs so that the bounding box maps to [0,1]×[0,1].
 * Returns an empty list if fewer than 2 distinct points exist.
 */
private fun normalizeRoute(points: List<Pair<Double, Double>>): List<Pair<Float, Float>> {
    if (points.size < 2) return emptyList()
    val minLat = points.minOf { it.first }
    val maxLat = points.maxOf { it.first }
    val minLon = points.minOf { it.second }
    val maxLon = points.maxOf { it.second }
    val dLat = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0
    val dLon = (maxLon - minLon).takeIf { it > 0.0 } ?: 1.0
    return points.map { (lat, lon) ->
        // Flip Y: latitude increases upward, screen Y increases downward
        Pair(((lon - minLon) / dLon).toFloat(), (1f - ((lat - minLat) / dLat).toFloat()))
    }
}
```

#### 1b. New `RideHistoryItem` fields

Extend the existing `RideHistoryItem` data class with:

```kotlin
data class RideHistoryItem(
    val id: String,
    val timeLabel: String,          // renamed from dateLabel — "8:14 AM" (time only, Saira 16sp/700)
    val durationLabel: String,      // "24 min" or "33 min · no GPS" when hasGps == false
    val distanceLabel: String,      // e.g. "3.24 mi"
    val topSpeedLabel: String,      // e.g. "↑ 19.6 mph" — prefix arrow in UI, not in label
    val boardName: String,          // full board ID / name for ellipsis display
    val hasGps: Boolean,
    val thumbnailPoints: List<Pair<Float, Float>>, // normalized [0,1]×[0,1]; empty if no GPS
    val startEpochMillis: Long,     // kept for date grouping in the screen
)
```

`timeLabel` is formatted as `SimpleDateFormat("h:mm a", Locale.getDefault())`.

`durationLabel` when `hasGps == false`: `"$minutes min · no GPS"`.

`topSpeedLabel` value: `"%.1f %s".format(topSpeedConverted, unitSuffix)` (no arrow prefix — arrow
is rendered as `Icons.Filled.ArrowUpward` in the row composable).

#### 1c. Connection state for empty CTA

Add `ConnectionManager` as a constructor-injected dependency of `RideHistoryViewModel`. Expose:

```kotlin
val isBoardConnected: StateFlow<Boolean> = connectionManager.connectionState
    .map { it == ConnectionState.Connected }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
```

Import `com.zwheel.app.ble.ConnectionManager`, `com.zwheel.app.ble.ConnectionState`.
`ConnectionManager` is `@Singleton`-scoped and already injected elsewhere (e.g.
`DashboardViewModel`) — Hilt will provide it without new bindings.

---

### 2. New composable files — `app/src/main/kotlin/com/zwheel/app/ui/history/`

Each file must stay under ~300 lines. Split further if needed.

#### 2a. `history/RouteThumbnail.kt`

```kotlin
@Composable
fun RouteThumbnail(
    points: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
)
```

Renders a 62×48 dp route-shape thumbnail using Compose `Canvas`. No map tiles.

- Background: `c.mapBg` (`Color(0xFF0C0E12)`), `RoundedCornerShape(10.dp)`.
- If `points` is empty or has fewer than 2 entries: draw a centered `Icons.Filled.LocationOff`
  icon in `c.textDim` at 20.dp size. The parent `RideRow` applies `alpha = 0.7f` for no-GPS rows
  — do NOT apply alpha inside this composable.
- If `points` is non-empty: draw the route path using `Canvas { ... }`:
  ```kotlin
  val path = Path()
  // Map normalized [0,1] coords to canvas pixel space with 6dp inset padding
  val padPx = 6.dp.toPx()
  val w = size.width - 2 * padPx
  val h = size.height - 2 * padPx
  points.forEachIndexed { i, (nx, ny) ->
      val x = padPx + nx * w
      val y = padPx + ny * h
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  drawPath(
      path = path,
      color = c.cyan,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
  // Start dot (lime)
  points.firstOrNull()?.let { (nx, ny) ->
      drawCircle(color = c.lime, radius = 3.dp.toPx(),
          center = Offset(padPx + nx * w, padPx + ny * h))
  }
  // End dot (red)
  points.lastOrNull()?.let { (nx, ny) ->
      drawCircle(color = c.rampDanger, radius = 3.dp.toPx(),
          center = Offset(padPx + nx * w, padPx + ny * h))
  }
  ```
- Wrap the `Canvas` in a `Box(modifier.size(62.dp, 48.dp).clip(RoundedCornerShape(10.dp)).background(c.mapBg))`.

---

#### 2b. `history/SpeedPolyline.kt`

A pure utility file — no composables. Provides helpers used by both `RideDetailScreen` and
`MapFullScreenScreen` to paint the speed-colored osmdroid route.

```kotlin
package com.zwheel.app.ui.history

import androidx.compose.ui.graphics.toArgb
import com.zwheel.app.ui.ZWheelDarkColors   // the singleton ZWheelColors instance
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * Converts a speed in m/s to an ARGB int using the design's cyan→red gradient.
 *
 * The gradient is:
 *   ≤ 0 m/s   → cyan   (#38E0FF)
 *   ≥ 10 m/s  → red    (#FF5A5A)  (≈ 22 mph, well above pushback)
 * In between: linear interpolation in RGB space through amber at 5 m/s.
 *
 * Null speed → dim gray (textDim).
 */
fun speedToArgb(speedMps: Double?): Int {
    val c = ZWheelDarkColors
    if (speedMps == null) return c.textDim.toArgb()
    val t = (speedMps / 10.0).coerceIn(0.0, 1.0).toFloat()
    return when {
        t <= 0.5f -> lerpColor(c.cyan.toArgb(), c.rampCaution.toArgb(), t * 2f)
        else      -> lerpColor(c.rampCaution.toArgb(), c.rampDanger.toArgb(), (t - 0.5f) * 2f)
    }
}

private fun lerpColor(from: Int, to: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    fun ch(fromC: Int, toC: Int) = (fromC + ((toC - fromC) * f)).toInt().coerceIn(0, 255)
    val a = ch((from ushr 24) and 0xFF, (to ushr 24) and 0xFF)
    val r = ch((from ushr 16) and 0xFF, (to ushr 16) and 0xFF)
    val g = ch((from ushr 8) and 0xFF, (to ushr 8) and 0xFF)
    val b = ch(from and 0xFF, to and 0xFF)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Clears all overlays on [mapView] and adds one [Polyline] segment per consecutive GPS point pair,
 * each colored by the starting point's speed. Also adds start/end circle markers.
 *
 * [strokeWidthPx] defaults to 10f for detail map, pass 12f for full-screen map.
 */
fun MapView.applySpeedColoredRoute(
    points: List<Triple<Double, Double, Double?>>,
    strokeWidthPx: Float = 10f,
) {
    overlays.clear()
    for (i in 0 until points.size - 1) {
        val (lat1, lon1, spd) = points[i]
        val (lat2, lon2, _) = points[i + 1]
        val segment = Polyline().apply {
            setPoints(listOf(GeoPoint(lat1, lon1), GeoPoint(lat2, lon2)))
            outlinePaint.color = speedToArgb(spd)
            outlinePaint.strokeWidth = strokeWidthPx
        }
        overlays.add(segment)
    }
    // Start marker — lime ring (unfilled circle)
    points.firstOrNull()?.let { (lat, lon, _) ->
        val marker = Polyline().apply {
            setPoints(buildCirclePoints(lat, lon, radiusDeg = 0.00005))
            outlinePaint.color = ZWheelDarkColors.lime.toArgb()
            outlinePaint.strokeWidth = strokeWidthPx
        }
        overlays.add(marker)
    }
    // End marker — red filled dot (approximate with a small filled circle)
    points.lastOrNull()?.let { (lat, lon, _) ->
        val marker = Polyline().apply {
            setPoints(buildCirclePoints(lat, lon, radiusDeg = 0.00004))
            outlinePaint.color = ZWheelDarkColors.rampDanger.toArgb()
            outlinePaint.strokeWidth = strokeWidthPx * 2.5f
        }
        overlays.add(marker)
    }
    invalidate()
}

/** Builds ~16 GeoPoints forming a small circle, used for start/end markers. */
private fun buildCirclePoints(lat: Double, lon: Double, radiusDeg: Double): List<GeoPoint> {
    val n = 16
    return List(n + 1) { i ->
        val angle = 2 * Math.PI * i / n
        GeoPoint(lat + radiusDeg * Math.sin(angle), lon + radiusDeg * Math.cos(angle))
    }
}
```

**Note on `ZWheelDarkColors`:** Slice 0 exposes the singleton as `ZWheelDarkColors` (a `val` of
type `ZWheelColors`). If Slice 0 named it differently, adjust the import to match; do NOT
redefine the colors.

---

#### 2c. `history/RideRow.kt`

```kotlin
@Composable
fun RideRow(
    item: RideHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Layout — a `Row` inside a `Surface(shape = RoundedCornerShape(12.dp), color = c.card,
border = BorderStroke(1.dp, c.border))`:

```
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    // LEFT: thumbnail (fixed size, never shrinks)
    Box(Modifier.alpha(if (item.hasGps) 1f else 0.7f)) {
        RouteThumbnail(points = item.thumbnailPoints)
    }

    // MIDDLE: flexible, min-width enforced via weight + no intrinsic measurement
    Column(
        modifier = Modifier.weight(1f),  // takes remaining space; long board name cannot push right column
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = item.timeLabel,
            fontFamily = SairaFamily, fontSize = 16.sp, fontWeight = FontWeight.W700,
            color = c.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.durationLabel,     // "24 min" or "33 min · no GPS"
            fontFamily = JetBrainsMonoFamily, fontSize = 10.sp,
            color = c.textMuted,
            maxLines = 1,
        )
        Text(
            text = item.boardName,
            fontFamily = JetBrainsMonoFamily, fontSize = 10.sp,
            color = c.textDimmest,
            maxLines = 1, overflow = TextOverflow.Ellipsis,  // MUST ellipsis
        )
    }

    // RIGHT: stat column — NO weight; wraps to intrinsic width so it never shrinks off-screen
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = item.distanceLabel,     // "3.24 mi"
            fontFamily = SairaFamily, fontSize = 19.sp, fontWeight = FontWeight.W800,
            color = c.textPrimary,
            fontFeatureSettings = "tnum",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = "top speed",
                tint = c.rampCaution,
                modifier = Modifier.size(10.dp),
            )
            Text(
                text = item.topSpeedLabel,   // "19.6 mph"
                fontFamily = JetBrainsMonoFamily, fontSize = 10.sp,
                color = c.rampCaution,
                fontFeatureSettings = "tnum",
            )
        }
    }
}
```

Apply `Modifier.clickable { onClick() }` to the outer `Surface`.

**Layout invariant:** `Modifier.weight(1f)` on the middle column is the critical fix. The right
column has no weight and no `fillMaxWidth` — it sizes to content. A board name like
"BoardyMcBoardface McGee" will ellipsis inside the weight column; it cannot push the stats column
off-screen.

---

#### 2d. `history/RideHistoryScreen.kt` (rewrite)

Replace the existing file entirely.

```kotlin
@Composable
fun RideHistoryScreen(
    viewModel: RideHistoryViewModel = hiltViewModel(),
    onRideClick: (sessionId: String) -> Unit = {},
)
```

**State collection:**
```kotlin
val sessions by viewModel.sessions.collectAsStateWithLifecycle()
val isBoardConnected by viewModel.isBoardConnected.collectAsStateWithLifecycle()
```

**Empty state** (when `sessions.isEmpty()`):

```
Box(Modifier.fillMaxSize().background(c.screenBg), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(12.dp),
           modifier = Modifier.padding(32.dp)) {

        // Icon tile
        Surface(shape = RoundedCornerShape(16.dp), color = c.card,
                border = BorderStroke(1.dp, c.border),
                modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Timeline, contentDescription = null,
                     tint = c.textDim, modifier = Modifier.size(36.dp))
            }
        }

        Text("No rides yet", fontFamily = SairaFamily, fontSize = 20.sp,
             fontWeight = FontWeight.W800, color = c.textPrimary)

        Text("Complete a ride to see it here.",
             fontFamily = SairaFamily, fontSize = 14.sp,
             color = c.textMuted, textAlign = TextAlign.Center)

        // Connection-aware CTA
        if (isBoardConnected) {
            // Muted secondary button — board is connected, user just needs to ride
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF1A1D24),
                border = BorderStroke(1.dp, c.border),
                modifier = Modifier.clickable { /* no-op: already connected */ },
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.DirectionsRun, contentDescription = null,
                         tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    Text("Go for a ride", fontFamily = SairaFamily, fontSize = 15.sp,
                         fontWeight = FontWeight.W600, color = c.textSecondary)
                }
            }
        } else {
            // Lime CTA — prompt to connect
            Button(
                onClick = { /* navigate to connect — wire via callback if nav is available,
                               or leave as TODO for the nav-shell gate */ },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.lime),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.BluetoothSearching, contentDescription = null,
                         tint = Color(0xFF0A0B0E), modifier = Modifier.size(18.dp))
                    Text("Connect a board", fontFamily = SairaFamily, fontSize = 15.sp,
                         fontWeight = FontWeight.W700, color = Color(0xFF0A0B0E))
                }
            }
        }
    }
}
```

**Non-empty state** — date-grouped `LazyColumn`:

Date grouping logic (file-level private function):

```kotlin
private enum class DateGroup { TODAY, EARLIER_THIS_WEEK, OLDER }

private fun dateGroupOf(epochMillis: Long, nowMillis: Long): DateGroup {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMillis
    val todayStart = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    // Start of the week (Monday = day 1 of week in ISO; use Sunday-based Calendar.DAY_OF_WEEK == 1)
    val weekStart = todayStart - (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY) * 86_400_000L
    return when {
        epochMillis >= todayStart -> DateGroup.TODAY
        epochMillis >= weekStart  -> DateGroup.EARLIER_THIS_WEEK
        else                      -> DateGroup.OLDER
    }
}

private fun DateGroup.label(): String = when (this) {
    DateGroup.TODAY             -> "TODAY"
    DateGroup.EARLIER_THIS_WEEK -> "EARLIER THIS WEEK"
    DateGroup.OLDER             -> "OLDER"
}
```

Build the grouped list inside the composable (not in the ViewModel — grouping is a display
concern dependent on `System.currentTimeMillis()`):

```kotlin
val now = remember { System.currentTimeMillis() }
val grouped: List<Pair<DateGroup, List<RideHistoryItem>>> = remember(sessions) {
    sessions
        .groupBy { dateGroupOf(it.startEpochMillis, now) }
        .entries
        .sortedBy { it.key.ordinal }
        .map { (g, items) -> g to items }
}
```

`LazyColumn` structure:

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize().background(c.screenBg),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    // Screen title
    item {
        Text("Ride history", fontFamily = SairaFamily, fontSize = 26.sp,
             fontWeight = FontWeight.W800, color = c.textPrimary,
             letterSpacing = (-0.5).sp,
             modifier = Modifier.padding(bottom = 6.dp))
    }

    grouped.forEach { (group, items) ->
        // Section eyebrow
        item(key = "header_${group.name}") {
            Text(
                text = group.label(),
                fontFamily = JetBrainsMonoFamily, fontSize = 9.sp,
                color = c.textDimmest,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
        items(items, key = { it.id }) { item ->
            RideRow(item = item, onClick = { onRideClick(item.id) })
        }
    }
}
```

---

### 3. Restyle `history/RideDetailScreen.kt`

Replace the existing file entirely. The `RideDetailViewModel` already computes all needed values
(`durationLabel`, `distanceLabel`, `topSpeedLabel`, `avgSpeedLabel`, `gpsDistanceLabel`,
`gpsPoints`) — do not change the ViewModel.

The `RideDetailUiState.boardId` field holds the raw board identifier. The spec calls for the
board name. The ViewModel uses `boardId` from `RideSession.boardId` — treat it as the display
name (it's populated by BLE identity). Render it as-is.

**Ah USED tile:** `RideSession.wattHoursUsed` is the stored field. Convert to Ah at a nominal
pack voltage of **63 V** (15S × 4.2 V, Pint X nominal): `ahUsed = wattHoursUsed / 63.0`. Expose
`ahUsedLabel` from `RideDetailUiState` as:

```kotlin
val ahUsedLabel: String = if (wattHoursUsed != null && wattHoursUsed > 0.0)
    "%.1f Ah".format(wattHoursUsed / 63.0)
else "--"
```

Add `ahUsedLabel: String` to `RideDetailUiState` and populate it inside `RideSession.toUiState()`.

**Screen structure:**

```kotlin
@Composable
fun RideDetailScreen(
    viewModel: RideDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenMap: () -> Unit = {},
)
```

Root: `Scaffold` with `topBar = { RideDetailTopBar(state, onBack, onShare = { /* TODO */ }) }`,
body content in a `LazyColumn`:

**Top bar** — `TopAppBar` with transparent container color `c.screenBg`:
- Navigation icon: `IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, tint = c.textPrimary) }`
- Title column (`Column`):
  - `Text(state.titleLabel, SairaFamily 20sp W800, c.textPrimary, letterSpacing = (-0.3).sp)`
    where `titleLabel = "Today · ${timeOfDayLabel}"` — add `titleLabel: String` to `RideDetailUiState`,
    formatted as `"Today · ${SimpleDateFormat("h:mm a").format(Date(startEpochMillis))}"` (use
    actual date check: today vs. older date shows `"${MonthDay} · ${time}"`; see below).
  - `Text(state.subtitleLabel, JetBrainsMonoFamily 10sp, c.textMuted, softWrap = true)` — wraps,
    NOT truncated. `subtitleLabel = "${monthDayLabel} · ${durationMinutes} min · ${boardId}"`.
    Add `subtitleLabel: String` to `RideDetailUiState`.
- Actions: `IconButton { Icon(Icons.Filled.IosShare, tint = c.textSecondary) }` — tapping is a
  no-op for now; add `// TODO(share): implement ride share export`.

**`titleLabel` / `subtitleLabel` formatting** — add private helper inside `RideDetailViewModel`:

```kotlin
private fun formatTitleAndSubtitle(startMs: Long, endMs: Long?, boardId: String): Pair<String, String> {
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(startMs))
    val monthDay = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(startMs))
    val title = if (startMs >= todayStart) "Today · $time" else "$monthDay · $time"
    val durationMin = ((endMs ?: startMs) - startMs) / 60_000
    val subtitle = "$monthDay · ${durationMin} min · $boardId"
    return title to subtitle
}
```

**Body `LazyColumn`** (horizontal padding 18.dp, vertical spacing 12.dp):

```
item { MiniMapCard(state, onOpenMap) }
item { StatGrid(state) }
item { BoardCard(state) }
```

**`MiniMapCard`** — a `Surface(shape = RoundedCornerShape(18.dp), …, modifier = Modifier.fillMaxWidth().height(230.dp))`:

- If `state.gpsPoints.isEmpty()`: `Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.LocationOff, tint = c.textDim, size = 48.dp) }`
- Else:
  ```kotlin
  Box(Modifier.fillMaxSize()) {
      // osmdroid map
      val context = LocalContext.current
      val mapView = remember {
          MapView(context).apply {
              Configuration.getInstance().userAgentValue = context.packageName
              setTileSource(TileSourceFactory.MAPNIK)
              setMultiTouchControls(false)   // mini map: no touch
              isClickable = false
          }
      }
      DisposableEffect(Unit) { mapView.onResume(); onDispose { mapView.onPause() } }
      AndroidView(
          factory = { mapView },
          update = { mv ->
              mv.applySpeedColoredRoute(state.gpsPoints, strokeWidthPx = 10f)
              val mid = state.gpsPoints[state.gpsPoints.size / 2]
              mv.controller.setZoom(15.5)
              mv.controller.setCenter(GeoPoint(mid.first, mid.second))
          },
          modifier = Modifier.fillMaxSize(),
      )

      // Speed legend chip — bottom left
      SpeedLegendChip(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))

      // Full map chip — top right
      Surface(
          shape = RoundedCornerShape(999.dp),
          color = Color(0xCC101319),   // legendCard at ~80% alpha
          modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clickable { onOpenMap() },
      ) {
          Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(Icons.Filled.Fullscreen, contentDescription = "Full map",
                   tint = c.textPrimary, modifier = Modifier.size(16.dp))
              Text("Full map", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp,
                   color = c.textPrimary)
          }
      }
  }
  ```

**`SpeedLegendChip`** — a composable to extract to `SpeedLegendChip.kt` (see §3a below).

**`StatGrid`** — a `Column(verticalArrangement = Arrangement.spacedBy(10.dp))` containing two
`Row`s of two tiles each (plus an extra row for GPS DISTANCE + Ah USED):

Tile template — `StatTile(label: String, value: String, valueColor: Color = c.textPrimary)`:

```kotlin
@Composable
private fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = c.card,
            border = BorderStroke(1.dp, c.border), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp,
                 color = c.textLabel, letterSpacing = 2.sp)
            Text(value, fontFamily = SairaFamily, fontSize = 26.sp,
                 fontWeight = FontWeight.W800, color = valueColor,
                 fontFeatureSettings = "tnum")
        }
    }
}
```

Grid layout:

```kotlin
// Row 1: DURATION | DISTANCE
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    StatTile("DURATION", state.durationLabel, c.textPrimary, Modifier.weight(1f))
    StatTile("DISTANCE", state.distanceLabel, c.textPrimary, Modifier.weight(1f))
}
// Row 2: TOP SPEED (amber) | AVG SPEED
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    StatTile("TOP SPEED", state.topSpeedLabel, c.rampCaution, Modifier.weight(1f))
    StatTile("AVG SPEED", state.avgSpeedLabel, c.textPrimary, Modifier.weight(1f))
}
// Row 3: GPS DISTANCE | Ah USED
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    StatTile("GPS DISTANCE", state.gpsDistanceLabel, c.cyan, Modifier.weight(1f))
    StatTile("Ah USED", state.ahUsedLabel, c.textPrimary, Modifier.weight(1f))
}
```

**`BoardCard`** — full-width card at the bottom:

```kotlin
Surface(shape = RoundedCornerShape(16.dp), color = c.card,
        border = BorderStroke(1.dp, c.border),
        modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("BOARD", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp,
             color = c.textLabel, letterSpacing = 2.sp)
        Text(state.boardId, fontFamily = SairaFamily, fontSize = 16.sp,
             fontWeight = FontWeight.W700, color = c.textPrimary,
             maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
```

#### 3a. `history/SpeedLegendChip.kt`

Extract the speed-legend gradient chip into its own composable (used in both detail and full-screen
map):

```kotlin
@Composable
fun SpeedLegendChip(modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xCC101319),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Gradient bar: cyan → amber → red
            Box(
                Modifier
                    .width(40.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(c.cyan, c.rampCaution, c.rampDanger)
                        )
                    )
            )
            Text("slow", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = c.cyan)
            Text("fast", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = c.rampDanger)
        }
    }
}
```

---

### 4. Restyle `history/MapFullScreenScreen.kt`

Replace the existing composable. Keep `MapFullScreenViewModel` in the same file (it is already
correct; only the screen composable changes).

```kotlin
@Composable
fun MapFullScreenScreen(
    viewModel: MapFullScreenViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
)
```

Root: `Box(Modifier.fillMaxSize())` with `WindowCompat.setDecorFitsSystemWindows(window, false)`
(pass `LocalView.current.context as Activity` to obtain the window — wrap in
`SideEffect { ... }`) so the map bleeds edge-to-edge under the status bar.

```kotlin
// Full-size map
if (gpsPoints.isNotEmpty()) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            Configuration.getInstance().userAgentValue = context.packageName
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isClickable = true
        }
    }
    DisposableEffect(Unit) { mapView.onResume(); onDispose { mapView.onPause() } }
    AndroidView(
        factory = { mapView },
        update = { mv ->
            mv.applySpeedColoredRoute(gpsPoints, strokeWidthPx = 12f)
            val mid = gpsPoints[gpsPoints.size / 2]
            mv.controller.setZoom(15.5)
            mv.controller.setCenter(GeoPoint(mid.first, mid.second))
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// Translucent top bar overlay
Box(
    Modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(
            listOf(Color(0xCC0A0B0E), Color.Transparent),
            endY = 120f,
        ))
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 10.dp)
) {
    // Close button — circular scrim
    Surface(
        shape = CircleShape,
        color = Color(0x99101319),
        modifier = Modifier.size(36.dp).clickable { onBack() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = "Close",
                 tint = c.textPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

// Bottom summary panel
Box(
    Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .background(Brush.verticalGradient(
            listOf(Color.Transparent, Color(0xDD0A0B0E)),
            startY = 0f, endY = 200f,
        ))
        .navigationBarsPadding()
        .padding(16.dp),
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Speed legend + start/end markers row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SpeedLegendChip()
            // Start marker legend
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).border(2.dp, c.lime, CircleShape))
                Text("Start", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = c.textMuted)
            }
            // End marker legend
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).background(c.rampDanger, CircleShape))
                Text("End", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = c.textMuted)
            }
        }
    }
}
```

**`MapFullScreenViewModel`** — no changes needed; it already loads all GPS points via
`repository.getPointsForSession(sessionId).first()` and exposes them as a `StateFlow`.

---

### 5. Screenshot tests

**File:** `app/src/test/kotlin/com/zwheel/app/ui/screenshots/HistoryScreenshotTest.kt`

Follow the exact pattern of `DashboardScreenshotTest.kt`:

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HistoryScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Mock data helpers — defined at bottom of this file
    private fun mockSessionWithGps() = RideHistoryItem(
        id = "session-1",
        timeLabel = "8:14 AM",
        durationLabel = "24 min",
        distanceLabel = "3.24 mi",
        topSpeedLabel = "19.6 mph",
        boardName = "BoardyMcBoardface McGee",
        hasGps = true,
        thumbnailPoints = listOf(
            0.1f to 0.9f, 0.2f to 0.7f, 0.4f to 0.5f,
            0.6f to 0.4f, 0.8f to 0.2f, 0.9f to 0.1f,
        ),
        startEpochMillis = System.currentTimeMillis() - 3_600_000L,
    )

    private fun mockSessionNoGps() = RideHistoryItem(
        id = "session-2",
        timeLabel = "7:02 AM",
        durationLabel = "33 min · no GPS",
        distanceLabel = "5.10 mi",
        topSpeedLabel = "18.2 mph",
        boardName = "Gemini",
        hasGps = false,
        thumbnailPoints = emptyList(),
        startEpochMillis = System.currentTimeMillis() - 7_200_000L,
    )

    @Test
    fun history_list_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                // Render the list directly with mock items (no ViewModel)
                // Pass sessions and isBoardConnected as params, or use a thin wrapper
                // composable that accepts these directly. Either wire a mock ViewModel
                // or extract a @Composable overload that accepts the data.
                // See note below on the preferred approach.
                HistoryListContent(
                    sessions = listOf(mockSessionWithGps(), mockSessionNoGps()),
                    isBoardConnected = false,
                    onRideClick = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/history.png")
    }

    @Test
    fun history_empty_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                HistoryListContent(
                    sessions = emptyList(),
                    isBoardConnected = false,
                    onRideClick = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/history_empty.png")
    }

    @Test
    fun ride_detail_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                RideDetailContent(state = mockDetailState())
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/ride_detail.png")
    }
}
```

**Testability pattern — preferred approach:** Extract the stateless content of
`RideHistoryScreen` into a private composable:

```kotlin
@Composable
internal fun HistoryListContent(
    sessions: List<RideHistoryItem>,
    isBoardConnected: Boolean,
    onRideClick: (String) -> Unit,
)
```

and the stateless content of `RideDetailScreen` into:

```kotlin
@Composable
internal fun RideDetailContent(
    state: RideDetailUiState,
    onBack: () -> Unit = {},
    onOpenMap: () -> Unit = {},
)
```

The Hilt `@HiltViewModel` screens become thin wrappers that collect state and call these. The
screenshot tests call the stateless variants directly — no Hilt, no Room needed.

**Mock detail state** in the test file:

```kotlin
private fun mockDetailState() = RideDetailUiState(
    dateLabel    = "Jun 18, 2024  8:14 AM",
    titleLabel   = "Today · 8:14 AM",
    subtitleLabel = "Jun 18 · 24 min · BoardyMcBoardface McGee",
    boardId      = "BoardyMcBoardface McGee",
    durationLabel = "24:08",
    distanceLabel = "3.24 mi",
    topSpeedLabel = "19.6 mph",
    avgSpeedLabel = "11.8 mph",
    gpsPoints     = emptyList(),   // MapView renders blank under Robolectric — acceptable
    gpsDistanceLabel = "3.19 mi",
    ahUsedLabel   = "4.1 Ah",
)
```

**osmdroid under Robolectric:** The `MapView` `AndroidView` will render blank in Robolectric
because osmdroid's tile engine requires a real GPU/EGL context. This is acceptable — the
surrounding chrome (top bar, stat grid, board card, legend chips) is what the screenshot test
inspects. Add this comment in the test class:

```kotlin
// NOTE: osmdroid MapView renders blank under Robolectric/Roborazzi — this is expected.
// The test captures the surrounding UI chrome (top bar, stat grid, board card).
// Visual coverage of the map itself requires a device/emulator instrumented test.
```

---

### 6. Icon reference table

All icons from `androidx.compose.material.icons` (extended artifact, already on classpath).

| Spec / description | Compose icon |
|---|---|
| `arrow_back` | `Icons.Filled.ArrowBack` |
| `arrow_upward` | `Icons.Filled.ArrowUpward` |
| `bluetooth_searching` | `Icons.Filled.BluetoothSearching` |
| `close` | `Icons.Filled.Close` |
| `directions_run` | `Icons.Filled.DirectionsRun` |
| `fullscreen` | `Icons.Filled.Fullscreen` |
| `ios_share` | `Icons.Filled.IosShare` |
| `location_off` | `Icons.Filled.LocationOff` |
| `timeline` | `Icons.Filled.Timeline` |

If any icon is absent from `Icons.Filled`, fall back to `Icons.Outlined` variant.

---

### 7. Typography shorthand

All text style references in this gate use the Slice 0 families. Do not redefine them.

| Shorthand | Equivalent |
|---|---|
| Saira Nsp / WXxx | `TextStyle(fontFamily = SairaFamily, fontSize = N.sp, fontWeight = FontWeight.WXxx)` |
| JBMono Nsp | `TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = N.sp)` |
| `tnum` | `fontFeatureSettings = "tnum"` |
| `c.xxx` | `LocalZWheelColors.current.xxx` |

Import `SairaFamily` and `JetBrainsMonoFamily` from `com.zwheel.app.ui.Type` (adjust package if
Slice 0 placed them elsewhere — search for the `val SairaFamily` declaration).

---

## Files to create / replace

| Action | File |
|---|---|
| Create | `app/src/main/kotlin/com/zwheel/app/ui/history/RouteThumbnail.kt` |
| Create | `app/src/main/kotlin/com/zwheel/app/ui/history/SpeedPolyline.kt` |
| Create | `app/src/main/kotlin/com/zwheel/app/ui/history/SpeedLegendChip.kt` |
| Create | `app/src/main/kotlin/com/zwheel/app/ui/history/RideRow.kt` |
| Replace | `app/src/main/kotlin/com/zwheel/app/ui/history/RideHistoryScreen.kt` |
| Replace | `app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailScreen.kt` |
| Replace | `app/src/main/kotlin/com/zwheel/app/ui/history/MapFullScreenScreen.kt` |
| Edit | `app/src/main/kotlin/com/zwheel/app/ui/history/RideHistoryViewModel.kt` |
| Edit | `app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailViewModel.kt` |
| Create | `app/src/test/kotlin/com/zwheel/app/ui/screenshots/HistoryScreenshotTest.kt` |

Do NOT touch `core/`, `DashboardComponents.kt`, `ZWheelAppScreen.kt`, or any file outside `app/`.

---

## Constraints (self-review before commit)

1. **`core/` untouched.** No Android imports in `core/`, no new BLE UUIDs, no new network calls
   in `app/main` sources.
2. **300-line soft limit per file.** Use the split files listed above.
3. **Token access only via `LocalZWheelColors.current`.** No hardcoded hex colors in composables.
   The one exception is `SpeedPolyline.kt` which accesses `ZWheelDarkColors` (the singleton)
   because it is not a composable and cannot call `LocalZWheelColors.current`.
4. **No `weight` on the right stats column** in `RideRow`. `Modifier.weight(1f)` on the middle
   column + no weight on the right is the fix for the prototype's layout bug where a long board
   name pushed distance off-screen.
5. **Board name ellipsis** (`maxLines = 1, overflow = TextOverflow.Ellipsis`) on the board name
   `Text` in `RideRow` and `BoardCard`.
6. **Subtitle wraps** in `RideDetailScreen` top bar — use `softWrap = true` (default) and do NOT
   set `maxLines` on the subtitle `Text`.
7. **osmdroid `MapView` lifecycle** — always `onResume()` / `onPause()` via `DisposableEffect`.
8. **Speed color** in `SpeedPolyline.kt` uses a continuous linear interpolation (cyan→amber→red
   over 0–10 m/s), not step thresholds. The old `speedColor()` / `speedColorFull()` functions in
   the replaced files are removed.
9. **Thumbnail normalization** flips Y-axis (latitude increases up, screen Y increases down).
10. **`wattHoursUsed` → Ah** conversion uses nominal 63 V. Add comment:
    `// TODO(hardware): nominal 63V (15S × 4.2V) — will under/over-report for other pack configs`.
11. All numeric `Text` values use `fontFeatureSettings = "tnum"`.

---

## Build & commit

```bash
GRADLE_OPTS="-Xmx4g" ./gradlew :app:compileDebugKotlin
```

Fix ALL errors until it passes, then:

```bash
GRADLE_OPTS="-Xmx4g" ./gradlew :app:recordRoborazziDebug
```

Verify that `app/build/outputs/roborazzi/history.png`, `history_empty.png`, and
`ride_detail.png` are produced.

Two commits, Conventional format, no Co-Authored-By:

```
feat(ui): dark ride-history list + detail + full-screen map
test(ui): screenshot tests for history screens
```
