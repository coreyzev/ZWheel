# Gate: ble-debug-toggle

Redesign BLE debug from a separate screen into a persistent inline toggle in the Developer section of Settings. The recorder is already capturing BLE events via the main connection — the toggle just arms/clears it and exposes upload controls. When logging is on, the app works completely normally; the user rides and the data is captured transparently.

Touch ONLY the files listed below. Run `:app:compileDebugKotlin`, fix all errors, commit.

---

## 1. Add `reset()` to `BleDebugRecorder`

**File:** `core/src/main/kotlin/com/zwheel/core/protocol/debug/BleDebugEvent.kt`

Make `startEpochMs` and `sessionId` mutable so `reset()` can restart them. Add `eventCount`. The `sessionId` constructor parameter is removed — it is always generated internally. Keep `salt` as a constructor param with its default.

Replace the class definition with:

```kotlin
class BleDebugRecorder(
    private val salt: String = UUID.randomUUID().toString(),
) {
    private val events = Collections.synchronizedList(mutableListOf<BleDebugEvent>())
    private var sessionId: String = UUID.randomUUID().toString()
    private var startEpochMs: Long = System.currentTimeMillis()

    val eventCount: Int get() = events.size

    fun reset() {
        synchronized(events) {
            events.clear()
            sessionId = UUID.randomUUID().toString()
            startEpochMs = System.currentTimeMillis()
        }
    }

    fun record(
        type: String,
        deviceId: String? = null,
        displayName: String? = null,
        rssi: Int? = null,
        characteristicUuid: String? = null,
        characteristicName: String? = null,
        rawValueHex: String? = null,
        status: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        events += BleDebugEvent(
            schemaVersion = SCHEMA_VERSION,
            sessionId = sessionId,
            offsetMs = nowEpochMs - startEpochMs,
            type = type,
            deviceHash = deviceId?.let(::hashDeviceId),
            displayName = displayName,
            rssi = rssi,
            characteristicUuid = characteristicUuid,
            characteristicName = characteristicName,
            rawValueHex = rawValueHex,
            status = status,
        )
    }

    fun toJsonLines(): String = synchronized(events) {
        events.joinToString(separator = "\n") { it.toJson() }
    }

    fun snapshot(): List<BleDebugEvent> = synchronized(events) { events.toList() }

    private fun hashDeviceId(deviceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$deviceId".encodeToByteArray())
        return digest.take(12).joinToString(separator = "") { byte ->
            byte.toUByte().toString(radix = 16).padStart(2, '0')
        }
    }

    private companion object {
        const val SCHEMA_VERSION = "m1-ble-debug-v1"
    }
}
```

---

## 2. Add `bleDebugPassword` to `UserPreferences`

**File:** `app/src/main/kotlin/com/zwheel/app/data/settings/UserPreferences.kt`

Add one field at the end of the data class:
```kotlin
val bleDebugPassword: String = "",
```

---

## 3. Add `saveDebugPassword` to `SettingsRepository`

**File:** `app/src/main/kotlin/com/zwheel/app/data/settings/SettingsRepository.kt`

Follow the exact pattern used for `HA_URL` / `HA_TOKEN`.

Add key (with the other `stringPreferencesKey` declarations):
```kotlin
private val BLE_DEBUG_PASSWORD = stringPreferencesKey("ble_debug_password")
```

Add to the `preferences` flow mapping (same place as `haUrl`/`haToken`):
```kotlin
bleDebugPassword = prefs[BLE_DEBUG_PASSWORD] ?: "",
```

Add method:
```kotlin
suspend fun saveDebugPassword(password: String) {
    dataStore.edit { prefs -> prefs[BLE_DEBUG_PASSWORD] = password }
}
```

---

## 4. Update `SettingsViewModel`

**File:** `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsViewModel.kt`

Add imports:
```kotlin
import android.content.Context
import com.zwheel.app.ui.ble.BleDebugLogExporter
import com.zwheel.core.protocol.debug.BleDebugRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
```

Update the constructor to inject `BleDebugRecorder` and `@ApplicationContext`:
```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val connectionManager: ConnectionManager,
    private val bleRecorder: BleDebugRecorder,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
```

