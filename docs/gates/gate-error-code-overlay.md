# Gate: Last Error Code Overlay (Dashboard + Watch)

## Purpose

When the board fires a non-zero error code on `LAST_ERROR_CODE`, show a red dismissable
overlay on the phone dashboard and a red error face on the watch. The error code must
survive BLE disconnect so users can see why the board cut out. It clears only on the
next successful `connect()` call.

This is a HIGH PRIORITY SAFETY FEATURE — implement fully with no skips.

---

## Architecture Decision

The error code lives in `ConnectionManager` as a separate `MutableStateFlow<Int?>`, NOT
inside `BoardState`. `BoardState` is cleared on `disconnect()`, which would erase the
error before the user reads it. `ConnectionManager._lastErrorCode` is reset only in
`connect()`.

---

## Implementation

### 1. `core/src/main/kotlin/com/zwheel/core/model/WatchDataLayerKeys.kt`

Add one constant at the bottom:

```kotlin
const val KEY_LAST_ERROR_CODE = "last_error_code"
```

### 2. `core/src/main/kotlin/com/zwheel/core/model/BoardModels.kt`

Add `lastErrorCode: Int? = null` to `WatchPayload`:

```kotlin
data class WatchPayload(
    val speedMetersPerSecondCorrected: Double?,
    val topSpeedMetersPerSecond: Double,
    val batteryPercent: Int?,
    val estimatedRangeMeters: Double?,
    val speedUnit: SpeedUnit,
    val isRiding: Boolean,
    val connectionState: ConnectionState,
    val safetyHeadroom: Int? = null,
    val lastErrorCode: Int? = null,
)
```

### 3. `core/src/main/kotlin/com/zwheel/core/protocol/Parsers.kt`

Add one new parser at the bottom of the `Parsers` object:

```kotlin
fun lastErrorCode(bytes: ByteArray): Int? {
    val code = unsignedInt16(bytes)
    return if (code == 0) null else code
}
```

### 4. `app/src/main/kotlin/com/zwheel/app/ble/ConnectionManager.kt`

Add a `_lastErrorCode` StateFlow and subscribe to notifications in `connect()`.

**Add class-level fields** (alongside the existing `_staleTelemetry` fields):

```kotlin
private val _lastErrorCode = MutableStateFlow<Int?>(null)
val lastErrorCode: StateFlow<Int?> = _lastErrorCode.asStateFlow()
```

**In `connect()`**, at the very top where `_staleTelemetry.value = false` is set, also reset:

```kotlin
_staleTelemetry.value = false
_lastErrorCode.value = null
```

**In `connect()`**, after `startStaleTelemetryWatcher()` and before `startKeepAlive(...)`, subscribe to error code notifications:

```kotlin
scope.launch {
    runCatching {
        transport.notifications(OwUuids.LAST_ERROR_CODE).collect { bytes ->
            val code = Parsers.lastErrorCode(bytes)
            if (code != null) _lastErrorCode.value = code
        }
    }
}
```

**In `disconnect()`**, do NOT touch `_lastErrorCode` — the value must persist after disconnect.

### 5. `app/src/main/kotlin/com/zwheel/app/ui/DashboardViewModel.kt`

Add two new StateFlows and a dismiss function:

```kotlin
val lastErrorCode: StateFlow<Int?> = connectionManager.lastErrorCode.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = null,
)

private val _dismissedErrorCode = MutableStateFlow<Int?>(null)
val dismissedErrorCode: StateFlow<Int?> = _dismissedErrorCode.asStateFlow()

fun dismissErrorCode() {
    _dismissedErrorCode.value = lastErrorCode.value
}
```

Required new imports (add if not already present):
`kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.asStateFlow`

### 6. New file: `app/src/main/kotlin/com/zwheel/app/ui/dashboard/ErrorCodeOverlay.kt`

```kotlin
package com.zwheel.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.SairaFamily

@Composable
fun ErrorCodeOverlay(
    errorCode: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(Color(0xFFB00020), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Text(
                text = "BOARD ERROR",
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 2.sp,
                ),
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "ERR $errorCode",
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.W700,
                ),
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss) {
                Text(
                    "Dismiss",
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = Color.White,
                )
            }
        }
    }
}
```

### 7. `app/src/main/kotlin/com/zwheel/app/ui/dashboard/DashboardScreen.kt`

Add `errorCode: Int?` and `onDismissError: () -> Unit` parameters to `DashboardScreen`:

```kotlin
@Composable
fun DashboardScreen(
    boardState: BoardState,
    // ... all existing params ...
    isReconnecting: Boolean = false,
    errorCode: Int? = null,
    onDismissError: () -> Unit = {},
)
```

