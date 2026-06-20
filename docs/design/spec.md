# Handoff: ZWheel — Dark Instrument-Cluster Redesign

## Overview

A full visual redesign of the ZWheel Onewheel ride-companion app: a dark, sunlight-legible
"instrument cluster" look for the phone app plus a glanceable Wear OS watch mirror. It covers
the live ride dashboard, ride history (list + detail + full-screen route map), settings, and the
connect / permissions / battery-optimization onboarding flow.

The product behavior is unchanged from the existing app (offline-first, local-only BLE telemetry +
ride logging, optional Home Assistant push). **This handoff changes how it looks, not what it does.**

---

## About the design files

The files in `screens/` and `ZWheel.dc.html` are **design references created in HTML** — a
clickable prototype showing intended look, layout, and behavior. They are **not production code to
port line-for-line.**

`ZWheel.dc.html` is a custom HTML component format (it renders via React internally and uses
`{{ }}` template bindings, `<sc-if>` / `<sc-for>` tags, and a `support.js` runtime). **Ignore that
machinery.** It exists only to make the prototype interactive in a browser. Read it for exact
colors, spacing, copy, and structure — not as an implementation to translate.

**The task:** recreate these designs in the existing **Kotlin / Jetpack Compose** codebase, using
its established patterns (Compose, Material3, the existing ViewModels, repositories, and BLE/data
layers). Re-skin and re-lay-out the UI; do not rewrite the data/service layers.

---

## Fidelity: HIGH

These are pixel-level mockups with final colors, typography, spacing, and interactions. Recreate the
UI faithfully using Compose primitives. Exact hex values, font sizes (px → sp/dp), and radii are
given below and are intended to be used as-is.

> **A note on the screenshots:** the Material Symbols icon font could not be embedded in the capture,
> so in the PNGs icons appear as their **ligature text names** (e.g. `my_location`, `expand_more`,
> `bluetooth_connected`). That is a capture artifact, not part of the design. Conveniently, those
> names map directly to Material icons you'll use in Compose. Each screen section below lists the
> real icon for every glyph.

---

## ⚠️ This REPLACES the current Material3 light theme

The current app (`app/src/main/kotlin/com/zwheel/app/ui/ZWheelTheme.kt`) uses a **light**
`lightColorScheme` — white surfaces, `#eeeeee` background, magenta `#e4007f`, cyan `#00a7c8`, and
`FontWeight.Black` text with `RoundedCornerShape(8.dp)`.

This redesign is the opposite: a **near-black dark theme** with a lime primary and cyan data accent.
You will:

1. Rewrite `ZWheelTheme.kt` to a dark scheme (use `darkColorScheme` + a custom token object — see
   Design Tokens below). Material3's semantic slots aren't enough; define an explicit `ZWheelColors`
   token object and pass it via a `CompositionLocal` (e.g. `LocalZWheelColors`).
2. Restyle the existing component helpers in
   `app/src/main/kotlin/com/zwheel/app/ui/DashboardComponents.kt` (`DashboardCard`, `SpeedGauge`,
   `Label`, `Metric`, `SmallStat`) to the new tokens — e.g. `DashboardCard` becomes
   `#121419` fill, `#20242d` 1dp border, `16.dp` radius (not 8).
3. Add two type families (Saira + JetBrains Mono) — see Typography.

---

## Design Tokens

### Color — paste into a `ZWheelColors` token object

