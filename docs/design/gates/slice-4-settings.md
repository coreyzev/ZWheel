# Gate: Slice 4 — Settings Screen (Dark Redesign)

You are implementing the Settings screen of the ZWheel dark instrument-cluster redesign, plus the
cross-cutting "custom board name" feature that propagates the user's chosen name everywhere a board
name is displayed. Read ONLY this file and `docs/design/spec.md`. Do not read any other gate files.

This slice is **app module only**. Do NOT touch anything under `core/` — it must stay Android-free.
Do not add new BLE UUIDs. Do not make network calls in `app/main` sources. The custom board name
is LOCAL ONLY — it must NEVER be written to the board.

Prior slices have already installed:
- `ZWheelColors` data class + `ZWheelDarkColors` singleton + `LocalZWheelColors` CompositionLocal
- `ZWheelColors.ramp(fraction: Float)` helper
- `SairaFamily` / `JetBrainsMonoFamily` + `Typography` in `Type.kt`
- Re-skinned `DashboardCard`, `Label`, `Metric`, `SmallStat` in `DashboardComponents.kt`
- Dark `DashboardScreen` and sub-composables under `ui/dashboard/`
- Nav shell `ZWheelAppScreen.kt` with bottom tab bar (Ride / History / Settings)

**Use these; do not redefine them.** Access colors via `val c = LocalZWheelColors.current`.

---

## Scope (do all of these, in order)

### 1. DataStore plumbing — `customBoardName` field

**File: `app/src/main/kotlin/com/zwheel/app/data/settings/UserPreferences.kt`**

Add one field to the `UserPreferences` data class (with a default so all existing call-sites
compile without changes):

```kotlin
data class UserPreferences(
    val speedUnit: SpeedUnit = SpeedUnit.MPH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val tireDiameterInches: Double = 11.5,
    val hasCustomTireDiameter: Boolean = false,
    val lastConnectedDeviceId: String? = null,
    val hasRequestedBatteryOptimization: Boolean = false,
    val hasAttemptedLocationPermission: Boolean = false,
    val haUrl: String = "",
    val haToken: String = "",
    val customBoardName: String? = null,   // NEW — null means "use board's own name"
)
```

**File: `app/src/main/kotlin/com/zwheel/app/data/settings/SettingsRepository.kt`**

Add a DataStore key and setter following the exact same pattern as `saveLastConnectedDeviceId`:

```kotlin
// In the companion object, alongside the other keys:
val CUSTOM_BOARD_NAME = stringPreferencesKey("custom_board_name")
```

In the `preferences` flow mapping block, add:

```kotlin
customBoardName = prefs[CUSTOM_BOARD_NAME]?.takeIf { it.isNotBlank() },
```

Add the setter:

```kotlin
suspend fun setCustomBoardName(name: String?) {
    dataStore.edit { preferences ->
        if (name != null && name.isNotBlank()) preferences[CUSTOM_BOARD_NAME] = name.trim()
        else preferences.remove(CUSTOM_BOARD_NAME)
    }
}
```

