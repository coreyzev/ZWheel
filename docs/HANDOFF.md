# ZWheel — Solo Handoff Guide

Updated 2026-06-15 (post-stability-review sprint). Supersedes the previous handoff.

---

## 1. Where the project is right now

**Completed previous sprint (merged to main):**
- #79 — GPS fine-location request (PR #87)
- #80 — HA token encrypted at rest with EncryptedSharedPreferences (PR #88)
- #81 — HA push cleartext + validation + Settings test action (PR #89)
- #86 — Drop embedded wearApp() delivery; document adb + Play install paths (PR #90)
- #84 — Closed as mooted by #86
- #82 — Split ZWheelAppScreen.kt under 500 lines (PR #91)
- #78 — Replace vacuous network guard with ADR-010 policy enforcement (PR #92)
- #85 — ADR-011: ConnectionManager single source of truth + service decomposition (PR #93)
- #96 — Fix GPS banner flicker (PR #98)
- #95 — Fix GPS permanently-denied flow (PR #96)
- #97 — Catch connect() exceptions (PR #97)

**Active known crash:** App crashes on device select (build 122). Root cause diagnosed in the
2026-06-15 stability review. Fixes tracked in issues #99–#107 below.

**Gemini-OK tasks were dispatched via:** `GEMINI_CLI_TRUST_WORKSPACE=true gemini -p "$(cat /path/to/gate.md)" --yolo`
run from the worktree directory.

---

## 2. Issue ordering — 2026-06-15 stability review

All issues below are from the full architecture review on 2026-06-15. Sonnet can implement
all of them. Codex returns 2026-06-18. Hardware (board) not available until Thursday.

### Wave 1 — no board needed, do first, can run in parallel

| Issue | Title | Notes |
|-------|-------|-------|
| **#107** | CI: releases on workflow_dispatch only | One-line YAML change. Do first — stops release noise immediately. |
| **#99** | CRASH: ticker/onDestroy/WearRepo runCatching | 3 small wrapping changes in 2 files. P0. |
| **#100** | CRASH: API 34 SecurityException / location type | Touches Manifest + UI permission gate. P0. Can be verified on emulator. |
| **#103** | Remove stale topSpeed mirror from DashboardViewModel | Small: remove one field and its usages. P2. |
| **#105** | Wakelock object leak + move acquire after startForeground | Small: single-file refactor. P2. |
| **#106** | HA token active migration + Keystore init protection | SettingsRepository only. P2. |
| **#110** | HA cleartext HTTP warning in Settings UI | One conditional Text composable. P3. |
| **#112** | ADR-007 data-path diagram update | Docs only, no code. P3. |

### Wave 2 — no board needed, but depend on Wave 1 being stable

| Issue | Title | Notes |
|-------|-------|-------|
| **#101** | BLE concurrent connect race + stale sharedFlows | ConnectionManager + KableBleTransport. P1. Includes `advertisements` map clear. |
| **#104** | Test coverage: ConnectionManager, RideDao, SettingsRepository, WearRepo, RideForegroundService | P2. RideDao and SettingsRepository gaps can start independently. **Do #99/#101/#105 first** so service tests reflect fixed behavior. |
| **#111** | Permission: openAppSettings before requestAttempted is set | Small UI fix in ZWheelAppScreen.kt. P3. |

### Wave 3 — implement now, verify with board Thursday

| Issue | Title | Notes |
|-------|-------|-------|
| **#102** | Orphan session recovery on START_STICKY restart | Implement now; test with board Thursday. |
| **#109** | BLE reconnect on unexpected disconnection | Depends on #101 (connectJob guard). Implement now; test with board Thursday. |

### Parallel to everything

Issue **#104** test gaps can be implemented in parallel with any wave — they have no runtime
dependencies on each other or on the other fixes. The `RideForegroundService` gap within #104
should wait until #99, #101, and #105 are merged so tests reflect the fixed behavior.

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

See previous HANDOFF section. Short version:
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
