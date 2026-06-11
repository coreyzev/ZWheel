# ZWheel — Architecture
Version 1.0. Read 01_PROJECT_BRIEF.md first. The 12 rules in 03_AGENTS.md are binding.

## 1. Stack (locked)

| Concern | Choice |
|---|---|
| Language | Kotlin (single language, phone + watch + core) |
| Phone UI | Jetpack Compose, Material 3, single-activity |
| Watch | Wear OS — Compose for Wear OS + **Tile** + ongoing activity |
| DI | Hilt |
| BLE | **Kable** (preferred; coroutines-first) or Nordic BLE Library — agent picks in ADR-003 after a spike; raw android.bluetooth is the fallback, never the first choice |
| DB | Room (SQLite) |
| Async | Coroutines + Flow everywhere; no RxJava, no LiveData |
| Phone↔Watch | Wearable Data Layer (`DataClient` for state, `MessageClient` for events) |
| Settings | DataStore (Preferences) |
| Build | Gradle Kotlin DSL, version catalog (`libs.versions.toml`) |
| Tests | JUnit5 + MockK + Turbine (Flow testing); Compose UI tests for the dashboard |
| CI | GitHub Actions: build both APKs + run unit tests on every PR; signed release on tag |
| Min SDK | 26 (phone), Wear OS 3+ (watch). Target latest stable. |

## 2. Module layout

```
zwheel/
├── core/            Pure Kotlin. ZERO Android imports. KMP-convertible by design.
│   ├── protocol/    BLE UUIDs, byte parsers, handshake strategies (logic only)
│   ├── model/       BoardState, RideSession, RideDataPoint, WatchPayload, BoardType...
│   ├── calc/        SpeedCalculator (diameter correction), RangeEstimator, TopSpeedTracker
│   └── ports/       Interfaces implemented by outer modules: BleTransport, Clock, Storage
├── app/             Phone app: Compose UI, ViewModels, Hilt graph
│   ├── ble/         BleTransport impl over chosen BLE lib; ConnectionManager
│   ├── service/     RideForegroundService (§6) — the heart of the app
│   ├── data/        Room db + repositories; DataStore settings
│   ├── watch/       WatchSyncService (phone side, Data Layer)
│   └── ui/          dashboard / boardlist / ridehistory / ridedetail / settings / onboarding
├── wear/            Watch app: Compose for Wear OS + Tile + listener service
└── baselineprofile/ (optional, later)
```

**Dependency rule (the one invariant): `app` and `wear` depend on `core`. `core`
depends on nothing.** All protocol logic, parsing, and math live in `core` and are
tested without an emulator. The Android modules are thin shells: transport, persistence,
UI, OS lifecycle.

## 3. BLE protocol layer (`core/protocol` + `app/ble`)

### 3.1 Connection lifecycle (state machine in core, transport in app)
```
Idle → Scanning → Connecting → DiscoveringServices → Handshaking
     → Subscribed (live) → [Degraded ↔ Reconnecting] → Disconnected
```
- Scan filter: Onewheel service UUID `e659f300-ea98-11e3-ac10-0800200c9a66`.
- All known characteristic UUIDs live in ONE object: `core/protocol/OwUuids.kt`
  (serial, rpm, speed, battery %, cell voltages, pitch/roll/yaw, amps, temps,
  odometer, trip, ride mode, lights, firmware rev, hardware rev, UART read/write for
  handshake). Populate from the reference repos; every UUID gets a doc comment citing
  its source.