Passing `null` or blank clears the override (the board's own name is used as fallback).

---

### 2. Custom board name propagation — every display site

The resolved display name formula (use this everywhere):

```kotlin
fun resolvedBoardName(customName: String?, identityName: String?): String =
    customName?.takeIf { it.isNotBlank() } ?: identityName ?: "Onewheel"
```

Define this as a top-level or extension function in `DashboardState.kt` (private to the file is
fine — each site that needs it imports or duplicates the one-liner).

#### Site A — `DashboardState.kt` › `BoardState.toDashboardUiState(...)`

`toDashboardUiState` already receives a `prefs: UserPreferences` argument. Change the `boardName`
line:

```kotlin
// Before:
boardName = identity?.name ?: "Onewheel",
// After:
boardName = resolvedBoardName(prefs.customBoardName, identity?.name),
```

No other changes to this function. `DashboardViewModel` already combines `settingsRepository.preferences`
in its `combine(...)` block, so `prefs.customBoardName` flows through automatically whenever it changes
in DataStore.

#### Site B — `ui/dashboard/BoardHeader.kt` (or wherever `DashboardUiState.boardName` is rendered)

`DashboardUiState.boardName` is already the resolved name after Site A's change — no additional
change required in `BoardHeader.kt`. Confirm `boardName` is displayed with `maxLines = 1` +
`TextOverflow.Ellipsis` (it already is per Slice 1).

#### Site C — `ui/history/RideHistoryViewModel.kt` › `RideHistoryItem`

The history list currently shows `item.dateLabel` and stats but NOT the board name in the current
implementation. Per spec §5, the board name must appear on its own line in the ride row (mono 10sp,
`textDimmest`, ellipsis). The board name stored per session is `RideSession.boardId` (the raw device
ID, not a friendly name). The custom name is keyed to the currently connected board, not per
session — so for history, pass through `prefs.customBoardName` alongside the board ID and let the UI
display `customBoardName ?: boardId` (truncating to a reasonable display label).

Add `boardName: String` to `RideHistoryItem`:

```kotlin
data class RideHistoryItem(
    val id: String,
    val dateLabel: String,
    val durationLabel: String,
    val distanceLabel: String,
    val topSpeedLabel: String,
    val boardName: String,     // NEW — resolved display name for this session
    val hasGps: Boolean = false,
)
```

In `RideHistoryViewModel.sessions` combine block (which already receives `prefs`), populate it:

```kotlin
boardName = prefs.customBoardName?.takeIf { it.isNotBlank() } ?: session.boardId,
```

`RideHistoryViewModel` already injects `SettingsRepository` as `prefs` and already calls
`combine(repository.getAllSessions(), prefs.preferences)` — add no new dependency.

In `RideHistoryScreen`, render `item.boardName` in the card (see §4C in this gate for the exact
styling).

#### Site D — `ui/history/RideDetailUiState` and `RideDetailViewModel`

`RideDetailUiState` currently has `boardId: String` showing the raw device ID. Rename or supplement:

```kotlin
data class RideDetailUiState(
    // ... existing fields ...
    val boardName: String,   // resolved display name (was boardId; keep boardId if it's still used elsewhere)
    // ...
)
```

In `RideDetailViewModel`, `prefs` is already injected as `SettingsRepository`. In the
`viewModelScope.launch` block, fetch `prefs.preferences.first()` (it already does this for `speedUnit`)
— add `customBoardName` read from the same fetch:

```kotlin
val prefs = prefs.preferences.first()
val speedUnit = prefs.speedUnit
val resolvedName = prefs.customBoardName?.takeIf { it.isNotBlank() } ?: session.boardId
```

Then in `session.toUiState(...)` pass `resolvedName` and assign `boardName = resolvedName`.

`RideDetailScreen` currently renders `"Board: ${s.boardId}"`. Change it to render `s.boardName`
using the spec §7 style: a full-width card at the bottom of the detail (label `BOARD`, value =
`s.boardName` in Saira 16sp W700, ellipsis on overflow).

#### Site E — Settings card itself

The Settings screen renders the board name inline (see §3A). It reads from `SettingsViewModel`
which already exposes `preferences: StateFlow<UserPreferences>`. The Settings card shows
`prefs.customBoardName ?: boardState.identity?.name ?: "Not connected"`. Inline-edit commits via
`viewModel.setCustomBoardName(name)` (see §3 below for ViewModel addition).

#### Summary of files touched for propagation

| File | Change |
|---|---|
| `UserPreferences.kt` | Add `customBoardName: String? = null` |
| `SettingsRepository.kt` | Add `CUSTOM_BOARD_NAME` key + `setCustomBoardName()` |
| `DashboardState.kt` | `boardName` assignment uses `resolvedBoardName(prefs.customBoardName, identity?.name)` |
| `RideHistoryViewModel.kt` | `RideHistoryItem` gains `boardName`; populated from `prefs.customBoardName ?: session.boardId` |
| `RideHistoryScreen.kt` | Renders `item.boardName` (mono 10sp, `textDimmest`, ellipsis) |
| `RideDetailViewModel.kt` | `RideDetailUiState.boardName` resolved from `prefs.customBoardName ?: session.boardId` |
| `RideDetailScreen.kt` | Renders `boardName` in a bottom BOARD card (spec §7) |

`DashboardViewModel.kt`, `BoardHeader.kt` require NO changes — they already flow through the
resolved name automatically.

---

### 3. `SettingsViewModel` additions

**File: `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsViewModel.kt`**

The SettingsViewModel already injects `SettingsRepository` as `repo`. Add two new functions:

```kotlin
fun setCustomBoardName(name: String?) {
    viewModelScope.launch {
        repo.setCustomBoardName(name)
    }
}

fun forgetBoard() {
    viewModelScope.launch {
        repo.saveLastConnectedDeviceId(null)   // clears LAST_DEVICE_ID from DataStore
        // Note: disconnect must be called separately by the caller
    }
}
```

`SettingsViewModel` does NOT have a `ConnectionManager` or `RideServiceController` injected. The
"Forget board" action in the UI must therefore be wired as two sequential calls from the composable:
`onDisconnect()` (passed in as a lambda from `ZWheelAppScreen`, which already calls
`viewModel::disconnect` on `DashboardViewModel`) then `settingsViewModel.forgetBoard()`. Thread both
as a combined `onForgetBoard` lambda at the `ZWheelAppScreen` call-site — see §6.

The `SettingsViewModel.preferences` flow already exposes `customBoardName` because it collects the
full `UserPreferences` from `repo.preferences`.

Also add a live board state reference so the Settings card can show the current board identity for
DEVICE INFO. `SettingsViewModel` does NOT currently inject `ConnectionManager`. Add the injection:

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val connectionManager: ConnectionManager,    // NEW
) : ViewModel() {
    val boardState: StateFlow<BoardState> = connectionManager.boardState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BoardState(),
    )
    // ... rest unchanged
}
```

Import `com.zwheel.app.ble.ConnectionManager` and `com.zwheel.core.model.BoardState`.
`ConnectionManager` is already `@Singleton` and injectable (see `DashboardViewModel`).

---

### 4. New composable files under `app/src/main/kotlin/com/zwheel/app/ui/settings/`

Create four files. Stay under ~300 lines each. The existing `SettingsScreen.kt` will be
**replaced in-place** (rewrite the whole file — it is currently a placeholder light-theme UI).

#### 4A. `settings/ConnectedBoardCard.kt`

```kotlin
package com.zwheel.app.ui.settings
```

**Composable:** `internal fun ConnectedBoardCard(boardState: BoardState, customBoardName: String?, onSaveName: (String?) -> Unit, onDisconnect: () -> Unit, onForgetBoard: () -> Unit, modifier: Modifier = Modifier)`

The resolved display name:

```kotlin
val displayName = customBoardName?.takeIf { it.isNotBlank() }
    ?: boardState.identity?.name
    ?: "Not connected"
