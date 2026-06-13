# Gate: M3 — Battery Optimization First-Launch Dialog

**Branch:** `codex/m3-battery-opt-dialog`
**One concern:** On first `RideForegroundService` start, request battery optimization exemption via the system dialog, once and only once.

---

## Problem

ADR-008 §6 specifies: "On first `RideForegroundService` start (tracked in DataStore), call `startActivity(Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))`."

Currently, the persistent banner in the dashboard warns the user but never prompts the system dialog automatically. Users who dismiss the banner may not know to act on it. The first-launch prompt is the safety net that catches this case.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/data/settings/UserPreferences.kt    ← add hasRequestedBatteryOptimization
app/src/main/kotlin/com/zwheel/app/data/settings/SettingsRepository.kt ← add save method + DataStore key
app/src/main/kotlin/com/zwheel/app/service/RideForegroundService.kt    ← call the dialog on first start
```

---

## Implementation spec

### 1. `UserPreferences.kt`

Add one field:
```kotlin
val hasRequestedBatteryOptimization: Boolean = false,
```

### 2. `SettingsRepository.kt`

Add a new DataStore key:
```kotlin
private val HAS_REQUESTED_BATTERY_OPT = booleanPreferencesKey("has_requested_battery_opt")
```

Add a new suspend function:
```kotlin
suspend fun saveHasRequestedBatteryOptimization() {
    dataStore.edit { it[HAS_REQUESTED_BATTERY_OPT] = true }
}
```

In the existing `preferences` Flow mapping, add the new field:
```kotlin
hasRequestedBatteryOptimization = it[HAS_REQUESTED_BATTERY_OPT] ?: false,
```

### 3. `RideForegroundService.kt`

In `onStartCommand()`, after the foreground notification is started and before the connect
logic, add a first-launch battery optimization request:

```kotlin
lifecycleScope.launch {
    val prefs = settingsRepository.preferences.first()
    if (!prefs.hasRequestedBatteryOptimization) {
        settingsRepository.saveHasRequestedBatteryOptimization()
        requestBatteryOptimizationExemption()
    }
}
```

Add the helper method:
```kotlin
private fun requestBatteryOptimizationExemption() {
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
        val intent = Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:$packageName"),
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
}
```

Add imports:
```kotlin
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.flow.first
```

Note: `startActivity()` with `FLAG_ACTIVITY_NEW_TASK` is valid from a Service context.
The system dialog appears once; future starts skip this block because the DataStore flag
is set.

---

## Constraints

- Only add the `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent — do not use any
  manufacturer-specific settings screens (those belong in `OemBatteryAdvice.kt`).
- Do NOT modify `OemBatteryAdvice.kt` or `ZWheelAppScreen.kt`.
- The DataStore edit and the `startActivity` call should happen sequentially (save first,
  then launch the activity) so the flag is set even if the activity launch fails.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin --no-daemon
```

Must pass.
