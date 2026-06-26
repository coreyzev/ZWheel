package com.zwheel.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.zwheel.app.ui.ZWheelColors
import com.zwheel.core.alerts.AlertOutput
import com.zwheel.core.alerts.AlertType
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit
import kotlin.math.roundToInt

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
            color = Color(0xFF3A3E48),
        )
    }
}

@Composable
internal fun AudioAlertsSection(
    prefs: UserPreferences,
    onAlertsEnabled: (Boolean) -> Unit,
    onAlertType: (AlertType) -> Unit,
    onThresholdMph: (Int) -> Unit,
    onThresholdHeadroom: (Int) -> Unit,
    onAlertOutput: (AlertOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionEyebrow("AUDIO ALERTS")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = c.card,
            border = BorderStroke(1.dp, c.border),
        ) {
            Column {
                // Enabled row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        val on = prefs.audioAlertsEnabled
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Color(0xFF15190D) else c.legendCard)
                                .border(1.dp, if (on) c.borderLime else c.border, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (on) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (on) c.lime else c.textLabel,
                                modifier = Modifier.size(23.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Alerts",
                                style = TextStyle(fontFamily = SairaFamily, fontSize = 16.sp, fontWeight = FontWeight.W700),
                                color = c.textPrimary,
                            )
                            Text(
                                if (on) "On — you'll hear it while you ride" else "Off — silent while you ride",
                                style = TextStyle(fontFamily = SairaFamily, fontSize = 12.sp),
                                color = c.textMuted,
                            )
                        }
                    }
                    Switch(
                        checked = prefs.audioAlertsEnabled,
                        onCheckedChange = onAlertsEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.screenBg,
                            checkedTrackColor = c.lime,
                            uncheckedTrackColor = c.buttonBorder,
                            uncheckedThumbColor = c.screenBg,
                        ),
                    )
                }

                AnimatedVisibility(visible = prefs.audioAlertsEnabled) {
                    Column {
                        HorizontalDivider(color = c.divider, thickness = 1.dp)
                        AlertTypeSection(prefs.audioAlertType, onAlertType)
                        HorizontalDivider(color = c.divider, thickness = 1.dp)
                        ThresholdSection(prefs, onThresholdMph, onThresholdHeadroom)
                        HorizontalDivider(color = c.divider, thickness = 1.dp)
                        OutputSection(prefs.audioAlertOutput, onAlertOutput)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertTypeSection(type: AlertType, onType: (AlertType) -> Unit) {
    val c = LocalZWheelColors.current
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "What should trigger an alert?",
                style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W700),
                color = c.textPrimary,
            )
            Text(
                if (type == AlertType.HEADROOM)
                    "Warns as your safety margin runs low — the headroom before the board gives out. Recommended."
                else
                    "Warns whenever you cross a top speed you set. Good for pacing, not for catching pushback.",
                style = TextStyle(fontFamily = SairaFamily, fontSize = 12.sp),
                color = c.textMuted,
                lineHeight = 18.sp,
            )
        }
        IconSegmented(
            options = listOf(
                Triple(AlertType.SPEED, Icons.Default.Speed, "Speed"),
                Triple(AlertType.HEADROOM, Icons.Default.Shield, "Headroom"),
            ),
            selected = type,
            onSelected = onType,
        )
    }
}

@Composable
private fun ThresholdSection(prefs: UserPreferences, onMph: (Int) -> Unit, onHeadroom: (Int) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        when (prefs.audioAlertType) {
            AlertType.HEADROOM -> HeadroomThreshold(prefs.audioAlertThresholdHeadroom, onHeadroom)
            AlertType.SPEED -> SpeedThreshold(prefs.audioAlertThresholdMph, onMph)
        }
    }
}

