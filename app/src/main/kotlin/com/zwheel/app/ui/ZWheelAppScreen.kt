package com.zwheel.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import android.os.Build
import android.os.PowerManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.ui.ble.BleDebugScreen
import com.zwheel.app.ui.onboarding.OemBatteryAdviceScreen
import com.zwheel.app.ui.onboarding.batteryAdviceForManufacturer
import com.zwheel.app.ui.ble.bleScanPermissions
import com.zwheel.app.ui.ble.hasLocationPermission
import com.zwheel.app.ui.ble.rideLocationPermissions
import com.zwheel.app.ui.history.MapFullScreenScreen
import com.zwheel.app.ui.history.RideDetailScreen
import com.zwheel.app.ui.history.RideHistoryScreen
import com.zwheel.app.ui.settings.SettingsScreen
import com.zwheel.app.ui.ble.hasAllRequiredPermissions
import com.zwheel.app.ui.ble.hasPermission
import com.zwheel.app.ui.ble.hasPermanentlyDeniedPermission
import com.zwheel.app.ui.ble.openAppSettings
import com.zwheel.app.ui.ble.openLocationPermissionSettings
import com.zwheel.core.ports.ScanResult

@Composable
fun ZWheelAppScreen() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            ZWheelDashboardScreen(
                onOpenHistory = { navController.navigate("history") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenBatteryAdvice = { navController.navigate("battery") },
            )
        }
        composable("history") {
            RideHistoryScreen(
                onRideClick = { sessionId -> navController.navigate("rideDetail/$sessionId") }
            )
        }
        composable("rideDetail/{sessionId}") {
            val sessionId = it.arguments?.getString("sessionId") ?: return@composable
            RideDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenMap = { navController.navigate("mapFullScreen/$sessionId") },
            )
        }
        composable("mapFullScreen/{sessionId}") {
            MapFullScreenScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") { SettingsScreen() }
        composable("battery") {
            val context = LocalContext.current
            OemBatteryAdviceScreen(
                advice = batteryAdviceForManufacturer(Build.MANUFACTURER),
                onOpenSettings = { context.openAppSettings() },
                onDone = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun ZWheelDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBatteryAdvice: () -> Unit = {},
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
    var batteryOptimized by remember { mutableStateOf(false) }

    val locationPermissions = remember { rideLocationPermissions() }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    // Persisted in DataStore: survives app restarts so "GPS DENIED" shows immediately on the
    // next session after permanent denial, without requiring a wasted first tap to discover it.
    val locationPermissionAttempted by viewModel.locationPermissionAttempted.collectAsStateWithLifecycle()
    var pendingConnectDeviceId by remember { mutableStateOf<String?>(null) }

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

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        locationGranted = locationPermissions.all { results[it] == true || hasPermission(context, it) }
        if (locationGranted) {
            pendingConnectDeviceId?.let { viewModel.connect(it) }
        }
        pendingConnectDeviceId = null
    }

    fun requestBlePermissions() {
        permissionRequestAttempted = true
        permissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    fun requestLocationPermission() {
        // Only route to Settings when we are sure the permission is permanently denied:
        // locationPermissionAttempted (persisted) AND shouldShowRationale is now false.
        val permanentlyDenied = locationPermissionAttempted && hasPermanentlyDeniedPermission(
            context = context,
            permissions = locationPermissions,
            requestAttempted = true,
        )
        if (permanentlyDenied) {
            context.openLocationPermissionSettings()
            return
        }
        // Persist the attempt before launching so that even if the Activity is killed
        // mid-dialog, the next session knows a request was previously made.
        viewModel.markLocationPermissionAttempted()
        locationLauncher.launch(locationPermissions.toTypedArray())
    }

    // Derived — no mutable state; recomputed on each recomposition from current system state.
    val locationPermanentlyDenied = !locationGranted && locationPermissionAttempted &&
        hasPermanentlyDeniedPermission(context, locationPermissions, requestAttempted = true)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsGranted = hasAllRequiredPermissions(context, requiredPermissions)
        if (permissionsGranted) permanentlyDenied = false
        locationGranted = hasLocationPermission(context)
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        batteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
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
        locationGranted = locationGranted,
        locationPermanentlyDenied = locationPermanentlyDenied,
        onGrantPermissions = ::requestBlePermissions,
        onRequestLocation = ::requestLocationPermission,
        onOpenSettings = { context.openAppSettings() },
        onScan = {
            if (permissionsGranted) {
                if (!locationGranted) requestLocationPermission()
                viewModel.scan()
            } else {
                requestBlePermissions()
            }
        },
        onConnect = { deviceId ->
            if (locationGranted) {
                viewModel.connect(deviceId)
            } else {
                pendingConnectDeviceId = deviceId
                requestLocationPermission()
            }
        },
        onDisconnect = viewModel::disconnect,
        onOpenHistory = onOpenHistory,
        onOpenSettingsScreen = onOpenSettings,
        onOpenBatteryAdvice = onOpenBatteryAdvice,
        batteryOptimized = batteryOptimized,
    )
}

@Composable
private fun ZWheelDashboard(
    state: DashboardUiState,
    connectionState: ConnectionState = ConnectionState.Idle,
    devices: List<ScanResult> = emptyList(),
    permissionsGranted: Boolean = true,
    permanentlyDenied: Boolean = false,
    locationGranted: Boolean = true,
    locationPermanentlyDenied: Boolean = false,
    onGrantPermissions: () -> Unit = {},
    onRequestLocation: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onScan: () -> Unit = {},
    onConnect: (String) -> Unit = {},
    onDisconnect: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenSettingsScreen: () -> Unit = {},
    onOpenBatteryAdvice: () -> Unit = {},
    batteryOptimized: Boolean = false,
) {
    // Debug panel is an explicit opt-in only; never driven by permission state.
    var debugVisible by remember { mutableStateOf(false) }

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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Keep this debug/log panel reachable until the app is ready to publish.
            TextButton(onClick = { debugVisible = !debugVisible }) {
                Text(if (debugVisible) "Hide BLE debug" else "Show BLE debug")
            }
            Row {
                TextButton(onClick = onOpenHistory) { Text("History") }
                TextButton(onClick = onOpenSettingsScreen) { Text("Settings") }
            }
        }
        if (batteryOptimized) {
            DashboardCard(color = Color(0xfff59e0b), contentColor = Color(0xff111111)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Battery optimization is ON — ZWheel may be killed mid-ride.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenBatteryAdvice) { Text("Fix") }
                }
            }
        }
        if (debugVisible) {
            BleDebugScreen()
        }
        Header(state)
        SpeedCard(state)
        BatteryPackCard(state)
        CellVoltageCard(state.cellVoltages)
        TripStatsCard(
            state,
            locationGranted = locationGranted,
            locationPermanentlyDenied = locationPermanentlyDenied,
            onRequestLocation = onRequestLocation,
        )
        RideModeCard(state)
    }
}

@Preview
@Composable
private fun ZWheelAppScreenPreview() {
    ZWheelTheme {
        ZWheelDashboard(state = mockDashboardState())
    }
}