```

**Name row (inline edit):**

Local state:

```kotlin
var editing by remember { mutableStateOf(false) }
var editText by remember(displayName) { mutableStateOf(displayName) }
```

When NOT editing:

```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    // Glowing status dot
    Box(
        modifier = Modifier
            .size(8.dp)
            .drawBehind { drawCircle(c.rampGood.copy(alpha = 0.35f), radius = 14.dp.toPx()) }
            .background(c.rampGood, CircleShape)
    )
    Spacer(Modifier.width(8.dp))
    Text(
        text = displayName,
        style = TextStyle(fontFamily = SairaFamily, fontSize = 16.sp, fontWeight = FontWeight.W700),
        color = c.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .weight(1f)
            .clickable { editing = true },
    )
    Spacer(Modifier.width(8.dp))
    Icon(
        imageVector = Icons.Filled.Edit,
        contentDescription = "Edit board name",
        tint = c.textMuted,
        modifier = Modifier.size(16.dp).clickable { editing = true },
    )
}
```

When editing:

```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    BasicTextField(
        value = editText,
        onValueChange = { if (it.length <= 24) editText = it },
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = SairaFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = c.textPrimary,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onSaveName(editText.trim().ifBlank { null })
            editing = false
        }),
        modifier = Modifier.weight(1f),
        cursorBrush = SolidColor(c.lime),
    )
    Spacer(Modifier.width(8.dp))
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = "Save board name",
        tint = c.lime,
        modifier = Modifier.size(20.dp).clickable {
            onSaveName(editText.trim().ifBlank { null })
            editing = false
        },
    )
}
```

On focus-lost (blur) cancel without saving: wrap the `BasicTextField` in a
`Box(Modifier.onFocusChanged { if (!it.isFocused && editing) editing = false })`.

**Secondary info row** (always visible, below the name row):

Use `FlowRow` from `androidx.compose.foundation.layout.FlowRow` (Foundation 1.7+, already on
classpath via Compose BOM used in prior slices — if not available, fall back to a `Row` with
`Modifier.horizontalScroll`; but prefer `FlowRow`):

```kotlin
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.padding(top = 6.dp),
) {
    // Board-type badge — same badge composable as BoardHeader
    val boardType = boardState.identity?.type ?: BoardType.UNKNOWN
    if (boardType != BoardType.UNKNOWN) {
        BoardTypeBadge(boardType)
    }
    // RSSI
    val rssiText = boardState.rssi?.let { "$it dBm" }  // rssi is NOT on BoardState in core
    // NOTE: RSSI is not a field on BoardState (see core/model/BoardModels.kt).
    // Pass rssi separately as a parameter: add `rssi: Int?` to ConnectedBoardCard.
    if (rssi != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NetworkWifi3Bar, contentDescription = "Signal", tint = c.textSecondary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text("$rssi dBm", style = mono10, color = c.textSecondary)
        }
    }
    // Firmware + pack size
    val fw = boardState.identity?.firmwareRevision
    val cells = boardState.cellVoltages.size.takeIf { it > 0 }
    if (fw != null || cells != null) {
        val label = buildString {
            if (fw != null) append("Fw $fw")
            if (fw != null && cells != null) append(" · ")
            if (cells != null) append("${cells}S")
        }
        Text(label, style = mono10, color = c.textSecondary)
    }
}
```

`mono10` local val: `TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, fontFeatureSettings = "tnum")`.

**Board-type badge helper** (private, reuse pattern from `BoardHeader.kt`):

```kotlin
@Composable
private fun BoardTypeBadge(type: BoardType) {
    val label = when (type) {
        BoardType.PINT_X      -> "PINT X"
        BoardType.XR          -> "XR"
        BoardType.PINT        -> "PINT"
        BoardType.PLUS        -> "PLUS"
        BoardType.ONEWHEEL_V1 -> "OW V1"
        BoardType.UNKNOWN     -> return
    }
    Surface(
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, c.borderLime),
        color = Color.Transparent,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, fontWeight = FontWeight.W700),
            color = c.lime,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
