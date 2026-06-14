# AGENTS.md — Contract for the implementing agent
**Orchestrator (Claude):** Read this file plus 01_PROJECT_BRIEF.md and 02_ARCHITECTURE.md
at the start of every session. Update §6 (Memory) as you learn.
**Implementing agent (Codex/Gemini) given a gate spec:** Read ONLY this file and the
gate file named in your prompt. Skip 01_PROJECT_BRIEF.md and 02_ARCHITECTURE.md —
the gate spec is the source of truth for your task.

## 1. Who does what
- **Implementing agent (Codex):** all feature code, tests, UI (Compose screens,
  ViewModels), mechanical/boilerplate tasks. Opens PRs for Claude to review.
- **Claude Code (this agent):** code review on every Codex PR, all security-sensitive
  code (handshake crypto, write allowlist), architecture decisions and ADRs,
  cross-file reasoning and design judgment, writing test scenarios and edge cases,
  maintaining AGENTS.md. Ends every review with exactly one verdict (see §5).
- **Corey (human):** physical-device testing (BLE cannot be emulated), milestone
  sign-off, product decisions, signing keys, anything touching real hardware.
- Agent NEVER claims hardware verification it didn't do. Anything requiring a real
  board/watch is handed to Corey with exact test steps (see 04_BUILD_PLAN gates).

## 2. The 12 Rules (binding; self-review every commit against these)
1. `core/` has zero Android imports. CI enforces (lint rule / forbidden-import check).
2. Interface-first: public behavior in `core/ports` or a service interface before impl.
3. Every public suspend fun touching I/O takes/respects a `CancellationToken`
   equivalent — i.e., is cancellable via structured concurrency; no `GlobalScope`.
4. No mutable singletons / no `object` state holders with `var`. State lives in
   Hilt-scoped classes exposing `StateFlow`.
5. Soft 300 / hard 500 line limit per file. Split before you hit it.
6. All BLE UUIDs in `OwUuids.kt` only. A test asserts the writable allowlist =
   {unlock write, ride mode, lights}. **Never add a firmware/OTA UUID. Never.**
7. No UI in services; services emit Flows, ViewModels render. Do not pass
   `MutableState<T>` into non-`@Composable` functions — extract the value at the
   call site and return/callback the new value instead.
8. Room access only via `app/data` repositories.
9. Tests land in the same commit as the code they cover. Parsers and handshake get
   fixture tests BEFORE hardware testing.
10. Conventional commits, one concern per commit:
    `feat(ble): parse cell voltage characteristic`.
11. No commented-out code; `// TODO(m3):` tags or GitHub issues instead.
12. On failure, write a short reflection in the PR/commit body: what failed, what
    changes, am I repeating myself? Then change approach — don't loop.

## 3. Safety rules (non-negotiable, product-level)
- This app must never be able to modify board firmware. No OTA UUIDs, no firmware
  download code, no FM cloud endpoints anywhere in the codebase. (Rule 6 test enforces.)
- Writable BLE operations are limited to: handshake response, ride mode, lights.
  Adding any new writable characteristic requires Corey's explicit sign-off in an ADR.
- Speed/battery/range shown to a rider are safety-relevant. The diameter correction and
  range estimate must fail conservative: if calibration data is missing, show raw value
  with an "uncorrected" badge rather than a guess.
- Never contact any network endpoint at runtime. The app is fully offline. CI may use
  the network; the APK may not (no INTERNET permission in v1 — this is a feature).

## 4. Licensing
- Project license: GPLv3.
- pOnewheel (GPL) and OWCE: you may read them as specs and reimplement; if you port
  code recognizably, keep attribution headers and note it in NOTICE.md. UWP-Onewheel:
  check its license before porting anything verbatim.
- Never copy proprietary/decompiled FM app code. The community-published protocol
  documentation (GitHub issues, wiki) is the source of truth.

## 5. Workflow
- Branch per task → PR (or direct commit if Corey runs trunk-based; his call) →
  CI green (build app+wear, unit tests) → merge.
