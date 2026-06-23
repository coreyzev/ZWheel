# Dark Redesign — Implementation Status

Tracks spec compliance against `docs/design/spec.md` as of 2026-06-23.
All items below are **shipped to `main`** unless noted otherwise.

---

## Design Tokens

| Token category | Status | Notes |
|----------------|--------|-------|
| Color (`ZWheelColors`) | ✅ Done | All tokens in `ui/ZWheelTheme.kt` via `LocalZWheelColors` CompositionLocal |
| Typography (Saira + JetBrains Mono) | ✅ Done | `SairaFamily` / `JetBrainsMonoFamily` in `ui/Type.kt` |
| Spacing / radii | ✅ Done | Applied per-screen; 18dp horizontal padding, hero 18dp, card 16dp, cardSmall 14dp |

---

## Screens

### 1. Connect / Scanning
| Item | Status | Notes |
|------|--------|-------|
| "Connect your board" title + subhead | ✅ Done | |
| BLE state chips row (idle/scanning/connecting/connected/disconnected) | ✅ Done | `Connecting` state added to `ConnectionState` enum |
| Found-device list rows | ✅ Done | |
| Cyan bluetooth_searching scanning indicator | ✅ Done | Static icon (no ring animation — intentional, see §Deviations) |

### 2. Permissions
| Item | Status | Notes |
|------|--------|-------|
| Per-permission cards with granted/denied state | ✅ Done | |
| `borderGreen` / `borderRed` borders | ✅ Done | |
| "Open settings" vs "Grant permission" button copy | ✅ Done | Fixed in spec audit |
| `onOpenLocationSettings` callback wired | ✅ Done | Was previously calling wrong handler |
| Legend card ("PERMISSION STATES HANDLED") | ✅ Done | |

### 3. Battery Optimization
| Item | Status | Notes |
|------|--------|-------|
| Samsung-specific onboarding screen | ✅ Done | Samsung-only copy — intentional (see §Deviations) |
| Numbered step cards with lime circles | ✅ Done | |

### 4. Live Dashboard (PRIMARY SCREEN)
| Item | Status | Notes |
|------|--------|-------|
| Board header row (status dot + name + board type badge) | ✅ Done | |
| Connection line (GPS · RSSI) | ✅ Done | RSSI format `{N}dBm` (no space) |
| Speed slab: 96sp Saira, speed value in ramp color | ✅ Done | `fontSize=96.sp`, `letterSpacing=(-6).sp` |
| Speed slab: dark-lime gradient background | ✅ Done | `Brush.verticalGradient(Color(0xFF13160D) → screenBg)` |
| Speed slab: TIRE-CORRECTED badge + top speed | ✅ Done | |
| Pushback headroom bar: lime→amber→red gradient | ✅ Done | `Brush.horizontalGradient` with 4 color stops; pill corners |
| Pushback label: "nominal" / "approaching pushback" / "pushback · ease off" | ✅ Done | |
| Battery + range hero band (equal-height cards) | ✅ Done | `Row(Modifier.height(IntrinsicSize.Min))` + `fillMaxHeight()` |
| Battery card: % in ramp color + mini fill bar | ✅ Done | |
| Battery card caption: `{N}S PACK` | ✅ Done | Removed voltage from caption |
| Range card: cyan estimate | ✅ Done | |
| Trip-distance wide card (18dp radius) | ✅ Done | |
| Small stat row (3 equal-height tiles, 14dp radius) | ✅ Done | |
| Cell strip: bars always visible (no AnimatedVisibility on collapsed) | ✅ Done | Removed `AnimatedVisibility` wrapper |
| Cell strip: zero-padded labels C01…C15 | ✅ Done | `"C%02d".format(index+1)` |
| Temps card | ✅ Done | |

### 5–6. History List + Empty State
| Item | Status | Notes |
|------|--------|-------|
| Ride rows (14dp radius, time/duration/board name/distance/top speed) | ✅ Done | `RoundedCornerShape(14.dp)` |
| Board name ellipsis; stats column never shrinks | ✅ Done | |
| Empty state: connection-aware CTA | ✅ Done | |

