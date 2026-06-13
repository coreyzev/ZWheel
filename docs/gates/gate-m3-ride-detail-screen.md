# Gate: M3 — Ride Detail Screen

**Branch:** `codex/m3-ride-detail-screen`
**One concern:** Tap a ride in the history list → full detail screen showing all session metrics.

---

## Problem

`RideHistoryScreen` shows a list of rides but tapping one does nothing (cards are not clickable). `RideSession` holds distance, duration, top speed, and board ID, but users can't see them in detail. `getSession(id)` exists in the DAO but not in the repository.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/data/ride/RideRepository.kt         ← add getSession()
app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailViewModel.kt   ← new
app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailScreen.kt      ← new
app/src/main/kotlin/com/zwheel/app/ui/history/RideHistoryScreen.kt     ← add onRideClick param + clickable cards
app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt               ← add rideDetail/{sessionId} route
```

---

## Implementation spec

### 1. `RideRepository.kt` — add `getSession`

```kotlin
suspend fun getSession(id: String): RideSession? = dao.getSession(id)?.toModel()
```

The `dao.getSession(id)` method already exists.

### 2. `RideDetailViewModel.kt` (new)

```kotlin
package com.zwheel.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.data.ride.RideRepository
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.core.calc.UnitConversions
import com.zwheel.core.model.RideSession
import com.zwheel.core.model.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RideDetailUiState(
    val dateLabel: String,
    val boardId: String,
    val durationLabel: String,
    val distanceLabel: String,
    val topSpeedLabel: String,
    val avgSpeedLabel: String,
)

@HiltViewModel
class RideDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RideRepository,
    private val prefs: SettingsRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow<RideDetailUiState?>(null)
    val state: StateFlow<RideDetailUiState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = repository.getSession(sessionId) ?: return@launch
            val speedUnit = prefs.preferences.first().speedUnit
            _state.value = session.toUiState(speedUnit)
        }
    }

    private fun RideSession.toUiState(speedUnit: SpeedUnit): RideDetailUiState {
        val isMph = speedUnit == SpeedUnit.MPH
        val distanceLabel = if (isMph) {
            "%.2f mi".format(UnitConversions.metersToMiles(distanceMetersCorrected))
        } else {
            "%.2f km".format(UnitConversions.metersToKilometers(distanceMetersCorrected))
        }
        val topSpeedLabel = if (isMph) {
            "%.1f mph".format(UnitConversions.metersPerSecondToMph(maxSpeedMetersPerSecondCorrected))
        } else {
            "%.1f kph".format(maxSpeedMetersPerSecondCorrected * 3.6)
        }
        val durationMillis = (endEpochMillis ?: startEpochMillis) - startEpochMillis
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
        val durationLabel = "%d:%02d".format(minutes, seconds)

        // Average speed = total distance / total duration (only meaningful if > 0)
        val avgSpeedMps = if (durationMillis > 0) {
            distanceMetersCorrected / (durationMillis / 1000.0)
        } else {
            0.0
        }
        val avgSpeedLabel = if (isMph) {
            "%.1f mph".format(UnitConversions.metersPerSecondToMph(avgSpeedMps))
        } else {
            "%.1f kph".format(avgSpeedMps * 3.6)
        }
        return RideDetailUiState(
            dateLabel = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
                .format(Date(startEpochMillis)),
            boardId = boardId,
            durationLabel = durationLabel,
            distanceLabel = distanceLabel,
            topSpeedLabel = topSpeedLabel,
            avgSpeedLabel = avgSpeedLabel,
        )
    }
}
```

### 3. `RideDetailScreen.kt` (new)

Simple scrollable column showing all metrics:

```kotlin
package com.zwheel.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RideDetailScreen(
    viewModel: RideDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Back button
        TextButton(onClick = onBack) {
            Text("← Back", color = Color(0xff00d8ff), fontSize = 14.sp)
        }

        if (state == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val s = state!!

        Text(
            text = s.dateLabel,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Text(
            text = "Board: ${s.boardId}",
            fontSize = 13.sp,
            color = Color(0xffbbbbbb),
        )

        HorizontalDivider(color = Color(0xff333333))

        DetailRow(label = "Distance", value = s.distanceLabel)
        DetailRow(label = "Duration", value = s.durationLabel)
        DetailRow(label = "Top Speed", value = s.topSpeedLabel)
        DetailRow(label = "Avg Speed", value = s.avgSpeedLabel)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.sp, color = Color(0xffbbbbbb))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    }
}
```

Import needed: `import androidx.lifecycle.compose.collectAsStateWithLifecycle`

### 4. `RideHistoryScreen.kt` — add `onRideClick` parameter

Update `RideHistoryScreen` to accept a callback and pass it to cards:

```kotlin
@Composable
fun RideHistoryScreen(
    viewModel: RideHistoryViewModel = hiltViewModel(),
    onRideClick: (sessionId: String) -> Unit = {},
) {
    // ... existing content ...
    items(sessions, key = { it.id }) { item ->
        RideHistoryCard(item, onClick = { onRideClick(item.id) })
    }
}
```

Update `RideHistoryCard` to accept and use the callback:
```kotlin
@Composable
private fun RideHistoryCard(item: RideHistoryItem, onClick: () -> Unit = {}) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },  // ADD THIS
        // ... rest unchanged ...
    )
}
```

Add import: `import androidx.compose.foundation.clickable`

### 5. `ZWheelAppScreen.kt` — add rideDetail route

In the `NavHost` block, after the `"history"` composable:

```kotlin
composable("history") {
    RideHistoryScreen(
        onRideClick = { sessionId -> navController.navigate("rideDetail/$sessionId") }
    )
}
composable("rideDetail/{sessionId}") {
    RideDetailScreen(onBack = { navController.popBackStack() })
}
```

Add the import: `import com.zwheel.app.ui.history.RideDetailScreen`

---

## Constraints

- `core/` is untouched.
- Do not add a Room query for data points — compute average speed from session metrics only
  (distance / duration). This avoids loading potentially thousands of data points.
- `HorizontalDivider` is the Material3 API (not `Divider`).
- The `collectAsStateWithLifecycle` import is `androidx.lifecycle.compose.collectAsStateWithLifecycle`.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin --no-daemon
```

Must pass.