- (Orchestrator/Claude only) At the start of each new task, check relevant open
  GitHub issues once to avoid duplicate work and capture known blockers.
  Implementing agents (Codex, Gemini) skip this step — the gate spec is the
  source of truth and already incorporates any relevant blockers.
- Before starting a phase: re-read the phase section of 04_BUILD_PLAN.md and confirm
  the previous milestone's exit criteria are met or explicitly waived by Corey.
- After each phase: update this file's §6 and write/refresh the phase ADRs.
- When blocked on hardware: prepare the build + a numbered test checklist for Corey,
  then move to the next non-blocked task. Never idle, never fake results.
- **PR review:** Codex opens PRs. Claude Code reviews every PR and ends with exactly
  one of two verdicts:
  - `LGTM — merge`
  - `Changes requested — [specific issue]`
  Corey merges by default. Claude Code web/CLI may merge when Corey explicitly directs
  it. Codex does not merge.
- Route work to Claude Code (not Codex) for: security-sensitive code, architecture
  violations, ADR authorship, or any task requiring cross-file design judgment.
  Everything else goes to Codex.

### Architect-Loop Dispatch Protocol (how Claude orchestrates Codex)

Source: https://github.com/DanMcInerney/architect-loop

**Roles are absolute:**
- Claude (this agent) = architect only. Specs, gates, review, judgment. Never writes
  implementation code, never commits feature work.
- Codex = builder. All feature code, tests, UI, mechanical tasks. Runs in an isolated
  git worktree via `codex exec --worktree`.

**The loop — one work block:**
1. **Gate spec first, always.** Commit the gate file to `docs/gates/` before any Codex
   dispatch. A builder that edits its own gate file automatically fails review.
2. **Fan out to parallel lanes.** Split the slice into 1–4 lanes whose file sets are
   non-overlapping. Each lane gets its own `codex exec` invocation in a fresh worktree.
3. **Tight Codex prompt — no preamble.** The prompt must be exactly:
   ```
   Read ONLY docs/gates/<gate-file>.md. Do NOT read any other files first.
   Write the files. Compile. Commit.
   ```
   Do NOT tell Codex to read AGENTS.md, 01_PROJECT_BRIEF.md, or 02_ARCHITECTURE.md —
   those exhaust Codex's context before it writes a single file (confirmed 2026-06-13).
4. **Claude judges, never trusts.** Run the gate verification commands yourself.
   Builder claims ("tests pass", "it compiled") are hearsay until you run them.
5. **Repo is the only memory.** `docs/gates/`, `docs/lanes/`, git history. Not in the
   repo = didn't happen.

**Over-dispatch Codex aggressively.** Codex finishes a lane with 70% of its context
window to spare while Claude's is maxed. The scarce resource is Claude-time, not
Codex-time. Four lanes where two might suffice is correct allocation, not waste. Last
session: Claude burned 40% of its context limit implementing a refactor while Codex
sat at 8% — a perfect illustration of misallocated effort.

**Claude implementing code directly is near-last resort.** If Codex runs out of
context: switch to a larger-context model (`-c model="o3"` or similar) and re-dispatch.
If Codex errors: tighten the prompt or gate spec, then re-dispatch. Taking
implementation back from Codex is the expensive wrong answer — fix the dispatch first.

**Dispatch command (gpt-5.5 is the default model — use it for all implementation work):**
```bash
git worktree add /tmp/zwheel-codex-<lane> -b codex/<lane> <base-branch>
codex exec -C /tmp/zwheel-codex-<lane> -s workspace-write \
  "Read ONLY docs/gates/<gate-file>.md. Do NOT read any other files first. Write the files. Compile. Commit." \
  < /dev/null > /tmp/codex-<lane>.log 2>&1 &
```
If gpt-5.5 runs out of context: add `-c model="o3"` and re-dispatch. Do NOT take the work back to Claude.

**Stall triage:** If Codex has not committed after 10 min, check `ps aux | grep codex`.
If blocked on Gradle daemon: `rm -rf /tmp/gradle-home && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew`.

