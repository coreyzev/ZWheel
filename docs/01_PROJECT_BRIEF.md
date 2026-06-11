# ZWheel — Project Brief
**A personal Android + Wear OS companion for stock (Future Motion) Onewheels.**
Version 1.0 — June 2026. Supersedes the OWCE NextGen MAUI plan entirely.

*(The project was officially renamed from the FloatDash working title to ZWheel.)*

---

## 1. Why this exists

- OWCE is dead, pOnewheel is deprecated, and the only maintained Android option for stock
  Onewheels is the official Future Motion app — which requires their cloud, and is the
  delivery vector for firmware updates the owner does not want.
- Nosedive covers iOS thoroughly. **iOS is out of scope, permanently for v1.**
- Float Control covers VESC thoroughly. **VESC is out of scope.**
- This is **built for one rider first** (Corey: Onewheel+ XR, hardware 4029, Samsung S25
  Ultra, Galaxy Watch 7 Classic, switching to Pixel later in 2026), released publicly as
  free open source with a donate button if/when it's good.

## 2. Product definition (v1)

An Android phone app + bundled Wear OS app that:

1. Connects to a stock Onewheel over BLE, performs the unlock handshake, and **stays
   connected for an entire ride without Android killing it.**
2. Shows live telemetry in an OWCE-style neon card dashboard (see §5).
3. Records rides (GPS + telemetry) to local SQLite. No cloud. No account. Ever.
4. Tracks **trip top speed** and **lifetime top speed**.
5. Applies a **user-configured wheel diameter correction** to speed, distance, and range
   (Corey's tire is ~1" smaller than stock XR; OWCE reads wrong because of this).
6. Pushes speed / top speed / battery % / est. remaining range to the watch at 1 Hz,
   displayed full-time including ambient mode, **never task-killed**.
7. Controls: ride mode selection, front/back light toggle.
8. **Never implements any firmware-write or update path.** This is a hard product rule,
   not a deferred feature. The app cannot brick or update a board by design.

### Explicitly OUT of v1
- iOS / watchOS (Nosedive owns it)
- VESC (Float Control owns it)
- GT / GT-S (Polaris handshake — board owner doesn't have one; protocol module is
  designed so it can be added later)
- Anything social, leaderboards, accounts, cloud sync, analytics/telemetry of the user
- Firmware updates (hard rule, see above)
- Monetization (donate link in About page only)

### v1.5 / v2 candidates (build the seams, not the features)
- GPX export (share sheet) — small, high value, likely first follow-up
- Ride route map on ride detail
- Standalone watch BLE mode (ride phone-free)
- GT/GT-S strategy module
- F-Droid listing (after a few stable tags)

## 3. Board support matrix (v1)

| Board | Handshake | Status |
|---|---|---|
| Onewheel V1 | None (open) | Supported |
| Onewheel+ (Plus) | Gemini challenge–response (fw ≥ Gemini) or open | Supported |
| **Onewheel+ XR (HW 4029)** | Gemini challenge–response | **Primary target — Corey's board** |
| Pint / Pint X | Gemini-era | Supported, community-tested |
| XR HW ≥ 4210 / newer fw | Tightened lockdown | Best-effort; document known limits |
| GT / GT-S | Polaris 6215 token | Out of scope v1 (strategy slot reserved) |

Protocol references the implementing agent must mine (read-only references, never
copy GPL code verbatim without checking license compatibility — see AGENTS.md §Licensing):
- `ponewheel/android-ponewheel` — esp. **issue #86** (Gemini unlock cracked here) and its
  BLE characteristic map
- `COM8/UWP-Onewheel` — cleanest written documentation of the challenge–response
- `OnewheelCommunityEdition/OWCE_App` (legacy Xamarin) — `OWBoard.cs` byte parsing,
  and the new repo's `HandshakeService.cs` / `BoardStateService.cs` as a C# spec
- `kite247/Onewheel2Garmin` — minimal working Gemini handshake, small and readable

## 4. The two features that make this app worth building

### 4.1 Wheel diameter correction (the reason OWCE reads wrong)
- Setting: **tire outer diameter**, per saved board, in inches (default: stock for the
  detected board type — XR stock = 11.5"). Hub size stored as informational only.
- The board firmware computes speed/odometer assuming the stock tire. Correction:
  - If RPM characteristic is available (it is on XR-era boards):
    `speed = rpm × π × userDiameter × unit-constants` — compute from first principles.
  - Otherwise scale firmware-reported speed/distance by `userDiameter / stockDiameter`.
  - Implementing agent verifies which path the 4029 XR supports from the references
    above and documents the decision in an ADR. Both paths ship behind one
    `SpeedCalculator` interface in `:core`.
- **Every consumer — dashboard, ride recorder, top speed, range estimate, watch
  payload — reads corrected values only.** Raw firmware values are stored alongside in
  ride records (for debugging) but never displayed.

### 4.2 The bulletproof ride service (the reason FM's app frustrates)
Full spec in 02_ARCHITECTURE.md §6. Summary: foreground service
(`connectedDevice|location` types), ongoing notification with live speed/battery,
partial wakelock during active ride only, battery-optimization exemption onboarding with
**Samsung-specific** steps (S25 Ultra is the dev device; Samsung is the most aggressive
killer — see dontkillmyapp.com/samsung), auto-reconnect with exponential backoff, and a
"connection lost" watch + phone alert. The 2-minute backgrounding Corey sees with the FM
app is exactly what this kills.

## 5. Design language

Keep the OWCE look — it's loved and it's distinctive:
- Stacked full-width rounded cards, one stat domain per card, each with a loud flat
  color: yellow = speed (with semicircular gauge), magenta = battery (with per-cell
  voltage grid), purple = current/amps, cyan = temperatures, black = ride mode.
- Huge condensed bold numerals, tiny uppercase labels, light-gray app background.
- Reference: the two OWCE TestFlight screenshots in the project folder. Reproduce the
  *feel* in Compose (Material 3 base, custom card components); don't pixel-copy.
- Add what OWCE never finished: a proper ride history list, ride detail page, settings
  with the diameter field, and a top-speed callout on the speed card.
- Watch: black background, giant speed numeral, battery + range + top speed in a ring or
  bottom row. Ambient mode = dimmed, 1-minute updates, burn-in-safe layout shifts.

## 6. Distribution & legal posture

- GitHub releases (signed APK, Wear app bundled) from day one; F-Droid later.
- Open source. **License: GPLv3** — pOnewheel is GPL and if any of its code (not just
  ideas) is ported, GPL is obligatory anyway; it also discourages closed-source forks.
- Standard disclaimer everywhere: not affiliated with/endorsed by Future Motion; use at
  your own risk; trademark used nominatively.
- The app only reads the same public BLE GATT interface every community app has used
  since 2016 and writes only the same unlock/light/ride-mode characteristics. No DRM
  circumvention beyond what the community has published openly for years; no FM cloud
  contact, ever. (Not legal advice; this is the community-standard posture.)

## 7. Development approach

- **One coding agent (Claude Code), one human (Corey). Hermes is not required.**
  Rationale: this is now a single-stack Kotlin/Compose project of moderate size; a
  multi-agent orchestra adds coordination overhead with no payoff. If Corey prefers
  Hermes as the executor, all docs still work — Hermes just plays the "implementing
  agent" role and AGENTS.md is its contract.
- Discipline carried over from the v2.1 plan (the good parts): AGENTS.md as shared
  memory, conventional commits, one-concern PRs/commits, interface-first in `:core`,
  tests in the same phase as code, human gates at milestones that need a physical board.
- Everything the agent needs is in: this brief, 02_ARCHITECTURE.md, 03_AGENTS.md,
  04_BUILD_PLAN.md. Corey's runbook is 05_COREY_RUNBOOK.md.
