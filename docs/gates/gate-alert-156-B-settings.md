# Gate B — Issue #156: Settings Model for Audio Alerts

## Depends on
Gate A must be merged first. These two enums already exist in `core/`:
- `com.zwheel.core.alerts.AlertType` — `SPEED`, `HEADROOM`
- `com.zwheel.core.alerts.AlertOutput` — `AUTO`, `WATCH`, `PHONE`

## Files to modify

### `app/src/main/kotlin/com/zwheel/app/data/settings/UserPreferences.kt`

Add five new fields (with defaults) to the existing `UserPreferences` data class.
The existing fields must not change. Add these at the end of the parameter list:

```kotlin
val audioAlertsEnabled: Boolean = false,
val audioAlertType: com.zwheel.core.alerts.AlertType = com.zwheel.core.alerts.AlertType.SPEED,
/** Speed threshold stored in mph. Convert to m/s when building AlertConfig. */
val audioAlertThresholdMph: Int = 16,
/** Headroom threshold (raw firmware integer). Alert when safetyHeadroom <= this value. */
val audioAlertThresholdHeadroom: Int = 0,
val audioAlertOutput: com.zwheel.core.alerts.AlertOutput = com.zwheel.core.alerts.AlertOutput.AUTO,
```

Import `com.zwheel.core.alerts.AlertType` and `com.zwheel.core.alerts.AlertOutput` at the top.

### `app/src/main/kotlin/com/zwheel/app/data/settings/SettingsRepository.kt`

**Add 5 new DataStore keys** in the `companion object`:
```kotlin
val AUDIO_ALERTS_ENABLED = booleanPreferencesKey("audio_alerts_enabled")
val AUDIO_ALERT_TYPE = stringPreferencesKey("audio_alert_type")
val AUDIO_ALERT_THRESHOLD_MPH = intPreferencesKey("audio_alert_threshold_mph")
val AUDIO_ALERT_THRESHOLD_HEADROOM = intPreferencesKey("audio_alert_threshold_headroom")
val AUDIO_ALERT_OUTPUT = stringPreferencesKey("audio_alert_output")
```

**Extend the `preferences` Flow** (in `dataStore.data.map { prefs -> ... }`) to read the new fields.
Add these inside the `UserPreferences(...)` constructor call:
```kotlin
audioAlertsEnabled = prefs[AUDIO_ALERTS_ENABLED] ?: false,
audioAlertType = prefs[AUDIO_ALERT_TYPE].toEnumOrDefault(com.zwheel.core.alerts.AlertType.SPEED),
audioAlertThresholdMph = prefs[AUDIO_ALERT_THRESHOLD_MPH] ?: 16,
audioAlertThresholdHeadroom = prefs[AUDIO_ALERT_THRESHOLD_HEADROOM] ?: 0,
audioAlertOutput = prefs[AUDIO_ALERT_OUTPUT].toEnumOrDefault(com.zwheel.core.alerts.AlertOutput.AUTO),
```

**Add 5 new setter methods** following the exact same pattern as the existing setters:
```kotlin
suspend fun setAudioAlertsEnabled(enabled: Boolean) {
    dataStore.edit { it[AUDIO_ALERTS_ENABLED] = enabled }
}

suspend fun setAudioAlertType(type: com.zwheel.core.alerts.AlertType) {
    dataStore.edit { it[AUDIO_ALERT_TYPE] = type.name }
}

suspend fun setAudioAlertThresholdMph(mph: Int) {
    dataStore.edit { it[AUDIO_ALERT_THRESHOLD_MPH] = mph.coerceIn(1, 60) }
}

suspend fun setAudioAlertThresholdHeadroom(value: Int) {
    dataStore.edit { it[AUDIO_ALERT_THRESHOLD_HEADROOM] = value.coerceIn(-10, 20) }
}

suspend fun setAudioAlertOutput(output: com.zwheel.core.alerts.AlertOutput) {
    dataStore.edit { it[AUDIO_ALERT_OUTPUT] = output.name }
}
```

Import `com.zwheel.core.alerts.AlertType` and `com.zwheel.core.alerts.AlertOutput` at the top of the file.

## Compile and verify
Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin`
Must compile clean. Fix any errors before committing.

## Commit
`feat(settings): add audio alert preferences — issue #156`

Do not modify any other files.
