# Codebase review — 2026-06-15

Full-codebase review against `AGENTS.md` (the 12 Rules + §3 safety rules). Seven
issues filed (#78–#84). This doc captures the **overarching themes** that no single
issue fully owns, plus the state of the safety-critical invariants.

## The one theme that matters: the offline contract silently broke

The biggest finding is not any single bug — it's that **the project's stated
identity diverged from what it ships, and the guard that was supposed to catch that
went blind at the same time.**

`AGENTS.md` §3 still says "fully offline … no INTERNET permission in v1 — this is a
feature." Reality: the app declares INTERNET in `src/main` (for OSMDroid maps + Home
Assistant push), so every release ships online. The `verifyNetworkPermissionScoping`
Gradle guard only inspects a release **overlay** manifest that doesn't exist, so it
passes vacuously while the real permission lives in `src/main`. CI is green and wrong.

This is the pattern to watch for going forward: **when a feature crosses a line the
docs drew, the line moved in code but not in the docs, and the enforcement moved with
neither.** Issues #78 (the ADR + honest guard), #80 (token at rest), and #81 (HA push
robustness) are all downstream of the same unmanaged shift to a connected app. They
should land together and be governed by one decision — **ADR-010** — not piecemeal.

Recommendation: treat #78 as a gating decision for Corey *before* dispatching #80/#81.
Once the connected-app posture is explicit and signed off, the rest are ordinary work.

## Secondary theme: features merged faster than their safety rails

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

## What's healthy (verified, not assumed)

The safety-critical core held up — these were checked, not taken on faith:

- **Rule 1** (core/ has zero Android imports): clean.
- **Rule 6** (BLE write allowlist = unlock, ride mode, lights, + signed-off firmware
  trigger; never an OTA UUID): `OwUuidsTest` intact and still asserts it.
- **Rules 3/4** (no `GlobalScope`, no mutable-`var` singletons): clean; state is in
  Hilt-scoped classes exposing `StateFlow`.
- The earlier OWCE parser concerns from `AGENTS.md` §6 (amps board-type scaling, cell
  voltage firmware-dependence) are **resolved** in `Parsers.kt` — those memory notes
  can be considered closed.

The no-firmware-modification invariant — the one rule that actually protects the
rider's board — is solid. The divergences above are about connectivity, permissions,
and hygiene, not about the board-safety boundary.

## Filed issues

| # | Title | Theme |
|---|-------|-------|
| 78 | Offline guarantee reversed without ADR; network guard is a no-op | connected-app decision |
| 79 | ACCESS_FINE_LOCATION never requested on Android 12+ | feature unreachable |
| 80 | HA token stored in plaintext DataStore | secret handling |
| 81 | HA push fails silently on release (cleartext + no validation) | failure surface |
| 82 | ZWheelAppScreen.kt 501 lines — over 500 hard limit (Rule 5) | hygiene |
| 83 | Missing Wear round-trip + HA push tests (Rule 9) | contract tests |
| 84 | Bundled wear versionCode hardcoded to 1 | release hygiene |

Each issue body embeds a self-contained gate spec (issue bodies can't be edited after
creation, so the gate is frozen inline) ready to drop into `docs/gates/` and dispatch
to Codex per the architect-loop. Suggested order: **#78 first (decision), then
#80 + #81 + #79 together (connected-app rails), then #82/#83/#84 (cleanup) in
parallel.**