Add after the existing `haTestResult` state:
```kotlin
private val bleExporter: BleDebugLogExporter by lazy { BleDebugLogExporter(appContext) }

private val _isDebugLogging = MutableStateFlow(false)
val isDebugLogging: StateFlow<Boolean> = _isDebugLogging.asStateFlow()

private val _debugStatus = MutableStateFlow<String?>(null)
val debugStatus: StateFlow<String?> = _debugStatus.asStateFlow()

fun setDebugLogging(enabled: Boolean) {
    if (enabled) {
        bleRecorder.reset()
        _isDebugLogging.value = true
        _debugStatus.value = "Logging"
    } else {
        _isDebugLogging.value = false
        _debugStatus.value = null
    }
}

fun restartDebugLogging() {
    bleRecorder.reset()
    _debugStatus.value = "Restarted — logging"
}

fun saveDebugPassword(password: String) {
    viewModelScope.launch { repo.saveDebugPassword(password) }
}

fun pairDebug() {
    val password = preferences.value.bleDebugPassword
    if (password.isBlank()) { _debugStatus.value = "Enter a password first"; return }
    _debugStatus.value = "Pairing..."
    viewModelScope.launch {
        runCatching { bleExporter.pair(password) }
            .onSuccess { msg -> _debugStatus.value = msg }
            .onFailure { err -> _debugStatus.value = "Pair failed: ${err.message}" }
    }
}

fun uploadDebug() {
    _debugStatus.value = "Uploading ${bleRecorder.eventCount} events..."
    viewModelScope.launch {
        runCatching { bleExporter.upload(bleRecorder.toJsonLines()) }
            .onSuccess { msg -> _debugStatus.value = msg }
            .onFailure { err -> _debugStatus.value = "Upload failed: ${err.message}" }
    }
}

fun shareDebug() {
    viewModelScope.launch {
        runCatching { bleExporter.share(bleRecorder.toJsonLines()) }
            .onSuccess { msg -> _debugStatus.value = msg }
            .onFailure { err -> _debugStatus.value = "Share failed: ${err.message}" }
    }
}
```

---

## 5. Update `SettingsScreen`

**File:** `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsScreen.kt`

### 5a — `SettingsScreen` composable (outer, wires ViewModel)

