# ADR-010: Runtime Networking Scope

Status: Accepted — Corey sign-off 2026-06-15

## Context

ZWheel shipped its first phases under a blanket rule (AGENTS.md §3): *"Never contact
any network endpoint at runtime. The app is fully offline … no INTERNET permission in
v1 — this is a feature."*

That rule was always a **proxy for the real concern**, which Corey has now stated
explicitly: the danger is **Future Motion (or any third party) reaching the board to
force a firmware update or otherwise alter it.** The blanket "no INTERNET ever" framing
was a conservative way to guarantee that, but it is broader than the actual threat
model and it now conflicts with two features the owner wants:

- **OpenStreetMap tiles** (OSMDroid) for the ride route map.
- **Home Assistant battery push** so the owner can automate charging (e.g. cut a smart
  outlet at 90%).

Both already landed, and `android.permission.INTERNET` is now declared in
`app/src/main`, so the release APK is online. The old `verifyNetworkPermissionScoping`
Gradle guard only inspected a non-existent `src/release` overlay manifest and so passed
vacuously — the offline guarantee was already broken in fact, just not in the docs or
the guard (see issue #78).

## Decision

INTERNET is an **accepted, shipped permission** in v1. Runtime network egress is
**limited by policy** to:

1. **OpenStreetMap tile servers** — read-only map tiles, no user data sent.
2. **The user-configured Home Assistant base URL** — the owner's own LAN/cloud
   instance, opt-in, configured in Settings. Battery percentage is the only payload.

The following remain **absolute and non-negotiable**, and are the real point of the
original rule:

- **No Future Motion endpoints.** The app never contacts any FM cloud/OTA/firmware
  service. Ever.
- **No board firmware modification.** No OTA UUIDs, no firmware download/flash code
  (AGENTS.md §3 + Rule 6 / `OwUuidsTest` still enforce the BLE write allowlist).
- **No analytics, telemetry, crash reporting, or ad/tracking SDKs.** ZWheel sends no
  data about the user or the board anywhere except the owner's own HA instance.

In short: the threat the offline rule guarded against (FM touching the board) is
preserved word-for-word; the over-broad "no INTERNET at all" clause is retired.

## Consequences

- AGENTS.md §3 is reworded to state the *intent* (no FM/OTA endpoints, no
  firmware modification, no analytics) rather than "fully offline / no INTERNET."
- `verifyNetworkPermissionScoping` is replaced by a guard that enforces *this* policy
  (no hardcoded non-OSM hosts; no analytics dependencies) instead of a vacuous
  release-overlay check. (Issue #78.)
- Home Assistant is the only outbound feature that transmits board-derived data, and
  only to an endpoint the owner supplies. Its token is a credential and must be stored
  encrypted (issue #80); its transport must handle the typical local-HTTP case and
  surface failures (issue #81).
- This ADR governs the connected-app posture; issues #79/#80/#81 implement the rails
  underneath it.

## Alternatives considered

- **Keep fully offline; move maps + HA to a debug-only flavor.** Rejected: maps and HA
  are core owner-facing value, not debug tooling. Forcing them into debug would make the
  shipping app strictly worse to preserve a rule whose actual intent is already met by
  the FM/OTA prohibitions.