```

**Action buttons row:**

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    TextButton(
        onClick = onDisconnect,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.textButtonColors(contentColor = c.rampDanger),
    ) {
        Text("Disconnect", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 14.sp)
    }
    TextButton(
        onClick = onForgetBoard,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.textButtonColors(contentColor = c.textSecondary),
    ) {
        Text("Forget board", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 14.sp)
    }
}
```

**IMPORTANT on RSSI:** `BoardState` in `core/` does NOT have an `rssi` field (RSSI is tracked
separately in `ConnectionManager.rssi: StateFlow<Int?>` and combined into `DashboardUiState.rssi`
in `DashboardViewModel`). `SettingsViewModel` must expose RSSI separately. After adding
`connectionManager` injection in §3, also expose:

```kotlin
val rssi: StateFlow<Int?> = connectionManager.rssi.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = null,
)
```

Then `SettingsScreen` collects both `viewModel.boardState` and `viewModel.rssi` and passes them
into `ConnectedBoardCard`.

---

#### 4B. `settings/DeviceInfoDisclosure.kt`

```kotlin
package com.zwheel.app.ui.settings
```

**Composable:** `internal fun DeviceInfoDisclosure(identity: BoardIdentity?, rssi: Int?, modifier: Modifier = Modifier)`

Local state:

```kotlin
var expanded by remember { mutableStateOf(false) }
val chevronRotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = tween(durationMillis = 220),
)
```