Remove `onOpenBleDebug: () -> Unit` param. Add debug state collection and pass to content:

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onDisconnect: () -> Unit = {},
    onForgetBoard: () -> Unit = {},
) {
    val preferences by viewModel.preferences.collectAsState()
    val haTestResult by viewModel.haTestResult.collectAsState()
    val boardState by viewModel.boardState.collectAsState()
    val rssi by viewModel.rssi.collectAsState()
    val isDebugLogging by viewModel.isDebugLogging.collectAsState()
    val debugStatus by viewModel.debugStatus.collectAsState()

    SettingsContent(
        preferences = preferences,
        haTestResult = haTestResult,
        boardState = boardState,
        rssi = rssi,
        isDebugLogging = isDebugLogging,
        debugStatus = debugStatus,
        onSaveBoardName = viewModel::setCustomBoardName,
        onSaveBoardTireDiameter = viewModel::saveBoardTireDiameter,
        onSpeedUnitSelected = viewModel::setSpeedUnit,
        onTemperatureUnitSelected = viewModel::setTemperatureUnit,
        onHaUrlChanged = viewModel::setHaUrl,
        onHaTokenChanged = viewModel::setHaToken,
        onTestHaConnection = viewModel::testHaConnection,
        onDisconnect = onDisconnect,
        onForgetBoard = onForgetBoard,
        onToggleDebugLogging = viewModel::setDebugLogging,
        onSaveDebugPassword = viewModel::saveDebugPassword,
        onRestartDebugLogging = viewModel::restartDebugLogging,
        onPairDebug = viewModel::pairDebug,
        onUploadDebug = viewModel::uploadDebug,
        onShareDebug = viewModel::shareDebug,
    )
}
```

### 5b — `SettingsContent` composable

Remove `onOpenBleDebug`. Add new debug params:

```kotlin
@Composable
internal fun SettingsContent(
    preferences: UserPreferences,
    haTestResult: HaPushResult?,
    boardState: BoardState,
    rssi: Int?,
    isDebugLogging: Boolean,
    debugStatus: String?,
    onSaveBoardName: (String?) -> Unit,
    onSaveBoardTireDiameter: (Double) -> Unit,
    onSpeedUnitSelected: (SpeedUnit) -> Unit,
    onTemperatureUnitSelected: (TemperatureUnit) -> Unit,
    onHaUrlChanged: (String) -> Unit,
    onHaTokenChanged: (String) -> Unit,
    onTestHaConnection: () -> Unit,
    onDisconnect: () -> Unit,
    onForgetBoard: () -> Unit,
    onToggleDebugLogging: (Boolean) -> Unit,
    onSaveDebugPassword: (String) -> Unit,
    onRestartDebugLogging: () -> Unit,
    onPairDebug: () -> Unit,
    onUploadDebug: () -> Unit,
    onShareDebug: () -> Unit,
) {
```

Replace the `DeveloperSection` call in the `LazyColumn`:
```kotlin
item {
    DeveloperSection(
        isDebugLogging = isDebugLogging,
        debugPassword = preferences.bleDebugPassword,
        debugStatus = debugStatus,
        onToggleLogging = onToggleDebugLogging,
        onSavePassword = onSaveDebugPassword,
        onRestartLogging = onRestartDebugLogging,
        onPair = onPairDebug,
        onUpload = onUploadDebug,
        onShare = onShareDebug,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
}
```

---

## 6. Rewrite `DeveloperSection` in `SettingsSections.kt`

**File:** `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsSections.kt`

Replace the existing `DeveloperSection` function with the new version below. Add any missing imports at the top of the file.

Required imports (add if missing):
```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.zwheel.app.ui.JetBrainsMonoFamily
```

New `DeveloperSection`:

```kotlin
@Composable
internal fun DeveloperSection(
    isDebugLogging: Boolean,
    debugPassword: String,
    debugStatus: String?,
    onToggleLogging: (Boolean) -> Unit,
    onSavePassword: (String) -> Unit,
    onRestartLogging: () -> Unit,
    onPair: () -> Unit,
    onUpload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current
    var localPassword by remember(debugPassword) { mutableStateOf(debugPassword) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow("DEVELOPER")

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val dotColor = if (isDebugLogging) c.rampGood else c.textDim
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .then(
                            if (isDebugLogging) Modifier.drawBehind {
                                drawCircle(c.rampGood.copy(alpha = 0.35f), radius = 14.dp.toPx())
                            } else Modifier
                        )
                        .background(dotColor, CircleShape),
                )
                Text(
                    "BLE debug logging",
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = c.textSecondary,
                )
            }
            Switch(
                checked = isDebugLogging,
                onCheckedChange = onToggleLogging,
                colors = settingsSwitchColors(),
            )
        }

        AnimatedVisibility(visible = isDebugLogging) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = c.divider, thickness = 0.5.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Password",
                        style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 11.sp),
                        color = c.textDim,
                        modifier = Modifier.width(72.dp),
                    )
                    BasicTextField(
                        value = localPassword,
                        onValueChange = { localPassword = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSavePassword(localPassword) }),
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = c.textPrimary,
                        ),
                        cursorBrush = SolidColor(c.lime),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, c.buttonBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = onRestartLogging,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = c.textSecondary),
                    ) {
                        Text(
                            "Restart",
                            fontFamily = SairaFamily,
                            fontWeight = FontWeight.W600,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    TextButton(
                        onClick = { onSavePassword(localPassword); onPair() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = c.textSecondary),
                    ) {
                        Text(
                            "Pair",
                            fontFamily = SairaFamily,
                            fontWeight = FontWeight.W600,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    TextButton(
                        onClick = onUpload,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = c.lime),
                    ) {
                        Text(
                            "Upload",
                            fontFamily = SairaFamily,
                            fontWeight = FontWeight.W600,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    TextButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = c.textSecondary),
                    ) {
                        Text(
                            "Share",
                            fontFamily = SairaFamily,
                            fontWeight = FontWeight.W600,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }

                if (debugStatus != null) {
                    Text(
                        debugStatus,
                        style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 10.sp),
                        color = c.textDim,
                    )
                }
            }
        }
    }
}
```

---

## 7. Remove `ble_debug` route from `ZWheelAppScreen.kt`

**File:** `app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt`

Three changes:
1. Remove `import com.zwheel.app.ui.ble.BleDebugScreen`
2. In the `SettingsScreen()` call, remove the `onOpenBleDebug = { ... }` argument
3. Remove the entire `composable("ble_debug") { BleDebugScreen() }` block

---

## 8. Delete obsolete files

Delete these four files:
- `app/src/main/kotlin/com/zwheel/app/ui/ble/BleDebugScreen.kt`
- `app/src/main/kotlin/com/zwheel/app/ui/ble/BleDebugViewModel.kt`
- `app/src/main/kotlin/com/zwheel/app/ui/ble/BlePermissionUtils.kt`
- `app/src/main/kotlin/com/zwheel/app/ui/ble/BleDebugSessionLogger.kt`

Keep (do NOT delete):
- `BleDebugFormat.kt`
- `BleDebugLogExporter.kt` (both debug and release variants)

---

## 9. Compile and commit

```
GRADLE_USER_HOME=/tmp/zwheel-gradle ./gradlew :app:compileDebugKotlin
```

Fix all errors, then commit:
```
feat(settings): BLE debug as inline toggle — logging, pair, upload, share in Developer section
```
