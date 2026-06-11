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
7. No UI in services; services emit Flows, ViewModels render.
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
  Onewheel2Garmin. Corey's board: XR HW 4029, fw Gemini-era. Dev phone: Samsung S25
  Ultra (aggressive battery mgmt — the bulletproof service in 02_ARCHITECTURE §6 exists
  because of it). Watch: Galaxy Watch 7 Classic (Wear OS 5).
- 2026-06-11: Corey officially renamed the project from the FloatDash working title to
  ZWheel. Phase 0 scaffold uses `com.zwheel` package/application IDs and `/zwheel/state`
  for the planned Wear Data Layer key.
- 2026-06-11: Phase 0 scaffold completed locally with Gradle modules `core`, `app`,
  and `wear`, CI workflow, README/NOTICE, ADR-001..005 drafts, core model/port/calc
  contracts, Onewheel UUID map, parser test harness, and M0 checklist. Local Gradle
  sync/build intentionally not run at Corey's request; CI remains the build gate.
- (append discoveries here…)