**Disclosure header row** (always visible, tappable):

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(
        "DEVICE INFO",
        style = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.W400,
            letterSpacing = 2.sp,
        ),
        color = c.textDimmest,
    )
    Icon(
        Icons.Filled.ExpandMore,
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = c.textMuted,
        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronRotation },
    )
}
```

**Expanded panel** (animated):

```kotlin
AnimatedVisibility(visible = expanded) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))   // chip token
            .background(c.insetRow),           // #0E1014
    ) {
        DeviceInfoRow("Serial", identity?.serialNumber ?: "—")
        DeviceInfoRow("Battery serial", null)  // NOTE: see below
        DeviceInfoRow("Hardware rev", identity?.hardwareRevision ?: "—")
        DeviceInfoRow("Firmware", identity?.firmwareRevision ?: "—")
        DeviceInfoRow("RSSI", rssi?.let { "$it dBm" } ?: "—")
    }
}
```

**Battery serial note:** `BoardIdentity` in `core/model/BoardModels.kt` does NOT have a
`batterySerialNumber` field. The golden screenshot (Image 3) shows "Battery serial 22136" — this
comes from a BLE characteristic (`OwUuids.BATTERY_SERIAL`, documented in
`core/src/main/kotlin/com/zwheel/core/protocol/OwUuids.kt`) that is NOT yet surfaced in
`BoardIdentity`. **Do not add it to `core/BoardIdentity`** (that would require modifying `core/`).
Instead, render the row with a placeholder `"—"` and add a TODO comment:

```kotlin
// TODO(battery-serial): BoardIdentity does not yet carry batterySerialNumber.
// Wire OwUuids.BATTERY_SERIAL through the BLE handshake and BoardIdentity in a future gate.
DeviceInfoRow("Battery serial", "—")
```

**Private row helper:**

```kotlin
@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = SairaFamily, fontSize = 13.sp, fontWeight = FontWeight.W400),
            color = c.textMuted,
        )
        Text(
            value,
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.W700,
                fontFeatureSettings = "tnum",
            ),
            color = c.textPrimary,
        )
    }
}
```

Add a `HorizontalDivider(color = c.divider, thickness = 0.5.dp)` between rows (but not after the
last one). Use `forEachIndexed` on a list of pairs to insert dividers.

---

#### 4C. `settings/SettingsSections.kt`

This file holds all sections below CONNECTED BOARD. All are `internal` composables.

**Section eyebrow label helper** (private to file):

```kotlin
@Composable
private fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.W400,
            letterSpacing = 2.sp,
        ),
        color = c.textDimmest,
        modifier = modifier,
    )
}
```

**`UnitsSection`** — `internal fun UnitsSection(prefs: UserPreferences, onSpeedUnit: (SpeedUnit) -> Unit, onTempUnit: (TemperatureUnit) -> Unit, modifier: Modifier = Modifier)`:

```kotlin
Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SectionEyebrow("UNITS")
    // Speed toggle
    SegmentedToggle(
        options = listOf(SpeedUnit.MPH to "MPH", SpeedUnit.KPH to "KPH"),
        selected = prefs.speedUnit,
        onSelected = onSpeedUnit,
    )
    // Temperature toggle
    SegmentedToggle(
        options = listOf(TemperatureUnit.FAHRENHEIT to "°F", TemperatureUnit.CELSIUS to "°C"),
        selected = prefs.temperatureUnit,
        onSelected = onTempUnit,
    )
}
```

**`SegmentedToggle`** generic helper (private to file):

```kotlin
@Composable
private fun <T> SegmentedToggle(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))     // pill token
            .border(1.dp, c.buttonBorder, RoundedCornerShape(999.dp)),
    ) {
        options.forEach { (option, label) ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) c.lime else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W700,
                    ),
                    color = if (isSelected) c.screenBg else c.textSecondary,   // dark text on lime
                )
            }
        }
    }
}
```

**`TireCalibrationSection`** — `internal fun TireCalibrationSection(prefs: UserPreferences, onDiameterChanged: (Double) -> Unit, modifier: Modifier = Modifier)`:

```kotlin
Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SectionEyebrow("TIRE CALIBRATION")
    Text(
        text = "Adjust if your speed or distance readings are off. The corrected value is applied to all speed calculations.",
        style = TextStyle(fontFamily = SairaFamily, fontSize = 13.sp, fontWeight = FontWeight.W400),
        color = c.textMuted,
        lineHeight = 19.sp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Tire diameter",
            style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W600),
            color = c.textSecondary,
        )
        Text(
            "%.1f in".format(prefs.tireDiameterInches),
            style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 14.sp, fontWeight = FontWeight.W700, fontFeatureSettings = "tnum"),
            color = c.lime,
        )
    }
    // Lime-accented Slider
    Slider(
        value = prefs.tireDiameterInches.toFloat(),
        onValueChange = { onDiameterChanged(it.toDouble()) },
        valueRange = 8f..16f,
        colors = SliderDefaults.colors(
            thumbColor = c.lime,
            activeTrackColor = c.lime,
            inactiveTrackColor = c.border,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("8 in", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp), color = c.textDim)
        Text("16 in", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp), color = c.textDim)
    }
    // Glow effect on knob: approximate by drawing a lime radial behind the thumb using
    // Modifier.drawBehind on the Slider container; exact approach depends on Compose Slider version.
    // As a simpler fallback, wrap the Slider in a Box with a centered radial gradient overlay at
    // the thumb x-position — optional polish, not required for build to pass.
}
```

**`HomeAssistantSection`** — `internal fun HomeAssistantSection(haUrl: String, haToken: String, haTestResult: HaPushResult?, onUrlChanged: (String) -> Unit, onTokenChanged: (String) -> Unit, onTestConnection: () -> Unit, modifier: Modifier = Modifier)`:

Restyle the EXISTING HA controls. Do NOT change behavior or remove any existing logic. Replace the
current `OutlinedTextField` / `Button` styling with dark-theme equivalents:

- `OutlinedTextField` → keep as is but supply dark colors via `OutlinedTextFieldDefaults.colors(...)`:
  `focusedBorderColor = c.lime`, `unfocusedBorderColor = c.buttonBorder`,
  `cursorColor = c.lime`, `focusedLabelColor = c.textSecondary`,
  `unfocusedLabelColor = c.textLabel`, `textColor = c.textPrimary`.
- The HTTP warning callout: `Surface(color = c.cardElevated, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, c.rampDanger.copy(alpha = 0.4f)))` wrapping the warning text.
- HA test result: map `HaPushResult.Success → c.rampGood`, `AuthFailed → c.rampDanger`,
  `Unreachable → c.rampCaution`, `BadUrl → c.rampDanger`.
- "Test connection" button: `Button(colors = ButtonDefaults.buttonColors(containerColor = c.cardElevated, contentColor = c.lime))`.
- "Push battery % to HA" toggle (spec §9): a lime `Switch` row at the top of the section using
  `SwitchDefaults.colors(checkedThumbColor = c.lime, checkedTrackColor = c.lime.copy(alpha = 0.3f),
  uncheckedTrackColor = c.buttonBorder)`. This toggle controls whether HA push is active — it maps
  to `prefs.haUrl.isNotBlank() && prefs.haToken.isNotBlank()` as a read-only derived state (HA is
  "on" when both fields are filled). No new boolean preference is needed; the toggle just opens/collapses
  the URL+token fields when tapped. When the toggle is turned off via the UI, clear both `haUrl` and
  `haToken` (call both `onUrlChanged("")` and `onTokenChanged("")`).

**`DeveloperSection`** — `internal fun DeveloperSection(modifier: Modifier = Modifier, onOpenBleDebug: () -> Unit)`:

```kotlin
Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionEyebrow("DEVELOPER")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "BLE debug view",
            style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W600),
            color = c.textSecondary,
        )
        Switch(
            checked = false,   // static off in the settings panel; navigates to BleDebugScreen on tap
            onCheckedChange = { if (it) onOpenBleDebug() },
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = c.buttonBorder,
                uncheckedThumbColor = c.textMuted,
                checkedTrackColor = c.lime.copy(alpha = 0.3f),
                checkedThumbColor = c.lime,
            ),
        )
    }
}
```

The BLE debug screen is a separate screen navigated to — the toggle acts as a launch shortcut, not
a persistent on/off state stored in preferences. `onOpenBleDebug` is wired in `ZWheelAppScreen`
to navigate to the BLE debug route.

**`SupportSection`** — `internal fun SupportSection(modifier: Modifier = Modifier)`:

```kotlin
Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionEyebrow("SUPPORT")
    Text(
        "ZWheel is free and open source. If you enjoy it, consider buying the developer a coffee.",
        style = TextStyle(fontFamily = SairaFamily, fontSize = 13.sp, fontWeight = FontWeight.W400),
        color = c.textMuted,
        lineHeight = 19.sp,
    )
    // Donate button (no-op for now; link to be wired in a later gate)
    OutlinedButton(
        onClick = { /* TODO: open donation URL */ },
        border = BorderStroke(1.dp, c.buttonBorder),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
    ) {
        Text("Support development", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 13.sp)
    }
}
```

**`AboutSection`** — `internal fun AboutSection(modifier: Modifier = Modifier)`:

```kotlin
Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionEyebrow("ABOUT")
    Text(
        "ZWheel does not collect, store, or transmit any personal data. All ride data stays on your device. " +
        "BLE telemetry is read-only — the app never writes to your board.",
        style = TextStyle(fontFamily = SairaFamily, fontSize = 13.sp, fontWeight = FontWeight.W400),
        color = c.textMuted,
        lineHeight = 19.sp,
    )
}
```

**`SettingsFooter`** — `internal fun SettingsFooter(modifier: Modifier = Modifier)`:

```kotlin
Box(modifier = modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
    Text(
        text = "ZWheel · v${BuildConfig.VERSION_NAME}",
        style = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.W400,
        ),
        color = Color(0xFF3A3E48),
    )
}
```

Import `com.zwheel.app.BuildConfig`. The app version comes from `BuildConfig.VERSION_NAME` which is
set to `System.getenv("VERSION_NAME") ?: "0.1.0-dev"` in `app/build.gradle.kts`. This is board-
independent; do NOT show firmware version here.

---

#### 4D. `settings/SettingsScreen.kt` (rewrite in-place)

**Rewrite the entire file.** Preserve the package declaration. The new structure:

```kotlin
package com.zwheel.app.ui.settings

