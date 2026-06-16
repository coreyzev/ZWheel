# ZWheel Deep Codebase Review — 2026-06-15

Line-by-line pass of all source files. SEV legend: CRASH / HIGH / MED / LOW / NITS / OK
Format: `[SEV] file:line — description`

Issues #99–#112 are already filed. New findings below are tagged **NEW** and annotated
with the GitHub issue created from them where applicable.

---

## Build & Config

```
[OK]  gradle/libs.versions.toml — All deps pinned, version catalog is clean.
[OK]  app/build.gradle.kts — ADR-010 enforceAdr010NetworkPolicy task correctly excludes
      debug/test sources from URL scan. Release signing left to Corey.
[LOW] wear/build.gradle.kts:14-15 — versionCode = 1 / versionName = "0.1.0" hardcoded.
      App module reads from CI env; wear APK is always build 1 on every CI run.
      → NEW #118
[OK]  .github/workflows/ — release-on-dispatch change tracked in #107.
[OK]  core/build.gradle.kts — forbiddenAndroidImports task enforced in CI.
```

---

## Core Module

### core/protocol/

```
[OK]  OwUuids.kt — All UUIDs in one object, writable allowlist test present.
[OK]  Parsers.kt — Pure byte→typed functions; each parser covered by fixture tests.
[MED] Parsers.kt (amps) — throws IAE for UNKNOWN board type. Safe because callers catch
      IAE, but silent: board gets no amps reading if type undetectable.
[OK]  BoardTypeDetector.kt — Clean prefix-match logic.
[OK]  handshake/GeminiStrategy.kt — MD5 challenge-response correct per protocol; has
      fixture tests before any hardware run.
[OK]  debug/ — BleDebugRecorder, BleDebugEvent clean. Protocol-level concerns only.
```

### core/service/

```
[MED] BoardStateServiceImpl.kt — 16 bare scope.launch {} blocks for notification
      collectors. Each catches only IllegalArgumentException. If Kable throws
      NotConnectedException or any other exception the coroutine dies silently — no
      reconnect, no notification to caller. Error category is narrower than actual
      error space.
      → NEW #115
[LOW] BoardStateServiceImpl.kt:collectOdometer() — hardcoded 42 ticks/revolution.
      Correct for XR; verify for other board types if support is added.
[OK]  BoardStateServiceImpl.kt:collectTripAmpHours() / collectTripRegenAmpHours() —
      Both call _state.update { it.copy(tripAmpHours = ..., tripRegenAmpHours = ...) }
      correctly. The data IS reaching BoardState from BLE. (The bug is in DashboardState,
      see App — UI section below.)
[NITS] BoardStateServiceImpl.kt — uses println() for errors; acceptable since core must
       stay Android-free, but a core Logger port would be cleaner.
```

### core/model/

```
[OK]  BoardModels.kt — Immutable data classes, nullable fields with safe defaults.
      BoardState has tripAmpHours and tripRegenAmpHours correctly defined.
[OK]  ConnectionState enum — 9 states (IDLE, SCANNING, CONNECTING, …, SUBSCRIBED).
      App-level ConnectionState has only 4 states; mapping is explicit in
      WearDataLayerRepository. SUBSCRIBED is the correct state sent to watch.
[OK]  WatchPayload — small, correct for Data Layer payload.
```

### core/ports/

```
[LOW] Ports.kt — RideStorage interface defined but RideRepository does NOT implement it.
      The port is dead code: never used as an abstraction, never injected by interface.
      Either implement it on RideRepository or delete it.
      → NEW #116 (grouped)
```

### core/calc/

```
[OK]  RangeEstimator.kt — Conservative fallback (returns null for UNKNOWN board type);
      DefaultRangeEstimator tested.
[OK]  TopSpeedTracker.kt — Correct max-over-session logic; tested.
[OK]  SpeedCalculator (RpmBased, ScaledFirmware) — Correct diameter math; both tested.
[OK]  UnitConversions.kt — Pure functions, tested.
```

### core/tests/