```kotlin
object ZWheelColors {
    // Surfaces (darkest → lightest)
    val screenBg      = Color(0xFF0A0B0E) // phone screen / app background
    val navBg         = Color(0xFF0C0D11) // bottom tab bar
    val mapBg         = Color(0xFF0C0E12) // map + ride thumbnails
    val insetRow      = Color(0xFF0E1014) // rows inside a card (device-info, etc.)
    val legendCard    = Color(0xFF101319) // secondary/legend cards
    val card          = Color(0xFF121419) // STANDARD card fill
    val cardElevated  = Color(0xFF181B22) // secondary buttons (Disconnect / Forget)

    // Borders & dividers
    val border        = Color(0xFF20242D) // standard card border (1dp)
    val divider       = Color(0xFF1A1D24) // hairline divider inside cards
    val frameBorder   = Color(0xFF23262F) // phone frame / device bezel
    val buttonBorder  = Color(0xFF262B35)
    val borderGreen   = Color(0xFF1C4030) // "granted" permission card border
    val borderRed     = Color(0xFF3A1C1C) // "denied" permission card border
    val borderLime    = Color(0xFF2A3520) // lime badge/border tint
    val borderBattery = Color(0xFF223018) // battery hero card border

    // Text (primary → dimmest)
    val textPrimary   = Color(0xFFF2F4F7)
    val textStatus    = Color(0xFFCFD5DE) // status-bar glyphs
    val textSecondary = Color(0xFF9AA4B2)
    val textMuted     = Color(0xFF7C8696)
    val textLabel     = Color(0xFF6B7484) // small caps labels
    val textDim       = Color(0xFF5A616E)
    val textDimmest   = Color(0xFF4A5260) // section eyebrow labels
    val separator     = Color(0xFF2A2E36)

    // Accents
    val lime          = Color(0xFFC6F24E) // PRIMARY — speed, active, CTAs
    val cyan          = Color(0xFF38E0FF) // data / secondary (range, GPS, scanning)

    // Battery / load "ramp" (good → caution → danger). Also used for cell voltages,
    // speed color, and pushback headroom.
    val rampGood      = Color(0xFF4FE086) // green
    val rampCaution   = Color(0xFFFFB22E) // amber (also "top speed" value color)
    val rampDanger    = Color(0xFFFF5A5A) // red
}
```

**Accent usage rule:** lime `#C6F24E` is the single brand/primary accent (speed readout, primary
buttons, active tab, "on" toggles, selected segmented option). cyan `#38E0FF` is reserved for
*data/secondary* meaning — range estimate, GPS indicator, scanning state, the slow end of the speed
legend. The green/amber/red ramp encodes magnitude (battery %, per-cell voltage, current speed vs.
pushback, motor temp). Don't introduce other hues.

### Typography

Two families. Add both to `res/font/` (or via Google Fonts in Compose) and define a `Type.kt`.

| Role | Family | Weight | Size | Tracking | Notes |
|------|--------|--------|------|----------|-------|
| Speed hero (dashboard) | **Saira** | 900 | 188px → ~96sp* | -6px | Decimal part 74px in `textSecondary`. *Scale to fit width; this is the focal element. |
| Watch speed | Saira | 700 | ~64sp | tabular nums | decimal smaller + dim |
| Screen title ("Settings", "Ride history") | Saira | 800 | 26px → 26sp | -0.5px | |
| Ride-detail title | Saira | 800 | 20px → 20sp | -0.3px | |
| Section heading (onboarding) | Saira | 800 | 24px → 24sp | -0.4px | |
| Stat value (detail grid) | Saira | 800 | 26px → 26sp | tabular nums | |
| Card number (history row dist.) | Saira | 800 | 19px → 19sp | tabular nums | |
| Body / list item | Saira | 600–700 | 15–16px → 15–16sp | | |
| Body copy / descriptions | Saira | 400 | 14px → 14sp | line-height 1.5 | `textMuted` |
| **Eyebrow / section label** | **JetBrains Mono** | 400 | 9px → 9sp | +1.5–2px, UPPERCASE | `textDimmest`/`textLabel` |
| **Telemetry meta** (firmware, RSSI, time, "24 min") | JetBrains Mono | 400 | 10–11px → 10–11sp | | `textMuted` |
| Mono numbers (device info, cell volts) | JetBrains Mono | 700 | 11–13px → 11–13sp | tabular nums | |
| Status bar clock | JetBrains Mono | 400 | 13px → 13sp | | |

All numeric telemetry uses **tabular figures** (`fontFeatureSettings = "tnum"`).

### Spacing

- Screen horizontal padding: **18dp** (content), **22dp** (titles/headers)
- Standard card padding: **12–16dp**
- Gaps between cards/sections: **8–14dp** (10dp is the default grid gap); **22dp** between major
  Settings sections
- Status bar height: **44dp**; bottom tab bar item padding: **10dp top / 15dp bottom**

### Radii

| Token | Value | Use |
|-------|-------|-----|
| frame | 46dp | phone screen container / device bezel |
| hero | 18dp | battery hero card, trip-distance card, map |
| card | 16dp | standard cards, onboarding cards |
| cardSmall | 14dp | dashboard stat tiles, cell strip, temps |
| chip | 10dp | inset rows, secondary buttons |
| badge | 5dp | PINT X / TIRE-CORRECTED micro badges |
| pill | 999dp | CTAs, status chips, toggles, board chip |

### Shadows / glows

