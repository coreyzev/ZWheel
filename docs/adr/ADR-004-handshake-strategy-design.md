# ADR-004: Handshake Strategy Design

Status: Draft

## Context

Supported stock boards may require no handshake or Gemini challenge-response. GT/GT-S
Polaris is explicitly out of v1 scope.

## Decision

Represent unlock behavior with a `HandshakeStrategy` port in `:core`, returning a
`HandshakeResult` and optional keep-alive flow. Implement `NoneStrategy` and
`GeminiStrategy` in Phase 1 after fixture tests exist.

## Consequences

The unlock path can be reviewed and tested in isolation before Corey performs hardware
testing at M1.
