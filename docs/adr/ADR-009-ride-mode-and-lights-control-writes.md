# ADR-009: Ride Mode & Lights Control Writes

Status: Proposed — requires Corey hardware confirmation + sign-off before implementation

## Context

Milestone M2 (`docs/04_BUILD_PLAN.md`) requires two interactive controls on the
dashboard: **ride mode selection** and **front/back light toggle**
(`docs/01_PROJECT_BRIEF.md` §2: "Controls: ride mode selection, front/back light
toggle").

These are the only **new writable** user actions in the app beyond the Gemini handshake
(ADR-004). AGENTS.md §3 is explicit:

> Writable BLE operations are limited to: handshake response, ride mode, lights. Adding
> any new writable characteristic requires Corey's explicit sign-off in an ADR.

`RIDE_MODE` (`e659f302`) and `LIGHTS` (`e659f30c`) are already present in
`OwUuids.writableAllowlist`, so no allowlist change is needed. What is **not** yet
settled — and what this ADR exists to pin down — is the **byte-level write encoding**,
which is board-generation-specific and was **not confirmed during M1**:

- M1 left this open (`docs/M1_BLE_CAPTURE_RUNBOOK.md`): "Does the lights control need
  only `LIGHTS`, or do `LIGHTS_FRONT` and `LIGHTS_BACK` need separate write access?"
- The M1 PASSED memo (AGENTS.md §6) records: "LIGHTS_FRONT/LIGHTS_BACK writability:
  still unconfirmed, not tested in this session."
- `Parsers.rideMode` currently decodes only `0 → CUSTOM, else → UNKNOWN` — the full
  value↔mode mapping is itself a stub. The write encoding and the read decoding share
  one value space and must be confirmed together.

Per AGENTS.md §4, the community-published protocol documentation (OWCE `OWBoard.cs`,
pOnewheel, UWP-Onewheel) is the source of truth; this ADR reimplements from those
descriptions, not from decompiled FM-app code.

## Decision

1. Implement ride-mode and lights changes as **single-byte writes** to `RIDE_MODE`
   (`e659f302`) and `LIGHTS` (`e659f30c`) respectively, through the existing
   `BleTransport.write(...)` path. No new UUID is added; no UUID leaves
   read-only status except the two already in the allowlist.
2. **Defer independent front/back lights** (`LIGHTS_FRONT` `e659f30d` /
   `LIGHTS_BACK` `e659f30e`). v1 uses the single `LIGHTS` characteristic.
   `LIGHTS_FRONT`/`LIGHTS_BACK` stay **read-only** and out of the allowlist until M2
   confirms that `LIGHTS` alone cannot drive both. If they are needed, a follow-up ADR
   + an `OwUuidsTest` update (Rule 6) adds them in the same commit.
3. The byte encodings below are **PROPOSED from community references and must be
   verified on Corey's XR (HW 4209, FW Gemini 4134) before this ADR moves to Accepted
   and before Codex implements the writes.**

## Proposed encoding (UNCONFIRMED — verify at M2)

### Lights — `LIGHTS` `e659f30c`

Single byte:

| Value | Meaning |
|------:|---------|
| `0x00` | Off |
| `0x01` | On (board-default brightness) |

Some community maps describe `0–3` for off / on / dim / etc. v1 ships **off/on only**
(`0x00` / `0x01`); higher values are not written until confirmed.

### Ride mode — `RIDE_MODE` `e659f302`

Single byte equal to the board's mode index. The index set is **generation-specific**;
for XR/Gemini the community-documented modes are Sequoia / Cruz / Mission / Elevated /
Delirium / Custom. The exact numeric values, and how they map onto the app's `RideMode`
enum (`CLASSIC, EXTREME, ELEVATED, DELIRIUM, MISSION, SEQUOIA, CUSTOM, UNKNOWN` in
`core/model/BoardModels.kt`), are the **primary unknown** and must be captured at M2.
Do not hardcode a guessed table into shipping code: until confirmed, the UI should only
offer modes whose byte value has been observed on hardware.

## Safety constraints (binding regardless of final encoding)

- **Read-back verification.** After writing, read/observe the corresponding
  notification (`RIDE_MODE` echoes the active mode; `LIGHTS` state) and reflect the
  *confirmed* value in `BoardState` — never optimistically assume the write took.
- **Range guard.** Only write byte values that are in the confirmed-valid set; reject
  anything else at the call site (fail-safe, like the diameter guard in ADR-006).
- **No firmware path.** These writes are normal control characteristics; they are not a
  trigger like ADR-004's `FIRMWARE_REVISION` write and have no OTA/flash capability.
  `OwUuids` contains no firmware/OTA UUID and this ADR adds none.
- **Allowlist unchanged.** `writableAllowlist` stays `{UART_WRITE, RIDE_MODE, LIGHTS,
  FIRMWARE_REVISION}`. The `OwUuidsTest` allowlist assertion must continue to pass.

## Hardware confirmation procedure (M2, with Corey)

For each control, while connected and unlocked, with the board safely on a stand:

1. **Lights:** write `0x01` to `LIGHTS`, observe the lights turn on; write `0x00`,
   observe off. Record any notification bytes emitted on `LIGHTS` for each state.
2. **Ride mode:** from the FM/OWCE app (or the board UI), set each ride mode in turn
   and record the byte value seen on the `RIDE_MODE` notification. This produces the
   value→mode table. Then verify the reverse: writing each recorded byte selects that
   mode. Capture all of this to a JSONL fixture (schema `m1-ble-debug-v1`) and commit it
   as the test fixture for `Parsers.rideMode` and the write encoder.
3. Confirm whether toggling `LIGHTS` drives both front and back; only if it does not,
   do `LIGHTS_FRONT`/`LIGHTS_BACK` need separate handling (follow-up ADR).

## Consequences

- A small `core/protocol` write-encoder (e.g. `BoardCommands`) plus an `app/ble` control
  path can be implemented **once the table is confirmed**, with fixture tests landing in
  the same commit (Rule 9).
- Until confirmed, ride-mode/lights controls remain **deferred** in the UI (the M2
  dashboard ships read-only telemetry first — see PR #24 / `docs/HANDOFF.md`).
- This ADR is the §3 sign-off vehicle: when Corey approves the confirmed encoding, flip
  Status to **Accepted** and reference the captured fixture.

## Status checklist

- [ ] M2 capture of `RIDE_MODE` value→mode table on XR 4209
- [ ] M2 confirmation that `LIGHTS` `0x00/0x01` toggles both lights
- [ ] Corey sign-off → Status: Accepted
- [ ] Then: implement encoder + UI controls (gate to Codex) with fixture tests
