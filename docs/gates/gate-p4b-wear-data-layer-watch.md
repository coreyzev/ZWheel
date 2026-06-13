# Gate: Phase 4b — Wear Data Layer (Watch Side)

**Branch:** `codex/p4b-wear-data-layer-watch`
**Depends on:** Phase 4a merged (phone pushes `/zwheel/state` to Data Layer)
**One concern:** Watch app receives `WatchPayload` and renders a Compose for Wear OS screen.

**Architecture spec in:** `docs/adr/ADR-007-wear-os-data-layer.md` — read it.

---

## Allowed files

```
wear/src/main/AndroidManifest.xml                                         ← add WearableListenerService
wear/src/main/kotlin/com/zwheel/wear/WearDataLayerRepository.kt           ← new: receive DataItems
wear/src/main/kotlin/com/zwheel/wear/MainViewModel.kt                     ← new: expose StateFlow<WatchPayload?>
wear/src/main/kotlin/com/zwheel/wear/MainScreen.kt                        ← new: Compose for Wear OS screen
wear/src/main/kotlin/com/zwheel/wear/WatchActivity.kt                     ← update or create if absent
wear/build.gradle.kts                                                      ← add play-services-wearable + wear compose deps
gradle/libs.versions.toml                                                  ← add wear-compose version if not present
```

---

## Implementation spec

### 1. `WearDataLayerRepository.kt`

```kotlin
@Singleton
class WearDataLayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DataClient.OnDataChangedListener {
    private val _payload = MutableStateFlow<WatchPayload?>(null)
    val payload: StateFlow<WatchPayload?> = _payload.asStateFlow()

    private val dataClient: DataClient = Wearable.getDataClient(context)

    fun register() { dataClient.addListener(this) }
    fun unregister() { dataClient.removeListener(this) }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/zwheel/state"
            ) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                _payload.value = dataMap.toWatchPayload()
            }
        }
    }
}

private fun DataMap.toWatchPayload(): WatchPayload {
    val speedRaw = getFloat("speed_mps_corrected")
    val batteryRaw = getInt("battery_pct")
    val rangeRaw = getFloat("estimated_range_m")
    val connState = try {
        com.zwheel.core.model.ConnectionState.valueOf(getString("connection_state") ?: "DISCONNECTED")
    } catch (e: IllegalArgumentException) {
        com.zwheel.core.model.ConnectionState.DISCONNECTED
    }
    return WatchPayload(
        speedMetersPerSecondCorrected = if (speedRaw < 0) null else speedRaw.toDouble(),
        topSpeedMetersPerSecond = getFloat("top_speed_mps").toDouble(),
        batteryPercent = if (batteryRaw < 0) null else batteryRaw,
        estimatedRangeMeters = if (rangeRaw < 0) null else rangeRaw.toDouble(),
        speedUnit = try { SpeedUnit.valueOf(getString("speed_unit") ?: "MPH") } catch (e: Exception) { SpeedUnit.MPH },
        isRiding = getBoolean("is_riding"),
        connectionState = connState,
    )
}
```

### 2. `MainViewModel.kt`

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: WearDataLayerRepository,
) : ViewModel() {
    val payload: StateFlow<WatchPayload?> = repository.payload

    init { repository.register() }
    override fun onCleared() { repository.unregister() }
}
```

### 3. `MainScreen.kt`

A single Compose for Wear OS screen showing:
- Large center: speed (e.g. "15 mph") or "--" if null
- Below: battery percent (e.g. "84%") or "--"
- Bottom: connection state label (shrunk to fit watch bezel)
- Background color: dark (#111111) always

Use `androidx.wear.compose.material.Text` and `Scaffold`. Keep it simple — no scrolling
content on a watch face. The screen is ~380dp wide.

```kotlin
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val payload by viewModel.payload.collectAsStateWithLifecycle()
    // render speed, battery, connection state
}
```

### 4. `WatchActivity.kt`

If a `WatchActivity` or equivalent entry point doesn't exist, create one:
```kotlin
@AndroidEntryPoint
class WatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen() }
    }
}
```

Add to manifest: `<activity android:name=".WatchActivity" android:exported="true">` with
the launcher intent filter.

### 5. `AndroidManifest.xml` (wear)

Register the `WearableListenerService` for data change events:
```xml
<service
    android:name=".WearDataLayerService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
        <data android:scheme="wear" android:host="*" android:pathPrefix="/zwheel/state" />
    </intent-filter>
</service>
```

Wait — actually a `WearableListenerService` is needed only if the app must react to data
while the activity is in the background. If the activity registers/unregisters in
onResume/onPause, no service is needed. For simplicity: use activity-level registration
(register in `WatchActivity.onResume()`, unregister in `onPause()`). Skip the service for now.

### 6. Dependencies (`wear/build.gradle.kts`)

```kotlin
implementation(libs.play.services.wearable)
implementation(libs.androidx.wear.compose.material)   // already declared
```

---

## Constraints

- `core/` untouched.
- Do NOT use `WatchFaceService` in this gate — that is Phase 4c (tile/face). This gate
  is just the Activity + screen.
- Compose for Wear OS uses `androidx.wear.compose.material` — not Material 3. Check that
  `Text`, `Scaffold`, `Button` come from the wear package.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :wear:compileDebugKotlin :wear:kspDebugKotlin
```

Must pass. Fix any errors before reporting done.
