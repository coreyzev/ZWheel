# ADR-002: Hilt Dependency Injection

Status: Draft

## Context

The phone app will have foreground services, repositories, BLE transport, DataStore,
Room, and watch sync. The wear app will have listener and tile components later.

## Decision

Use Hilt in `:app` and `:wear`. Keep `:core` free of DI framework annotations.

## Consequences

Android-owned implementations can be scoped explicitly while core contracts remain
plain Kotlin. Phase 0 wires only the Hilt application/activity shells.
