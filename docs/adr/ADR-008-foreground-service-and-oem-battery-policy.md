# ADR-008: Foreground Service and OEM Battery Policy

Status: Accepted

## Context

Currently `ConnectionManager` is a Hilt `@Singleton` injected directly into `DashboardViewModel`. This means the BLE connection is effectively owned by the ViewModel lifecycle — when Android kills the app process (which Samsung's S25 Ultra does aggressively after ~2 minutes in background), the connection drops and any active ride is interrupted.

Phase 3 goal: a connection that survives screen-off, swipe-from-recents, and 30 seconds of airplane mode with reconnect.

## Decision

### 1. Service architecture

Introduce `RideForegroundService extends LifecycleService` in `app/service/`:

- **Foreground service types:** `connectedDevice | location` (both required: `connectedDevice` for the BLE permit, `location` for GPS ride recording per Android 14+ requirements).
- **Owns `ConnectionManager`.** The service holds the Hilt-injected `ConnectionManager` and starts/stops BLE from within the service lifecycle, not from UI.
- **Exports `boardState: StateFlow<BoardState>` and `connectionState: StateFlow<ConnectionState>`** via a Hilt-singleton `RideServiceRepository` that the service writes to and ViewModels read from. This decouples UI from service lifecycle completely.
- **Started by:** `MainActivity.onCreate()` calling `startForegroundService(Intent(this, RideForegroundService::class.java))` when the user presses "Connect." The service calls `startForeground()` within 10 seconds per Android requirements.
- **Stopped by:** explicit disconnect (user action or board lost > 10 min).

### 2. Notification

Ongoing notification with:
- Title: board name or "ZWheel"
- Content: `{speed} {unit} · {battery}%` updated every second while riding
- Action button: "Disconnect"
- Channel: `IMPORTANCE_LOW` (no sound, non-intrusive)
- Small icon: use app icon until a proper notification icon is created

### 3. Wakelock policy

- `PARTIAL_WAKE_LOCK` acquired when `BoardState.speedMetersPerSecondCorrected > 0` for 3+ seconds (i.e. ride started).
- Released when speed drops to 0 for > 90 seconds (ride ended) or on explicit disconnect.
- Tag: `"zwheel:ride"`.
- Never acquire `FULL_WAKE_LOCK` or `SCREEN_BRIGHT_WAKE_LOCK`.

### 4. Process death / START_STICKY

- `onStartCommand` returns `START_STICKY`.
- On restart after process death: check `RideRepository.getOpenSession()` — if a session has no `endTs`, resume it and attempt BLE reconnect to the last-known `boardId`.
- Last-known `boardId` is persisted in `DataStore` (add a `lastConnectedDeviceId: String?` field to `UserPreferences`) so it survives process death.

### 5. Reconnect during active ride

On unexpected `ConnectionState.Disconnected` while a session is open:
- Exponential backoff: 1s → 2s → 4s → 8s → 16s → 30s (cap), for up to 10 minutes.
- Notification content flips to "Reconnecting…" during backoff.
- Session stays open; ride data gap is acceptable (GPS and BLE gaps are normal).
- After 10 minutes without reconnect: close the session (set `endTs`), release wakelock, update notification to "Disconnected."

### 6. Battery-optimization onboarding

- On first `RideForegroundService` start (tracked in DataStore), call `startActivity(Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))` with honest copy explaining why.
- On every `MainActivity.onResume()`: check `PowerManager.isIgnoringBatteryOptimizations(packageName)`. If false, show a dismissible `Snackbar`/banner: "Battery optimization is on — ZWheel may be killed mid-ride. Tap to fix."
- Samsung-specific: `OemBatteryAdvice.kt` (already exists in `app/ui/onboarding/`) provides the Samsung-specific copy. The service start triggers this advice when `Build.MANUFACTURER == "samsung"`.

### 7. RideServiceRepository (the bridge)

```kotlin
@Singleton
class RideServiceRepository @Inject constructor() {
    private val _boardState = MutableStateFlow(BoardState())
    val boardState: StateFlow<BoardState> = _boardState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Called by RideForegroundService only
    internal fun updateBoardState(state: BoardState) { _boardState.value = state }
    internal fun updateConnectionState(state: ConnectionState) { _connectionState.value = state }
}
```

`DashboardViewModel` is refactored to inject `RideServiceRepository` instead of `ConnectionManager` directly. `ConnectionManager` becomes an internal implementation detail of the service.

## Consequences

- ViewModels no longer inject `ConnectionManager` — they go through `RideServiceRepository`.
- `DashboardViewModel.connect()` / `disconnect()` now `startForegroundService()` / `stopService()` instead of calling `connectionManager` directly. A thin `RideServiceController` interface can wrap this for testability.
- This is a breaking refactor of `DashboardViewModel` and `MainActivity` — it must land as its own PR before any ride-recording work.
- Gate P3b (foreground service) implements this ADR. Gate P3c (ride recording) depends on P3b.

## ADRs not yet written

- ADR-007 (Data Layer / Watch sync payload) — deferred to Phase 4.
