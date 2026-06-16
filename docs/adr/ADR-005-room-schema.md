# ADR-005: Room Schema

Status: Accepted

## Context

Ride recording must store corrected values for user-facing calculations while retaining
raw values for debugging.

## Decision

Phase 3 will implement Room tables matching the build plan:

- `ride_session`
- `ride_point`

All Room access will live behind repositories in `app/data`.

## Consequences

`:core` defines ride session and ride point models in Phase 0. Android persistence is
deferred until the foreground service and ride recording phase.
