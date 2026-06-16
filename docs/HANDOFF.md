# ZWheel — Solo Handoff Guide

Updated 2026-06-16 (Wave 2 complete; release triggered after this update). Supersedes previous handoff.

---

## 1. Where the project is right now

**Completed Wave 1 (2026-06-15/16, all merged):**
- #96 → GPS permission state flicker (PR #98)
- #97 → Catch connect() exceptions (PR #97)
- #99 → Service crash guards: runCatching in ticker, onDestroy, Wear (PR #124)
- #100 → API 34 SecurityException: gate Connect on location permission (PR #125)
- #103 → Remove stale topSpeed mirror from DashboardViewModel (PR #126)
- #105 → Wakelock object leak + move acquire after startForeground (PR #127)
- #107 → CI: releases on workflow_dispatch only (PR #120)
- #110 → HA cleartext HTTP warning in Settings (PR #129)
- #111 → GPS permanently-denied pre-denied edge case (via #98 refactor)
- #112 → ADR-007 data-path diagram + wear connectionLabel fix (PR #121)
- #113 → tripAmpHours/regenAmpHours never mapped in toDashboardUiState (PR #123)
- #114 → HA token shown in plaintext (PR #130)
- #115 → BoardStateServiceImpl collectors catch only IAE (PR #128)
- #118 → wear versionCode/versionName hardcoded (PR #122)
- #106 → Keystore init protection + active HA token migration at startup (PR #134)

**Completed Wave 2 (2026-06-16, all merged):**
- #101 → BLE concurrent connect race + stale sharedFlows on reconnect (PR #133)
- #116 → Tire diameter equality check bug + remove dead RideStorage port (PR #135)
- #117 → Room exportSchema + Robolectric JUnit5 for RideDaoTest (PR #136)
- #119 → Dashboard RSSI always "0 dBm" — forward scan RSSI to state (PR #137)

**2026-06-16 status:** All Wave 1 + Wave 2 issues merged. Release triggered post-merge.
Codex returns 2026-06-18. Board hardware available 2026-06-19 (Thursday).

---

## 2. Open issues — next priorities

### Wave 3 — implement now, verify with board 2026-06-19

| Issue | Title | Notes |
|-------|-------|-------|
| **#102** | Orphan session recovery on START_STICKY restart | Fix must close ALL open sessions (LIMIT 1 in getOpenSession only returns one). |
| **#109** | BLE reconnect on unexpected disconnection | Depends on #101 (connectJob guard, now merged). |

### Test coverage (#104)

Remaining gaps — can be done in parallel with any other work:

| Area | Status |
|------|--------|
| RideDao | **Done** (PR #136, 6 tests) |
| SettingsRepository migration | Open — test `migrateHaTokenIfNeeded()` and Keystore-null fallback |
| ConnectionManager | Open — test connect race guard, rssi capture, disconnect reset |
| WearDataLayerRepository | Open |
| RideForegroundService | Open — wait until #102/#109 land so tests reflect final service behavior |

### Not yet scheduled (parked)

- **#74** battery progress in expanded notification — Gemini-OK, small
- **#48** Live Updates / Samsung Now Bar — medium feature
- **#26 / #17 / #14** — blocked on hardware telemetry capture

---

## 3. Agent division (IMPORTANT)

**Codex** returns 2026-06-18. Until then, Claude (Sonnet) implements directly.
Going forward: **Codex-first** per AGENTS.md when available. Claude reviews/specs;
Codex implements; Gemini for trivial mechanical tasks with explicit "Gemini-OK" gate label.

---

## 4. Implementing directly (while Codex is down)

```bash
git checkout -b fix/<slug> origin/main
# ... implement per gate ...
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
git add <files> && git commit -m "<conventional message>"   # NO Co-Authored-By
git push origin fix/<slug>
gh pr create --base main --fill && gh pr merge <N> --squash --admin
gh issue close <N> --comment "Done in #<PR>."
```

**Gemini dispatch pattern (for Gemini-OK gates):**
```bash
git worktree add /tmp/zw-issue-<N> -b fix/<slug> origin/main
# write gate spec to /tmp/gate-<N>.md
cd /tmp/zw-issue-<N> && GEMINI_CLI_TRUST_WORKSPACE=true gemini -p "$(cat /tmp/gate-<N>.md)" --yolo
# review the diff carefully before merging
```

If /tmp fills up: `rm -rf /tmp/zwheel-gradle /tmp/gradle-home /tmp/gradle-wrapper-cache`
frees ~2.5 GB.

**Triggering a release:**
```bash
gh workflow run release.yml --repo coreyzev/ZWheel
```
Releases are `workflow_dispatch` only (PR #120). Each run produces `build-<run_number>` tag
and a pre-release APK artifact.

---

## 5. Guardrails (non-negotiable — from AGENTS.md)

- **Never** add a firmware/OTA UUID. No new writable characteristics without an ADR +
  Corey sign-off. `OwUuidsTest` enforces the allowlist.
- `core/` stays Android-free (CI enforces).
- One concern per commit/PR. **No `Co-Authored-By` lines.**
- Never merge without CI green AND a diff skim.
- Networking follows **ADR-010**: no OEM/vendor cloud, no firmware modification, no
  analytics; egress limited to OSM tiles + the user HA URL. The `enforceAdr010NetworkPolicy`
  Gradle task now enforces this on every `./gradlew check`.

---

## 6. Verify / merge ritual

```bash
gh pr checks <N>
gh pr diff <N>            # skim
gh pr merge <N> --squash --admin
```

---

## 7. Useful paths

- ADRs: `docs/adr/` (ADR-010: networking, ADR-011: board state ownership)
- Gate specs: `docs/gates/`
- Review (2026-06-15): `docs/reviews/2026-06-15-codebase-review.md`
- Open issues: `gh issue list`
