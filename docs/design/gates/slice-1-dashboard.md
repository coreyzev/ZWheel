# Gate: Slice 1 — Live Dashboard (Dark Instrument-Cluster)

You are implementing the Live Dashboard screen of the ZWheel dark instrument-cluster redesign. Read
ONLY this file and `docs/design/spec.md`. Do not read any other docs or gate files.

This slice is **app module only**. Do NOT touch anything under `core/` — it must stay Android-free.
Do not add new BLE UUIDs. Do not make network calls in `app/main` sources.

This slice builds directly on the Slice 0 foundation. That slice has already installed:
- `ZWheelColors` data class + `ZWheelDarkColors` singleton + `LocalZWheelColors` CompositionLocal
  in `app/src/main/kotlin/com/zwheel/app/ui/ZWheelColors.kt`
- `ZWheelColors.ramp(fraction: Float)` helper (0f = rampDanger, 1f = rampGood)
- `SairaFamily` / `JetBrainsMonoFamily` + `Typography` in `Type.kt`
- Re-skinned `DashboardCard`, `Label`, `Metric`, `SmallStat`, `SpeedGauge` in `DashboardComponents.kt`

**Use these; do not redefine them.** Access colors via `val c = LocalZWheelColors.current`.

---

## Scope (do all of these, in order)

### 1. New `DashboardUiState` fields in `DashboardState.kt`

The current `DashboardUiState` is missing these fields needed by the new dashboard. Add them:

| New field | Type | Source in `toDashboardUiState` |
|---|---|---|
| `motorTempF` | `Int` | `BoardState.motorTempCelsius?.toDisplayTemperature(prefs.temperatureUnit)?.toInt() ?: 0` |
| `batteryTempF` | `Int` | `BoardState.batteryTempCelsius?.toDouble()?.toDisplayTemperature(prefs.temperatureUnit)?.toInt() ?: 0` |
| `boardType` | `BoardType` | `identity?.type ?: BoardType.UNKNOWN` — needed for model top-speed lookup |
| `lightsOn` | `Boolean` | `lightsOn ?: false` |
| `avgSpeedMph` | `Double` | `0.0` — **TODO**: no avg-speed source exists in `RideServiceRepository`; hard-code 0.0 and add a `// TODO(avg-speed): wire RideServiceRepository once rolling avg is tracked` comment |

`motorTempF`, `batteryTempF`, and `lightsOn` are fields on `BoardState` in
`core/src/main/kotlin/com/zwheel/core/model/BoardModels.kt` — read them from `this` inside
`BoardState.toDashboardUiState(...)`. `boardType` comes from `this.identity?.type`.

Also update `mockDashboardState()` with plausible values:
- `motorTempF = 104`, `batteryTempF = 88`, `boardType = BoardType.PINT_X`,
  `lightsOn = true`, `avgSpeedMph = 8.3`

Import `com.zwheel.core.model.BoardType` at the top of `DashboardState.kt` (it is already
transitively visible via `identity?.type` but add an explicit import to be safe).

---

### 2. Model top-speed constants — new file `dashboard/PushbackConstants.kt`

Create `app/src/main/kotlin/com/zwheel/app/ui/dashboard/PushbackConstants.kt`:

```kotlin
package com.zwheel.app.ui.dashboard

import com.zwheel.core.model.BoardType

/**
 * Firmware pushback thresholds as a fraction of model top speed.
 * // TODO(hardware-tune): exact thresholds vary per board / firmware version;
 * // tune on real hardware before shipping. These approximate values match
 * // observed behavior on XR (15.8 mph amber, 19.5 mph red) and Pint X.
 */
object PushbackThresholds {
    const val CAUTION_FRACTION = 0.80f // speed/modelTopSpeed ≥ this → approaching
    const val DANGER_FRACTION  = 0.95f // speed/modelTopSpeed ≥ this → at/above pushback
}

/**
 * Nominal firmware top speed in MPH, per model. Used to derive the pushback-headroom fraction.
 * // TODO(hardware-tune): Pint / Plus / V1 values estimated; verify on hardware.
 */
fun BoardType.modelTopSpeedMph(): Float = when (this) {
    BoardType.PINT_X        -> 20.0f
    BoardType.XR            -> 20.0f
    BoardType.PINT          -> 16.0f
    BoardType.PLUS          -> 19.0f
    BoardType.ONEWHEEL_V1   -> 15.0f
    BoardType.UNKNOWN       -> 20.0f
}

/** Fraction of model top speed, clamped [0, 1]. */
fun speedFraction(speedMph: Double, modelTopSpeedMph: Float): Float =
    (speedMph.toFloat() / modelTopSpeedMph).coerceIn(0f, 1f)
```