```
[OK]  ParsersTest — fixture-based, covers happy + edge cases for each parser.
[OK]  BoardStateServiceImplTest — covers RPM, battery, malformed payload, odometer. Good.
[NITS] BoardStateServiceImplTest — No test for the "unknown exception kills collector"
       path (see [MED] above). Adding one would lock in the fix from #115.
[OK]  GeminiChallengeResponseTest / GeminiStrategyTest — critical crypto path tested.
[OK]  OwUuidsTest — writable allowlist enforced.
[OK]  RangeEstimatorTest, TopSpeedTrackerTest, UnitConversionsTest — clean.
```

---

## App — BLE Layer

```
[OK]  ConnectionState.kt (app) — 4-state enum; intentionally simpler than core's 9-state.
[MED→#101] KableBleTransport.kt — notifications() SharedFlow stops immediately on
           last-subscriber leave (WhileSubscribed with no stopTimeout). Rapid reconnect
           can lose events. + advertisements map never cleared.
[LOW] KableBleTransport.kt — advertisements StateFlow not cleaned up between scans;
      devices from previous scan sessions accumulate. (Part of #101.)
[OK]  GeminiKeepAliveRunner.kt — 15s write interval, correct cancel on disconnect.
[LOW] ConnectionManager.kt — tire diameter comparison bug:
      `if (savedDiameter != UserPreferences().tireDiameterInches)` — if user
      deliberately sets 11.5" (XR stock default), the condition is false and stock
      diameter is used instead of their explicit preference. Semantically incorrect.
      → NEW #116 (grouped with RideStorage)
[NITS] ConnectionManager.kt — onScanResult() and pruneStaleDevices() call
       System.currentTimeMillis() instead of injected clock.nowEpochMillis().
       Reduces test determinism; should use the injected Clock.
       → Note in #104 (testability)
```

---

## App — Data Layer

```
[OK]  RideEntities.kt — v2 schema correct. altitude column added in MIGRATION_1_2.
[OK]  RideDao.kt — queries correct. getOpenSession() LIMIT 1 means only one orphan
      is returned if multiple exist; fix in #102 should close ALL open sessions
      (not just one). Add a `getAllOpenSessions()` query or WHERE + DELETE ALL.
[LOW] ZWheelDatabase.kt — exportSchema = false. Room does not generate schema JSON or
      verify migration correctness against schema history. Silent migration bugs are
      possible. Should export schema and commit it; test with MigrationTestHelper.
      → NEW #117
[OK]  RideRepository.kt — thin wrapper, no logic.
[OK]  SettingsRepository.kt — lazy encryptedPrefs (no try/catch → #106). Passive
      migration only → #106. DataStore preferences clean otherwise.
[OK]  UserPreferences.kt — correct defaults. tireDiameterInches defaults to 11.5 (XR).
```

---

## App — Service Layer

```
[CRASH→#99] RideForegroundService.onDestroy() — runBlocking { rideRecorder?.endCurrentSession() }
            blocks main thread; can ANR. Fix: use blocking call only as last resort,
            prefer coroutine scope with timeout.
[CRASH→#99] startRideRecorderTicker() — recorder.onTick() not wrapped in runCatching.
            Exception propagates to lifecycleScope and may crash service.
[LOW→#105]  RideForegroundService.onCreate() — acquireWakelockIfNeeded() called BEFORE
            startForeground(); wakelock leaks if ANR during startForeground().
[OK]  RideForegroundService.onStartCommand() — deviceId null path correctly reconnects
      to last-known ID from DataStore (START_STICKY semantics partially in place; full
      orphan recovery is #102).
[OK]  RideForegroundService — wakelock policy: acquire after 3 ticks above threshold,
      release after 90 ticks below. Matches ADR-008 §3. Correct.
[OK]  RideForegroundService — speed unit for notification tracked reactively via
      settingsRepository.preferences.collect.
[NITS] RideForegroundService.onCreate() — HomeAssistantSync constructed inline:
       `HomeAssistantSync(settingsRepository, connectionManager.boardState).start(...)`.
       Not Hilt-injected, harder to test. Should be a Hilt-injected singleton or at
       least constructed from injected deps with no hidden state.
[OK]  RideRecorder.kt — session lifecycle (3-tick threshold, endCurrentSession) correct.
      tripDistanceMeters accumulated per tick at correctedSpeed; assumes 1-second ticks
      (minor drift possible under load, acceptable).
[OK]  LocationTracker.kt — PRIORITY_HIGH_ACCURACY, 5s interval, 30m accuracy gate.
      Uses Looper.getMainLooper(); callbacks are cheap (just store lat/lon in vars)
      so main-thread delivery is acceptable here.
[OK]  HomeAssistantPusher.kt / HomeAssistantSync.kt — deduplication on percent change;
      correctly skips if URL or token blank; haEndpoint strips trailing slash.
[OK]  RideNotifications.kt — IMPORTANCE_LOW channel, ongoing, silent, progress bar for
      battery, Disconnect action. All correct per ADR-008.
[OK]  RideServiceController.kt — thin startForegroundService/stopService wrapper.
[OK]  RideServiceRepository.kt — clean: only service-derived state (isRiding,
      tripDistance, gpsLocked, topSpeed). Matches ADR-011.
```

