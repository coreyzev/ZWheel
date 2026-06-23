package com.zwheel.app.ui.connect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.zwheel.core.model.BoardType
import com.zwheel.core.ports.ScanResult

@Composable
fun ConnectScreen(
    connectionState: ConnectionState,
    devices: List<ScanResult>,
    savedBoardDeviceId: String?,
    savedBoardType: BoardType?,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current
    val scanning = connectionState == ConnectionState.Scanning

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.screenBg),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
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
            items(devices) { device ->
                val knownType = if (device.deviceId.equals(savedBoardDeviceId, ignoreCase = true)) savedBoardType else null
                DeviceRow(device = device, connectionState = connectionState, knownBoardType = knownType, onConnect = onConnect)
            }
        }

        ScanButton(
            scanning = scanning,
            onScan = onScan,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun ScanButton(
    scanning: Boolean,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalZWheelColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "icon_rotation",
    )

    Button(
        onClick = onScan,
        enabled = !scanning,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.lime,
            contentColor = c.screenBg,
            disabledContainerColor = c.cardElevated,
            disabledContentColor = c.textSecondary,
        ),
        modifier = modifier,
    ) {
        Icon(
            Icons.Filled.BluetoothSearching,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .then(if (scanning) Modifier.rotate(rotation) else Modifier),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (scanning) "Searching..." else "Scan for boards",
            fontFamily = SairaFamily,
            fontWeight = FontWeight.W700,
            fontSize = 15.sp,
        )
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
            ConnectionState.Connecting,
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
private fun DeviceRow(
    device: ScanResult,
    connectionState: ConnectionState,
    knownBoardType: BoardType?,
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
                    if (knownBoardType != null && knownBoardType != BoardType.UNKNOWN) {
                        val label = when (knownBoardType) {
                            BoardType.PINT_X -> "PINT X"
                            BoardType.XRC -> "XRC"
                            BoardType.XR -> "XR"
                            BoardType.PINT -> "PINT"
                            BoardType.PLUS -> "PLUS"
                            BoardType.ONEWHEEL_V1 -> "OW V1"
                            BoardType.UNKNOWN -> ""
                        }
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, c.borderLime),
                        ) {
                            Text(
                                label,
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
            savedBoardDeviceId = "board-1",
            savedBoardType = BoardType.PINT_X,
            onScan = {},
            onConnect = {},
            onDisconnect = {},
        )
    }
}
