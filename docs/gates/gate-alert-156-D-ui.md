# Gate D — Issue #156: Audio Alerts Settings UI

## Depends on
Gates A and B must be merged first.

**Already exists in `core/alerts/`:**
- `AlertType` — `SPEED`, `HEADROOM`
- `AlertOutput` — `AUTO`, `WATCH`, `PHONE`

**Already added to `UserPreferences`:**
```kotlin
val audioAlertsEnabled: Boolean
val audioAlertType: AlertType
val audioAlertThresholdMph: Int
val audioAlertThresholdHeadroom: Int
val audioAlertOutput: AlertOutput
```

**Already in `SettingsRepository`:**
```kotlin
suspend fun setAudioAlertsEnabled(enabled: Boolean)
suspend fun setAudioAlertType(type: AlertType)
suspend fun setAudioAlertThresholdMph(mph: Int)
suspend fun setAudioAlertThresholdHeadroom(value: Int)
suspend fun setAudioAlertOutput(output: AlertOutput)
```

**Pattern reference:** Look at existing functions in `SettingsViewModel` like `setSpeedUnit()`, `setTemperatureUnit()`. Follow the same `viewModelScope.launch { repo.setX(x) }` pattern.

**Composable pattern reference:** Look at `UnitsSection` in `SettingsSections.kt` which uses `SegmentedToggle`. Use the same color tokens (`LocalZWheelColors.current`), typography (`SairaFamily`, `JetBrainsMonoFamily`), and spacing patterns.

## Files to modify

### `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsViewModel.kt`

Add 5 setter functions following the existing `setSpeedUnit` / `setTemperatureUnit` pattern:

```kotlin
fun setAudioAlertsEnabled(enabled: Boolean) {
    viewModelScope.launch { repo.setAudioAlertsEnabled(enabled) }
}

fun setAudioAlertType(type: com.zwheel.core.alerts.AlertType) {
    viewModelScope.launch { repo.setAudioAlertType(type) }
}

fun setAudioAlertThresholdMph(mph: Int) {
    viewModelScope.launch { repo.setAudioAlertThresholdMph(mph) }
}

fun setAudioAlertThresholdHeadroom(value: Int) {
    viewModelScope.launch { repo.setAudioAlertThresholdHeadroom(value) }
}

fun setAudioAlertOutput(output: com.zwheel.core.alerts.AlertOutput) {
    viewModelScope.launch { repo.setAudioAlertOutput(output) }
}
```

No other changes to SettingsViewModel.

### `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsSections.kt`

Add a new `AudioAlertsSection` composable at the end of the file (before the closing of the file), after the existing `AboutSection`/`SettingsFooter` composables. It should follow the style of `UnitsSection` and `DeveloperSection`.

