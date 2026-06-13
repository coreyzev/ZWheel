# Gate: Phase 3b — RideForegroundService and RideServiceRepository

**Branch:** `codex/p3-foreground-service`
**Depends on:** `codex/p3-room-schema` merged first (needs `RideRepository`)
**One concern:** Introduce `RideForegroundService` that owns the BLE connection; decouple UI from `ConnectionManager` via `RideServiceRepository`.

**Architecture spec in:** `docs/adr/ADR-008-foreground-service-and-oem-battery-policy.md` — read it.

---

## Allowed files

```
app/src/main/AndroidManifest.xml                                          ← add service declaration + permissions
app/src/main/kotlin/com/zwheel/app/data/settings/UserPreferences.kt       ← add lastConnectedDeviceId field
app/src/main/kotlin/com/zwheel/app/data/settings/SettingsRepository.kt    ← add persist/read for lastConnectedDeviceId
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt       ← new: the service
app/src/main/kotlin/com/zwheel/app/service/RideServiceRepository.kt       ← new: StateFlow bridge
app/src/main/kotlin/com/zwheel/app/service/RideServiceController.kt       ← new: thin interface for start/stop
app/src/main/kotlin/com/zwheel/app/ui/DashboardViewModel.kt               ← inject RideServiceRepository instead of ConnectionManager
app/src/main/kotlin/com/zwheel/app/MainActivity.kt                        ← use RideServiceController
app/src/main/res/values/strings.xml                                       ← notification strings (create if absent)
app/src/main/res/xml/battery_optimization.xml                             ← if needed for backup rules
```

---

## Implementation spec

### 1. New permissions in `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

Declare the service inside `<application>`:
```xml
<service
    android:name=".service.RideForegroundService"
    android:foregroundServiceType="connectedDevice|location"
    android:exported="false" />
```

### 2. `RideServiceRepository.kt` (new `@Singleton`)

Exactly as specified in ADR-008 §7. The service writes to it; ViewModels read from it.

### 3. `RideForegroundService.kt`

- `@AndroidEntryPoint` (required for Hilt injection in services)
- Inject: `ConnectionManager`, `RideServiceRepository`, `SettingsRepository`, `RideRepository`
- `onStartCommand`: receives `deviceId` extra, calls `connectionManager.connect(deviceId)` in a `lifecycleScope.launch {}`, persists `deviceId` to `SettingsRepository.saveLastConnectedDeviceId()`, calls `startForeground()` with the notification.
- Mirror `boardState` and `connectionState` from `ConnectionManager` into `RideServiceRepository` via a `lifecycleScope.launch { connectionManager.boardState.collect { ... } }`.
- `onDestroy`: calls `connectionManager.disconnect()`, releases wakelock.
- Notification: channel `"zwheel_ride"`, `IMPORTANCE_LOW`, content `"ZWheel · Connected"` initially, updated every second with speed/battery via `lifecycleScope.launch { rideServiceRepository.boardState.collect { ... } }`. Disconnect action sends `stopSelf()`.
- Wakelock: acquire `PARTIAL_WAKE_LOCK` when `boardState.speedMetersPerSecondCorrected ?: 0.0 > 0.5` for 3+ seconds; release when speed < 0.5 for 90+ seconds. Track with a simple debounce counter updated in the collect loop.
- `START_STICKY` return from `onStartCommand`.
- On restart (no intent extras): check `SettingsRepository` for `lastConnectedDeviceId`, attempt reconnect if present.

### 4. `RideServiceController.kt`

```kotlin
interface RideServiceController {
    fun connect(deviceId: String)
    fun disconnect()
}

@Singleton
class RideServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : RideServiceController {
    override fun connect(deviceId: String) {
        val intent = Intent(context, RideForegroundService::class.java).apply {
            putExtra("deviceId", deviceId)
        }
        context.startForegroundService(intent)
    }
    override fun disconnect() {
        context.stopService(Intent(context, RideForegroundService::class.java))
    }
}
```

Bind `RideServiceController` → `RideServiceControllerImpl` in a new `@Binds` in `AppModule` or a new `ServiceModule`.

### 5. `DashboardViewModel.kt`

Replace injection of `ConnectionManager` with `RideServiceRepository` (for state) and `RideServiceController` (for actions):

```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val rideServiceRepository: RideServiceRepository,
    private val rideServiceController: RideServiceController,
    settingsRepository: SettingsRepository,
    private val rangeEstimator: RangeEstimator,
) : ViewModel() {
    // boardState and connectionState now come from rideServiceRepository
    fun connect(deviceId: String) { rideServiceController.connect(deviceId) }
    fun disconnect() { rideServiceController.disconnect() }
}
```

Update all `connectionManager.*` references to `rideServiceRepository.*`.

### 6. `MainActivity.kt`

Remove any direct `ConnectionManager` usage. Everything routes through `DashboardViewModel` → `RideServiceController`.

### 7. `UserPreferences.kt` and `SettingsRepository.kt`

Add `lastConnectedDeviceId: String? = null` to `UserPreferences`. Add `suspend fun saveLastConnectedDeviceId(id: String?)` to `SettingsRepository`.

---

## Constraints

- `core/` untouched.
- `RideForegroundService` must be `@AndroidEntryPoint` for Hilt field injection.
- Wakelock tag must be `"zwheel:ride"`. Never `SCREEN_BRIGHT_WAKE_LOCK`.
- `ConnectedDevice | location` foreground service type required for Android 14+.
- `ConnectionManager` stays as-is — the service wraps it, not replaces it.

---

## Verification

```bash
./gradlew :app:compileDebugKotlin :app:kspDebugKotlin
```

Both must pass. Fix any errors before reporting done.