---

### 3. New composable files under `app/src/main/kotlin/com/zwheel/app/ui/dashboard/`

Create the package directory and the following files. Each file must stay under ~300 lines.
Do NOT touch `DashboardComposables.kt` directly yet — the new top-level `DashboardScreen`
composable (section 4) replaces it as the entry point; the old composable can remain for now.

#### 3a. `dashboard/BoardHeader.kt`

A self-contained composable `BoardHeader(state: DashboardUiState, modifier: Modifier = Modifier)`.

Layout (see spec §4, items 2 & 3):
- A `Row` with `verticalAlignment = Alignment.CenterVertically`:
  - **Status dot**: `Box` 8.dp × 8.dp, circular (`CircleShape`), color = `c.rampGood`.
    Add a soft green glow using `Modifier.drawBehind { drawCircle(color = c.rampGood.copy(alpha = 0.35f), radius = 12.dp.toPx()) }`.
  - **Board name** (`Text`, Saira 14sp / FontWeight.W700, color = `c.textPrimary`,
    `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, `modifier = Modifier.weight(1f).padding(horizontal = 8.dp)`).
  - **Board-type badge** (never shrinks): an `OutlinedBox` / `Surface` with `border = BorderStroke(1.dp, c.borderLime)`, radius 5.dp (`badge` token), padding 3.dp × 6.dp.
    Badge text = `state.boardType.displayName` uppercased to a short form:
    map `PINT_X → "PINT X"`, `XR → "XR"`, `PINT → "PINT"`, `PLUS → "PLUS"`, `ONEWHEEL_V1 → "OW V1"`, `UNKNOWN → ""` (hide badge if UNKNOWN).
    Text: JetBrains Mono 9sp / W700, color = `c.lime`.
- Below that `Row`, a second `Row` (connection line):
  - `Icon(Icons.Filled.MyLocation, contentDescription = "GPS", tint = c.cyan, modifier = Modifier.size(12.dp))`
  - `Text("GPS · ", style = mono11, color = c.cyan)`
  - `Icon(Icons.Filled.BluetoothConnected, contentDescription = "BLE", tint = c.textMuted, modifier = Modifier.size(12.dp))`
  - `Text(state.rssi?.let { "$it dBm" } ?: "--", style = mono11, color = c.textMuted)`

`mono11` local val: `TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, fontFeatureSettings = "tnum")`.

Icon imports: `Icons.Filled.MyLocation` → `androidx.compose.material.icons.filled.MyLocation`;
`Icons.Filled.BluetoothConnected` → `androidx.compose.material.icons.filled.BluetoothConnected`.

---

#### 3b. `dashboard/SpeedSlab.kt`

A self-contained composable `SpeedSlab(state: DashboardUiState, modifier: Modifier = Modifier)`.

Derives the speed ramp color:
```kotlin
val fraction = speedFraction(state.speedMph, state.boardType.modelTopSpeedMph())
val speedColor = when {
    fraction >= PushbackThresholds.DANGER_FRACTION  -> c.rampDanger
    fraction >= PushbackThresholds.CAUTION_FRACTION -> c.rampCaution
    else                                            -> c.rampGood
}
```

Layout (spec §4, item 4):
- `Box` filling full width, background = `Brush.verticalGradient(listOf(Color(0xFF13160D), Color(0xFF0A0B0E)))`,
  `contentAlignment = Alignment.Center`, vertical padding ~24.dp.
- Inside, a `Column(horizontalAlignment = Alignment.CenterHorizontally)`:
  - Speed hero: split integer and decimal. Parse `state.speedMph`:
    `val intPart = state.speedMph.toInt().toString()`
    `val decPart = ".%01d".format(((state.speedMph % 1) * 10).toInt())`
    Display as an inline `Row(baseline alignment)`:
    - Integer digits: Saira 96sp / W900, color = `speedColor`, `fontFeatureSettings = "tnum"`
    - Decimal: Saira 48sp / W900, color = `c.textSecondary`, `fontFeatureSettings = "tnum"`
  - Unit + badge + top-speed row (a `Row`, `horizontalArrangement = SpaceBetween`, `fillMaxWidth` with 40.dp horizontal padding):
    - `Text(state.speedUnitLabel, ...)` — Saira 14sp / W700, `c.textSecondary`
    - If `state.isSpeedCorrected`: lime outline badge `Surface(border = BorderStroke(1.dp, c.borderLime), shape = RoundedCornerShape(5.dp))` → `Text("TIRE-CORRECTED", JBMono 9sp W700, c.lime)`
    - `Row`: `Icon(Icons.Filled.ArrowUpward, tint = c.rampCaution, 12.dp)` +
      `Text("%.1f".format(state.topSpeedMph), JBMono 11sp, c.rampCaution)`

Icon: `Icons.Filled.ArrowUpward` → `androidx.compose.material.icons.filled.ArrowUpward`.

---

#### 3c. `dashboard/PushbackBar.kt`

A self-contained composable `PushbackBar(state: DashboardUiState, modifier: Modifier = Modifier)`.

```kotlin
val fraction = speedFraction(state.speedMph, state.boardType.modelTopSpeedMph())
val barColor = when {
    fraction >= PushbackThresholds.DANGER_FRACTION  -> c.rampDanger
    fraction >= PushbackThresholds.CAUTION_FRACTION -> c.rampCaution
    else                                            -> c.rampGood
}
val statusLabel = if (fraction >= PushbackThresholds.CAUTION_FRACTION) "approaching pushback" else "nominal"
```

Layout (spec §4, item 5):
- `Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp))`:
  - Zoned track drawn with `Canvas(Modifier.weight(1f).height(6.dp))`:
    - Draw full track rect in `c.border` (background).
    - Draw three colored zone segments (equal thirds): `c.rampGood` (0→1/3 width), `c.rampCaution` (1/3→2/3), `c.rampDanger` (2/3→full) — each at 30% alpha to create the zone tint.
    - Draw filled marker rect: width 4.dp, height 10.dp, centered vertically at x = `fraction * trackWidth`, color = `barColor`. Draw it last so it sits on top.
  - 8.dp spacer
  - `Text(statusLabel, JBMono 10sp W400, color = barColor)`

---

#### 3d. `dashboard/BatteryBand.kt`

A self-contained composable `BatteryBand(state: DashboardUiState, modifier: Modifier = Modifier)`.

This is a two-card horizontal band (spec §4, item 6). Use a `Row(horizontalArrangement = Arrangement.spacedBy(10.dp))`.

**Left card — Battery** (`DashboardCard`, `Modifier.weight(1f)`):
- Battery ramp fraction = `state.batteryPercent / 100f`
- `batteryColor = c.ramp(state.batteryPercent / 100f)`
- Layout `Column(verticalArrangement = Arrangement.spacedBy(4.dp))`:
  - Eyebrow label: `Label("BATTERY")` (uses Slice 0 re-skinned `Label`)
  - Big % value: Saira 48sp / W800, `fontFeatureSettings = "tnum"`, color = `batteryColor`,
    text = `"${state.batteryPercent}%"`
  - Mini fill bar: `LinearProgressIndicator(progress = state.batteryPercent / 100f, ...)`,
    color = `batteryColor`, trackColor = `c.border`, height 4.dp, rounded.
  - Pack caption: JBMono 9sp, `c.textLabel`,
    text = `"${state.cellVoltages.size}S-pack · %.1fV".format(state.packVoltage)`

**Right card — Range** (`DashboardCard`, `Modifier.weight(1f)`):
- Layout `Column`:
  - Eyebrow label: `Label("EST. REMAINING")`
  - Range value: Saira 48sp / W800, `fontFeatureSettings = "tnum"`, color = `c.cyan`,
    text = `"%.1f".format(state.estimatedRangeMiles)`
  - Unit: JBMono 11sp, `c.textSecondary`, text = `"${state.rangeUnitLabel} at current draw"`

---

#### 3e. `dashboard/TripCard.kt`

A self-contained composable `TripCard(state: DashboardUiState, modifier: Modifier = Modifier)`.

Full-width `DashboardCard` (spec §4, item 7):
- `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically)`:
  - Left: `Column`:
    - `Label("TRIP DISTANCE")`
    - Saira 48sp / W800 `fontFeatureSettings = "tnum"`, `c.textPrimary`, text = `"%.2f".format(state.tripMiles)`
    - JBMono 11sp `c.textLabel`, text = `state.rangeUnitLabel`
  - Right: `Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp))`:
    - `SmallStat("Ah", "%.2f".format(state.tripAmpHours))` (uses Slice 0 `SmallStat`)
    - `SmallStat("REGEN", "+%.2f".format(state.regenAmpHours))`
    - `SmallStat("AVG", if (state.avgSpeedMph > 0.0) "%.1f %s".format(state.avgSpeedMph, state.speedUnitLabel) else "--")` with a trailing `// TODO(avg-speed)` comment