@Composable
private fun HeadroomThreshold(threshold: Int, onThreshold: (Int) -> Unit) {
    val c = LocalZWheelColors.current
    var localVal by remember(threshold) { mutableStateOf(threshold.toFloat()) }
    val rampColor = headroomRampColor(localVal.toInt(), c)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    "Alert when headroom falls to",
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W700),
                    color = c.textPrimary,
                )
                Text(
                    "0–100% · drag to set",
                    style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, letterSpacing = 1.sp),
                    color = c.textLabel,
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${localVal.toInt()}",
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 38.sp, fontWeight = FontWeight.W900, lineHeight = 32.sp),
                    color = rampColor,
                )
                Text(
                    "%",
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 18.sp, fontWeight = FontWeight.W700),
                    color = rampColor,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }

        GradientSlider(
            normalizedValue = localVal / 100f,
            onNormalizedChange = { t -> localVal = (t * 100).roundToInt().coerceIn(0, 100).toFloat() },
            onFinished = { onThreshold(localVal.toInt()) },
            thumbRingColor = rampColor,
            c = c,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0% floor", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp), color = c.rampDanger)
            Text("50%", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp), color = c.textLabel)
            Text("100% full", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp), color = c.rampGood)
        }

        Text(
            if (localVal.toInt() == 0)
                "Only at 0% — the absolute floor, the instant before the board can no longer hold you up. Set it higher for an earlier heads-up."
            else
                "Fires the moment your reserve drops to ${localVal.toInt()}% or below. A higher number warns you sooner.",
            style = TextStyle(fontFamily = SairaFamily, fontSize = 12.sp),
            color = c.textMuted,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun SpeedThreshold(thresholdMph: Int, onThreshold: (Int) -> Unit) {
    val c = LocalZWheelColors.current
    var localVal by remember(thresholdMph) { mutableStateOf(thresholdMph.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    "Alert when I ride faster than",
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W700),
                    color = c.textPrimary,
                )
                Text(
                    "10–35 mph · drag to set",
                    style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, letterSpacing = 1.sp),
                    color = c.textLabel,
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${localVal.toInt()}",
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 38.sp, fontWeight = FontWeight.W900, lineHeight = 32.sp),
                    color = c.lime,
                )
                Text(
                    "mph",
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 15.sp, fontWeight = FontWeight.W700),
                    color = c.textSecondary,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }

        SpeedSlider(
            normalizedValue = (localVal - 10f) / 25f,
            onNormalizedChange = { t -> localVal = (10 + t * 25).roundToInt().coerceIn(10, 35).toFloat() },
            onFinished = { onThreshold(localVal.toInt()) },
            c = c,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("10", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp), color = c.textLabel)
            Text("22", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp), color = c.textLabel)
            Text("35 mph", style = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 9.sp), color = c.textLabel)
        }

        Text(
            "Fires once each time your speed climbs past ${localVal.toInt()} mph.",
            style = TextStyle(fontFamily = SairaFamily, fontSize = 12.sp),
            color = c.textMuted,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun OutputSection(output: AlertOutput, onOutput: (AlertOutput) -> Unit) {
    val c = LocalZWheelColors.current
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(
            "Where should it play?",
            style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W700),
            color = c.textPrimary,
        )
        IconSegmented(
            options = listOf(
                Triple(AlertOutput.WATCH, Icons.Default.Watch, "Watch"),
                Triple(AlertOutput.PHONE, Icons.Default.Smartphone, "Phone"),
            ),
            selected = output,
            onSelected = onOutput,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.mapBg)
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = c.cyan,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 1.dp),
            )
            Text(
                if (output == AlertOutput.WATCH)
                    "Plays through the watch speaker. Falls back to the phone if the watch is unavailable."
                else
                    "Plays through the phone's current audio route — speaker, headphones, or car stereo.",
                style = TextStyle(fontFamily = SairaFamily, fontSize = 12.sp),
                color = c.textMuted,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun GradientSlider(
    normalizedValue: Float,
    onNormalizedChange: (Float) -> Unit,
    onFinished: () -> Unit,
    thumbRingColor: Color,
    c: ZWheelColors,
) {
    val thumbRadiusDp = 10.dp
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .height(20.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val compute = { x: Float ->
                        val tR = thumbRadiusDp.toPx()
                        val tW = size.width - 2 * tR
                        ((x - tR) / tW).coerceIn(0f, 1f)
                    }
                    onNormalizedChange(compute(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        onNormalizedChange(compute(change.position.x))
                    }
                    onFinished()
                }
            },
    ) {
        val thumbR = thumbRadiusDp.toPx()
        val trackH = 8.dp.toPx()
        val trackStart = thumbR
        val trackEnd = size.width - thumbR
        val trackW = trackEnd - trackStart
        val centerY = size.height / 2

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(0f to c.rampDanger, 0.45f to c.rampCaution, 1f to c.rampGood),
                startX = trackStart,
                endX = trackEnd,
            ),
            topLeft = Offset(trackStart, centerY - trackH / 2),
            size = Size(trackW, trackH),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )

        val thumbX = trackStart + normalizedValue * trackW
        drawCircle(Color(0x55000000), radius = thumbR + 2.dp.toPx(), center = Offset(thumbX, centerY + 1.5.dp.toPx()))
        drawCircle(Color.White, radius = thumbR, center = Offset(thumbX, centerY))
        drawCircle(thumbRingColor, radius = thumbR - 1.5.dp.toPx(), center = Offset(thumbX, centerY), style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun SpeedSlider(
    normalizedValue: Float,
    onNormalizedChange: (Float) -> Unit,
    onFinished: () -> Unit,
    c: ZWheelColors,
) {
    val thumbRadiusDp = 10.dp
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .height(20.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val compute = { x: Float ->
                        val tR = thumbRadiusDp.toPx()
                        val tW = size.width - 2 * tR
                        ((x - tR) / tW).coerceIn(0f, 1f)
                    }
                    onNormalizedChange(compute(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        onNormalizedChange(compute(change.position.x))
                    }
                    onFinished()
                }
            },
    ) {
        val thumbR = thumbRadiusDp.toPx()
        val trackH = 8.dp.toPx()
        val trackStart = thumbR
        val trackEnd = size.width - thumbR
        val trackW = trackEnd - trackStart
        val centerY = size.height / 2
        val fillW = normalizedValue * trackW

        drawRoundRect(
            color = c.buttonBorder,
            topLeft = Offset(trackStart, centerY - trackH / 2),
            size = Size(trackW, trackH),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        if (fillW > 0) {
            drawRoundRect(
                color = c.lime,
                topLeft = Offset(trackStart, centerY - trackH / 2),
                size = Size(fillW + thumbR, trackH),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }

        val thumbX = trackStart + normalizedValue * trackW
        drawCircle(Color(0x55000000), radius = thumbR + 2.dp.toPx(), center = Offset(thumbX, centerY + 1.5.dp.toPx()))
        drawCircle(Color.White, radius = thumbR, center = Offset(thumbX, centerY))
        drawCircle(c.lime, radius = thumbR - 1.5.dp.toPx(), center = Offset(thumbX, centerY), style = Stroke(3.dp.toPx()))
    }
}

private fun headroomRampColor(pct: Int, c: ZWheelColors): Color = when {
    pct < 25 -> c.rampDanger
    pct < 50 -> c.rampCaution
    else -> c.rampGood
}

@Composable
private fun <T> IconSegmented(
    options: List<Triple<T, ImageVector, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    val c = LocalZWheelColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .border(1.dp, c.border, RoundedCornerShape(11.dp))
            .background(c.screenBg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (option, icon, label) ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) c.lime else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) c.screenBg else c.textMuted,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = label,
                        style = TextStyle(fontFamily = SairaFamily, fontSize = 14.sp, fontWeight = FontWeight.W700),
                        color = if (isSelected) c.screenBg else c.textMuted,
                    )
                }
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