- Phone frame / elevated surface: `0 40px 80px -30px rgba(0,0,0,0.8)` (soft, very diffuse — in
  Compose approximate with a large blur, low-alpha ambient shadow or a dark scrim).
- **Lime glow** (logo tile, toggle knob, active markers): `0 0 30px rgba(198,242,78,0.4)` and
  `0 0 12px rgba(198,242,78,0.5)`. Render as a soft radial behind the element.
- **Cyan / status-dot glows** use the same pattern with the relevant accent.
- Speed digits have a subtle pulsing glow (`zwGlow`, 2.4s ease-in-out) — optional polish.

---

## Screens / Views

Phone screens are **390 × 844** (logical). Bottom tab bar = **Ride / History / Settings**
(icons: `speed`, `timeline`, `tune`; active tab in lime, inactive in `textLabel`).

### 1. Connect / Scanning — `screens/01-connect.png`
**Purpose:** find & pair a board on launch when none is connected.
- Title "Connect your board" + muted subhead "Power on your Onewheel and keep it nearby."
- Row of BLE **state chips** (idle / scanning / connecting / connected / disconnected) — pill
  outlines, `9sp` mono, current state highlighted.
- **Scanning indicator:** cyan `bluetooth_searching` icon with an expanding ring pulse
  (`zwScan`, 1.8s ease-out) + "Scanning for boards…" in cyan.
- Found-device list rows: board name (Saira 15sp/700) + RSSI/meta (mono 10sp), tap to connect.
- Existing board card shows name + **PINT X** lime badge.

### 2. Permissions — `screens/02-permissions.png`
**Purpose:** runtime permission states for BLE + location.
- Cards per permission. **Granted** card: `borderGreen` border, `● GRANTED` in `rampGood` mono.
  **Permanently denied** card: `borderRed` border + a lime "Open settings" action button.
- A legend card ("PERMISSION STATES HANDLED") enumerating handled states.

### 3. Battery optimization — `screens/03-battery-optimization.png`
**Purpose:** Samsung/Android background-kill mitigation.
- Pill tag, title "Keep ZWheel alive while you ride", explanatory body.
- Numbered step cards (lime `#C6F24E` 26dp numbered circles, dark text): "Unrestrict battery usage",
  "Exclude from sleeping apps".

### 4. Live Dashboard — `screens/04-dashboard.png` (PRIMARY SCREEN)
**Purpose:** at-a-glance live ride telemetry.
Top → bottom:
1. **Status bar** (clock + `signal_cellular_alt` / `wifi` / `battery_full`).
2. **Board header row** (own line): green status dot (`rampGood`, glowing) + board **name** (Saira
   14sp/700, ellipsis on overflow, `flex:1`) + **PINT X** outline badge (right, never shrinks).
3. **Connection line** below: `my_location` (cyan) `GPS · ` `bluetooth_connected` `{RSSI}` — mono
   11sp, each glyph an explicit row item.
4. **Speed slab** (bleeds to edges, subtle dark-lime gradient `#13160D→#0A0B0E`): giant speed value
   (Saira 900, color = current ramp color) + decimal; below it `MPH`, a `TIRE-CORRECTED` lime badge,
   and `↑ {topSpeed}` (`arrow_upward`, amber).
5. **Pushback headroom bar:** horizontal track with lime→amber→red zones and a filled marker that
   advances with speed; right label = state ("nominal" / "approaching pushback" in ramp color).
6. **Battery + range hero band** (2 cards): battery `%` (big, ramp-colored, with a mini fill bar +
   "S-pack" caption) | range estimate in **cyan** ("EST. REMAINING at current draw").
7. **Trip-distance wide card:** distance (Saira 800) + small right-aligned stats (Ah, regen `+0.6`,
   avg).
8. **Small stat row (3 tiles):** amps/`DRAW · Ah` (`bolt`), ride mode (`MISSION`), lights
   (`lightbulb` ON) — lime where active.
9. **Per-cell strip (tap to expand):** collapsed = a row of vertical bars, one per series cell,
   colored by the ramp; header "PER-CELL · {N}S" with a chevron + min/max volts. **Tapping expands**
   a grid of every cell's exact voltage (label `C01…`, value mono 700, low cells flagged red border);
   tap again collapses. **N is derived from the data** (15 for a Pint X) — must adapt to any pack size.
10. **Temps card (deprioritized):** CTRL / MOTOR / BATT °F, evenly spaced with hairline dividers.