---

#### 3f. `dashboard/StatRow.kt`

A self-contained composable `StatRow(state: DashboardUiState, modifier: Modifier = Modifier)`.

Three equal-width tiles in a `Row(horizontalArrangement = Arrangement.spacedBy(10.dp))` (spec §4, item 8).
Each tile is a `DashboardCard(modifier = Modifier.weight(1f))`:

**Tile 1 — Draw (amps)**:
- `Icon(Icons.Filled.Bolt, tint = c.lime, 16.dp)` — `androidx.compose.material.icons.filled.Bolt`
- `Text("%.1f A".format(state.amps), Saira 20sp W800, c.textPrimary, tnum)`
- `Label("DRAW · %.2f Ah".format(state.tripAmpHours))`

**Tile 2 — Ride mode**:
- `Label("MODE")`
- `Text(state.rideMode, Saira 18sp W800, c.lime)` (lime = active mode accent)

**Tile 3 — Lights**:
- `Icon(Icons.Filled.Lightbulb, tint = if (state.lightsOn) c.lime else c.textDim, 16.dp)`
  — `androidx.compose.material.icons.filled.Lightbulb`
- `Text(if (state.lightsOn) "ON" else "OFF", Saira 20sp W800, if (state.lightsOn) c.lime else c.textDim)`
- `Label("LIGHTS")`

