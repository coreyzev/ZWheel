# Gate: Slice 1b — Dashboard fidelity fixes

The dashboard (Slice 1) is structurally correct but a headless render vs. the golden mockups revealed
four fidelity gaps. Fix all four. Read ONLY this gate.

App module only. `core/` untouched. No network in app/main. No new BLE UUID.
Make **two commits** (icons separate from calibration) as noted at the end.

---

## Fix 1 — Correct Material icons (currently placeholders)

The dashboard was built with core-only icons because `material-icons-extended` wasn't on the
classpath, so several glyphs are wrong (Bluetooth→gear, amps→warning triangle, lights→info).

- In `gradle/libs.versions.toml` add `androidx.compose.material:material-icons-extended` (no explicit
  version if a Compose BOM governs it; otherwise match the existing Compose Material version). Add it to
  `app/build.gradle.kts` as an `implementation(...)`.
- Replace the placeholder icons in `app/src/main/kotlin/com/zwheel/app/ui/dashboard/` with the correct
  `androidx.compose.material.icons.filled.*`:

| Use site | Correct icon |
|---|---|
| GPS / location (BoardHeader) | `MyLocation` |
| Bluetooth connected (BoardHeader) | `BluetoothConnected` |
| Top-speed arrow (SpeedSlab) | `ArrowUpward` |
| Per-cell max arrow (CellStrip) | `ArrowUpward` |
| Per-cell min arrow (CellStrip) | `ArrowDownward` |
| Per-cell expand chevron (CellStrip) | `ExpandMore` |
| Draw / amps tile (StatRow) | `Bolt` |
| Lights tile (StatRow) | `Lightbulb` |

Remove now-unused placeholder imports (Info, Settings, Warning, LocationOn, KeyboardArrowUp/Down) where
they were only substitutes.

---

## Fix 2 — Per-cell voltage color calibration

Healthy Onewheel cells (~3.6–4.0 V) must render **green**, not amber. The golden reference shows cells
at 3.66–3.70 V as green and only a 3.41 V outlier as red. The current ramp `(v-3.5)/0.7` is wrong.

- Replace the per-cell color logic (in `CellStrip.kt`) with explicit voltage thresholds:
  - `>= 3.60 V` → `rampGood` (green)
  - `3.40–3.60 V` → `rampCaution` (amber)
  - `< 3.40 V` → `rampDanger` (red)
  Put these constants next to `PushbackThresholds` in `PushbackConstants.kt` (e.g. `CellThresholds`)
  with a `// TODO(hardware-tune)` note. Use them for BOTH the collapsed bar color and the expanded grid
  value color (so a green cell's number is green, a low cell's number/border is red).
- Fix the `isLow` threshold: a cell is low only when `< 3.40 V` (NOT `< 3.9`). Update wherever
  `CellVoltageUiState.isLow` is computed — in `DashboardState.kt` `toDashboardUiState(...)` AND in
  `mockDashboardState()`. After this change, the mock's ~3.86–3.96 V cells must all be green/not-low.

---

## Fix 3 — Pushback bar zones must align with thresholds

The bar currently draws three **equal-third** zones, so the marker sits in the visual "red" third while
the status label still reads "nominal". Make the colored zone widths match the real thresholds in
`PushbackThresholds`:
- green segment: `0 → CAUTION_FRACTION` of track width
- amber segment: `CAUTION_FRACTION → DANGER_FRACTION`
- red segment: `DANGER_FRACTION → 1.0`
(each at ~30% alpha as today). The marker x-position stays `fraction * trackWidth`. Result: at a nominal
speed the marker sits in the green segment, consistent with the green speed value and "nominal" label.

---

## Fix 4 — Threshold tuning so the design states reproduce

In `PushbackConstants.kt`, set `CAUTION_FRACTION = 0.78f` (keep `DANGER_FRACTION = 0.95f`) and keep the
`// TODO(hardware-tune)` note. Rationale: the golden states show ~15.8 mph as amber and ~19.5 mph as red
on an XR-class board (top ~20 mph); 0.78 makes 15.8 render amber while a cruising 14–15 mph stays green.
Do not change anything else about the speed-color logic.

---

## Build & commit
1. `GRADLE_OPTS="-Xmx4g" ./gradlew :app:compileDebugKotlin` — must pass.
2. Two commits, no Co-Authored-By line:
   - `fix(ui): use material-icons-extended for correct dashboard glyphs` (Fix 1)
   - `fix(ui): calibrate per-cell colors and align pushback bar zones` (Fixes 2–4)
