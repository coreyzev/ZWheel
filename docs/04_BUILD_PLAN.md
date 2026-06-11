# FloatDash — Build Plan
Phases are sequential; each ends in a gate. Gates marked 🧍 need Corey + hardware.
Estimates assume one coding agent working in focused sessions; treat as relative sizes.

## Phase 0 — Repo & contracts (small)
- New GitHub repo (fresh — do NOT build on OWCE_App_NextGen; that repo becomes a
  read-only reference). Copy in: 01–05 docs, AGENTS.md at root, LICENSE (GPLv3),
  NOTICE.md, README with disclaimer.
- Gradle multi-module skeleton (`core`, `app`, `wear`), version catalog, Hilt wired,
  empty Compose screens, CI (build + test on PR).
- `core`: all interfaces, models, enums; `OwUuids.kt` fully populated from references
  with cited doc comments; Parsers.kt stubs with fixture-test harness.
- ADR-001..005 drafted.
**Gate M0:** CI green; `core` compiles with zero Android deps (enforced); Corey skims
interfaces + UUID citations. No hardware needed.

## Phase 1 — BLE connect + handshake (the hard one) (medium-large)
- BLE library spike → ADR-003. Implement transport, scan, connect state machine.
- Implement `Parsers.kt` against fixtures from reference repos.
- **GeminiStrategy** from written docs (ponewheel #86, UWP-Onewheel) with
  transform fixture tests. NoneStrategy for V1 boards.
- Debug screen: raw connect → unlock → live characteristic dump.
**Gate M1 🧍:** Corey connects to his 4029 XR; unlock holds (board stays unlocked,
telemetry flows for 10+ min); characteristic dump captured and committed as new
test fixtures. *This gate de-risks the whole project — everything after is normal
app development.*

## Phase 2 — Telemetry, diameter correction, dashboard (medium)
- BoardStateService, SpeedCalculator (both impls; pick per ADR-006 using M1 capture),
  TopSpeedTracker, RangeEstimator v1.
- Dashboard UI in OWCE style (cards per 02_ARCHITECTURE §8), board list, settings
  (units + per-board tire diameter), ride mode change + lights toggle.
**Gate M2 🧍:** Corey rides. Checks: speed/distance now match GPS reality with his
smaller tire (compare against a GPS app over a known ~2 mi route, expect ≤ ~2% error);
ride mode + lights work; dashboard matches OWCE feel.

## Phase 3 — Bulletproof service + ride recording (medium)
- RideForegroundService per 02_ARCHITECTURE §6 (notification, wakelock policy,
  reconnect, START_STICKY resume), OEM battery onboarding (Samsung copy first).
- Room schema + repositories; auto start/stop ride; ride history + detail screens.
**Gate M3 🧍:** torture tests — 30 min screen-off ride; swipe-from-recents mid-ride;
30 s airplane mode mid-ride (reconnect + session continuity); verify on the S25 Ultra
with default battery settings *after* onboarding flow. Ride appears in history with
correct corrected top speed/distance.

## Phase 4 — Watch (medium)
- Phone-side WatchSyncService (DataClient 1 Hz, MessageClient events, ongoing
  activity); wear module: main screen, ambient mode, Tile.
**Gate M4 🧍:** full ride with Galaxy Watch 7 Classic — speed/top/battery/range live on
wrist entire ride incl. ambient; watch vibrates on connection loss; Tile shows latest
state. The "always visible, never killed" acceptance test.

## Phase 5 — Polish & release (small-medium)
- Onboarding flow final, empty/error states, app icon, About (disclaimer, GPL,
  donate, source).
- Signed release pipeline (tag → APK artifact on GitHub Releases, wear bundled).
- README install instructions for sideloading; note F-Droid as future.
**Gate M5 🧍:** Corey daily-drives a tagged release for ~a week, files issues; then
v1.0.0 public.

## Backlog (post-v1, pre-seamed)
GPX export → ride map → standalone-watch BLE → GT/GT-S strategy → F-Droid →
voltage-based range model.