---

#### 3g. `dashboard/CellStrip.kt`

A self-contained composable
`CellStrip(cells: List<CellVoltageUiState>, modifier: Modifier = Modifier)`.

Tap-to-expand state is local: `var expanded by remember { mutableStateOf(false) }`.

N = `cells.size`. Derive min/max:
```kotlin
val minVolts = cells.minOfOrNull { it.volts } ?: 0.0
val maxVolts = cells.maxOfOrNull { it.volts } ?: 0.0
val voltRange = 4.2 - 3.5 // typical Li range; fraction = (volts - 3.5) / 0.7 clamped [0,1]
```

**Header row** (always shown) — `Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically)`:
- `Label("PER-CELL · ${cells.size}S", modifier = Modifier.weight(1f))`
- `Text("↑ %.2fV".format(maxVolts), JBMono 11sp W700, c.rampGood)` — use `Icon(Icons.Filled.ArrowUpward, 10.dp)` instead of literal `↑`
- 4.dp spacer
- `Text("↓ %.2fV".format(minVolts), JBMono 11sp W700, c.rampDanger)` — use `Icon(Icons.Filled.ArrowDownward, 10.dp)` for `↓`
- `Icon(Icons.Filled.ExpandMore, tint = c.textMuted, modifier = Modifier.rotate(if (expanded) 180f else 0f).animateContentSize())`
  Animate chevron rotation: wrap in `graphicsLayer { rotationZ = ... }` driven by
  `animateFloatAsState(targetValue = if (expanded) 180f else 0f, animationSpec = tween(220))`.