### 5. History list — `screens/05-history.png`
**Purpose:** browse past rides (newest first, grouped "TODAY" / "EARLIER THIS WEEK").
- Title "Ride history".
- **Ride row:** left = small route-shape thumbnail (62×48, 10dp radius) — or `location_off` glyph in
  a dim tile for GPS-less rides (row at 0.7 opacity). Middle (`flex:1`, `min-width:0`): time (Saira
  16sp/700) → duration (mono 10sp, `textMuted`); if no GPS, the duration line is `"33 min · no GPS"`
  → **board name** on its own line (mono 10sp, `textDimmest`, **ellipsis**). Right (never shrinks):
  distance (Saira 19sp/800) + top-speed (`↑ x mph`, amber).
- **Layout rule that matters:** the board name must truncate with ellipsis and the stats column must
  be `flex-shrink:0`, so a long board name never pushes the distance/speed out of place and never
  hides the "no GPS" tag. (This was a real bug in the prototype iterations.)

### 6. History empty — `screens/06-history-empty.png`
**Purpose:** no rides recorded yet.
- Centered icon tile, "No rides yet", body copy.
- **Behavior:** if a board **is connected**, the CTA is a muted secondary "Go for a ride"
  (`#1A1D24` fill, `directions_run`) — **not** a bright "Connect a board" button. Only show the
  lime "Connect a board" CTA when no board is connected. (Gate on connection state.)

### 7. Ride detail — `screens/07-ride-detail.png`
**Purpose:** one ride's full breakdown.
- Back arrow + title "Today · 8:14 AM" + subtitle "Jun 18 · 24 min · {board}" (mono 10sp; **wraps**,
  not truncated) + `ios_share`.
- **Mini map** (230dp tall, 18dp radius): route polyline colored by speed (slow=cyan → fast=red),
  start = lime ring, end = red dot; speed-legend gradient chip bottom-left; "Full map" chip
  top-right (`fullscreen`) → opens screen 8.
- **Stat grid (2-col):** DURATION, DISTANCE, TOP SPEED (amber value), AVG SPEED, then **GPS DISTANCE**
  and **Ah USED** as their own grid cards (same style — each is a labelled tile, Saira 26sp value).
- **BOARD** as its **own full-width card at the bottom** (label + board name, name ellipsis).

### 8. Full-screen route map — `screens/08-full-map.png`
**Purpose:** immersive route view.
- Edge-to-edge map; translucent top bar (`close` in a circular scrim + ride time chip).
- Bottom **summary overlay** (blurred panel): speed legend + start/end markers legend + key stats.

### 9. Settings — `screens/09-settings.png` (single scrolling page)
Section eyebrow labels are mono 9sp `textDimmest`, +2px tracking, UPPERCASE. Sections top→bottom:
- **CONNECTED BOARD:** card with glowing status dot + **editable board name** (tap name → inline
  text field, `maxLength=24`, Enter or check-circle to save; name propagates app-wide). A secondary
  info row below the name holds the **PINT X** badge, RSSI (`network_wifi_3_bar` + value), and
  "Fw 4134 · 15S" — this row **wraps** (`flex-wrap`) so nothing clips. Buttons: Disconnect (red text)
  / Forget board. **DEVICE INFO** disclosure row (tap chevron to expand): Serial, Battery serial,
  Hardware rev, Firmware, RSSI — board-specific identifiers in an inset (`#0E1014`) panel.
  *(App version is NOT here — see footer.)*
- **UNITS:** Speed MPH/KPH + Temperature °F/°C segmented toggles (selected = lime fill, dark text).
- **TIRE CALIBRATION:** explanatory text + a slider (lime track + glowing knob) showing corrected
  value (e.g. "18.5…"), with min/max captions.
- **HOME ASSISTANT (OPTIONAL):** "Push battery % to HA" toggle (on = lime), URL field, token field,
  a warning/status callout, Test connection / save actions.
- **DEVELOPER:** "BLE debug view" toggle (off = `#262B35`).
- **SUPPORT:** donate.
- **ABOUT:** privacy text.
- **Footer:** quiet centered "ZWheel · v1.0" (mono 10sp `#3A3E48`) just above the tab bar — this is
  the **app** version (board-independent).

### 10. Wear OS watch faces — `screens/10-watch-faces.png`
**Purpose:** glanceable mirror, 4 states. 240px round.
- **ACTIVE / nominal:** lime progress arc, big speed (Saira 700, lime), `{batt%}  {dist}mi`,
  `TOP x` caption.
