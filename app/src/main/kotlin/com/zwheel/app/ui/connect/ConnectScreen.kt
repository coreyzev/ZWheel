package com.zwheel.app.ui.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.ui.JetBrainsMonoFamily
import com.zwheel.app.ui.LocalZWheelColors
import com.zwheel.app.ui.SairaFamily
import com.zwheel.app.ui.ZWheelTheme
import com.zwheel.core.ports.ScanResult

@Composable
fun ConnectScreen(
    connectionState: ConnectionState,
    devices: List<ScanResult>,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(c.screenBg),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Connect your board",
                    fontFamily = SairaFamily,
                    fontWeight = FontWeight.W800,
                    fontSize = 26.sp,
                    letterSpacing = (-0.4).sp,
                    color = c.textPrimary,
                )
                Text(
                    text = "Power on your Onewheel and keep it nearby.",
                    fontFamily = SairaFamily,
                    fontWeight = FontWeight.W400,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = c.textMuted,
                )
            }
        }
        item { BleStateChips(connectionState) }
        item {
            if (connectionState == ConnectionState.Scanning) {
                ScanningIndicator()
            }
        }
        items(devices) { device ->
            DeviceRow(device = device, connectionState = connectionState, onConnect = onConnect)
        }
        item {
            val scannable = connectionState != ConnectionState.Scanning && connectionState != ConnectionState.Connected
            if (devices.isEmpty() && scannable) {
                EmptyDeviceState(onScan = onScan)
            }
        }
    }
}

@Composable
private fun BleStateChips(connectionState: ConnectionState) {
    val c = LocalZWheelColors.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            ConnectionState.Idle,
            ConnectionState.Scanning,
            ConnectionState.Connected,
            ConnectionState.Disconnected,
        ).forEach { state ->
            val active = connectionState == state
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (active) c.lime else Color.Transparent,
                border = if (active) null else BorderStroke(1.dp, c.border),
            ) {
                Text(
                    text = state.name.uppercase(),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W400,
                    color = if (active) c.screenBg else c.textLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ScanningIndicator() {
    val c = LocalZWheelColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.BluetoothSearching,
            contentDescription = "Scanning",
            tint = c.cyan,
            modifier = Modifier.size(22.dp),
        )
        Text(
            "Scanning for boards...",
            fontFamily = SairaFamily,
            fontWeight = FontWeight.W600,
            fontSize = 15.sp,
            color = c.cyan,
        )
    }
}

@Composable
private fun DeviceRow(
    device: ScanResult,
    connectionState: ConnectionState,
    onConnect: (String) -> Unit,
) {
    val c = LocalZWheelColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.card,
        border = BorderStroke(1.dp, c.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        device.displayName ?: device.deviceId,
                        fontFamily = SairaFamily,
                        fontWeight = FontWeight.W700,
                        fontSize = 15.sp,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (device.displayName != null) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, c.borderLime),
                        ) {
                            // TODO: use device.boardType when available.
                            Text(
                                "PINT X",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.W700,
                                color = c.lime,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    device.rssi?.let { "$it dBm" } ?: "-",
                    color = c.textMuted,
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
            Button(
                onClick = { onConnect(device.deviceId) },
                enabled = connectionState != ConnectionState.Connected,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.lime,
                    contentColor = c.screenBg,
                    disabledContainerColor = c.cardElevated,
                    disabledContentColor = c.textDim,
                ),
            ) {
                Text(
                    "Connect",
                    fontFamily = SairaFamily,
                    fontWeight = FontWeight.W700,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyDeviceState(onScan: () -> Unit) {
    val c = LocalZWheelColors.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.BluetoothSearching,
            contentDescription = null,
            tint = c.textDim,
            modifier = Modifier.size(48.dp),
        )
        Text(
            "No boards found",
            fontFamily = SairaFamily,
            fontWeight = FontWeight.W600,
            fontSize = 16.sp,
            color = c.textSecondary,
        )
        Text(
            "Tap Scan to search for nearby boards.",
            fontFamily = SairaFamily,
            fontWeight = FontWeight.W400,
            fontSize = 14.sp,
            color = c.textMuted,
        )
        Button(
            onClick = onScan,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.lime,
                contentColor = c.screenBg,
            ),
        ) {
            Text(
                "Scan for boards",
                fontFamily = SairaFamily,
                fontWeight = FontWeight.W700,
                fontSize = 14.sp,
            )
        }
    }
}

@Preview
@Composable
private fun ConnectScreenPreview() {
    ZWheelTheme {
        ConnectScreen(
            connectionState = ConnectionState.Scanning,
            devices = listOf(
                ScanResult("board-1", "Corey's Pint X", -57),
                ScanResult("board-2", null, -71),
            ),
            onScan = {},
            onConnect = {},
            onDisconnect = {},
        )
    }
}
