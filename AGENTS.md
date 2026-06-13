# AGENTS.md — Contract for the implementing agent
Place this file at repo root. Read it at the start of EVERY session, along with
01_PROJECT_BRIEF.md and 02_ARCHITECTURE.md. Update §6 (Memory) as you learn.

## 1. Who does what
- **Implementing agent (Claude Code — or Hermes if Corey chooses):** all code, tests,
  ADRs, docs. One agent. No multi-agent handoffs.
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
- Before starting a phase: re-read the phase section of 04_BUILD_PLAN.md and confirm
  the previous milestone's exit criteria are met or explicitly waived by Corey.
- After each phase: update this file's §6 and write/refresh the phase ADRs.
- When blocked on hardware: prepare the build + a numbered test checklist for Corey,
  then move to the next non-blocked task. Never idle, never fake results.

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
- M1 open question: confirm RPM characteristic is e659f30b on Corey's 4209 XR from
  the board capture fixture. Tighten OwUuids RPM doc comment to cite the specific
  pOnewheel source file/commit once confirmed.
- M1 open question: confirm whether LIGHTS_FRONT (e659f30d) and LIGHTS_BACK (e659f30e)
  require writes for independent front/back control, or whether LIGHTS (e659f30c) alone
  is sufficient. If they need to be writable, add them to writableAllowlist AND update
  OwUuidsTest in the same commit.
- 2026-06-12: Agent division of labor established by Corey. Claude: code review on every
  PR before Corey merges, all security-sensitive code (handshake crypto, write allowlist),
  architecture decisions and ADRs, cross-file reasoning and design judgment, writing test
  scenarios and edge cases, maintaining AGENTS.md. Codex: feature implementation once
  shape is known, mechanical/boilerplate tasks, UI (Compose screens, ViewModels), writing
  tests from specs Claude provides. Workflow: Codex opens PRs, Claude reviews, Corey
  merges. Neither agent merges. If both would touch the same file: Codex implements,
  Claude reviews — never both implementing simultaneously on the same module.
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
- 2026-06-12: M1 log showed Gemini UART_READ can deliver the 20-byte CRX challenge
  fragmented across multiple small BLE notifications. GeminiStrategy buffers UART_READ
  fragments for up to the existing 5s timeout, logs each `gemini_raw_notification`, and
  logs `gemini_challenge_assembled` when the first 20 CRX bytes are complete.
- 2026-06-12: Workflow update — token efficiency. Codex writes code and opens PRs. Claude Code CLI reviews PRs directly via GH access and ends every review with one of three verdicts: 'LGTM — merge', 'Changes requested — [specific issue]', or 'Escalate to Claude Online — [reason]'. Claude Online acts as orchestrator: directs next steps, writes agent prompts, reviews logs and video/screenshots from Corey, and makes decisions when Claude Code CLI escalates. Claude Online does not review PRs unless Claude Code CLI escalates. Corey merges. Neither Codex nor Claude Code CLI merges.

Route to Claude Code CLI (not Codex) only when: security-sensitive code, architecture violations, or explicit escalation needed. Everything else goes to Codex.
- (append discoveries here…)
