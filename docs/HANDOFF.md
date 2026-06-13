# ZWheel — Solo Handoff Guide (continue without Claude)

Written 2026-06-13 during Phase 2. Read this top-to-bottom once, then use the
"Daily loop" section as your checklist.

---

## 1. Where the project is right now

**Merged to `main` (all CI-green):**
- Phase 2 calculators: `RpmBased`, `ScaledFirmware`, `DefaultTopSpeedTracker`,
  `DefaultRangeEstimator`, `UnitConversions` (in `core/calc/`).
- `BoardStateServiceImpl` (`core/service/`) — subscribes to BLE characteristics,
  emits `StateFlow<BoardState>`, selects speed calculator per ADR-006.
- Settings: DataStore-backed `SettingsRepository` (tire diameter + speed/temp units)
  with Hilt module, ViewModel, and Settings screen.
- PR #24 dashboard integration: Hilt DI, `ConnectionManager` (owns
  `BoardStateServiceImpl`, single shared `BleTransport`), and `DashboardViewModel`
  mapping live `BoardState` → dashboard.
- ADR-009: proposed ride-mode/lights write encoding and M2 hardware confirmation
  checklist.

The app is at the **M2-testable** point: connect to the board and the dashboard shows
live, diameter-corrected telemetry. Use the latest GitHub prerelease APK for the §6
ride test, or use the local fallback at `app/build/outputs/apk/debug/app-debug.apk` if
building locally.

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
cd /root/zwheel-wt/<name>
cp docs/gates/gate-<name>.md /root/zwheel-wt/<name>/docs/gates/   # if not already there
codex exec -C /root/zwheel-wt/<name> -s workspace-write \
  "Read docs/gates/gate-<name>.md and implement exactly what it specifies. \
   Run ./gradlew :app:compileDebugKotlin :core:test and fix until green BEFORE finishing. \
   Stay strictly within the allowed files." < /dev/null
```

**What Codex CAN do:** write files, and **run gradle to self-verify** (network +
build are enabled in `~/.codex/config.toml`). Always tell it to compile before it
reports done — that keeps CI green on the first try.

**What Codex CANNOT do:** `git commit` (its sandbox protects `.git`). So **you commit
its output yourself**:
```bash
cd /root/zwheel-wt/<name>
git add <the files the gate allowed>          # do NOT add docs/gates/*
git commit -m "feat(scope): ..."              # NO "Co-Authored-By" lines (house rule)
git push -u origin codex/<name>
gh pr create --title "..." --body "..."
```

**Gate-writing tips that make Codex reliable:**
- List an explicit "Allowed files" set; tell it to STOP rather than touch anything else.
- Give exact signatures / class names / Hilt annotations. The more precise, the better.
- For pure-`core/` work, remind it: zero `android.*`/`androidx.*` imports (CI enforces).
- Tell it to run the build and fix until green before finishing.

If Codex hangs: you forgot `< /dev/null`. Kill it (`pkill -f 'codex exec'`) and rerun.

---

## 3. Merging a PR (the safe ritual)

```bash
gh pr checks <N>                              # or: gh run watch <run-id> --exit-status
```
- **Read the actual test step result, not just the run's exit status.** (A test file
  can compile yet hang — that's how `main` briefly went red on the BLE tests; the fix
  was running test collectors on `backgroundScope`.)
- Skim the diff: `gh pr diff <N>`. For BLE work, confirm there is only **one**
  `BleTransport` instance (two fight over the adapter).
- Merge: `gh pr merge <N> --merge`.
- If a PR branched from a now-updated `main`, refresh it before merging:
  `cd <worktree> && git fetch origin && git merge origin/main` (resolve any
  `libs.versions.toml` conflict by keeping ALL version entries), then push.

---

## 4. Daily loop (what to actually do)

1. **Install the latest debug APK** from GitHub Releases, or the local fallback at
   `app/build/outputs/apk/debug/app-debug.apk`, and run the §6 ride test on your XR.
   File issues for anything off.
2. Pick the next roadmap slice (§5), write a gate, dispatch Codex (§2), commit (§2),
   merge (§3). One concern per PR.

---

## 5. Roadmap after #24 (priority order)

**Finish M2 (rideable dashboard polish):**
- Board-type detection from hardware/firmware revision so `stockTireDiameterInches`
  and saved per-board tire diameter are correct (currently defaults to XR 11.5).
- Persist/show "uncorrected" badge when diameter is out of range or RPM missing
  (AGENTS.md §3 safety rule).
- Wire Settings tire-diameter so it flows into `ConnectionManager` at connect.

**Ride mode + lights writes** (needs YOU first):
- These are **deferred on purpose**: the write byte-encoding is hardware-unconfirmed
  and AGENTS.md §3 requires an ADR + your sign-off for any new writable behavior.
  RIDE_MODE and LIGHTS are already in `OwUuids.writableAllowlist`, but confirm the
  payload encoding against your board before implementing. ADR-009 is now proposed;
  use its hardware confirmation checklist, then flip it to Accepted only after Corey
  sign-off.

**Phase 3 (per docs/04_BUILD_PLAN.md):** `RideForegroundService` (the "never killed"
service), Room schema + ride recording, Samsung battery onboarding. This is the next
big phase; `02_ARCHITECTURE.md §6` is the spec.

---

## 6. M2 ride test checklist (do this on the board)

> The whole point of M2: does diameter correction make speed/distance match reality?

1. Install the debug APK, grant BLE + location permissions.
2. In **Settings**, set tire diameter to your actual (smaller) tire size.
3. From the dashboard, **Scan → Connect** to the XR. Confirm the Gemini unlock holds
   and the dashboard populates: speed, battery %, pack voltage, temps, cell grid,
   ride mode.
4. Ride a known **~2 mile GPS route** (use a GPS app/bike computer as reference).
5. Compare app distance & speed to GPS — **expect ≤ ~2% error**. If corrected speed is
   off, note your tire diameter setting and the observed vs GPS values.
6. Note: **ride-mode and lights buttons are not wired yet** (deferred — see §5).

Report results as a GitHub issue with the numbers; that closes/extends the M2 gate.

---

## 7. Guardrails (non-negotiable — from AGENTS.md)

- **Never** add a firmware/OTA UUID. Never add a writable characteristic without an
  ADR + your sign-off. Don't touch `OwUuids.writableAllowlist` casually.
- `core/` stays Android-free (CI enforces).
- One concern per commit/PR. No `Co-Authored-By` lines.
- Never merge without CI green AND a diff skim.
- The app is offline; no network at runtime (no INTERNET permission in release).

---

## 8. Useful paths & commands

- Gate examples: `docs/gates/`  (unit-conversions, settings, ble-service, integration).
- Codex config: `~/.codex/config.toml` (network + repo writable already set; `.git`
  stays sandbox-protected by design).
- Worktrees live under `/root/zwheel-wt/`. List: `git worktree list`. Clean up merged
  ones: `git worktree remove --force <path>`.
- Codex run logs (if you redirect them): `/tmp/codex-*.log`.