Inside the outer `Box`, add `ErrorCodeOverlay` as the last child (renders on top of everything):

```kotlin
if (errorCode != null) {
    ErrorCodeOverlay(
        errorCode = errorCode,
        onDismiss = onDismissError,
    )
}
```

Add import: `import com.zwheel.app.ui.dashboard.ErrorCodeOverlay`

### 8. `app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt`

Find where `DashboardViewModel` state is collected and `DashboardScreen` is called.
Add collection of the two new StateFlows and compute the active error code:

```kotlin
val lastErrorCode by viewModel.lastErrorCode.collectAsStateWithLifecycle()
val dismissedErrorCode by viewModel.dismissedErrorCode.collectAsStateWithLifecycle()
val activeErrorCode = lastErrorCode?.takeIf { it != dismissedErrorCode }
```

Pass `errorCode = activeErrorCode` and `onDismissError = viewModel::dismissErrorCode`
to wherever `DashboardScreen` is called (through `RideTabContent` if there's a wrapper —
thread the params through all intermediate composables).

### 9. `app/src/main/kotlin/com/zwheel/app/wear/WearDataLayerRepository.kt`

**Update the `combine` call in `startSync()`** to include `connectionManager.lastErrorCode`.

`combine` supports up to 5 typed lambdas; for 6 flows use the array-based form:

```kotlin
combine(
    flows = listOf(
        connectionManager.boardState,
        connectionManager.connectionState,
        rideServiceRepository.isRiding,
        settingsRepository.preferences,
        rideServiceRepository.topSpeedMetersPerSecond,
        connectionManager.lastErrorCode,
    )
) { values ->
    @Suppress("UNCHECKED_CAST")
    val boardState = values[0] as BoardState
    val connectionState = values[1] as com.zwheel.app.ble.ConnectionState
    val isRiding = values[2] as Boolean
    val prefs = values[3] as com.zwheel.app.data.settings.UserPreferences
    val topSpeedMps = values[4] as Double
    val errorCode = values[5] as Int?
    val estimatedRangeMeters = DefaultRangeEstimator.estimateKilometersRemaining(
        batteryPct = boardState.batteryPercent,
        boardType = boardState.identity?.type ?: BoardType.UNKNOWN,
    )?.let { it * 1000.0 }
    toWatchPayload(boardState, connectionState, isRiding, prefs.speedUnit, topSpeedMps, estimatedRangeMeters, errorCode)
}
```

**Add `KEY_LAST_ERROR_CODE` to `putPayload()`**:

```kotlin
dataMap.putInt(KEY_LAST_ERROR_CODE, payload.lastErrorCode ?: -1)
```

**Add to `toDataEntries()`**:

```kotlin
KEY_LAST_ERROR_CODE to (lastErrorCode ?: -1),
```

**Update `toWatchPayload()` signature** to accept `errorCode: Int?` and pass it through:

```kotlin
private fun toWatchPayload(
    boardState: BoardState,
    connectionState: com.zwheel.app.ble.ConnectionState,
    isRiding: Boolean,
    speedUnit: SpeedUnit,
    topSpeedMetersPerSecond: Double,
    estimatedRangeMeters: Double?,
    errorCode: Int?,
): WatchPayload {
    // ... existing body unchanged ...
    return WatchPayload(
        // ... existing fields ...
        safetyHeadroom = boardState.safetyHeadroom,
        lastErrorCode = errorCode,
    )
}
```

Add import: `import com.zwheel.core.model.KEY_LAST_ERROR_CODE`

### 10. `wear/src/main/kotlin/com/zwheel/wear/WearDataLayerRepository.kt`

**Add `lastErrorCodeRaw: Int` parameter to `decodeWatchPayload()`** and decode:

```kotlin
internal fun decodeWatchPayload(
    speedRaw: Float,
    topSpeedRaw: Float,
    batteryRaw: Int,
    rangeRaw: Float,
    speedUnitStr: String?,
    isRiding: Boolean,
    connStateStr: String?,
    safetyHeadroomRaw: Int,
    lastErrorCodeRaw: Int,
): WatchPayload = WatchPayload(
    // ... existing fields unchanged ...
    safetyHeadroom = if (safetyHeadroomRaw < 0) null else safetyHeadroomRaw,
    lastErrorCode = if (lastErrorCodeRaw <= 0) null else lastErrorCodeRaw,
)
```

**Update `DataMap.toWatchPayload()`** to pass the new param:

```kotlin
private fun DataMap.toWatchPayload(): WatchPayload = decodeWatchPayload(
    speedRaw = getFloat(KEY_SPEED_MPS_CORRECTED),
    topSpeedRaw = getFloat(KEY_TOP_SPEED_MPS),
    batteryRaw = getInt(KEY_BATTERY_PCT),
    rangeRaw = getFloat(KEY_ESTIMATED_RANGE_M),
    speedUnitStr = getString(KEY_SPEED_UNIT),
    isRiding = getBoolean(KEY_IS_RIDING),
    connStateStr = getString(KEY_CONNECTION_STATE),
    safetyHeadroomRaw = getInt(KEY_SAFETY_HEADROOM, -1),
    lastErrorCodeRaw = getInt(KEY_LAST_ERROR_CODE, -1),
)
```

Add import: `import com.zwheel.core.model.KEY_LAST_ERROR_CODE`

### 11. `wear/src/main/kotlin/com/zwheel/wear/ui/ZWheelWearScreen.kt`

**Add `lastErrorCode: Int?` to `WearDashboardUiState`**:

```kotlin
internal data class WearDashboardUiState(
    // ... existing fields ...
    val pushbackApproaching: Boolean,
    val isConnected: Boolean,
    val lastErrorCode: Int? = null,
)
```

**Add `ERROR` to the `Face` enum** (before `ACTIVE`):

```kotlin
enum class Face { ERROR, ACTIVE, CAUTION, AMBIENT, DISCONNECTED }
```

**Update `activeFace()`** — ERROR has highest priority:

```kotlin
fun activeFace(isAmbient: Boolean): Face = when {
    lastErrorCode != null -> Face.ERROR
    isAmbient -> Face.AMBIENT
    !isConnected -> Face.DISCONNECTED
    pushbackApproaching -> Face.CAUTION
    else -> Face.ACTIVE
}
```

**Update `empty()`** to include the new field:

```kotlin
fun empty() = WearDashboardUiState(
    // ... existing fields ...
    pushbackApproaching = false,
    isConnected = false,
    lastErrorCode = null,
)
```

**In `WatchPayload.toUiState()`**, populate `lastErrorCode`:

```kotlin
return WearDashboardUiState(
    // ... existing fields ...
    pushbackApproaching = pushbackApproaching,
    isConnected = connected,
    lastErrorCode = lastErrorCode,
)
```

**In `ZWheelWearScreen`**, add the `ERROR` case to the `when`:

```kotlin
when (state.activeFace(isAmbient)) {
    WearDashboardUiState.Face.ERROR -> ErrorFace(state.lastErrorCode!!)
    WearDashboardUiState.Face.ACTIVE -> ActiveFace(state)
    WearDashboardUiState.Face.CAUTION -> CautionFace(state)
    WearDashboardUiState.Face.AMBIENT -> AmbientFace(state)
    WearDashboardUiState.Face.DISCONNECTED -> DisconnectedFace()
}
```

**In `previewPayload()`**, add `lastErrorCode: Int? = null` parameter and include it in
the `WatchPayload(...)` construction.

### 12. `wear/src/main/kotlin/com/zwheel/wear/ui/WearFaces.kt`

Add a new composable at the bottom. Check what `wearSpeedStyle` and `wearLabelStyle` are
defined as (look at how `ActiveFace` and `CautionFace` use them in the same file) and reuse:

```kotlin
@Composable
internal fun ErrorFace(errorCode: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFB00020)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "BOARD ERROR",
                style = wearLabelStyle,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ERR $errorCode",
                style = wearSpeedStyle,
                color = Color.White,
            )
        }
    }
}
```

Add import: `import androidx.compose.ui.graphics.Color` (if not already present).

### 13. Fix test files

**`app/src/test/kotlin/com/zwheel/app/wear/WearPayloadEncodeTest.kt`**

Any `WatchPayload(...)` construction: the new `lastErrorCode` field has a default of `null`,
so named-argument calls will compile unchanged. Check if positional calls exist and add
`null` as the last argument if so.

**`wear/src/test/kotlin/com/zwheel/wear/WearPayloadDecodeTest.kt`**

The `decodeWatchPayload(...)` function now has a new last parameter `lastErrorCodeRaw: Int`.
Find all call sites in this test file and add `lastErrorCodeRaw = -1` as the last named argument.

---

## Compile check

```
gradle :core:compileDebugKotlin :app:compileDebugKotlin :wear:compileDebugKotlin :app:check :wear:check
```

Must pass with zero errors and zero test failures.

---

## Commit

```
feat(safety): last error code overlay on dashboard and watch
```

No Co-Authored-By line.
