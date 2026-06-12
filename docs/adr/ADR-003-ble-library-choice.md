# ADR-003: BLE Library Choice And Performance Profile

Status: Draft

## Context

The architecture prefers Kable because it is coroutine-first, with Nordic BLE Library
as fallback and raw `android.bluetooth` as last resort.

ZWheel's BLE workload is narrow but demanding:

- scan by the stock Onewheel service UUID;
- connect and discover services quickly enough for a rider standing near the board;
- perform one Gemini unlock sequence;
- subscribe to a stable set of telemetry notifications for an entire ride;
- write only the approved characteristics: UART unlock, ride mode, lights;
- reconnect without UI ownership when Android background pressure is high.

No BLE implementation is added in Phase 0. This ADR defines the spike criteria and the
expected performance profile for Phase 1.

## Options

### Kable

Kable describes itself as a Kotlin asynchronous Bluetooth Low Energy library with a
coroutines-powered API. Its scanner exposes advertisements as `Flow`, supports service
filters, and recommends service-only native filters where feasible for system scan
optimization. Its `Peripheral` model owns a coroutine scope and exposes Android-specific
configuration hooks such as scan settings, MTU requests, and service-discovery actions.

Expected fit:

- best alignment with ZWheel's coroutine/Flow architecture;
- smaller adapter layer from `Peripheral`/`Flow` concepts into `BleTransport`;
- easier cancellation review because BLE work naturally lives in coroutine scopes;
- less Android-only leakage into the protocol-facing app code.

Risks:

- fewer Onewheel/community examples than Nordic;
- Phase 1 must verify reconnect semantics and notification stability on Samsung;
- if Android-specific escape hatches are insufficient, the abstraction may become leaky.

### Nordic Android BLE Library

Nordic's Android BLE Library centers on `BleManager`. Its documented feature set includes
connection with retries, service discovery, a queued operation model, MTU and connection
priority handling, RSSI, operation timeouts, error handling, logging, and Kotlin support
with coroutines and Flow. The project notes that scanning is not included and recommends
Nordic's Android Scanner Compat Library for that part.

Expected fit:

- mature Android-specific BLE stack with robust request queueing;
- strong fit if Kable's reconnect or notification behavior is weak on Corey's S25 Ultra;
- explicit timeout/error surfaces are useful for the foreground service.

Risks:

- Android-first API makes future KMP conversion harder;
- scanning requires an additional library;
- more adapter code is likely to preserve ZWheel's `Flow`-first repository shape.

### Raw `android.bluetooth`

Raw platform APIs remain a last resort only.

Expected fit:

- maximum control if both libraries fail a critical board behavior.

Risks:

- highest implementation burden;
- highest chance of lifecycle and cancellation mistakes;
- least compatible with the architecture's "thin Android shell" rule.

## Phase 1 Spike Criteria

The BLE spike must produce evidence for:

- service-filtered scan latency and stability;
- connection and service-discovery time;
- telemetry notification throughput at 1 Hz or higher without drops during a desk test;
- reconnect behavior after board-side or phone-side disconnect;
- cancellation behavior when the foreground service stops;
- amount of Android-specific code required outside `app/ble`.

The spike must not add firmware/OTA UUIDs or any write beyond the existing allowlist.

## Decision

Prefer Kable for Phase 1 unless the spike shows it cannot sustain reconnects or
notifications on Corey's Samsung test phone. Keep Nordic as the fallback for Android BLE
robustness. Raw `android.bluetooth` remains the last resort.

## Consequences

`:core` exposes transport and GATT IO ports now, so the eventual BLE implementation can
be swapped without changing protocol code.

If Kable passes the spike, Phase 1 implements `BleTransport` over Kable. If Kable fails
on reconnect or notification stability, update this ADR with evidence and implement the
adapter over Nordic BLE Library plus Scanner Compat.

## References

- JuulLabs Kable README: coroutine-powered BLE API, Flow scanning, service filters, and
  Android configuration hooks.
- Nordic Android BLE Library README: `BleManager` features, request queueing, timeouts,
  coroutines/Flow support, and scanner-library split.