Existing composable patterns to match:
- Use `SectionEyebrow("AUDIO ALERTS")` for the section header (same as "UNITS", "DEVELOPER")
- `Switch` component (same as DeveloperSection's BLE debug switch)
- `SegmentedToggle` for multi-option pickers (same as UnitsSection)
- `BasicTextField` pattern for numeric input (same as DeveloperSection password field)
- Color tokens: `LocalZWheelColors.current` → `c.textPrimary`, `c.textSecondary`, `c.textDim`, `c.lime`, `c.buttonBorder`
- Font: `SairaFamily` for labels, `JetBrainsMonoFamily` for small labels/values

```kotlin
@Composable
internal fun AudioAlertsSection(
    prefs: com.zwheel.app.data.settings.UserPreferences,
    onAlertsEnabled: (Boolean) -> Unit,
    onAlertType: (com.zwheel.core.alerts.AlertType) -> Unit,
    onThresholdMph: (Int) -> Unit,
    onThresholdHeadroom: (Int) -> Unit,
    onAlertOutput: (com.zwheel.core.alerts.AlertOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionEyebrow("AUDIO ALERTS")

        // Enabled toggle row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Speed alerts",
                style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W600),
                color = c.textSecondary,
            )
            Switch(
                checked = prefs.audioAlertsEnabled,
                onCheckedChange = onAlertsEnabled,
                colors = settingsSwitchColors(),
            )
        }

        // Subsettings — only visible when enabled
        AnimatedVisibility(visible = prefs.audioAlertsEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = c.divider, thickness = 0.5.dp)

                // Alert type picker
                Text(
                    "ALERT TYPE",
                    style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, letterSpacing = 1.5.sp),
                    color = c.textDimmest,
                )
                SegmentedToggle(
                    options = listOf(
                        com.zwheel.core.alerts.AlertType.SPEED to "Speed",
                        com.zwheel.core.alerts.AlertType.HEADROOM to "Headroom",
                    ),
                    selected = prefs.audioAlertType,
                    onSelected = onAlertType,
                )

                // Threshold input — switches label based on type
                val thresholdLabel = when (prefs.audioAlertType) {
                    com.zwheel.core.alerts.AlertType.SPEED -> "THRESHOLD (mph)"
                    com.zwheel.core.alerts.AlertType.HEADROOM -> "THRESHOLD (headroom ≤)"
                }
                Text(
                    thresholdLabel,
                    style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, letterSpacing = 1.5.sp),
                    color = c.textDimmest,
                )

                when (prefs.audioAlertType) {
                    com.zwheel.core.alerts.AlertType.SPEED -> {
                        // Slider for speed threshold: 5–40 mph in steps of 1
                        var sliderMph by remember(prefs.audioAlertThresholdMph) {
                            mutableStateOf(prefs.audioAlertThresholdMph.toFloat())
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Slider(
                                value = sliderMph,
                                onValueChange = { sliderMph = it },
                                onValueChangeFinished = { onThresholdMph(sliderMph.toInt()) },
                                valueRange = 5f..40f,
                                steps = 34,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = c.lime,
                                    activeTrackColor = c.lime,
                                    inactiveTrackColor = c.buttonBorder,
                                ),
                            )
                            Text(
                                "${sliderMph.toInt()} mph",
                                style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                                color = c.textSecondary,
                                modifier = Modifier.width(52.dp),
                            )
                        }
                    }
                    com.zwheel.core.alerts.AlertType.HEADROOM -> {
                        // Slider for headroom threshold: -10..10 in steps of 1
                        var sliderHeadroom by remember(prefs.audioAlertThresholdHeadroom) {
                            mutableStateOf(prefs.audioAlertThresholdHeadroom.toFloat())
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Slider(
                                value = sliderHeadroom,
                                onValueChange = { sliderHeadroom = it },
                                onValueChangeFinished = { onThresholdHeadroom(sliderHeadroom.toInt()) },
                                valueRange = -10f..10f,
                                steps = 19,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = c.lime,
                                    activeTrackColor = c.lime,
                                    inactiveTrackColor = c.buttonBorder,
                                ),
                            )
                            Text(
                                "≤ ${sliderHeadroom.toInt()}",
                                style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                                color = c.textSecondary,
                                modifier = Modifier.width(52.dp),
                            )
                        }
                    }
                }

                // Output picker
                Text(
                    "OUTPUT",
                    style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, letterSpacing = 1.5.sp),
                    color = c.textDimmest,
                )
                SegmentedToggle(
                    options = listOf(
                        com.zwheel.core.alerts.AlertOutput.AUTO to "Auto",
                        com.zwheel.core.alerts.AlertOutput.WATCH to "Watch",
                        com.zwheel.core.alerts.AlertOutput.PHONE to "Phone",
                    ),
                    selected = prefs.audioAlertOutput,
                    onSelected = onAlertOutput,
                )

                // Output hint
                val outputHint = when (prefs.audioAlertOutput) {
                    com.zwheel.core.alerts.AlertOutput.AUTO -> "Plays through the watch speaker if connected, otherwise the phone."
                    com.zwheel.core.alerts.AlertOutput.WATCH -> "Plays through the watch speaker. Falls back to phone if unavailable."
                    com.zwheel.core.alerts.AlertOutput.PHONE -> "Plays through the phone's current audio route."
                }
                Text(
                    outputHint,
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 12.sp, fontWeight = FontWeight.W400),
                    color = c.textMuted,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
```

Note: `c.textDimmest`, `c.divider`, `c.textMuted` — use the same color tokens as the rest of the file. Check the existing imports and add any needed ones (`Slider`, `SliderDefaults`, `AnimatedVisibility` are already imported in SettingsSections.kt — verify and add if missing).

The `SegmentedToggle` composable is already defined in SettingsSections.kt and works generically with any type that has an `equals()` implementation. Use it directly.

Do NOT add a `SectionEyebrow` call for the title inside the column — `SectionEyebrow("AUDIO ALERTS")` IS the section title at the top of the Column.

### `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsScreen.kt`

1. Add 5 new lambda parameters to `SettingsContent()` signature (after `onShareDebug`):
```kotlin
onAudioAlertsEnabled: (Boolean) -> Unit,
onAudioAlertType: (com.zwheel.core.alerts.AlertType) -> Unit,
onAudioAlertThresholdMph: (Int) -> Unit,
onAudioAlertThresholdHeadroom: (Int) -> Unit,
onAudioAlertOutput: (com.zwheel.core.alerts.AlertOutput) -> Unit,
```

2. Add the `AudioAlertsSection` call in the `LazyColumn` body, between the UNITS block and the HomeAssistant block (after the UNITS spacer):
```kotlin
item { Spacer(Modifier.height(22.dp)) }
item {
    AudioAlertsSection(
        prefs = preferences,
        onAlertsEnabled = onAudioAlertsEnabled,
        onAlertType = onAudioAlertType,
        onThresholdMph = onAudioAlertThresholdMph,
        onThresholdHeadroom = onAudioAlertThresholdHeadroom,
        onAlertOutput = onAudioAlertOutput,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
}
```
Place this after the UnitsSection `item` and its `Spacer`, before the HomeAssistantSection `item`.

3. In `SettingsScreen()` composable (the top-level one that calls `SettingsContent`), pass the ViewModel callbacks:
```kotlin
onAudioAlertsEnabled = viewModel::setAudioAlertsEnabled,
onAudioAlertType = viewModel::setAudioAlertType,
onAudioAlertThresholdMph = viewModel::setAudioAlertThresholdMph,
onAudioAlertThresholdHeadroom = viewModel::setAudioAlertThresholdHeadroom,
onAudioAlertOutput = viewModel::setAudioAlertOutput,
```

## Compile and verify
Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin`
Must compile clean. Fix any import errors, missing color tokens, or undefined composables.

## Commit
`feat(ui): audio alerts settings section — issue #156`

Do not modify any other files.