- **CAUTION / approaching pushback:** amber arc + amber `▲ PUSHBACK SOON` warning (blinks), amber speed.
- **AMBIENT / always-on:** pure black bg (`#000`), dimmed greyed speed, "SPEED · MPH" label — minimal
  for AOD power saving.
- **DISCONNECTED / scanning:** cyan `bluetooth_searching` + expanding ring + "SCANNING" / "looking
  for board…".

---

## Interactions & behavior

- **Tap per-cell strip** → expand/collapse full per-cell voltage grid (chevron rotates 180°,
  0.22s ease). Cell count derives from pack data.
- **Tap board name (Settings)** → inline edit (text field, max 24 chars). Enter / check-circle saves;
  Escape cancels. The saved name updates everywhere it appears: dashboard header, history rows, ride
  detail board card, connect screen. Persist via the existing settings store
  (`SettingsRepository` / `UserPreferences`).
- **Tap DEVICE INFO row** → expand/collapse identifiers (chevron rotates).
- **Full map chip** (ride detail) → navigate to full-screen map; `close` returns.
- **Bottom tab bar** → Ride / History / Settings navigation.
- **Empty-state CTA** is connection-aware (see screen 6).
- **Toggles / segmented controls:** on/selected = lime fill + dark text; off = `#262B35` / `#0A0B0E`.
- Animations present in the prototype: speed-digit glow pulse (2.4s), scanning ring expand (1.8s),
  pushback-warning blink (1.1s). All optional polish; the static states are the spec.

---

## State (already exists — reuse, don't rebuild)

The data/service layer is in place; wire the new UI to it:
- **Live telemetry / connection:** `ui/DashboardViewModel.kt`, `ui/DashboardState.kt`,
  `ui/ConnectionBar.kt`, `ble/ConnectionManager.kt`, `ble/ConnectionState.kt`. Speed, battery %,
  per-cell voltages, amps, temps, RSSI, top speed, mode, lights, range estimate, pushback headroom.
- **Board name / units / tire calibration / Home Assistant / dev flags:**
  `data/settings/SettingsRepository.kt`, `data/settings/UserPreferences.kt`.
- **Ride history + detail:** `data/ride/RideRepository.kt`, `RideDao.kt`, `RideEntities.kt`.
- **Route/GPS:** `service/LocationTracker.kt`, `service/RideRecorder.kt`.
- **BLE debug screen:** `ui/ble/BleDebugScreen.kt` (gated by the Settings dev toggle).
- Screen scaffolding / nav: `ui/ZWheelAppScreen.kt`.

Derived UI values to compute: ramp color from a magnitude (battery %, cell voltage, speed vs.
pushback, motor temp), pushback-headroom fill %, per-cell min/max, route-segment colors by speed.

---

## Assets

- **Icons:** Material Symbols Rounded throughout — use `androidx.compose.material.icons` (Material
  Symbols / extended). Glyphs used: `radio_button_unchecked` (logo mark), `my_location`,
  `bluetooth_connected`, `bluetooth_searching`, `arrow_upward`, `bolt`, `lightbulb`, `expand_more`,
  `network_wifi_3_bar`, `speed`, `timeline`, `tune`, `ios_share`, `arrow_back`, `fullscreen`,
  `close`, `location_off`, `signal_cellular_alt`, `wifi`, `battery_full`, `directions_run`,
  `check_circle`, `edit`.
- **Fonts:** Saira (400–900) + JetBrains Mono (400/500/700). Both on Google Fonts.
- **No raster art / logos** — the logo is the lime rounded-square tile + `radio_button_unchecked`
  glyph. Route maps and ride thumbnails are drawn vector polylines (Compose `Canvas` / `drawPath`),
  not images.

---

## Files in this bundle

- `README.md` — this spec (self-sufficient).
- `screens/01-connect.png … 09-settings.png` — phone screens (logical 390×844; Settings is the full
  scroll).
- `screens/10-watch-faces.png` — the four Wear OS states.
- `ZWheel.dc.html` — the interactive HTML prototype. Open in a browser to inspect exact values,
  spacing, copy, and the live tap-to-expand / inline-edit behaviors. Reference only — see "About the
  design files".

> Tip for Claude Code: start from `ZWheelTheme.kt` (new dark tokens + type), then
> `DashboardComponents.kt` (re-skin the shared card/label/stat helpers), then rebuild screen by
> screen against the existing ViewModels. The dashboard (screen 4) is the anchor; everything else
> reuses its card vocabulary.
