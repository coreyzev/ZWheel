# ADR-003: BLE Library Choice

Status: Accepted

## Context

The architecture prefers Kable because it is coroutine-first, with Nordic BLE Library
as fallback and raw `android.bluetooth` as last resort. Phase 1 needs a concrete choice
before any BLE implementation code lands.

ZWheel's BLE workload is narrow but demanding:

- scan by the stock Onewheel service UUID;
- connect and discover services quickly enough for a rider standing near the board;
- perform one Gemini unlock sequence;
- subscribe to a stable set of telemetry notifications for an entire ride;
- write only the approved characteristics: UART unlock, ride mode, lights;
- reconnect without UI ownership when Android background pressure is high.

The BLE adapter must stay inside `:app/ble`. `:core` continues to see only `BleTransport`
and `GattIo`, with raw bytes and cancellable suspend/Flow APIs.

## Options

### Kable

Kable describes itself as a Kotlin asynchronous Bluetooth Low Energy library with a
coroutines-powered API. Its scanner exposes advertisements as `Flow`; scanning starts
when that flow is collected and stops when collection terminates. It supports native
service filters on Android and recommends service-only filters where feasible so the OS
can optimize scans. Its `Peripheral` model owns coroutine scopes and exposes
Android-specific hooks for scan settings, service discovery actions, MTU requests,
transport, PHY, logging, and `autoConnect`.

Expected fit:

- best alignment with ZWheel's coroutine/Flow architecture;
- smaller adapter layer from `Peripheral`/`Flow` concepts into `BleTransport`;
- easier cancellation review because BLE work naturally lives in coroutine scopes;
- less Android-only leakage into the protocol-facing app code.
- cross-platform design keeps the future standalone watch/KMP option less constrained.

Risks:

- fewer Onewheel/community examples than Nordic;
- Phase 1 hardware testing must verify reconnect semantics and notification stability on
  Corey's Samsung S25 Ultra;
- if Android-specific escape hatches are insufficient, the abstraction may become leaky.

### Nordic Android BLE Library

Nordic's Android BLE Library centers on `BleManager`. Its documented feature set includes
connection with retries, service discovery, a queued operation model, MTU and connection
priority handling, RSSI, operation timeouts, error handling, logging, and Kotlin support
with coroutines and Flow. The current stable library is Android-first; Nordic's README
also points to a newer Kotlin BLE Library, but it explicitly says that newer library is
early-stage and not recommended for production use. The stable BLE Library does not
include scanning, so ZWheel would also need Nordic's Android Scanner Compat Library.

Expected fit:

- mature Android-specific BLE stack with robust request queueing;
- strong fit if Kable's reconnect or notification behavior is weak on Corey's S25 Ultra;
- explicit timeout/error surfaces are useful for the foreground service.

Risks:

- Android-first API makes future KMP conversion harder;
- scanning requires an additional library;
- more adapter code is likely to preserve ZWheel's `Flow`-first repository shape.
- `BleManager`'s queue/manager shape is useful but heavier than the current single-board,
  narrow-GATT workload requires.

### Raw `android.bluetooth`

Raw platform APIs remain a last resort only.

Expected fit:

- maximum control if both libraries fail a critical board behavior.

Risks:

- highest implementation burden;
- highest chance of lifecycle and cancellation mistakes;
- least compatible with the architecture's "thin Android shell" rule.

## Phase 1 Spike Criteria

The BLE implementation spike must produce evidence for:

- service-filtered scan latency and stability;
- connection and service-discovery time;
- telemetry notification throughput at 1 Hz or higher without drops during a desk test;
- reconnect behavior after board-side or phone-side disconnect;
- cancellation behavior when the foreground service stops;
- amount of Android-specific code required outside `app/ble`.

The spike must not add firmware/OTA UUIDs or any write beyond the existing allowlist.

## Decision

Use Kable for Phase 1.

Kable best matches the contract ZWheel has already committed to: coroutine cancellation,
Flow-based state, a thin Android shell, and interface-first protocol code in `:core`.
ZWheel can scan by the Onewheel service UUID using Kable's native service filter path,
convert the advertisement into a `Peripheral`, run the Gemini handshake in a cancellable
connection scope, and expose notifications as flows without wrapping a second callback
model.

Keep Nordic Android BLE Library plus Android Scanner Compat as the fallback if Kable
fails one of the hard Android behaviors during Corey's M1 test: reconnect stability,
notification continuity, or Samsung background behavior. That fallback should be chosen
only with captured evidence, because it adds a separate scanner dependency and more
Android-specific adapter code.

Raw `android.bluetooth` remains a last resort only.

## Consequences

`:core` already exposes transport and GATT IO ports, so the BLE implementation can still
be swapped without changing protocol code.

Phase 1 implementation work should add Kable dependencies only to `:app`. No Kable type
may cross into `:core`, `:wear`, or public service interfaces. The first implementation
must include logging hooks that can capture scan, connect, discovery, subscribe,
disconnect, and reconnect timings for the M1 hardware gate.

If Kable fails on reconnect or notification stability, update this ADR with the captured
failure evidence and implement the adapter over Nordic BLE Library plus Scanner Compat.

## References

- JuulLabs Kable README: coroutine-powered BLE API, Flow scanning, service filters,
  native service-filter recommendation, scoped `Peripheral` lifecycle, connection
  scopes, service discovery hooks, MTU request hook, and Android `autoConnect` behavior.
- Nordic Android BLE Library README: `BleManager` features, request queueing, automatic
  retries, timeouts, coroutines/Flow support, Java 17 requirement, stable dependency
  coordinates, scanner-library split, and note that Nordic's newer Kotlin BLE Library is
  early-stage and not recommended for production use.
- Nordic Android Scanner Compat Library README: service-filtered scan examples,
  background scanning constraints, Android 12+ scan permission notes, and Maven
  dependency coordinates.