---

## App — DI

```
[OK]  AppModule.kt — ConnectionManager/KableBleTransport bound as singletons.
      Note: ConnectionManager takes KableBleTransport concrete type, not BleTransport
      interface — makes it harder to mock for unit tests. Tracked in #104.
[OK]  DatabaseModule.kt — Room singleton, fallbackToDestructiveMigration not set.
[OK]  ServiceModule.kt — RideServiceController bound to interface.
[OK]  SettingsModule.kt — SettingsRepository provided via @Provides.
```

---

## App — UI

```
[HIGH] DashboardState.kt:122-123 — toDashboardUiState() hardcodes:
         tripAmpHours = 0.0
         regenAmpHours = 0.0
       BoardState.tripAmpHours and tripRegenAmpHours ARE populated from BLE
       (BoardStateServiceImpl.kt:77, :87). The mapping is just missing. Fix:
         tripAmpHours = boardState.tripAmpHours ?: 0.0,
         regenAmpHours = boardState.tripRegenAmpHours ?: 0.0,
       "USED 0.00 AH" / "REGEN 0.00 AH" permanently broken on dashboard.
       → NEW #113
[LOW]  DashboardState.kt:102 — rssi = 0 hardcoded. BoardState has no RSSI field.
       Dashboard shows "RSSI 0 dBm" when connected. RSSI is available in ScanResult
       but not tracked through to ConnectionManager after connect. Design decision
       needed: add rssi to ConnectionManager or remove the field from DashboardUiState.
       → NEW #119
[MED→#110] SettingsScreen.kt — HA token OutlinedTextField lacks PasswordVisualTransformation.
           Token displayed in plaintext on screen. High risk: shoulder-surfing on a
           phone screen while riding. Fix: visualTransformation = PasswordVisualTransformation()
           plus a show/hide toggle icon.
           → NEW #114
[OK→#103] DashboardViewModel.kt — topSpeedTracker = DefaultTopSpeedTracker() is a stale
          mirror; does not reset on session end; diverges from service tracker. Fix in #103.
[OK]  DashboardViewModel.kt:39 — defaults unknown board type to BoardType.XR for range
      estimation. Acceptable until more board types are supported.
[OK]  DashboardComposables.kt — layout clean; TripStatsCard shows USED/REGEN AH
      (which will be fixed by #113).
[OK]  DashboardComponents.kt — BatteryCard, SpeedCard composables clean.
[OK]  ConnectionBar.kt — scan/connect/disconnect states handled correctly.
[NITS] ZWheelWearScreen.kt:239 — connectionLabel = connectionState.name emits raw
       enum name ("SUBSCRIBED") as connection status on watch. HARDWARE_TEST_CHECKLIST
       step 30 expects "CONNECTED". Should map SUBSCRIBED → "CONNECTED".
       → Note in #112 or new NITS issue
[MED→#111] ZWheelAppScreen.kt — locationRequestAttempted not set in locationLauncher
           result before hasPermanentlyDeniedPermission check. Correct in #111.
[OK→#110] SettingsScreen.kt — HA URL HTTP warning tracked in #110.
[OK]  SettingsViewModel.kt — correct; reads/writes through SettingsRepository.
[LOW→#106] SettingsScreen.kt — EncryptedSharedPreferences lazy init no try/catch.
           Tracked in #106 (Keystore init protection).
[OK]  RideHistoryScreen.kt — correct date formatting, navigation to RideDetailScreen.
[OK]  RideDetailViewModel.kt — haversineMeters correct; GPS distance displayed alongside
      BLE-corrected distance. avgSpeed uses total session duration (includes stops) — this
      is correct behavior but lower than riding speed; label could clarify "avg incl. stops."
[OK→#102] RideDetailViewModel.kt:89 — (endEpochMillis ?: startEpochMillis) - startEpochMillis
          shows 0 duration for open sessions; harmless since history only shows closed
          sessions normally.
[OK]  OemBatteryAdvice.kt — Samsung-specific copy present. Correct.
[OK→#100] ZWheelAppScreen.kt / AndroidManifest.xml — API 34 SecurityException from
          foreground service location type tracked in #100.
```

