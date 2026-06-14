# ZWheel — Solo Handoff Guide

Updated 2026-06-13 (Phase 3 complete, Phase 4a merged, Phase 4b/4c in progress).

---

## 1. Where the project is right now

**Merged to `main` (all CI-green):**
- All of Phase 2: calculators, `BoardStateServiceImpl`, settings, dashboard integration,
  BLE subscription conflict fix, amps OWCE scaling, speed/debug fixes.
- `BoardTypeDetector` in `core/protocol/` — detects `BoardType` from HW revision. (PR #31)
- `BleDebugRecorder` moved to singleton scope. (PR #30)
- UNCORRECTED badge on speed card (PR #32), Room schema (PR #33).
- Phase 3 complete: `RideForegroundService` (PR #34), `RideRecorder` (PR #35),
  `RideHistoryScreen` + NavHost (PR #36), docs (PR #37).
- **Phase 4a** (#38): `WearDataLayerRepository` phone-side Wear OS sync. The phone app
  now pushes `BoardState + connectionState + isRiding + prefs` to `/zwheel/state` DataItem.
  `RideServiceRepository` gains `isRiding: StateFlow<Boolean>`.

**PRs in flight (work in progress, not yet opened):**
- **P4b** (branch: `codex/p4b-wear-data-layer-watch`) — Watch-side: `WearDataLayerRepository`
  listener, `MainViewModel`, wire existing `ZWheelWearScreen` UI to real data. Codex implementing.
- **P4c** (branch: `codex/p4c-wear-top-speed-range`) — Wire `DefaultTopSpeedTracker` +
  `DefaultRangeEstimator` into the service so the watch shows real top-speed and range. Codex implementing.

**The app is M2-testable:**
- Foreground service keeps BLE alive when screen is off / app killed
- Rides auto-record to Room DB, but the current auto-end-after-idle behavior needs a
  product correction: breaks should stay inside the same ride. Track this in #46.
- History screen shows past rides with distance, speed, date, duration
- Phone pushes live data to watch (P4a); watch receiving implementation in progress (P4b)

---

## 2. How to drive Codex (the proven workflow)

**Golden pattern — spec to a file, then run Codex on it:**
```bash
# 1. Write a precise gate spec listing the exact files Codex may touch.
#    Put it at docs/gates/gate-<name>.md  (see docs/gates/ for examples).

# 2. Make an isolated worktree so Codex can't wander:
cd /root/ZWheel && git fetch origin
git worktree add /root/zwheel-wt/<name> -b codex/<name> origin/main

# 3. Run Codex (ALWAYS `< /dev/null`, ALWAYS `-C <worktree>`):
codex exec -C /root/zwheel-wt/<name> -s workspace-write \
  "You are a code writer. Read ONLY docs/gates/gate-<name>.md. Do NOT read any other files.
   Write the files exactly as the gate specifies. After writing all files, run:
   GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin and fix errors.
   Then: git add <files> && git commit -m 'feat(...): ...'
   Do NOT check GitHub issues. Do NOT read AGENTS.md or project briefs." < /dev/null
```

**Codex context-window tip:** Do NOT ask Codex to read AGENTS.md, 01_PROJECT_BRIEF.md,
or 02_ARCHITECTURE.md — it exhausts its context before writing any files.

If /tmp fills up: `rm -rf /tmp/zwheel-gradle /tmp/gradle-home /tmp/gradle-wrapper-cache`
frees ~2.5 GB.

---

## 3. Merging a PR (the safe ritual)

```bash
gh pr checks <N>
gh pr diff <N>            # skim the diff
gh pr merge <N> --squash  # merge in order: #34 → #35 → #36
```

For PRs in a chain (each branched from the previous), merge the base first,
then the next will need a rebase/merge from the updated base. The CI will rerun.

---

## 4. Daily loop

1. **Check PR status:** `gh pr list --state open` and `gh pr checks <N>`.
2. **Merge ready PRs** in chain order (#34 → #35 → #36).
3. **Install the debug APK** from `app/build/outputs/apk/debug/` and run the §6
   ride test to validate foreground service + recording.
4. Pick the next roadmap item (§5), write a gate spec, dispatch Codex.

---

## 5. Roadmap next (priority order)

**Phase 4 — Wear OS sync (in progress):**
- **P4b**: Watch-side `WearDataLayerRepository` + wire existing `ZWheelWearScreen` to real data (Codex implementing)
- **P4c**: Wire `DefaultTopSpeedTracker` + `DefaultRangeEstimator` into service for watch (Codex implementing)
- **P4d** (future): Watch tile / WatchFaceService — not started

**M2 hardware validation needed:**
- Validate foreground service survives screen-off on Samsung S25 Ultra
- Confirm ride sessions actually appear in history after a ride
- Verify UNCORRECTED badge clears once corrected speed is active
- Fix ride recording semantics (#46): auto-start when riding begins, but end only on
  disconnect/manual stop so normal breaks do not split a ride.
- Write the full Corey hardware validation runbook (#47) before asking Corey to do
  extended M3/M4 testing.

**Ready to implement (gate specs written):**
- Battery optimization first-launch dialog (`docs/gates/gate-m3-battery-opt-dialog.md`)

**Ride mode + lights writes (needs Corey sign-off first):**
- ADR-009 is proposed — run the hardware confirmation checklist, then accept it
- Add RIDE_MODE and LIGHTS writes (already in `OwUuids.writableAllowlist`)

**Deferred items:**
- Cell voltage parsing for `e659f31b` (firmware-version-dependent, needs capture)
- Ride detail screen (tap a history item → detail) — `// TODO(m3)` in code
- GPS integration for ride recording latitude/longitude — `// TODO(m3)`

---

## 6. M2 + Phase 3 ride test checklist

1. Install debug APK, grant BLE + location + notification permissions.
2. **Settings** → set tire diameter to actual.
3. **Scan → Connect** — confirm Gemini unlock, live telemetry, board name/type.
4. Lock screen. Wait 2 minutes. Unlock — confirm BLE still connected and notification
   shows speed + battery (validates foreground service).
5. Ride for >3 seconds at >1.5 mph. Take a normal break, then continue riding. After
   #46 is fixed, this should remain one ride session until disconnect/manual stop.
   Check **Ride History** — a session should appear with correct distance, duration,
   and top speed.
6. Validate corrected speed matches GPS reference (~2 mile loop, expect ≤2% error).

---

## 7. Guardrails (non-negotiable — from AGENTS.md)

- **Never** add a firmware/OTA UUID. No new writable chars without ADR + Corey sign-off.
- `core/` stays Android-free (CI enforces).
- One concern per commit/PR. No `Co-Authored-By` lines.
- Never merge without CI green AND diff skim.
- App is offline; no INTERNET permission in release.

---

## 8. Useful paths & commands

- Gate specs: `docs/gates/`
- Worktrees: `/root/zwheel-wt/`. List: `git worktree list`. Clean merged: `git worktree remove --force <path>`.
- Gradle cache cleanup: `rm -rf /tmp/zwheel-gradle /tmp/gradle-home /tmp/gradle-wrapper-cache`
- PR chain: #34 (P3b service) → #35 (P3c recording) → #36 (P3d history) → #38 (P4a phone wear) → P4b (watch) / P4c (topspeed+range)
