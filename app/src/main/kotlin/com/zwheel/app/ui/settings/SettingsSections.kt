package com.zwheel.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.BuildConfig
import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit

@Composable
internal fun UnitsSection(
    prefs: UserPreferences,
    onSpeedUnit: (SpeedUnit) -> Unit,
    onTempUnit: (TemperatureUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedToggle(
            options = listOf(SpeedUnit.MPH to "MPH", SpeedUnit.KPH to "KPH"),
            selected = prefs.speedUnit,
            onSelected = onSpeedUnit,
        )
        SegmentedToggle(
            options = listOf(TemperatureUnit.FAHRENHEIT to "°F", TemperatureUnit.CELSIUS to "°C"),
            selected = prefs.temperatureUnit,
            onSelected = onTempUnit,
        )
    }
}

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
                    "BLE debug view",
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
                            .border(1.dp, c.buttonBorder, RoundedCornerShape(10.dp))
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

@Composable
internal fun SupportSection(modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow("SUPPORT")
        Text(
            "ZWheel is free and open source. If you enjoy it, consider buying the developer a coffee.",
            style = TextStyle(fontFamily = SairaFamily, fontSize = 13.sp, fontWeight = FontWeight.W400),
            color = c.textMuted,
            lineHeight = 19.sp,
        )
        OutlinedButton(
            onClick = { /* TODO(support): open donation URL. */ },
            border = BorderStroke(1.dp, c.buttonBorder),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
        ) {
            Text("Support development", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun AboutSection(modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
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
}

@Composable
internal fun SettingsFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ZWheel · v${BuildConfig.VERSION_NAME}",
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.W400,
            ),
            color = Color(0xFF3A3E48), // spec §9 footer dim
        )
    }
}

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Speed alerts",
                style = TextStyle(
                    fontFamily = SairaFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                ),
                color = c.textSecondary,
            )
            Switch(
                checked = prefs.audioAlertsEnabled,
                onCheckedChange = onAlertsEnabled,
                colors = settingsSwitchColors(),
            )
        }

        AnimatedVisibility(visible = prefs.audioAlertsEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = c.divider, thickness = 0.5.dp)

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

                val thresholdLabel = when (prefs.audioAlertType) {
                    com.zwheel.core.alerts.AlertType.SPEED -> "THRESHOLD (mph)"
                    com.zwheel.core.alerts.AlertType.HEADROOM -> "THRESHOLD (headroom <=)"
                }
                Text(
                    thresholdLabel,
                    style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, letterSpacing = 1.5.sp),
                    color = c.textDimmest,
                )

                when (prefs.audioAlertType) {
                    com.zwheel.core.alerts.AlertType.SPEED -> {
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
                                valueRange = 0f..100f,
                                steps = 99,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = c.lime,
                                    activeTrackColor = c.lime,
                                    inactiveTrackColor = c.buttonBorder,
                                ),
                            )
                            Text(
                                "<= ${sliderHeadroom.toInt()}",
                                style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                                color = c.textSecondary,
                                modifier = Modifier.width(52.dp),
                            )
                        }
                    }
                }

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

                val outputHint = when (prefs.audioAlertOutput) {
                    com.zwheel.core.alerts.AlertOutput.AUTO ->
                        "Plays through the watch speaker if connected, otherwise the phone."
                    com.zwheel.core.alerts.AlertOutput.WATCH ->
                        "Plays through the watch speaker. Falls back to phone if unavailable."
                    com.zwheel.core.alerts.AlertOutput.PHONE ->
                        "Plays through the phone's current audio route."
                }
                Text(
                    outputHint,
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                    ),
                    color = c.textMuted,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedToggle(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit) {
    val c = LocalZWheelColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
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
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W700),
                    color = if (isSelected) c.screenBg else c.textSecondary,
                )
            }
        }
    }
}

@Composable
internal fun SectionEyebrow(text: String, modifier: Modifier = Modifier) {
    val c = LocalZWheelColors.current
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

@Composable
internal fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = LocalZWheelColors.current.lime,
    checkedTrackColor = LocalZWheelColors.current.lime.copy(alpha = 0.3f),
    uncheckedTrackColor = LocalZWheelColors.current.buttonBorder,
    uncheckedThumbColor = LocalZWheelColors.current.textMuted,
)