### BLE Debug UI

```
[LOW] BleDebugViewModel.kt:onCleared() — runBlocking { runCatching { transport.disconnect() } }
      blocks main thread. Low risk (disconnect is fast), but same pattern as service
      onDestroy issue (#99). Should use GlobalScope.launch or a coroutine with timeout.
      (Test already covers this path in BleDebugSessionLoggerTest.)
[OK]  BleDebugSessionLogger.kt — startDumpJobs clean; appendLog callback pattern correct.
[OK]  BleDebugFormat.kt / BleDebugScreen.kt — debug-only path; logging and display correct.
[OK]  BlePermissionUtils.kt — permission checks clean.
```

---

## App — Wear Layer (phone side)

```
[OK]  WearDataLayerRepository (app).kt — correct deduplication via lastSentPayload.
      ConnectionState mapping: Connected→SUBSCRIBED, Scanning→SCANNING, etc. Explicit.
[NITS] WearDataLayerRepository (app).kt — dataClient.putDataItem(request) result Future
       is not checked. If Wear OS returns an error (e.g., watch not paired), the failure
       is silently dropped. Low severity — Data Layer is fire-and-forget by design.
[OK]  WearPayloadEncodeTest — comprehensive encode+roundtrip coverage.
```

---

## Wear Module

```
[OK]  ZWheelWearApp.kt — minimal @HiltAndroidApp, correct.
[OK]  MainActivity.kt — AmbientLifecycleObserver correct. register()/unregister() in
      onResume/onPause is the correct foreground-listener pattern.
[LOW] WearDataLayerRepository (wear).kt — implements DataClient.OnDataChangedListener
      AND ZWheelWearableListenerService routes to repository.onDataMapReceived().
      When app is in foreground, same DataItem event may arrive twice. Effect: benign
      (StateFlow value set to same payload twice), but redundant. The WearableListenerService
      is already sufficient for background; the DataClient.addListener() is for foreground.
      This is actually the recommended dual-path pattern from Google Wear OS docs — both
      paths can coexist. NITS at worst.
[OK]  ZWheelWearableListenerService.kt — clean, delegates to repository.
[OK]  MainViewModel.kt — thin StateFlow pass-through. Correct.
[OK]  MainScreen.kt — collectAsStateWithLifecycle correct for Wear lifecycle.
[OK]  ZWheelWearScreen.kt — ambient/interactive split clean. BatteryRing arc math correct.
      Speed/battery/range formatting correct per unit.
[LOW] ZWheelWearScreen.kt:239 — connectionState.name shows raw enum ("SUBSCRIBED" not
      "CONNECTED"). CHECKLIST step 30 expects "CONNECTED". Map: SUBSCRIBED→"CONNECTED",
      SCANNING→"SCANNING", etc. → Note in review; add to CHECKLIST footnote.
[LOW] wear/build.gradle.kts — versionCode = 1 always. → #118
[OK]  WearPayloadDecodeTest — all decode paths tested including sentinel -1 and fallbacks.
[OK]  wear/src/main/AndroidManifest.xml — exported WearableListenerService with correct
      DATA_CHANGED intent filter and /zwheel/state path.
```

---

## Tests (overall assessment)

```
[OK]  Core tests comprehensive — parsers, handshake, board state service, calculators all
      covered with fixtures and unit tests.
[OK]  RideRecorderTest — full session lifecycle (threshold, distance, GPS, end) covered.
[OK]  HomeAssistantPusherTest — pure function coverage (haBody, haEndpoint).
[OK]  BleDebugSessionLoggerTest — notification dump path tested.
[OK]  WearPayloadEncodeTest + WearPayloadDecodeTest — full encode/decode roundtrip.
[OK]  DashboardStateTest — covers the corrected-vs-raw speed display logic.
[GAP→#104] ConnectionManager — no unit tests.
[GAP→#104] SettingsRepository — no migration path tests.
[GAP→#104] RideDao — stub file only (Robolectric JUnit5 blocker noted; should unblock with
           robolectric-junit-platform as per the TODO comment).
[GAP→#104] RideForegroundService — no lifecycle tests.
[GAP→#104] WearDataLayerRepository (app) — no tests for sync logic.
[NITS] DashboardStateTest — only 1 test case (raw speed display). Coverage for
       tripAmpHours/regenAmpHours mapping should be added when #113 is fixed.
```

