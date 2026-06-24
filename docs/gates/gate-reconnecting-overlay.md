# Gate: Reconnecting Overlay

## Purpose

Show a "RECONNECTING" overlay on the dashboard when:
1. **Hard BLE drop**: `connectionState` transitions to `Connecting` after a prior successful `Connected` (board dropped, service is auto-reconnecting via `observeUnexpectedDisconnect()`).
2. **Zero burst**: Board telemetry goes all-zero while `connectionState` stays `Connected` — packVoltage drops to 0.0 for ≥ 3 s after having been > 0.

Currently in both cases the UI shows nothing changing, which is confusing to the rider.

---

## Files to create

### `app/src/main/kotlin/com/zwheel/app/ui/dashboard/ReconnectingOverlay.kt`

New file. Full content:

```kotlin
package com.zwheel.app.ui.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

@Composable
fun ReconnectingOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "reconnecting")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "RECONNECTING",
            color = Color.White.copy(alpha = alpha),
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp,
            letterSpacing = 4.sp,
        )
    }
}
```

---

## Files to modify

### 1. `app/src/main/kotlin/com/zwheel/app/ble/ConnectionManager.kt`

Add stale-telemetry detection. Insert after the existing `_rssi` declaration (around line 59):

```kotlin
private val _staleTelemetry = MutableStateFlow(false)
val staleTelemetry: StateFlow<Boolean> = _staleTelemetry.asStateFlow()
private var staleTelemetryJob: Job? = null
```

At the **top** of `connect()` (before `connectJob?.cancelAndJoin()`), reset the flag:

```kotlin
_staleTelemetry.value = false
staleTelemetryJob?.cancel()
```

After `service.start(scope)` (just before `stateMirrorJob = scope.launch { ... }`), start the watcher:

```kotlin
startStaleTelemetryWatcher()
```

In `disconnect()`, cancel and reset:

```kotlin
staleTelemetryJob?.cancel()
staleTelemetryJob = null
_staleTelemetry.value = false
```
(Add these two lines alongside the existing `stateMirrorJob?.cancel()` block.)

Add the private helper function at the bottom of the class (before the closing `}`):

```kotlin
private fun startStaleTelemetryWatcher() {
    staleTelemetryJob?.cancel()
    staleTelemetryJob = scope.launch {
        var hadNonZeroVoltage = false
        var pendingStaleJob: Job? = null
        _boardState.collect { state ->
            if (connectionState.value != ConnectionState.Connected) return@collect
            if (state.packVoltage > 0.0) {
                hadNonZeroVoltage = true
                pendingStaleJob?.cancel()
                pendingStaleJob = null
                _staleTelemetry.value = false
            } else if (hadNonZeroVoltage && pendingStaleJob?.isActive != true) {
                pendingStaleJob = launch {
                    delay(3_000L)
                    _staleTelemetry.value = true
                }
            }
        }
    }
}
```

Add `delay` to the import list if not already present (it is — `kotlinx.coroutines.delay`).

### 2. `app/src/main/kotlin/com/zwheel/app/ui/DashboardViewModel.kt`

Add one line after `val connectionState`:

```kotlin
val staleTelemetry: StateFlow<Boolean> = connectionManager.staleTelemetry
```

### 3. `app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt`

**In `ZWheelAppScreen` composable** (around line 137–139 where state is collected):

Add after `val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()`:

```kotlin
val staleTelemetry by viewModel.staleTelemetry.collectAsStateWithLifecycle()
```

**In the `composable("ride")` block** (around line 185), add `staleTelemetry` to `RideTabContent` call:

```kotlin
RideTabContent(
    ...
    staleTelemetry = staleTelemetry,
    ...
)
```

**In `RideTabContent` function signature** (around line 313), add parameter:

```kotlin
staleTelemetry: Boolean,
```

**Inside `RideTabContent`**, replace the existing routing block:

```kotlin
// OLD:
} else if (connectionState != ConnectionState.Connected) {
    ConnectScreen(...)
} else {
    DashboardScreen(...)
}

// NEW:
} else {
    var hadSuccessfulConnection by remember { mutableStateOf(false) }
    // Track whether we've ever reached Connected in this session (resets on explicit disconnect/scan).
    if (connectionState == ConnectionState.Connected) hadSuccessfulConnection = true
    if (connectionState == ConnectionState.Idle || connectionState == ConnectionState.Scanning) {
        hadSuccessfulConnection = false
    }

    val isReconnecting = staleTelemetry ||
        (connectionState == ConnectionState.Connecting && hadSuccessfulConnection)

    if (!isReconnecting && connectionState != ConnectionState.Connected) {
        ConnectScreen(
            connectionState = connectionState,
            devices = devices,
            savedBoardDeviceId = savedBoardDeviceId,
            savedBoardType = savedBoardType,
            onScan = onScan,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )
    } else {
        DashboardScreen(
            state = state,
            onRequestLocation = onRequestLocation,
            locationGranted = locationGranted,
            locationPermanentlyDenied = locationPermanentlyDenied,
            isReconnecting = isReconnecting,
        )
    }
}
```

Also add `mutableStateOf` to the import if not present — check `import androidx.compose.runtime.mutableStateOf` and `import androidx.compose.runtime.setValue`.

### 4. `app/src/main/kotlin/com/zwheel/app/ui/dashboard/DashboardScreen.kt`

**Add `isReconnecting` parameter** to the function signature:

```kotlin
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onRequestLocation: () -> Unit = {},
    locationGranted: Boolean = true,
    locationPermanentlyDenied: Boolean = false,
    isReconnecting: Boolean = false,
)
```

**Add overlay** inside the outer `Box`, after the `LazyColumn` closing `}` and before the `Box` closing `}`:

```kotlin
if (isReconnecting) {
    ReconnectingOverlay()
}
```

Add the import:
```kotlin
import com.zwheel.app.ui.dashboard.ReconnectingOverlay
```
(Same package, so the import may be implicit — but include it to be safe.)

---

## Compile check

Run:
```
gradle :app:compileDebugKotlin
```

Must pass with zero errors. Fix any import or signature issues before committing.

---

## Commit

Single commit on the current branch:
```
feat(ble): reconnecting overlay on dashboard during BLE drop and zero burst
```

No Co-Authored-By line.