**Collapsed body** (shown when `!expanded`):
A `Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))`:
- For each cell: a `Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(3.dp)).background(c.ramp(((cell.volts - 3.5) / 0.7f).toFloat().coerceIn(0f, 1f))))`.

**Expanded body** (shown when `expanded`):
A `LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), userScrollEnabled = false)`:
For each cell:
```kotlin
Surface(
    shape = RoundedCornerShape(14.dp), // cardSmall token
    color = c.card,
    border = if (cell.isLow) BorderStroke(1.dp, c.rampDanger) else BorderStroke(1.dp, c.border),
    modifier = Modifier.padding(3.dp),
) {
    Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(cell.label, JBMono 9sp W400, c.textLabel)
        Text("%.2f".format(cell.volts), JBMono 12sp W700, c.rampGood, fontFeatureSettings = "tnum")
    }
}
```

Wrap both collapsed and expanded body in an `AnimatedVisibility` / `animateContentSize()` on the parent `Column` so the strip height animates smoothly.

Icon: `Icons.Filled.ExpandMore` → `androidx.compose.material.icons.filled.ExpandMore`;
`Icons.Filled.ArrowDownward` → `androidx.compose.material.icons.filled.ArrowDownward`.

---

#### 3h. `dashboard/TempsCard.kt`

A self-contained composable `TempsCard(state: DashboardUiState, modifier: Modifier = Modifier)`.

This is deprioritized but required. Full-width `DashboardCard`:
- `Label("TEMPERATURES")`
- `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly)`:
  - `TemperatureTile("CTRL", state.controllerTempF, state.temperatureUnitLabel)`
  - Vertical `Divider(color = c.divider, modifier = Modifier.height(40.dp).width(1.dp))`
  - `TemperatureTile("MOTOR", state.motorTempF, state.temperatureUnitLabel)`
  - Vertical `Divider(...)`
  - `TemperatureTile("BATT", state.batteryTempF, state.temperatureUnitLabel)`

Private helper `TemperatureTile(label: String, tempValue: Int, unit: String)`:
- `Column(horizontalAlignment = Alignment.CenterHorizontally)`:
  - `Label(label)`
  - `Text("$tempValue°$unit", Saira 24sp W800, c.textPrimary, tnum)`

---

### 4. New entry-point composable `dashboard/DashboardScreen.kt`

Create `app/src/main/kotlin/com/zwheel/app/ui/dashboard/DashboardScreen.kt`.
This is the new primary dashboard screen composable, replacing `DashboardComposables.kt` as the
screen root. Do NOT delete `DashboardComposables.kt` yet — it compiles and may still be wired in
`ZWheelAppScreen.kt`. Introduce `DashboardScreen` as the new preferred composable; it will be
swapped in as a follow-up.

```kotlin
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onRequestLocation: () -> Unit = {},
    locationGranted: Boolean = true,
    locationPermanentlyDenied: Boolean = false,
)
```

Layout (edge-to-edge, real Android status bar — do NOT draw a fake status bar):
- Root: `Box(Modifier.fillMaxSize().background(c.screenBg).systemBarsPadding())`
  — `systemBarsPadding()` lets the real status bar show through; content starts below it.
  The speed slab should bleed to the horizontal edges (no horizontal padding on that section).
- Inside: `LazyColumn(contentPadding = PaddingValues(bottom = 16.dp))` with these items in order:

  ```
  item { BoardHeader(state, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) }
  item { SpeedSlab(state) }           // no horizontal padding — bleeds to edges
  item { PushbackBar(state) }
  item {
      BatteryBand(state, Modifier.fillMaxWidth().padding(horizontal = 18.dp))
  }
  item {
      TripCard(state, Modifier.fillMaxWidth().padding(horizontal = 18.dp, top = 10.dp))
  }
  item {
      StatRow(state, Modifier.fillMaxWidth().padding(horizontal = 18.dp, top = 10.dp))
  }
  item {
      DashboardCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, top = 10.dp)) {
          CellStrip(state.cellVoltages)
      }
  }
  item {
      TempsCard(state, Modifier.fillMaxWidth().padding(horizontal = 18.dp, top = 10.dp))
  }
  ```

