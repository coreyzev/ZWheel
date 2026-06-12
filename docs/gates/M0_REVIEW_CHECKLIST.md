# Gate M0 Review Checklist

Milestone: Phase 0 — Repo & contracts
Date: 2026-06-11
Hardware required: No

## Build And CI

- [ ] GitHub Actions CI runs on the branch/PR.
- [ ] CI completes `clean check :app:assembleDebug :wear:assembleDebug`.
- [ ] `:core` compiles as a Kotlin/JVM module.
- [ ] `:app` and `:wear` assemble debug APKs.
- [ ] Local Gradle baseline rerun completes after AndroidX, Java 17, and coarse-location fixes.
- [ ] If local sandbox blocks Gradle daemon IPC, verify the same command in CI or an unrestricted shell.

## Core Purity And Contracts

- [ ] `core/src/main/kotlin` has no `android.*` or `androidx.*` imports.
- [ ] Public behavior is represented through core ports/service interfaces before Android implementations.
- [ ] `BleTransport`, `GattIo`, `HandshakeStrategy`, `RideStorage`, `Clock`, and `BoardStateService` interfaces exist.
- [ ] `BleTransport` is a raw wire only: scan/connect/disconnect/read/write/notifications as raw bytes, with no `BoardState` dependency.
- [ ] `BoardStateService` is the separate parsed state surface exposing `StateFlow<BoardState>`.
- [ ] Core models/enums cover board identity, connection state, ride mode, board state, ride sessions, ride points, watch payload, speed units, and temperature units.
- [ ] Speed, range, and top-speed calculation contracts exist in `:core` as interface stubs only; implementations are deferred to Phase 2.

## BLE Safety

- [ ] All BLE UUIDs live in `core/protocol/OwUuids.kt`.
- [ ] UUID doc comments cite the public community source family used for Phase 0.
- [ ] `OwUuids.writableAllowlist` is exactly `{UART_WRITE, RIDE_MODE, LIGHTS}`.
- [ ] No firmware-update, OTA, or Future Motion cloud endpoint UUID/path exists.
- [ ] `OwUuidsTest` enforces the writable allowlist.

## Parser Harness

- [ ] `Parsers.kt` contains pure `ByteArray` parser stubs/helpers.
- [ ] Parser fixture tests exist and can be expanded with M1 captured board data.

## Android/Wear Skeleton

- [ ] Gradle multi-module layout exists: `:core`, `:app`, `:wear`.
- [ ] Version catalog exists at `gradle/libs.versions.toml`.
- [ ] Hilt is wired in phone and wear application/activity shells.
- [ ] Phone app has an empty OWCE-style Compose dashboard shell.
- [ ] Wear app has an empty black-background speed screen shell.
- [ ] Main/release manifests do not declare `android.permission.INTERNET`; debug-only
      fixture tooling may declare it under `app/src/debug`.

## Docs And ADRs

- [ ] Project identity is ZWheel; remaining FloatDash mentions are rename history only.
- [ ] `README.md` includes scope, GPL/safety posture, and no-cloud/no-firmware warning.
- [ ] `NOTICE.md` records public protocol reference projects.
- [ ] ADR-001 through ADR-005 are drafted.
- [ ] `AGENTS.md` §6 memory records the rename and Phase 0 scaffold.

## Corey Review

- [ ] Skim core interfaces for fit with planned phases.
- [ ] Skim UUID names/citations for obvious omissions or unsafe writes.
- [ ] Confirm no hardware testing is expected at M0.
- [ ] Approve moving to Phase 1 BLE library spike and Gemini handshake fixtures.
