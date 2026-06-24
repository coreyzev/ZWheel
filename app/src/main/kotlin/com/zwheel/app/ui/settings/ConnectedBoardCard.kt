package com.zwheel.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.core.model.BoardIdentity
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.BoardType

private val TIRE_DIAMETER_RANGE = 8f..13f
private val disconnectBg = Color(0xFF1A0E0E)

@Composable
internal fun ConnectedBoardCard(
    boardState: BoardState,
    effectiveIdentity: BoardIdentity?,
    rssi: Int?,
    cellCount: Int?,
    customBoardName: String?,
    tireDiameterInches: Double,
    onSaveName: (String?) -> Unit,
    onSaveTireDiameter: (Double) -> Unit,
    onDisconnect: () -> Unit,
    onForgetBoard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current
    val displayName = customBoardName?.takeIf { it.isNotBlank() }
        ?: effectiveIdentity?.name
        ?: "Not connected"
    val connected = boardState.identity != null
    var editingName by remember { mutableStateOf(false) }
    var editText by remember(displayName) { mutableStateOf(displayName) }
    var editingTire by remember { mutableStateOf(false) }
    var sliderValue by remember(tireDiameterInches) { mutableFloatStateOf(tireDiameterInches.toFloat()) }
    var deviceInfoExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (deviceInfoExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "device-info-chevron",
    )

    val monoLabel = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontSize = 9.sp,
        letterSpacing = 1.sp,
    )
    val monoValue = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.W700,
        fontFeatureSettings = "tnum",
    )

    Column(modifier = modifier) {

        // ── 1. Identity row ───────────────────────────────────────────────────
        if (editingName) {
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
                        editingName = false
                    }),
                    cursorBrush = SolidColor(c.lime),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, c.buttonBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Save board name",
                    tint = c.lime,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            onSaveName(editText.trim().ifBlank { null })
                            editingName = false
                        },
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val dotColor = if (connected) c.rampGood else c.textDim
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .then(
                            if (connected) Modifier.drawBehind { drawCircle(c.rampGood.copy(alpha = 0.35f), radius = 14.dp.toPx()) }
                            else Modifier,
                        )
                        .background(dotColor, CircleShape),
                )
                Text(
                    text = displayName,
                    style = TextStyle(
                        fontFamily = SairaFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W700,
                    ),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { editingName = true },
                )
                BoardTypeBadge(effectiveIdentity?.type ?: BoardType.UNKNOWN)
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit board name",
                    tint = c.textMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { editingName = true },
                )
            }
        }

        // ── 2. Stats band ─────────────────────────────────────────────────────
        val lifetimeMiles = effectiveIdentity?.lifetimeMiles
        val lifetimeAh = effectiveIdentity?.lifetimeAmpHours
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(10.dp))
                .background(c.insetRow),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("LIFETIME ODO", style = monoLabel, color = c.textDimmest)
                Text(
                    text = if (lifetimeMiles != null) "$lifetimeMiles mi" else "—",
                    style = monoValue.copy(fontSize = 20.sp),
                    color = if (lifetimeMiles != null) c.textPrimary else c.textDim,
                )
            }
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .background(c.divider),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("LIFETIME AH", style = monoLabel, color = c.textDimmest)
                Text(
                    text = if (lifetimeAh != null) "%.1f Ah".format(lifetimeAh) else "—",
                    style = monoValue.copy(fontSize = 20.sp),
                    color = if (lifetimeAh != null) c.textPrimary else c.textDim,
                )
            }
        }

        // ── 3. Tire diameter row (bordered top + bottom) ──────────────────────
        HorizontalDivider(
            color = c.divider,
            thickness = 0.5.dp,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (editingTire) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Tire diameter",
                        style = TextStyle(fontFamily = SairaFamily, fontSize = 13.sp, fontWeight = FontWeight.W600),
                        color = c.textSecondary,
                    )
                    Text("%.1f in".format(sliderValue), style = monoValue, color = c.lime)
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = TIRE_DIAMETER_RANGE,
                    colors = SliderDefaults.colors(
                        thumbColor = c.lime,
                        activeTrackColor = c.lime,
                        inactiveTrackColor = c.border,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("8 in", style = monoLabel, color = c.textDim)
                    Text("13 in", style = monoLabel, color = c.textDim)
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = {
                            sliderValue = tireDiameterInches.toFloat()
                            editingTire = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = c.textSecondary),
                    ) {
                        Text("Cancel", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 14.sp)
                    }
                    TextButton(
                        onClick = {
                            onSaveTireDiameter(sliderValue.toDouble())
                            editingTire = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = c.lime),
                    ) {
                        Text("Save", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 14.sp)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingTire = true }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tire diameter",
                    style = TextStyle(fontFamily = SairaFamily, fontSize = 13.sp, fontWeight = FontWeight.W600),
                    color = c.textSecondary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "%.1f in".format(tireDiameterInches),
                        style = monoValue,
                        color = c.lime,
                    )
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit tire diameter",
                        tint = c.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = c.divider, thickness = 0.5.dp)

        // ── 4. Action buttons ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onDisconnect,
                enabled = connected,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = disconnectBg,
                    contentColor = c.rampDanger,
                    disabledContainerColor = c.cardElevated,
                    disabledContentColor = c.textDim,
                ),
            ) {
                Text("Disconnect", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 13.sp)
            }
            Button(
                onClick = onForgetBoard,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.cardElevated,
                    contentColor = c.textLabel,
                ),
            ) {
                Text("Forget board", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 13.sp)
            }
        }

        // ── 5. Device Info expander ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clickable { deviceInfoExpanded = !deviceInfoExpanded },
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
                contentDescription = if (deviceInfoExpanded) "Collapse" else "Expand",
                tint = c.textMuted,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
        AnimatedVisibility(visible = deviceInfoExpanded) {
            val rows = listOf(
                "Serial" to (effectiveIdentity?.serialNumber ?: "—"),
                "Battery serial" to (effectiveIdentity?.batterySerialNumber ?: "—"),
                "Battery cells" to (cellCount?.let { "${it}S" } ?: "—"),
                "Hardware rev" to (effectiveIdentity?.hardwareRevision ?: "—"),
                "Firmware" to (effectiveIdentity?.firmwareRevision ?: "—"),
                "RSSI" to (rssi?.let { "$it dBm" } ?: "—"),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.insetRow),
            ) {
                rows.forEachIndexed { index, (label, value) ->
                    DeviceInfoRow(label = label, value = value)
                    if (index < rows.lastIndex) {
                        HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    val c = LocalZWheelColors.current
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

@Composable
private fun BoardTypeBadge(type: BoardType) {
    val c = LocalZWheelColors.current
    val label = when (type) {
        BoardType.PINT_X -> "PINT X"
        BoardType.XRC -> "XRC"
        BoardType.XR -> "XR"
        BoardType.PINT -> "PINT"
        BoardType.PLUS -> "PLUS"
        BoardType.ONEWHEEL_V1 -> "OW V1"
        BoardType.UNKNOWN -> return
    }
    Surface(
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, c.borderLime),
        color = Color.Transparent,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.W700,
            ),
            color = c.lime,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