## 6. Memory (append-only; agent maintains)
- 2026-06: Project initialized from Fable design package. Reference protocol sources:
  ponewheel issue #86 (Gemini unlock), UWP-Onewheel docs, OWCE legacy OWBoard.cs,
  Onewheel2Garmin. Corey's board: XR HW 4209 (0x1071), fw Gemini-era. Dev phone: Samsung S25
  Ultra (aggressive battery mgmt — the bulletproof service in 02_ARCHITECTURE §6 exists
  because of it). Watch: Galaxy Watch 7 Classic (Wear OS 5).
- 2026-06-11: Corey officially renamed the project from the FloatDash working title to
  ZWheel. Phase 0 scaffold uses `com.zwheel` package/application IDs and `/zwheel/state`
  for the planned Wear Data Layer key.
- 2026-06-11: Phase 0 scaffold completed locally with Gradle modules `core`, `app`,
  and `wear`, CI workflow, README/NOTICE, ADR-001..005 drafts, core model/port/calc
  contracts, Onewheel UUID map, parser test harness, and M0 checklist. Local Gradle
  sync/build intentionally not run at Corey's request; CI remains the build gate.
- 2026-06-11: Gradle wrapper/bootstrap fixups added after first local build pass found
  missing AndroidX project properties, Java/KSP target mismatch, and Android 12+
  coarse+fine location lint requirements. A later restricted sandbox prevented rerunning
  Gradle because local socket creation for Gradle daemon IPC was denied.
- 2026-06-12: Phase 0 calc boundary corrected: `SpeedCalculator`, `TopSpeedTracker`,
  and `RangeEstimator` are interface stubs only. Concrete speed/range/top-speed
  implementations are intentionally deferred to Phase 2.
- 2026-06-12: Phase 1 BLE spike selected Kable `0.35.0` for Android compatibility with
  the repo's Kotlin 2.0.21 / AGP 8.7.3 toolchain. Full `clean check :app:assembleDebug
  :wear:assembleDebug` passed locally after the Kable transport, Gemini handshake,
  debug BLE screen, and Samsung battery advice screen were added.
- 2026-06-12: FIRMWARE_REVISION (e659f311) added to writable allowlist for Gemini trigger
  only — read-then-write-same-value. Corey sign-off given. See ADR-004.
- M1 open question: confirm whether LIGHTS_FRONT (e659f30d) and LIGHTS_BACK (e659f30e)
  require writes for independent front/back control, or whether LIGHTS (e659f30c) alone
  is sufficient. If they need to be writable, add them to writableAllowlist AND update
  OwUuidsTest in the same commit.
- 2026-06-12: Agent division of labor established by Corey. Claude Code: code review on
  every PR before merge, all security-sensitive code (handshake crypto, write allowlist),
  architecture decisions and ADRs, cross-file reasoning and design judgment, writing test
  scenarios and edge cases, maintaining AGENTS.md. Codex: feature implementation once
  shape is known, mechanical/boilerplate tasks, UI (Compose screens, ViewModels), writing
  tests from specs Claude provides. Workflow: Codex opens PRs, Claude Code reviews, Corey
  merges by default; Claude Code web/CLI may merge when Corey explicitly directs it. If
  both would touch the same file: Codex implements, Claude Code reviews — never both
  implementing simultaneously on the same module.
- 2026-06-12: PR #3 review established pattern: BLE transport (KableBleTransport) must
  emit every advertisement it receives — dedup and staleness logic belong in the UI/call
  site, not the transport. The seenDeviceIds filter was removed from scan(); the debug
  screen owns dedup via deviceLastSeen. Apply this to any future scan flow work.
- Standing rule: Never pass MutableState<T>, MutableList, MutableMap, or Job references
  into non-@Composable functions. State belongs in a ViewModel exposed as StateFlow;
  Composables collect it. If a helper function needs to mutate state, it belongs in the
  ViewModel, not in a free function taking MutableState params.
- 2026-06-12: Rule reminder — one concern per PR. PR #4 combined log noise fix +
  ViewModel refactor. Keep these separate going forward.
- 2026-06-12: M1 debug BLE fixture workflow added: app-level BLE events export as
  redacted JSONL using schema `m1-ble-debug-v1` for AI/human review. Upload is debug-only;
  `INTERNET` permission is scoped to `app/src/debug`, and release remains offline.
  Receiver tooling lives under `tools/ble_debug_receiver/` and expects HTTPS via a
  tunnel/reverse proxy if used off-device.
- 2026-06-12: Corey chose fixed debug receiver IP `116.203.200.55` for M1 log uploads.
  Debug app upload pairing is password-only and targets `http://116.203.200.55:8765`;
  cleartext is allowed only in the debug network security config for that IP.