Add a `@Preview` at the bottom using `mockDashboardState()`.

---

### 5. Icon imports — reference table

All icons are from `androidx.compose.material.icons`. Use the `extended` artifact (already on
classpath). Explicit mappings for this slice:

| Spec ligature name | Compose icon object |
|---|---|
| `my_location` | `Icons.Filled.MyLocation` |
| `bluetooth_connected` | `Icons.Filled.BluetoothConnected` |
| `arrow_upward` | `Icons.Filled.ArrowUpward` |
| `arrow_downward` | `Icons.Filled.ArrowDownward` |
| `bolt` | `Icons.Filled.Bolt` |
| `lightbulb` | `Icons.Filled.Lightbulb` |
| `expand_more` | `Icons.Filled.ExpandMore` |

If any icon is missing from `Icons.Filled`, fall back to `Icons.Outlined` variant.

---

### 6. Typography shorthand for this slice

All text style references above map to the Slice 0 `Type.kt` families. Quick reference:

| Shorthand used above | Equivalent |
|---|---|
| Saira Nsp / WXxx | `TextStyle(fontFamily = SairaFamily, fontSize = N.sp, fontWeight = FontWeight.WXXX)` |
| JBMono Nsp | `TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = N.sp)` |
| `tnum` | Add `fontFeatureSettings = "tnum"` to any numeric `TextStyle` |
| `Label(...)` | Existing Slice 0 composable (eyebrow label, mono, small caps) |
| `SmallStat(...)` | Existing Slice 0 composable |

Do NOT redefine `SairaFamily` or `JetBrainsMonoFamily` — import them from
`com.zwheel.app.ui.Type` (or whatever package Slice 0 placed them in).

---

## Constraints (self-review before commit)

1. **`core/` untouched.** No Android imports, no new BLE UUIDs, no network calls in `app/main`.
2. **300-line soft limit per file.** Split further if a file grows too large.
3. **Token access only via `LocalZWheelColors.current`.** No hardcoded hex colors anywhere in this slice.
4. **`ramp()` helper** for all magnitude-based color derivations (battery %, cell voltages).
   Speed color uses explicit threshold comparisons against `PushbackThresholds` constants, not `ramp()`.
5. **`DashboardState.kt` / `DashboardViewModel.kt` are the only existing files you may edit.**
   Do not edit `DashboardComponents.kt`, `DashboardComposables.kt`, `ConnectionBar.kt`,
   `ZWheelAppScreen.kt`, or any file outside `app/`.
6. **Tap-to-expand state** (`CellStrip`) is `remember { mutableStateOf(false) }` — NOT in ViewModel.
7. **No fake OS status bar.** Use `Modifier.systemBarsPadding()` so the real Android status bar
   shows through. Do not render a Row mimicking clock/signal/battery icons.
8. **`DashboardScreen` is additive.** It must compile alongside the existing `DashboardComposables.kt`;
   do not make `ZWheelAppScreen.kt` call it yet (that wiring is a follow-up gate).
9. All numeric `Text` values use `fontFeatureSettings = "tnum"` for tabular figures.
10. The per-cell expanded grid uses `userScrollEnabled = false` on `LazyVerticalGrid` — the outer
    `LazyColumn` handles scrolling.

---

## Build & commit

1. From the worktree root run:
   ```
   GRADLE_OPTS="-Xmx4g" ./gradlew :app:compileDebugKotlin
   ```
   Fix ALL errors until it succeeds.

2. Commit everything (new files + edits to `DashboardState.kt`) with:
   ```
   feat(ui): dark instrument-cluster dashboard
   ```
   Conventional Commits, one commit. Do NOT add any Co-Authored-By line.