- Every parser is a pure function `ByteArray → typed value` in
  `core/protocol/Parsers.kt`, each with unit tests using real captured byte fixtures
  (capture from Corey's board at M2; until then use byte examples from the reference
  repos' tests/issues).

### 3.2 Handshake (`core/protocol/handshake/`)
```kotlin
interface HandshakeStrategy {
    suspend fun unlock(io: GattIo): HandshakeResult
    fun keepAlive(): Flow<KeepAliveAction>   // empty flow if none needed
}
class NoneStrategy        // V1, pre-Gemini
class GeminiStrategy      // challenge–response: read challenge from UART read char,
                          // compute MD5-based response w/ known appended secret bytes,
                          // write to UART write char. Periodic re-auth if fw requires.
// PolarisStrategy        // GT/GT-S — slot reserved, NOT implemented in v1
```
Strategy selected from firmware revision read at connect. **GeminiStrategy is the only
crypto-sensitive code in the project: implement it from the written descriptions in
UWP-Onewheel's docs and ponewheel issue #86, with fixture tests for the
challenge→response transform before ever testing on hardware.** It must be reviewed
line-by-line (agent review pass + Corey reads it) before M2.

### 3.3 Hard rule, enforced in code
No code path writes to any firmware/OTA characteristic. `OwUuids` does not even contain
them. A unit test asserts the writable-UUID allowlist is exactly: {unlock/UART write,
ride mode, lights}.

## 4. Telemetry & calculation (`core/calc`)

- `BoardStateService` (core): consumes characteristic notifications → emits
  `StateFlow<BoardState>` (immutable data class).
- `SpeedCalculator`: one interface, two impls (`RpmBased`, `ScaledFirmware`) per the
  diameter spec in 01_PROJECT_BRIEF §4.1. Chosen per-board at connect; decision recorded
  in ADR-006. Config: `tireOuterDiameterInches` per saved board (DataStore), default =
  stock for board type.
- `TopSpeedTracker`: max over corrected speed during active ride; lifetime max via Room
  query (`MAX(maxSpeed)` across sessions) — never a separately stored mutable value that
  can drift.
- `RangeEstimator` v1 = simple: battery % × user-configurable "miles per %" learned from
  the last N completed rides (corrected distance ÷ % consumed), falling back to a
  board-type default. Document as approximate in UI. Fancier voltage-based estimation is
  a v2 item.
- Unit policy: store SI/raw internally (m/s, meters, °C, volts); convert at the UI edge.
  Separate user toggles for speed unit and temperature unit (OWCE issue #115 lesson).

## 5. Ride recording (`app/data`)

Room tables (mirror the v2.1 plan's schema, it was fine):
- `ride_session(id, boardId, startTs, endTs, maxSpeedCorrected, distanceCorrected,
  distanceRaw, whUsed, notes)`
- `ride_point(id, sessionId, ts, speedCorrected, speedRaw, rpm, battery, lat, lon,
  amps, pitch, roll, controllerTemp, motorTemp)` — 1 Hz while riding.
- GPS via fused location, only while ride active, `location` foreground-service type.
- A ride auto-starts when corrected speed > 1.5 mph for > 3 s, auto-ends after 90 s
  stationary or manual stop. Manual start/stop always available.

## 6. RideForegroundService — the bulletproof spec (most important section)

This is the component that fixes "app gets killed after 2 minutes."

1. **Foreground service**, types `connectedDevice|location`, started when the user
   connects to a board; runs until explicit disconnect or board lost > 10 min.
2. **Ongoing notification** (required anyway): live speed + battery + top speed, with
   Disconnect action. Use the ongoing-activity API so it also surfaces on the watch.
3. **Owns the BLE connection.** ViewModels/UI bind to it via a repository
   `StateFlow` — UI death never touches the connection. One connection, phone-owned;
   the watch never talks BLE in v1.
4. **Wakelock:** `PARTIAL_WAKE_LOCK` held only while a ride is active (speed > 0
   recently), released when idle-connected. Never a screen wakelock from the service.
5. **Battery-optimization onboarding (first run + Settings entry):**
   - Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` exemption with honest copy.
   - Detect manufacturer; for Samsung show specific steps: Settings → Apps → ZWheel
     → Battery → **Unrestricted**; and Device Care → Battery → Background usage limits →
     ensure app is not in "Sleeping/Deep sleeping apps", add to "Never sleeping apps".
     (Mirror dontkillmyapp.com/samsung; keep copy in one
     `OemBatteryAdvice.kt` so Pixel/Xiaomi variants are easy.)
   - Re-check on every app start; show a dismissible warning banner if restricted.
6. **Reconnect:** on unexpected disconnect during an active ride — exponential backoff
   scan-and-reconnect (1s→2s→4s… cap 30s, give up after 10 min), notification flips to
   "Reconnecting…", watch gets a `ConnectionLost` message and vibrates. Ride session
   stays open across reconnects within 10 min.
7. **Process death:** `START_STICKY`; on restart, if a ride session row has no `endTs`,
   resume recording state and attempt reconnect to the last board.
8. Test plan at M2: ride with screen off 30+ min; swipe app from recents mid-ride
   (service must survive); airplane-mode the phone for 30 s mid-ride (reconnect path).

## 7. Watch (`wear/` + `app/watch`)

- Phone pushes `WatchPayload(speedCorrected, topSpeed, batteryPct, estRangeRemaining,
  speedUnit, isRiding, connectionState)` at 1 Hz via `DataClient` (latest-value
  semantics; key `/zwheel/state`). `MessageClient` for one-shot events:
  rideStarted/rideStopped/connectionLost.
- Watch app: single Compose screen — giant speed numeral; battery %, range, top speed
  in a bottom row; connection state glyph. Tap cycles primary metric.
- **Ambient mode supported** (AmbientLifecycleObserver): dimmed monochrome layout,
  updates ≤ 1/min, slight layout jitter for burn-in. This + the phone-side ongoing
  activity is what keeps it on the wrist "at all times".
- **Tile**: speed/battery/range snapshot, refreshed from the latest DataItem — survives
  even if the watch activity is killed; one swipe from watchface.
- Galaxy Watch 7 Classic (Wear OS 5) is the test device.

## 8. Phone UI map (Compose)

- **Dashboard** (home, OWCE-style cards per Brief §5): Speed card (gauge + trip distance
  + lifetime odo + top-speed callout), Battery card (% + cell-voltage grid + pack volts),
  Current card (amps + trip Ah/regen), Temperatures card, Ride Mode card (tap to change),
  Lights toggle row. Board name + RSSI + serial/fw/hw in a details sheet.
- **Board list**: scan results + saved boards, auto-connect-to-last toggle.
- **Ride history**: list (date, distance, top speed) → **Ride detail** (stats; map +
  GPX export are v1.5 — leave the composable slots).
- **Settings**: units (speed/temp separate), **tire diameter per board**, range
  calibration display, battery-advice re-run, theme, About (license, disclaimer,
  donate link, source link).
- **Onboarding**: BLE + location permission rationale → battery exemption flow (§6.5)
  → scan.

## 9. ADRs the agent must write before/while implementing
ADR-001 module layout & core purity · ADR-002 Hilt · ADR-003 BLE library choice (after
spike) · ADR-004 handshake strategy design · ADR-005 Room schema · ADR-006 diameter
correction path chosen for XR (rpm vs scaled) with evidence · ADR-007 Data Layer payload
& throttling · ADR-008 foreground service & OEM battery policy.
