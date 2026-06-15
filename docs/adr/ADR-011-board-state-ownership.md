# ADR-011: Board/Connection State Ownership

Status: Accepted — Claude decision 2026-06-15 (Corey delegated: "you decide for #85")

## Context

Live board telemetry currently has **two holders** (issue #85):

- `ConnectionManager` (`@Singleton`) — the true BLE source. Owns the Kable transport;
  exposes `boardState`, `connectionState`, `devices`. Outlives the foreground service
  (process-lifetime singleton).
- `RideServiceRepository` (`@Singleton`) — a **mirror**. `RideForegroundService`
  collects `connectionManager.boardState`/`connectionState` and copies each emission
  into it.

`DashboardViewModel` reads board/connection state from the **mirror** but reads
`devices` / `scan()` directly from `ConnectionManager`. Consequences: the UI's live
telemetry is silently gated on the service running; every new `BoardState` field must
be hand-wired through the mirror or it never reaches the dashboard; and `BleDebugViewModel`
observes the source directly, so the same flow is consumed through two lifecycles.

## Decision

**Option 1: `ConnectionManager` is the single source of truth for board + connection
state.**

- `DashboardViewModel` (and any other UI) read `boardState` / `connectionState` /
  `devices` **directly from `ConnectionManager`**.
- `RideServiceRepository` keeps **only service-derived state** that `ConnectionManager`
  cannot know on its own: `isRiding`, `tripDistanceMeters`, `topSpeedMetersPerSecond`,
  `gpsLocked`.
- The foreground service **stops mirroring** `boardState`/`connectionState` into the
  repository. It still consumes `connectionManager.boardState` for its own work
  (recorder tick, top-speed tracking, notification text, wakelock), and it still
  *writes* the service-derived fields into `RideServiceRepository`.

## Rationale

- Deletes the hand-maintained mirror — the part most likely to rot and the cause of the
  "new field silently missing" failure mode.
- Matches reality: `ConnectionManager` already outlives the service and is already the
  direct source for `devices`/`scan()`. Option 1 makes the ViewModel consistent instead
  of straddling two holders.
- The service's role narrows to what it uniquely owns (lifecycle + ride-derived
  metrics), which dovetails with the god-service decomposition (#85 Part B).

## Rejected: Option 2 (RideServiceRepository as the single UI-facing facade)

Proxying `devices`/`scan()` and all telemetry through the repository would make the UI
depend on one holder, but it keeps the mirror (more copying, not less) and couples all
live UI data to the service lifecycle — the opposite of what we want for a dashboard
that should reflect connection state whether or not a ride is active.

## Consequences

- #85 Part B (service decomposition) proceeds on top of this; the gate must drop the
  `mirrorBoardStateAndUpdateNotification` / `mirrorConnectionState` copy-collectors for
  board/connection state and repoint `DashboardViewModel` at `ConnectionManager`.
- `DashboardViewModel` already injects both `ConnectionManager` and
  `RideServiceRepository`, so no new wiring — just change which flows it reads.
- Verify after refactor: dashboard shows live speed/battery while connected even with
  no active ride session; connection label tracks scanning/connected without the
  service mirror.