---

## Docs / ADRs

```
[OK]  ADR-001 — module layout enforced by CI. Accepted.
[OK]  ADR-002 — Hilt rationale clear. Accepted.
[OK]  ADR-003 — Kable chosen, rationale present.
[OK]  ADR-004 — Handshake strategy design clear.
[NITS] ADR-005 — Status: Draft. Implementation is live (Room v2, migrations done).
       Should be updated to Accepted with final schema summary.
[OK]  ADR-006 — Speed calculator diameter correction. Accepted with evidence.
[MED→#112] ADR-007 — Data path diagram references RideServiceRepository at top.
           Actual implementation reads directly from ConnectionManager per ADR-011.
           Diagram is misleading. Fix tracked in #112.
[OK]  ADR-008 — Foreground service spec comprehensive. Sections 4 (START_STICKY) and 5
       (reconnect) not yet fully implemented (#102, #109).
[OK]  ADR-009 — Ride mode/lights write protocol. Placeholder; not yet implemented.
[OK]  ADR-010 — Networking policy. Enforced by Gradle task on every build.
[OK]  ADR-011 — ConnectionManager as single source of truth. Accepted and implemented.
[NITS] 03_AGENTS.md §6 Memory — append-only log shows last entry 2026-06-12. Should be
       updated with: (a) current issues #99–#112 sprint, (b) Codex return date, (c) board
       not available until Thursday.
[OK]  COREY_HARDWARE_TEST_CHECKLIST.md — comprehensive; covers M1–M4.
[LOW] COREY_HARDWARE_TEST_CHECKLIST.md:30 — says watch shows "CONNECTED" but wear app
      actually shows raw enum "SUBSCRIBED". Checklist will fail. Fix watch UI (#119 note)
      or update checklist to reflect actual behavior.
```

---

## Punch List (new findings only, not duplicating #99–#112)

| SEV | File | Line | Description | Issue |
|-----|------|------|-------------|-------|
| HIGH | DashboardState.kt | 122-123 | `tripAmpHours = 0.0` / `regenAmpHours = 0.0` hardcoded; BoardState fields populated but never mapped | #113 |
| MED | SettingsScreen.kt | ~70 | HA token OutlinedTextField missing PasswordVisualTransformation — token shown in plaintext | #114 |
| MED | BoardStateServiceImpl.kt | ~16 collectors | Bare scope.launch catches only IAE; NotConnectedException kills collector silently | #115 |
| LOW | DashboardState.kt | 102 | `rssi = 0` hardcoded; no RSSI path from scan result to connected BoardState | #119 |
| LOW | ConnectionManager.kt | connect() | Tire diameter equality vs UserPreferences() default — user setting 11.5" is ignored | #116 |
| LOW | core/ports/Ports.kt | all | RideStorage interface is unused dead code | #116 |
| LOW | ZWheelDatabase.kt | ~10 | `exportSchema = false` — Room migration safety not verified | #117 |
| LOW | wear/build.gradle.kts | 14-15 | versionCode/versionName hardcoded; always build 1 from CI | #118 |
| LOW | BleDebugViewModel.kt | onCleared() | runBlocking on main thread during transport.disconnect() | note in #99 |
| NITS | HomeAssistantSync | constructed inline | Not Hilt-injected in RideForegroundService; harder to test | note in #104 |
| NITS | ZWheelWearScreen.kt | 239 | connectionState.name → "SUBSCRIBED" shown to user instead of "CONNECTED" | note in #112 |
| NITS | ADR-005 | status | Status: Draft — implementation is live, should be Accepted | n/a (in-place) |
| NITS | 03_AGENTS.md §6 | memory | Last entry 2026-06-12 — should reflect current sprint | n/a (in-place) |
