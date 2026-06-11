# ADR-003: BLE Library Choice

Status: Draft

## Context

The architecture prefers Kable because it is coroutine-first, with Nordic BLE Library
as fallback and raw `android.bluetooth` as last resort.

## Decision

Not decided in Phase 0. Phase 1 starts with a BLE library spike and will update this
ADR before implementation.

## Consequences

`:core` exposes transport and GATT IO ports now, so the eventual BLE implementation can
be swapped without changing protocol code.