- 2026-06-12: M1 PASSED. Gemini handshake verified on XR HW 4209 FW 4134.
  e659f30b confirmed as RPM. Reconnect verified. M1 observed a telemetry burst of zeros
  followed by silence; this may have been board power-off behavior, but it still needs a
  controlled confirmation before Phase 3 uses it as an auto-disconnect signal. Firmware
  trigger write (read-then-write-back on e659f311) confirmed working. Challenge arrives
  fragmented across ~10 BLE packets, assembled by buffer. Two AGENTS.md open questions
  resolved: RPM UUID e659f30b: CONFIRMED. LIGHTS_FRONT/LIGHTS_BACK writability: still
  unconfirmed, not tested in this session.
- 2026-06-13: Workflow simplified — Claude Code (web/CLI) is the sole reviewing agent.
  No separate "Claude Online" escalation path. Two verdicts only: `LGTM — merge` or
  `Changes requested — [specific issue]`. Corey enabled RC on the review server. Corey
  merges by default; Claude Code web/CLI may merge when Corey explicitly directs it.
- 2026-06-13: PR #18 review — three required changes: (1) `batteryPercent` lost its
  `.coerceIn(0, 100)` clamp (safety rule); (2) `amps` uses uint16 but current can be
  negative during regen — needs signed int16; (3) `amps` returns `Int` but
  `BoardState.amps` is `Double?` — will be a compile error at wiring time.
- 2026-06-13: M2 first ride smoke test on XR: app connected/unlocked cleanly and showed
  live voltage/amps while Corey rode back and forth ~4 ft for ~40s, then disconnected
  in-app. Speed display was wrong, showing only 0 or ~29 mph. Root cause likely service
  falling back to unconfirmed odometer-derived raw speed when stationary RPM did not
  notify inside the old 3s calculator-selection window. Amps were active but looked off;
  wait for uploaded log parse before changing amps scaling beyond existing signed int16
  tenths parser. Corey clarified any zero-burst interpretation from this M2 test was not
  board power-off: the board was still on/ridable, and was powered off only after in-app
  disconnect.
- 2026-06-13: M2 log analysis found debug export was truncated by
  `BleDebugSessionLogger.startDumpJobs()` using `.take(20)` per characteristic. Remove
  that cap before the next capture. Gemini suggested amps may be centiamps, but do not
  change amps scaling on plausibility alone; keep signed int16 tenths unless OWCE/protocol
  evidence or controlled capture proves otherwise.
- 2026-06-13: OWCE `OWBoard.cs` parses `e659f312` current as signed/two's-complement
  raw value multiplied by a board-type scale, not `/10` or `/100`; XR/Pint/Pint X/GT
  use `0.002`, Plus uses `0.0018`, V1 uses `0.0009`. ZWheel's current
  `Parsers.amps()` signed-int16 `/10.0` behavior does not match OWCE and should be
  replaced with board-type-aware scaling after byte-order confirmation. OWCE cell
  voltage parsing for `e659f31b` is firmware-dependent: FW >= 4141 uses high-nibble cell
  ID and low 12 bits * `0.0011`; older FW uses `data[1]` cell ID and `data[0] * 0.02`.
