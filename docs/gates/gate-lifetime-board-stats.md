# Gate: Lifetime Board Stats, Custom Name Seeding, Tooltip

## Purpose

Surface lifetime odometer and lifetime amp-hours from the board controller in Settings.
Seed the app's stored custom board name from the controller's `CUSTOM_NAME` characteristic
on first-ever connect (if user hasn't set a name yet).
Add a single info tooltip at the top of the Connected Board section.

---

## Implementation

### 1. `core/src/main/kotlin/com/zwheel/core/model/BoardModels.kt`

Add two nullable fields to `BoardIdentity`:

```kotlin
data class BoardIdentity(
    val boardId: String,
    val name: String,
    val type: BoardType,
    val serialNumber: String? = null,
    val batterySerialNumber: String? = null,
    val firmwareRevision: String? = null,
    val hardwareRevision: String? = null,
    val lifetimeMiles: Int? = null,
    val lifetimeAmpHours: Double? = null,
)
```

No other changes to `BoardModels.kt`.

---

### 2. `core/src/main/kotlin/com/zwheel/core/protocol/Parsers.kt`

Add three new parser functions **at the bottom** of the `Parsers` object (above the closing `}`):

```kotlin
fun lifetimeOdometer(bytes: ByteArray): Int = unsignedInt16(bytes)

fun lifetimeAmpHours(bytes: ByteArray): Double = unsignedInt16(bytes) / 10.0

fun customName(bytes: ByteArray): String? {
    val nullIndex = bytes.indexOfFirst { it == 0.toByte() }.let { if (it < 0) bytes.size else it }
    return bytes.copyOf(nullIndex).decodeToString().trim().takeIf { it.isNotBlank() }
}
```

Do NOT modify any existing parser functions.

---

### 3. `app/src/main/kotlin/com/zwheel/app/ble/ConnectionManager.kt`

**In `connect()`**, after reading `serialNumber` and `batterySerialNumber` but BEFORE constructing `identity`, add reads for lifetime data and custom name:

```kotlin
val lifetimeMiles = runCatching {
    Parsers.lifetimeOdometer(transport.read(OwUuids.LIFETIME_ODOMETER))
}.getOrNull()
val lifetimeAmpHours = runCatching {
    Parsers.lifetimeAmpHours(transport.read(OwUuids.LIFETIME_AMP_HOURS))
}.getOrNull()
val boardCustomName = runCatching {
    Parsers.customName(transport.read(OwUuids.CUSTOM_NAME))
}.getOrNull()
```

**Update the `identity` construction** to include lifetime fields:

```kotlin
val identity = BoardIdentity(
    boardId = deviceId,
    name = scanResult?.displayName ?: boardType.displayName,
    type = boardType,
    serialNumber = serialNumber,
    batterySerialNumber = batterySerialNumber,
    firmwareRevision = fwRev.toString(),
    hardwareRevision = hwRev.toString(),
    lifetimeMiles = lifetimeMiles,
    lifetimeAmpHours = lifetimeAmpHours,
)
```

**After calling `settingsRepository.saveLastConnectedIdentityDetails(...)`**, add custom name seeding.
Seed only if the user has NOT already set a custom name (i.e., `savedPrefs.customBoardName == null`).
Note: `savedPrefs` is read later in `connect()` — move the `savedPrefs` read to be BEFORE this block,
or do a second `preferences.first()` call here. Use a second call to be safe:

```kotlin
val currentCustomName = settingsRepository.preferences.first().customBoardName
if (currentCustomName == null && boardCustomName != null) {
    settingsRepository.setCustomBoardName(boardCustomName)
}
```

**Add a `refreshLifetimeStats()` public function** (non-suspending, fire-and-forget):

```kotlin
fun refreshLifetimeStats() {
    scope.launch {
        val currentIdentity = _boardState.value.identity ?: return@launch
        val newMiles = runCatching {
            Parsers.lifetimeOdometer(transport.read(OwUuids.LIFETIME_ODOMETER))
        }.getOrNull()
        val newAh = runCatching {
            Parsers.lifetimeAmpHours(transport.read(OwUuids.LIFETIME_AMP_HOURS))
        }.getOrNull()
        if (newMiles != null || newAh != null) {
            _boardState.update { state ->
                state.copy(
                    identity = state.identity?.copy(
                        lifetimeMiles = newMiles ?: currentIdentity.lifetimeMiles,
                        lifetimeAmpHours = newAh ?: currentIdentity.lifetimeAmpHours,
                    )
                )
            }
        }
    }
}
```

---

### 4. `app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt`

**In `startRideRecorderTicker()`**, add a call to `connectionManager.refreshLifetimeStats()` when a session ends.

Find the `recorder.onSessionChanged` lambda — it currently looks like:
```kotlin
recorder.onSessionChanged = { isRiding ->
    rideServiceRepository.updateIsRiding(isRiding)
    if (isRiding) {
        rideServiceRepository.markRideStarted(clock.nowEpochMillis())
    } else {
        rideServiceRepository.markRideStopped()
        topSpeedTracker = DefaultTopSpeedTracker()
        rideServiceRepository.updateTopSpeed(0.0)
    }
}
```

Add `connectionManager.refreshLifetimeStats()` in the `else` block:

```kotlin
recorder.onSessionChanged = { isRiding ->
    rideServiceRepository.updateIsRiding(isRiding)
    if (isRiding) {
        rideServiceRepository.markRideStarted(clock.nowEpochMillis())
    } else {
        rideServiceRepository.markRideStopped()
        topSpeedTracker = DefaultTopSpeedTracker()
        rideServiceRepository.updateTopSpeed(0.0)
        connectionManager.refreshLifetimeStats()
    }
}
```

---

### 5. `app/src/main/kotlin/com/zwheel/app/ui/settings/ConnectedBoardCard.kt`

Add a lifetime stats block **above** the Disconnect/Forget buttons row.

In the `Column(modifier = modifier)` body, after the chip row (FlowRow) and before the `// ── Disconnect / Forget` row, insert:

```kotlin
// ── Lifetime stats ────────────────────────────────────────────────────
val lifetimeMiles = effectiveIdentity?.lifetimeMiles
val lifetimeAmpHours = effectiveIdentity?.lifetimeAmpHours
if (lifetimeMiles != null || lifetimeAmpHours != null) {
    HorizontalDivider(color = c.divider, thickness = 0.5.dp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (lifetimeMiles != null) {
            Column {
                Text(
                    "LIFETIME ODO",
                    style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, letterSpacing = 1.sp),
                    color = c.textDim,
                )
                Text(
                    "$lifetimeMiles mi",
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W700,
                        fontFeatureSettings = "tnum",
                    ),
                    color = c.textPrimary,
                )
            }
        }
        if (lifetimeAmpHours != null) {
            Column {
                Text(
                    "LIFETIME AH",
                    style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, letterSpacing = 1.sp),
                    color = c.textDim,
                )
                Text(
                    "%.1f Ah".format(lifetimeAmpHours),
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W700,
                        fontFeatureSettings = "tnum",
                    ),
                    color = c.textPrimary,
                )
            }
        }
    }
}
```

Required imports (add if not already present):
- `androidx.compose.material3.HorizontalDivider` (already in file)
- `androidx.compose.ui.text.font.FontWeight` (already in file)

---

### 6. `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsScreen.kt`

**Add tooltip state** in `SettingsContent`, before the `LazyColumn`:

```kotlin
var showBoardTooltip by remember { mutableStateOf(false) }
if (showBoardTooltip) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { showBoardTooltip = false },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { showBoardTooltip = false }) {
                Text("OK", fontFamily = SairaFamily)
            }
        },
        title = { Text("About board data", fontFamily = SairaFamily, fontWeight = FontWeight.W700) },
        text = {
            Text(
                "Lifetime ODO and Ah are reported by the board controller, not calculated by ZWheel. " +
                    "Swapping tires doesn't reset the ODO. Replacing the battery doesn't reset Ah. " +
                    "These values reflect what the controller has recorded since the factory.",
                fontFamily = SairaFamily,
                fontSize = 14.sp,
            )
        },
    )
}
```

**Replace the CONNECTED BOARD eyebrow item** — change:

```kotlin
item { SectionEyebrowRow("CONNECTED BOARD", modifier = Modifier.padding(horizontal = 18.dp)) }
```

to:

```kotlin
item {
    Row(
        modifier = Modifier.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionEyebrowRow(
            text = "CONNECTED BOARD",
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 6.dp),
        )
        androidx.compose.material.icons.Icons.Default.Info.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = "About board data",
                tint = c.textDimmest,
                modifier = Modifier
                    .size(14.dp)
                    .clickable { showBoardTooltip = true },
            )
        }
    }
}
```

Wait — use it more cleanly. The `Icons.Default.Info` import is:
`import androidx.compose.material.icons.filled.Info` (add to imports).

Replace the eyebrow item with:

```kotlin
item {
    Row(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "CONNECTED BOARD",
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.W400,
                letterSpacing = 2.sp,
            ),
            color = c.textDimmest,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "About board data",
            tint = c.textDimmest,
            modifier = Modifier
                .size(14.dp)
                .clickable { showBoardTooltip = true },
        )
    }
}
```

Required new imports for `SettingsScreen.kt`:
```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
```

Check which are already imported before adding. The `SectionEyebrowRow` function should remain unchanged (still used elsewhere potentially) but the eyebrow row item for CONNECTED BOARD is now inlined.

---

## Compile check

```
gradle :core:compileDebugKotlin :app:compileDebugKotlin
```

Must pass with zero errors.

---

## Commit

```
feat(settings): lifetime ODO/Ah from controller, custom name seeding, board tooltip
```

No Co-Authored-By line.