// ... imports ...

/** Hilt shell — collects state and wires callbacks. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onDisconnect: () -> Unit = {},
    onForgetBoard: () -> Unit = {},
    onOpenBleDebug: () -> Unit = {},
) {
    val preferences by viewModel.preferences.collectAsState()
    val haTestResult by viewModel.haTestResult.collectAsState()
    val boardState by viewModel.boardState.collectAsState()
    val rssi by viewModel.rssi.collectAsState()

    SettingsContent(
        preferences = preferences,
        haTestResult = haTestResult,
        boardState = boardState,
        rssi = rssi,
        onSaveBoardName = viewModel::setCustomBoardName,
        onSpeedUnitSelected = viewModel::setSpeedUnit,
        onTemperatureUnitSelected = viewModel::setTemperatureUnit,
        onTireDiameterChanged = viewModel::setTireDiameter,
        onHaUrlChanged = viewModel::setHaUrl,
        onHaTokenChanged = viewModel::setHaToken,
        onTestHaConnection = viewModel::testHaConnection,
        onDisconnect = onDisconnect,
        onForgetBoard = onForgetBoard,
        onOpenBleDebug = onOpenBleDebug,
    )
}

/** Stateless content — renderable without Hilt (for screenshot tests). */
@Composable
internal fun SettingsContent(
    preferences: UserPreferences,
    haTestResult: HaPushResult?,
    boardState: BoardState,
    rssi: Int?,
    onSaveBoardName: (String?) -> Unit,
    onSpeedUnitSelected: (SpeedUnit) -> Unit,
    onTemperatureUnitSelected: (TemperatureUnit) -> Unit,
    onTireDiameterChanged: (Double) -> Unit,
    onHaUrlChanged: (String) -> Unit,
    onHaTokenChanged: (String) -> Unit,
    onTestHaConnection: () -> Unit,
    onDisconnect: () -> Unit,
    onForgetBoard: () -> Unit,
    onOpenBleDebug: () -> Unit,
) {
    val c = LocalZWheelColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.screenBg)
            .systemBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Page title
        item {
            Text(
                text = "Settings",
                style = TextStyle(fontFamily = SairaFamily, fontSize = 26.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.5).sp),
                color = c.textPrimary,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp),
            )
        }

        // CONNECTED BOARD section
        item {
            SectionEyebrowRow("CONNECTED BOARD", modifier = Modifier.padding(horizontal = 18.dp))
        }
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = c.card,
                border = BorderStroke(1.dp, c.border),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnectedBoardCard(
                        boardState = boardState,
                        rssi = rssi,
                        customBoardName = preferences.customBoardName,
                        onSaveName = onSaveBoardName,
                        onDisconnect = onDisconnect,
                        onForgetBoard = onForgetBoard,
                    )
                    HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                    DeviceInfoDisclosure(identity = boardState.identity, rssi = rssi)
                }
            }
        }

        // Separator
        item { Spacer(Modifier.height(22.dp)) }

        // UNITS
        item {
            SectionEyebrowRow("UNITS", modifier = Modifier.padding(horizontal = 18.dp))
        }
        item {
            UnitsSection(
                prefs = preferences,
                onSpeedUnit = onSpeedUnitSelected,
                onTempUnit = onTemperatureUnitSelected,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        item { Spacer(Modifier.height(22.dp)) }

        // TIRE CALIBRATION
        item {
            TireCalibrationSection(
                prefs = preferences,
                onDiameterChanged = onTireDiameterChanged,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        item { Spacer(Modifier.height(22.dp)) }

        // HOME ASSISTANT
        item {
            HomeAssistantSection(
                haUrl = preferences.haUrl,
                haToken = preferences.haToken,
                haTestResult = haTestResult,
                onUrlChanged = onHaUrlChanged,
                onTokenChanged = onHaTokenChanged,
                onTestConnection = onTestHaConnection,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        item { Spacer(Modifier.height(22.dp)) }

        // DEVELOPER
        item {
            DeveloperSection(
                onOpenBleDebug = onOpenBleDebug,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        item { Spacer(Modifier.height(22.dp)) }

        // SUPPORT
        item {
            SupportSection(modifier = Modifier.padding(horizontal = 18.dp))
        }

        item { Spacer(Modifier.height(22.dp)) }

        // ABOUT
        item {
            AboutSection(modifier = Modifier.padding(horizontal = 18.dp))
        }

        // Footer
        item {
            SettingsFooter()
        }
    }
}

// Private section eyebrow with bottom spacing used inline
@Composable
private fun SectionEyebrowRow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, fontWeight = FontWeight.W400, letterSpacing = 2.sp),
        color = c.textDimmest,
        modifier = modifier.padding(bottom = 6.dp),
    )
}
```

The `SectionEyebrowRow` local composable inside `SettingsScreen.kt` and the `SectionEyebrow`
inside `SettingsSections.kt` are identical; move the shared private helper into whichever file
keeps the line count lowest, or simply duplicate it (it is trivial).

---

### 5. Wire new callbacks in `ZWheelAppScreen.kt`

The `SettingsScreen` composable now takes `onDisconnect`, `onForgetBoard`, and `onOpenBleDebug`
lambdas. Update the call-site in `ZWheelAppScreen.kt` (find the `composable("settings")` route):

```kotlin
composable("settings") {
    SettingsScreen(
        onDisconnect = viewModel::disconnect,            // DashboardViewModel.disconnect()
        onForgetBoard = {
            viewModel.disconnect()                       // 1. end BLE session
            settingsViewModel.forgetBoard()              // 2. clear saved device ID
        },
        onOpenBleDebug = { navController.navigate("ble_debug") },
    )
}
```

`settingsViewModel` must be obtained at the `ZWheelAppScreen` scope — add:

```kotlin
val settingsViewModel: SettingsViewModel = hiltViewModel()
```

alongside the existing `val viewModel: DashboardViewModel = hiltViewModel()`.

`ZWheelAppScreen` already has `viewModel::disconnect` wired for the connect screen; reuse the same
reference.

---

### 6. Screenshot test — `settings/SettingsScreenshotTest.kt`

**File:** `app/src/test/kotlin/com/zwheel/app/ui/screenshots/SettingsScreenshotTest.kt`

Follow the exact same harness as `DashboardScreenshotTest.kt` (same annotations, same Roborazzi
API, `@RunWith(RobolectricTestRunner::class)` / `@GraphicsMode(GraphicsMode.Mode.NATIVE)` /
`@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)`).

The test renders `SettingsContent` (the stateless internal composable — no Hilt injection needed)
directly, using a mock board state and a custom name long enough to trigger the ellipsis scenario.

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SettingsScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settings_connected_board_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                SettingsContent(
                    preferences = UserPreferences(
                        speedUnit = SpeedUnit.MPH,
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                        tireDiameterInches = 10.5,
                        customBoardName = "BoardyMcBoardface McGee",  // long name → ellipsis test
                    ),
                    haTestResult = null,
                    boardState = BoardState(
                        identity = BoardIdentity(
                            boardId = "TEST_001",
                            name = "Pint X",
                            type = BoardType.PINT_X,
                            serialNumber = "18694",
                            firmwareRevision = "4134",
                            hardwareRevision = "4209",
                        ),
                        connectionState = ConnectionState.SUBSCRIBED,
                        cellVoltages = List(15) { 3.70 },
                        batteryPercent = 80,
                    ),
                    rssi = -60,
                    onSaveBoardName = {},
                    onSpeedUnitSelected = {},
                    onTemperatureUnitSelected = {},
                    onTireDiameterChanged = {},
                    onHaUrlChanged = {},
                    onHaTokenChanged = {},
                    onTestHaConnection = {},
                    onDisconnect = {},
                    onForgetBoard = {},
                    onOpenBleDebug = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/settings.png",
        )
    }

    @Test
    fun settings_device_info_expanded_record() {
        // Same content but DeviceInfoDisclosure starts expanded.
        // Since expanded state is local remember{}, we can't pre-expand it from outside.
        // Perform a UI action instead: find and click the DEVICE INFO row, then capture.
        composeTestRule.setContent {
            ZWheelTheme {
                SettingsContent(
                    preferences = UserPreferences(customBoardName = "BoardyMcBoardface McGee"),
                    haTestResult = null,
                    boardState = BoardState(
                        identity = BoardIdentity(
                            boardId = "TEST_001",
                            name = "Pint X",
                            type = BoardType.PINT_X,
                            serialNumber = "18694",
                            firmwareRevision = "4134",
                            hardwareRevision = "4209",
                        ),
                        connectionState = ConnectionState.SUBSCRIBED,
                        cellVoltages = List(15) { 3.70 },
                    ),
                    rssi = -60,
                    onSaveBoardName = {},
                    onSpeedUnitSelected = {},
                    onTemperatureUnitSelected = {},
                    onTireDiameterChanged = {},
                    onHaUrlChanged = {},
                    onHaTokenChanged = {},
                    onTestHaConnection = {},
                    onDisconnect = {},
                    onForgetBoard = {},
                    onOpenBleDebug = {},
                )
            }
        }
        // Click DEVICE INFO disclosure to expand it before capturing
        composeTestRule.onNodeWithText("DEVICE INFO").performClick()
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/settings_device_info_expanded.png",
        )
    }
}
```

Import `androidx.compose.ui.test.onNodeWithText` and `androidx.compose.ui.test.performClick`.
Import `com.zwheel.core.model.BoardIdentity`, `com.zwheel.core.model.BoardState`,
`com.zwheel.core.model.BoardType`, `com.zwheel.core.model.ConnectionState`.

---

### 7. Icon reference table for this slice

All icons are in `androidx.compose.material.icons.filled` (extended artifact, already on classpath):

| Spec glyph | Compose icon |
|---|---|
| `edit` | `Icons.Filled.Edit` |
| `check_circle` | `Icons.Filled.CheckCircle` |
| `network_wifi_3_bar` | `Icons.Filled.NetworkWifi3Bar` |
| `expand_more` | `Icons.Filled.ExpandMore` |

---

## Constraints (self-review before each commit)

1. **`core/` untouched.** No Android imports, no BLE UUIDs, no network calls in `app/main`.
2. **300-line soft limit per file.** The four new files are `ConnectedBoardCard.kt`,
   `DeviceInfoDisclosure.kt`, `SettingsSections.kt`, `SettingsScreen.kt`. If any exceeds ~300 lines,
   extract additional private composables into a fifth file.
3. **Token access only via `LocalZWheelColors.current`.** No hardcoded hex values except
   `Color(0xFF3A3E48)` in `SettingsFooter` (spec §9 explicitly specifies this color for the footer
   text; use it as a one-off literal with an inline comment `// spec §9 footer dim`).
4. **Custom board name is LOCAL.** The string is written only to DataStore via `SettingsRepository`.
   It is NEVER sent to the board. Enforce with a comment in `setCustomBoardName`.
5. **Inline edit is `remember` state** — NOT in ViewModel. The `editing: Boolean` and `editText: String`
   local state lives entirely in `ConnectedBoardCard`.
6. **Disclosure expanded state is `remember` state** — NOT in ViewModel. The `expanded: Boolean`
   local state lives entirely in `DeviceInfoDisclosure`.
7. **`SettingsScreen` is the Hilt shell; `SettingsContent` is the testable stateless composable.**
   The screenshot test calls `SettingsContent` directly and must compile without Hilt.
8. **`ZWheelAppScreen.kt` is the only file outside `ui/settings/` that may be edited** in this
   slice (to add `onForgetBoard`, `onOpenBleDebug` wiring). Do not edit `DashboardComponents.kt`,
   `ZWheelTheme.kt`, or any file under `core/`.
9. All numeric `Text` values use `fontFeatureSettings = "tnum"` for tabular figures.
10. **Battery serial row shows `"—"` with a TODO comment.** Do not add `batterySerialNumber` to
    `BoardIdentity` or any `core/` model — it is out of scope for this gate.

---

## Build & commit

1. From the worktree root run:
   ```
   GRADLE_OPTS="-Xmx4g" ./gradlew :app:compileDebugKotlin
   ```
   Fix ALL errors until it succeeds.

2. Then run the screenshot test to produce the output PNG:
   ```
   GRADLE_OPTS="-Xmx4g" ./gradlew :app:recordRoborazziDebug
   ```
   Confirm `app/build/outputs/roborazzi/settings.png` and
   `app/build/outputs/roborazzi/settings_device_info_expanded.png` are created.

3. Three commits, Conventional style, NO Co-Authored-By lines:

   ```
   feat(settings): local editable board name + propagation
   ```
   Files: `UserPreferences.kt`, `SettingsRepository.kt`, `DashboardState.kt`,
   `SettingsViewModel.kt`, `RideHistoryViewModel.kt`, `RideHistoryScreen.kt`,
   `RideDetailViewModel.kt`, `RideDetailScreen.kt`.

   ```
   feat(ui): dark settings screen with device-info disclosure
   ```
   Files: `ConnectedBoardCard.kt`, `DeviceInfoDisclosure.kt`, `SettingsSections.kt`,
   `SettingsScreen.kt` (rewrite), `ZWheelAppScreen.kt`.

   ```
   test(ui): settings screenshot test
   ```
   Files: `SettingsScreenshotTest.kt`.