- 2026-06-13: Phase 3 kick-off. PRs merged or in-flight: #30 (BLE subscription fix + recorder singleton), #31 (board-type detection + BoardIdentity population), #32 (UNCORRECTED badge), #33 (Room schema). Gate specs written for P3b (foreground service, ADR-008 Accepted) and P3c (ride recording). Next: merge pending PRs, implement P3b then P3c.
- 2026-06-13: Codex context-window issue discovered: when dispatched with `codex exec`, Codex reads AGENTS.md + 01_PROJECT_BRIEF.md + 02_ARCHITECTURE.md first (following AGENTS.md instructions) and exhausts its context before writing files. Fix: per AGENTS.md §1 updated heading, sub-agents skip the full doc preamble when given a gate spec. Tight prompt that works: "Read ONLY docs/gates/gate-<name>.md. Do NOT read any other files. Write files. Compile. Commit."
- 2026-06-13: /tmp fills up quickly from Gradle caches. If build daemons crash, run: `rm -rf /tmp/zwheel-gradle /tmp/gradle-home /tmp/gradle-wrapper-cache` to free ~2.5 GB.
- 2026-06-13: BoardTypeDetector HW revision ranges are approximate (Codex estimated them). Anchor: HW 4209 → XR ✓. Other ranges flagged for OWCE OWBoard.cs verification when other board types available.
- 2026-06-13: Phase 3 implementation complete. PRs #34 (P3b foreground service), #35 (P3c ride recording), #36 (P3d history UI) open and pending merge in chain order. Key design decisions: RideServiceRepository as StateFlow bridge between service and ViewModels; RideRecorder as pure-Kotlin state machine (no Android deps); NavHost introduced with dashboard/history/settings routes; ConnectionManager kept for scan/devices (scan not yet in service). POST_NOTIFICATIONS permission required for Android 13+ notification posting.
- 2026-06-13: Phase 4 (Wear OS Data Layer) complete. P4a (#38): WearDataLayerRepository (phone-side) pushes BoardState + connectionState + isRiding + prefs to /zwheel/state DataItem; RideServiceRepository gains isRiding StateFlow. P4b (#40): Watch-side WearDataLayerRepository listens on DataItem, parses WatchPayload, exposes StateFlow; ZWheelWearScreen wired to real live data via MainViewModel. P4c (#41): DefaultTopSpeedTracker and DefaultRangeEstimator wired into RideForegroundService + WearDataLayerRepository — watch now shows real top speed and range. Known limitation: TopSpeedTracker does not reset between ride sessions within a single service lifetime (reset() is internal to core module; workaround is re-instantiation, deferred to m3-milestone or a core interface update).
- 2026-06-13: M3 in-progress: battery optimization first-launch dialog (ADR-008 §6, PR #42 open), ride detail screen (gate spec written, Codex implementing), gate specs written for both.
- 2026-06-14: XR 4209 capture `b60247b3c3838b77-zwheel-ble-3168329bc037bbfc.jsonl`
  showed Gemini unlock success followed by a telemetry zero burst exactly ~20.041s after
  live notifications began. Root cause confirmed by Corey: board drops telemetry ~20s
  after unlock every time without fail. OWCE confirms Gemini boards need a handshake
  keep-alive: write the same firmware revision value back to `FIRMWARE_REVISION`
  immediately after unlock and every 15s while connected. This is the same signed-off
  ADR-004 trigger path, not a new writable UUID or firmware-update path. Fix landed in
  PR #49.
- 2026-06-14: PR #49 code review found 3 issues dispatched to Codex via architect-loop:
  (1) triple duplication of debugName/toRawHexString/shortMessage across KableBleTransport,
  ConnectionManager, and BleDebugFormat — fix: consolidate to BleFormatExtensions.kt in
  com.zwheel.app.ble; (2) 60+ lines of keep-alive logic copy-pasted between
  ConnectionManager and BleDebugViewModel — fix: shared GeminiKeepAliveRunner.kt;
  (3) startKeepAlive called before post-unlock GATT reads (race) — fix: move to after
  connection setup. Gate: docs/gates/gate-pr49-keepalive-cleanup.md.
- (append discoveries here…)
