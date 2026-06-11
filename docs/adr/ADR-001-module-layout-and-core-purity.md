# ADR-001: Module Layout and Core Purity

Status: Draft

## Context

ZWheel needs Android phone, Wear OS, and protocol/calculation code without letting
Android framework dependencies leak into the protocol layer.

## Decision

Use three Gradle modules:

- `:core`: Kotlin/JVM only, no Android or AndroidX imports.
- `:app`: phone application depending on `:core`.
- `:wear`: Wear OS application depending on `:core`.

`:core` owns models, ports, UUIDs, parsers, handshake contracts, and calculators.
Android modules own lifecycle, permissions, UI, persistence, and BLE transport.

## Consequences

Core protocol logic can be tested without an emulator. A `forbiddenAndroidImports`
Gradle check fails if Android imports appear under `core/src/main/kotlin`.
