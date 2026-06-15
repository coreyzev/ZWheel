# ZWheel — Solo Handoff Guide

Updated 2026-06-15 (post-Sonnet sprint). Supersedes the previous handoff.

---

## 1. Where the project is right now

All P0/P1/P2 issues from the 2026-06-15 codebase review are **done or in PR**.

**Completed this sprint (merged to main):**
- #79 — GPS fine-location request (PR #87)
- #80 — HA token encrypted at rest with EncryptedSharedPreferences (PR #88)
- #81 — HA push cleartext + validation + Settings test action (PR #89)
- #86 — Drop embedded wearApp() delivery; document adb + Play install paths (PR #90)
- #84 — Closed as mooted by #86
- #82 — Split ZWheelAppScreen.kt under 500 lines (PR #91)
- #78 — Replace vacuous network guard with ADR-010 policy enforcement (PR #92)
- #85 Part A — ADR-011: ConnectionManager is single source of truth (commit 3742c99)
- BLE debug panel fix (commit efae4ac)

**In PR pending CI:**
- #85 Part B — Service decomposition (PR #93): extract HomeAssistantSync, LocationTracker,
  RideNotifications; drop board/connection state mirror; repoint DashboardViewModel +
  WearDataLayerRepository at ConnectionManager directly; RideForegroundService: 302 → 198 lines

**Gemini-OK tasks were dispatched via:** `GEMINI_CLI_TRUST_WORKSPACE=true gemini -p "$(cat /path/to/gate.md)" --yolo`
run from the worktree directory.

---

## 2. Agent division (IMPORTANT)

**Codex** was unavailable this sprint (credits depleted until 2026-06-18). Claude (Sonnet)
implemented everything directly. Gemini handled two Gemini-OK tasks (#86, #82) via headless
dispatch.

Going forward: **Codex-first** per AGENTS.md when available. Claude reviews/specs;
Codex implements; Gemini for trivial mechanical tasks with explicit "Gemini-OK" gate label.

---

## 3. Remaining backlog

### P3 — pre-existing (independent of the review)
- **#74** battery progress bar in the expanded notification — small, Gemini-OK
- **#48** surface active ride via Android Live Updates / Samsung Now Bar — medium
- **#26 / #17 / #14** research items — **blocked on hardware capture** (Corey). Leave parked.

### Technical debt
- `#83` — Wear round-trip + HA push tests (Claude). Easier now that #80/#81 are settled.
  Closes the Rule 9 (tests required) gap for service and HA features.

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
