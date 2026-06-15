# Codebase review — 2026-06-15

Full-codebase review against `AGENTS.md` (the 12 Rules + §3 safety rules). Eight issues
filed (#78–#85). This doc captures the **overarching themes** and the state of the
safety-critical invariants.

## Networking posture — resolved (see ADR-010)

The review's headline finding was that the original "fully offline / no INTERNET"
stance had diverged from what ships (INTERNET is now in `src/main` for maps + Home
Assistant) while the `verifyNetworkPermissionScoping` guard had gone vacuous. **This is
now resolved at the policy level:** the networking posture was a proxy for ZWheel's
actual identity — a user-owned, offline-first telemetry companion with no
manufacturer/vendor cloud, no firmware modification, and no analytics. **ADR-010
(Accepted)** documents the connected-app scope (egress limited to OpenStreetMap tiles +
the user-configured Home Assistant URL), and `AGENTS.md` §3 was reworded to state that
intent.

What remains is mechanical, tracked in the issues: make the build guard honest (#78),
and build the rails the connected features need (#79/#80/#81). The *decision* is done;
those are *execution*.

## The theme worth carrying forward: features merged faster than their safety rails

GPS, Home Assistant, and Wear all shipped functionally but skipped the
non-functional half:

- **GPS** works in code but is **unreachable for users** — fine-location is never
  requested on Android 12+ (#79). A feature that can't get its permission is dark.
- **HA push** swallows every error and assumes HTTPS, so the common local-HTTP setup
  fails with zero feedback (#81); its token sits in plaintext (#80).
- **Wear ↔ phone** payload is a hand-serialized `DataMap` contract with **no test on
  either side** (#83) — a field rename breaks the watch silently, not the build.

Across all three, the gap is the same shape: the happy path was built; the
permission prompt, the failure surface, the credential handling, and the
contract test were deferred and then forgotten. Rule 9 ("tests land with the code")
is the specific rule bent, but the broader habit worth correcting is **treating "it
compiles and demos" as done** when the feature has a permission, a secret, a network
hop, or a cross-process contract.

## Structural smell: state ownership + a god-service (#85)

Board/connection state has two holders — `ConnectionManager` (the true BLE source) and
`RideServiceRepository` (a mirror the foreground service populates by collecting
ConnectionManager's flows). `DashboardViewModel` reads board/connection state from the
mirror but `devices`/`scan()` from ConnectionManager directly, so live telemetry is
gated on the service running while scanning is not. `RideForegroundService` (~302 lines)
compounds this by owning six responsibilities. Issue #85 splits this into a design
decision (the ownership model) and a mechanical extraction (service collaborators).

## What's healthy (verified, not assumed)

The safety-critical core held up — these were checked, not taken on faith:

- **Rule 1** (core/ has zero Android imports): clean.
- **Rule 6** (BLE write allowlist = unlock, ride mode, lights, + signed-off firmware
  trigger; never an OTA UUID): `OwUuidsTest` intact and still asserts it.
- **Rules 3/4** (no `GlobalScope`, no mutable-`var` singletons): clean; state is in
  Hilt-scoped classes exposing `StateFlow`.
- The earlier OWCE parser concerns from `AGENTS.md` §6 (amps board-type scaling, cell
  voltage firmware-dependence) are **resolved** in `Parsers.kt`.

The no-firmware-modification invariant — the one rule that actually protects the
rider's board — is solid.

## Filed issues

| # | Title | Theme |
|---|-------|-------|
| 78 | Replace vacuous network guard with an ADR-010 policy guard | execution (decision done) |
| 79 | ACCESS_FINE_LOCATION never requested on Android 12+ | feature unreachable |
| 80 | HA token stored in plaintext DataStore | secret handling |
| 81 | HA push fails silently on release (cleartext + no validation) | failure surface |
| 82 | ZWheelAppScreen.kt 501 lines — over 500 hard limit (Rule 5) | hygiene |
| 83 | Missing Wear round-trip + HA push tests (Rule 9) | contract tests |
| 84 | Bundled wear versionCode hardcoded to 1 | release hygiene |
| 85 | State-ownership mirror + RideForegroundService god object | architecture |

Each issue body embeds a self-contained gate spec (issue bodies are frozen at creation,
so the gate is inline) ready to drop into `docs/gates/` and dispatch. Suggested order:
**#78 + #79 + #80 + #81 together** (the connected-app rails), then **#82/#83/#84** in
parallel, with **#85** gated behind its own ownership-model decision.