### 7. Ride Detail
| Item | Status | Notes |
|------|--------|-------|
| Mini map (speed-colored route, start/end markers) | ✅ Done | |
| Full-screen map chip | ✅ Done | |
| Stat grid (DURATION, DISTANCE, TOP SPEED, AVG SPEED, GPS DISTANCE, Ah USED) | ✅ Done | |
| BOARD card at bottom | ✅ Done | |

### 8. Full-Screen Route Map
| Item | Status | Notes |
|------|--------|-------|
| Edge-to-edge map | ✅ Done | `WindowCompat.setDecorFitsSystemWindows(false)` in `MainActivity` |
| Translucent top bar with close button + ride-time chip | ✅ Done | Added in `feat(history): add full map ride overlays` |
| Bottom summary overlay: speed legend + start/end markers + stats | ✅ Done | `distanceLabel`, `durationLabel`, `topSpeedLabel` |

### 9. Settings
| Item | Status | Notes |
|------|--------|-------|
| CONNECTED BOARD section: editable board name | ✅ Done | |
| Info row: RSSI + board type badge + firmware | ✅ Done | |
| DEVICE INFO disclosure (expandable) | ✅ Done | |
| UNITS: MPH/KPH + °F/°C segmented toggles | ✅ Done | |
| TIRE CALIBRATION: slider in Connected Board section | ✅ Done | Post-spec decision: kept in CONNECTED BOARD, not own section (see §Deviations) |
| HOME ASSISTANT section | ✅ Done | |
| DEVELOPER: "BLE debug view" toggle | ✅ Done | Label uses "BLE debug view" (not "logging") |
| SUPPORT: donate | ⚠️ TODO | Placeholder only — see §Deviations |
| ABOUT / Footer | ✅ Done | |

### 10. Wear OS Watch Faces
| Item | Status | Notes |
|------|--------|-------|
| Active / nominal state | ✅ Done | |
| Caution / pushback warning | ✅ Done | |
| Ambient / always-on | ✅ Done | |
| Disconnected / scanning | ✅ Done | |

---

## Intentional Deviations (do not revert)

These items differ from the spec by deliberate product decision:

| Spec item | Deviation | Reason |
|-----------|-----------|--------|
| **C2-4**: Scanning ring expand animation (1.8s) | Not implemented — static icon only | Corey chose simple static UI; animation adds complexity without functional value |
| **B1-2**: Samsung battery optimization copy | Samsung-specific text shown only on Samsung devices | Deliberate — the spec text makes sense only for Samsung (aggressive battery kill) |
| **S1**: Tire calibration as its own Settings section | Lives inside CONNECTED BOARD section | Post-spec intentional placement; calibration is meaningless without a connected board |
| **S4**: Donate button in SUPPORT section | Placeholder / TODO only | Not implemented yet; will be a future addition |

---

## Post-Spec System Changes

These changes were made after the spec was written but are correct behavior:

| Change | Commit | Notes |
|--------|--------|-------|
| Edge-to-edge layout (`setDecorFitsSystemWindows(false)`) | `3b9778d` | Applied to all screens via `MainActivity`; per-screen `systemBarsPadding()` calls removed |
| `ConnectionState.Connecting` added | `3b9778d` | Spec shows "connecting" chip — enum was missing the state |
| BLE debug logging toggle in Settings | `1d0bfa5` | New feature beyond spec scope; gated by dev toggle |
| `BleDebugRecorder` internal test constructor | `743db28` | Testability pattern — keeps public API clean while allowing deterministic test injection |

---

## CI / Build Notes

- **Roborazzi is NOT a dependency** — Roborazzi 1.64.0 requires Kotlin 2.3.0; project uses 2.0.21. Do not add it.
- **Screenshot test directory deleted** — `app/src/test/kotlin/com/zwheel/app/ui/screenshots/` was removed (dead code blocking `:app:test`).
- **Gate docs archived** — `docs/gates/` contains 11 committed gate files for reference.
