# ZWheel — Solo Handoff Guide

Updated 2026-06-23 (dark redesign spec audit complete; release triggered). Supersedes previous handoff.

---

## 1. Where the project is right now

**M1 PASSED** — Gemini unlock verified on XR HW 4209 FW 4134. Keep-alive (every 15 s write
to FIRMWARE_REVISION) confirmed holding telemetry indefinitely.

**Dark redesign complete** — All spec items from `docs/design/spec.md` implemented and
shipped. Full implementation status: `docs/design/IMPLEMENTATION_STATUS.md`.

**Completed (2026-06-22/23, all pushed to main):**
- `3b9778d` fix(ui): spec audit — layout, insets, pushback gradient, map overlays
  - Edge-to-edge via `WindowCompat.setDecorFitsSystemWindows(false)` in MainActivity
  - Removed per-screen `systemBarsPadding()` calls
  - Pushback bar: pill corners + 4-stop lime→amber→red gradient brush
  - `ConnectionState.Connecting` added to enum; wired in ConnectScreen + WearDataLayer
- `0ac4ef2` feat(history): full-screen map ride overlays (ride time, stats, legend)
- `1d0bfa5` feat(ble): Settings BLE debug logging toggle
- `1c5a00f` fix(test): GeminiStrategyTest CI fix
- `743db28` fix(test): restore BleDebugRecorder internal test constructor
- `d664364` chore: archive 11 gate docs; remove dead Roborazzi screenshot test

---

## 2. Open issues — next priorities

### Wave 3 — implement now, verify with board

| Issue | Title | Notes |
|-------|-------|-------|
| **#102** | Orphan session recovery on START_STICKY restart | Must close ALL open sessions (LIMIT 1 in getOpenSession only returns one) |
| **#109** | BLE reconnect on unexpected disconnection | Depends on #101 (merged) |

### Test coverage (#104)

| Area | Status |
|------|--------|
| RideDao | Done (PR #136, 6 tests) |
| SettingsRepository migration | Open — test `migrateHaTokenIfNeeded()` + Keystore-null fallback |
| ConnectionManager | Open — test connect race guard, rssi capture, disconnect reset |
| WearDataLayerRepository | Open |
| RideForegroundService | Open — wait until #102/#109 land |

### Not yet scheduled (parked)

- **#74** battery progress in expanded notification — Gemini-OK, small
- **#48** Live Updates / Samsung Now Bar — medium feature
- **#26 / #17 / #14** — blocked on hardware telemetry capture

---

## 3. Agent division

**Codex-first** per AGENTS.md §5. Claude reviews/specs; Codex implements; Gemini for
trivial mechanical tasks with explicit "Gemini-OK" gate label.

---

## 4. Standard workflow

```bash
# Implement on a branch
git checkout -b fix/<slug> origin/main
# ... implement per gate ...
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
git add <files> && git commit -m "<conventional message>"   # NO Co-Authored-By
git push origin fix/<slug>
gh pr create --base main --fill && gh pr merge <N> --squash --admin
gh issue close <N> --comment "Done in #<PR>."

# Triggering a release (workflow_dispatch only):
gh workflow run release.yml --repo coreyzev/ZWheel
```

**Codex dispatch:**
```bash
# Always commit gate files before creating worktrees (untracked files don't appear)
git add docs/gates/gate-*.md && git commit -m "docs(gates): ..."

git -C /root/ZWheel worktree add /tmp/zwheel-codex-<lane> -b codex/<lane> main
codex exec -C /tmp/zwheel-codex-<lane> -s workspace-write \
  "Read ONLY docs/gates/<gate-file>.md. Do NOT read any other files first. Write the files. Compile. Commit." \
  < /dev/null > /tmp/codex-<lane>.log 2>&1 &

# Clean up after merge:
git -C /root/ZWheel worktree remove /tmp/zwheel-codex-<lane> --force
```

**Gemini dispatch pattern (for Gemini-OK gates):**
```bash
git -C /root/ZWheel worktree add /tmp/zw-issue-<N> -b fix/<slug> origin/main
# write gate spec to /tmp/gate-<N>.md
cd /tmp/zw-issue-<N> && GEMINI_CLI_TRUST_WORKSPACE=true gemini -p "$(cat /tmp/gate-<N>.md)" --yolo
# review diff carefully before merging
```

**Disk management (/tmp = 3.8 GB tmpfs):**
```bash
# Always specify:
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon ...

# If /tmp fills (Gradle daemon crash):
rm -rf /tmp/zwheel-gradle /tmp/gradle-home /tmp/gradle-wrapper-cache
# Frees ~2.5 GB. Then re-run with GRADLE_USER_HOME=/tmp/gradle-home.

# Never add Roborazzi (requires Kotlin 2.3.0; project uses 2.0.21)
```

---

## 5. Guardrails (non-negotiable — from AGENTS.md)

- **Never** add a firmware/OTA UUID. No new writable characteristics without an ADR +
  Corey sign-off. `OwUuidsTest` enforces the allowlist.
- `core/` stays Android-free (CI enforces).
- One concern per commit/PR. **No `Co-Authored-By` lines.**
- Never merge without CI green AND a diff skim.
- Networking follows **ADR-010**: no OEM/vendor cloud, no firmware modification, no
  analytics; egress limited to OSM tiles + the user HA URL.

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
- Gate specs: `docs/gates/` (archived; 11 files)
- Design spec: `docs/design/spec.md`
- Implementation status: `docs/design/IMPLEMENTATION_STATUS.md`
- Review (2026-06-15): `docs/reviews/2026-06-15-codebase-review.md`
- Open issues: `gh issue list`
