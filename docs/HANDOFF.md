# ZWheel — Solo Handoff Guide

Updated 2026-06-13 (Phase 3 complete — PRs open for merge). Read this top-to-bottom
once, then use the "Daily loop" section as your checklist.

---

## 1. Where the project is right now

**Merged to `main` (all CI-green):**
- All of Phase 2: calculators, `BoardStateServiceImpl`, settings, dashboard integration,
  BLE subscription conflict fix, amps OWCE scaling, speed/debug fixes.
- `BoardTypeDetector` in `core/protocol/` — detects `BoardType` from HW revision. (PR #31)
- `BleDebugRecorder` moved to singleton scope. (PR #30)
- UNCORRECTED badge on speed card (PR #32), Room schema (PR #33).

**PRs open, pending merge (merge in order — each depends on the previous):**
- **PR #34** — `RideForegroundService` + `RideServiceRepository` + `RideServiceController` + `DashboardViewModel` refactor. Branch: `codex/p3-foreground-service`. CI was re-run after POST_NOTIFICATIONS fix — should be green.
- **PR #35** — `RideRecorder` state machine, 1Hz ride data point recording. Branch: `codex/p3-ride-recording`. Depends on #34.
- **PR #36** — `RideHistoryScreen` + `RideHistoryViewModel` + NavHost navigation + Settings screen wired. Branch: `codex/p3-ride-history`. Depends on #35.

**The app is M2-testable and Phase 3 is feature-complete.** After merging #34→#35→#36:
- Foreground service keeps BLE alive when screen is off / app killed
- Rides auto-record to Room DB (auto-start at 1.5 mph / 3s, auto-end after 90s idle)
- History screen shows past rides with distance, speed, date, duration

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

**Phase 3 wrap-up (hardware needed):**
- Validate foreground service survives screen-off on Samsung S25 Ultra
- Confirm ride sessions actually appear in history after a ride
- Verify UNCORRECTED badge clears once corrected speed is active

**Phase 4 — Wear OS sync:**
- ADR-007 (Data Layer / Watch payload) — write the ADR first
- `WearDataLayerRepository` sending `WatchPayload` via DataClient
- Wear app `WatchFaceService` rendering speed/battery

**Ride mode + lights writes (needs Corey sign-off first):**
- ADR-009 is proposed — run the hardware confirmation checklist, then accept it
- Add RIDE_MODE and LIGHTS writes (already in `OwUuids.writableAllowlist`)

**Other deferred items:**
- Cell voltage parsing for `e659f31b` (firmware-version-dependent, needs capture)
- Ride detail screen (tap a history item → detail) — `// TODO(m3)` in code
- GPS integration for ride recording latitude/longitude — `// TODO(m3)`
- Battery optimization onboarding dialog (ADR-008 §6 spec exists)

---

## 6. M2 + Phase 3 ride test checklist

1. Install debug APK, grant BLE + location + notification permissions.
2. **Settings** → set tire diameter to actual.
3. **Scan → Connect** — confirm Gemini unlock, live telemetry, board name/type.
4. Lock screen. Wait 2 minutes. Unlock — confirm BLE still connected and notification
   shows speed + battery (validates foreground service).
5. Ride for >3 seconds at >1.5 mph. Stop for 90 seconds. Check **Ride History** screen
   — a session should appear with correct distance, duration, top speed.
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
- PR chain: #34 (P3b service) → #35 (P3c recording) → #36 (P3d history)
