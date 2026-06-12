# ADR-004: Gemini Handshake Strategy And Byte-Level Spec

Status: Draft

## Context

Supported stock boards may require no handshake or Gemini challenge-response. GT/GT-S
Polaris is explicitly out of v1 scope.

Public community references describe Gemini-era boards as requiring a UART-style
challenge-response over the stock Onewheel BLE service. ZWheel's primary board target is
Corey's XR HW 4029, which is expected to use this Gemini path.

This ADR is a blueprint only. Phase 0 does not implement the handshake and does not add
live BLE connection code.

## UUIDs

All UUIDs live in `core/protocol/OwUuids.kt`.

- Service: `e659f300-ea98-11e3-ac10-0800200c9a66`
- Firmware revision trigger/read: `e659f311-ea98-11e3-ac10-0800200c9a66`
- UART read/challenge notification: `e659f3fe-ea98-11e3-ac10-0800200c9a66`
- UART write/unlock response: `e659f3ff-ea98-11e3-ac10-0800200c9a66`

Only `UART_WRITE` is writable for the unlock path. Firmware revision is used as metadata
and, if the Phase 1 evidence confirms it, as the challenge trigger. It must not become a
general-purpose write path.

## Byte Flow

The Phase 1 handshake fixture tests must lock down the exact byte transforms before any
hardware test:

1. Connect and discover the Onewheel service.
2. Subscribe to `UART_READ` notifications before triggering the challenge.
3. Trigger the Gemini challenge using the public-reference-described firmware-revision
   interaction.
4. Receive challenge bytes from `UART_READ`.
5. Validate payload length and framing. Unknown length or framing fails closed.
6. Compute response bytes using the public Gemini MD5 challenge-response transform.
7. Write the response bytes to `UART_WRITE`.
8. Treat unlock as successful only after telemetry characteristics begin producing
   expected values or a documented success signal is observed.

## Transform Contract

The implementation must expose the transform as a pure function in `:core`, separate
from GATT I/O:

```kotlin
fun geminiResponse(challenge: ByteArray): ByteArray
```

Required tests:

- known public challenge fixture produces known public response fixture;
- response is deterministic;
- malformed challenge length throws a typed failure;
- no firmware/OTA UUIDs are referenced;
- writes are attempted only through `OwUuids.UART_WRITE`.

The known appended secret bytes and any byte ordering details must be copied only from
public community documentation. If a reference is GPL code rather than prose, the commit
must either reimplement from understanding or include attribution in `NOTICE.md`.

## Failure Policy

Handshake failure leaves the connection in a locked/degraded state. It must not retry
tight loops, write other characteristics, or guess alternate transforms. The foreground
service may schedule reconnect/backoff later, but Phase 1 debug UI should show the exact
failure category.

## Decision

Represent unlock behavior with a `HandshakeStrategy` port in `:core`, returning a
`HandshakeResult` and optional keep-alive flow. Implement `NoneStrategy` and
`GeminiStrategy` in Phase 1 after fixture tests exist.

## Consequences

The unlock path can be reviewed and tested in isolation before Corey performs hardware
testing at M1.

Corey must review the fixture-backed transform before M1 hardware testing. The agent
must never claim unlock verification without Corey's physical board result.

## References

- `ponewheel/android-ponewheel` issue #86 for public Gemini unlock discussion.
- `COM8/UWP-Onewheel` written challenge-response documentation.
- `kite247/Onewheel2Garmin` for a minimal public reference.
- `OnewheelCommunityEdition/OWCE_App` for legacy characteristic behavior.
