package com.zwheel.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.ui.ble.BleDebugScreen
import com.zwheel.app.ui.ble.bleScanPermissions
import com.zwheel.app.ui.ble.hasAllRequiredPermissions
import com.zwheel.app.ui.ble.hasPermission
import com.zwheel.app.ui.ble.hasPermanentlyDeniedPermission
import com.zwheel.app.ui.ble.openAppSettings
import com.zwheel.core.ports.ScanResult

@Composable
fun ZWheelAppScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val requiredPermissions = remember { bleScanPermissions() }
    var permissionRequestAttempted by remember { mutableStateOf(false) }
    var permissionsGranted by remember {
        mutableStateOf(hasAllRequiredPermissions(context, requiredPermissions))
    }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantResults ->
        permissionsGranted = requiredPermissions.all { permission ->
            grantResults[permission] == true || hasPermission(context, permission)
        }
        permanentlyDenied = !permissionsGranted && hasPermanentlyDeniedPermission(
            context = context,
            permissions = requiredPermissions,
            requestAttempted = permissionRequestAttempted,
        )
    }

    fun requestBlePermissions() {
        permissionRequestAttempted = true
        permissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsGranted = hasAllRequiredPermissions(context, requiredPermissions)
        if (permissionsGranted) {
            permanentlyDenied = false
        }
    }

    LaunchedEffect(requiredPermissions) {
        if (!permissionsGranted && !permissionRequestAttempted) {
            requestBlePermissions()
        }
    }

    ZWheelDashboard(
        state = state.copy(connectionLabel = connectionState.name.uppercase()),
        connectionState = connectionState,
        devices = devices,
        permissionsGranted = permissionsGranted,
        permanentlyDenied = permanentlyDenied,
        onGrantPermissions = ::requestBlePermissions,
        onOpenSettings = { context.openAppSettings() },
        onScan = {
            if (permissionsGranted) {
                viewModel.scan()
            } else {
                requestBlePermissions()
            }
        },
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
    )
}

@Composable
private fun ZWheelDashboard(
    state: DashboardUiState,
    connectionState: ConnectionState = ConnectionState.Idle,
    devices: List<ScanResult> = emptyList(),
    permissionsGranted: Boolean = true,
    permanentlyDenied: Boolean = false,
    onGrantPermissions: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onScan: () -> Unit = {},
    onConnect: (String) -> Unit = {},
    onDisconnect: () -> Unit = {},
) {
    var showDebug by remember { mutableStateOf(false) }
    val debugVisible = showDebug || !permissionsGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xffeeeeee))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionBar(
            connectionState = connectionState,
            devices = devices,
            permissionsGranted = permissionsGranted,
            permanentlyDenied = permanentlyDenied,
            onGrantPermissions = onGrantPermissions,
            onOpenSettings = onOpenSettings,
            onScan = onScan,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )
        // Keep this debug/log panel reachable until the app is ready to publish. It is the
        // M2 hardware-capture path for permissions, BLE logs, share, pair, and upload.
        TextButton(onClick = { showDebug = !showDebug }) {
            Text(if (debugVisible) "Hide BLE debug + log upload" else "Show BLE debug + log upload")
        }
        if (debugVisible) {
            BleDebugScreen()
        }
        Header(state)
        SpeedCard(state)
        BatteryPackCard(state)
        CellVoltageCard(state.cellVoltages)
        TripStatsCard(state)
        RideModeCard(state)
    }
}
@Composable
private fun ConnectionBar(
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

@Composable
private fun Header(state: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = state.boardName,
                color = Color(0xff111111),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
            Text(
                text = "${state.connectionLabel}  RSSI ${state.rssi} dBm",
                color = Color(0xff555555),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
        }
        Text(
            text = state.firmwareLabel,
            color = Color(0xff555555),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun SpeedCard(state: DashboardUiState) {
    DashboardCard(color = Color(0xffffd400)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Label("SPEED")
                Metric(value = "%.1f".format(state.speedMph), unit = state.speedUnitLabel, size = 64)
                Text(
                    text = "TOP %.1f   RANGE %.1f %s".format(state.topSpeedMph, state.estimatedRangeMiles, state.rangeUnitLabel),
                    color = Color(0xff111111),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
            }
            SpeedGauge(progress = state.speedMph / 25.0)
        }
    }
}

@Composable
private fun BatteryPackCard(state: DashboardUiState) {
    DashboardCard(color = Color(0xffe4007f), contentColor = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Label("BATTERY PACK", Color.White)
                Metric(value = state.batteryPercent.toString(), unit = "%", size = 56)
            }
            Column(horizontalAlignment = Alignment.End) {
                SmallStat("PACK", "%.1f V".format(state.packVoltage), Color.White)
                SmallStat("AMPS", "%.1f A".format(state.amps), Color.White)
                SmallStat("TEMP", "${state.controllerTempF} ${state.temperatureUnitLabel}", Color.White)
            }
        }
    }
}

@Composable
private fun CellVoltageCard(cells: List<CellVoltageUiState>) {
    DashboardCard(color = Color(0xff7a3cff), contentColor = Color.White) {
        Label("CELL VOLTAGES", Color.White)
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 146.dp, max = 146.dp),
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
        ) {
            items(cells) { cell ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = if (cell.isLow) 0.95f else 0.18f),
                    contentColor = if (cell.isLow) Color(0xff7a003d) else Color.White,
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = cell.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.sp,
                        )
                        Text(
                            text = "%.2f".format(cell.volts),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripStatsCard(state: DashboardUiState) {
    DashboardCard(color = Color(0xff00a7c8), contentColor = Color(0xff061016)) {
        Label("TRIP STATS")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallStat("DISTANCE", "%.2f MI".format(state.tripMiles))
            SmallStat("USED", "%.2f AH".format(state.tripAmpHours))
            SmallStat("REGEN", "%.2f AH".format(state.regenAmpHours))
        }
    }
}

@Composable
private fun RideModeCard(state: DashboardUiState) {
    DashboardCard(color = Color(0xff111111), contentColor = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Label("RIDE MODE", Color.White)
                Text(
                    text = state.rideMode,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SmallStat("LIGHTS", state.lightsLabel, Color.White)
                SmallStat("TIRE", "%.1f IN".format(state.tireDiameterInches), Color.White)
            }
        }
    }
}

@Preview
@Composable
private fun ZWheelAppScreenPreview() {
    ZWheelTheme {
        ZWheelDashboard(state = mockDashboardState())
    }
}

private fun ScanResult.connectionLabel(): String =
    listOfNotNull(displayName, deviceId, rssi?.let { "$it dBm" }).joinToString("  ")
