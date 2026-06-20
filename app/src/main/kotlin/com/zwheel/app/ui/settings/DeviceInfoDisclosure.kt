package com.zwheel.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.core.model.BoardIdentity

@Composable
internal fun DeviceInfoDisclosure(
    identity: BoardIdentity?,
    rssi: Int?,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "device-info-chevron",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
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
                modifier = Modifier
                    .padding(start = 8.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
        }
        AnimatedVisibility(visible = expanded) {
            val rows = listOf(
                "Serial" to (identity?.serialNumber ?: "—"),
                // TODO(battery-serial): BoardIdentity does not yet carry batterySerialNumber.
                // Wire OwUuids.BATTERY_SERIAL through BLE and BoardIdentity in a future gate.
                "Battery serial" to "—",
                "Hardware rev" to (identity?.hardwareRevision ?: "—"),
                "Firmware" to (identity?.firmwareRevision ?: "—"),
                "RSSI" to (rssi?.let { "$it dBm" } ?: "—"),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.insetRow),
            ) {
                rows.forEachIndexed { index, row ->
                    DeviceInfoRow(label = row.first, value = row.second)
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
            style = TextStyle(
                fontFamily = SairaFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.W400,
            ),
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
