package com.zwheel.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zwheel.app.ble.ConnectionState
import com.zwheel.core.ports.ScanResult

@Composable
internal fun ConnectionBar(
    connectionState: ConnectionState,
    devices: List<ScanResult>,
    permissionsGranted: Boolean,
    permanentlyDenied: Boolean,
    onGrantPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    DashboardCard(color = Color.White, contentColor = Color(0xff111111)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Label("CONNECTION")
                    Text(
                        text = connectionState.name.uppercase(),
                        color = Color(0xff111111),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (permissionsGranted) {
                        Button(
                            enabled = connectionState != ConnectionState.Scanning &&
                                connectionState != ConnectionState.Connected,
                            onClick = onScan,
                        ) {
                            Text(if (connectionState == ConnectionState.Scanning) "Scanning" else "Scan")
                        }
                    } else {
                        Button(onClick = onGrantPermissions) {
                            Text("Grant permissions")
                        }
                    }
                    Button(
                        enabled = connectionState == ConnectionState.Connected,
                        onClick = onDisconnect,
                    ) {
                        Text("Disconnect")
                    }
                }
            }
            if (!permissionsGranted) {
                Text(
                    text = "Bluetooth permissions are required before ZWheel can scan for your board.",
                    color = Color(0xff555555),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
                if (permanentlyDenied) {
                    TextButton(onClick = onOpenSettings) {
                        Text("Open app settings")
                    }
                }
            }
            devices.take(4).forEach { device ->
                TextButton(enabled = connectionState != ConnectionState.Connected, onClick = { onConnect(device.deviceId) }) {
                    Text(device.connectionLabel())
                }
            }
        }
    }
}

internal fun ScanResult.connectionLabel(): String =
    listOfNotNull(displayName, deviceId, rssi?.let { "$it dBm" }).joinToString("  ")
