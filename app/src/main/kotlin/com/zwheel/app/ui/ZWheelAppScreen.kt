package com.zwheel.app.ui

import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.ui.ble.bleScanPermissions
import com.zwheel.app.ui.ble.hasAllRequiredPermissions
import com.zwheel.app.ui.ble.hasLocationPermission
import com.zwheel.app.ui.ble.hasPermission
import com.zwheel.app.ui.ble.hasPermanentlyDeniedPermission
import com.zwheel.app.ui.ble.openAppSettings
import com.zwheel.app.ui.ble.openLocationPermissionSettings
import com.zwheel.app.ui.ble.rideLocationPermissions
import com.zwheel.app.ui.connect.ConnectScreen
import com.zwheel.app.ui.dashboard.DashboardScreen
import com.zwheel.app.ui.history.MapFullScreenScreen
import com.zwheel.app.ui.history.RideDetailScreen
import com.zwheel.app.ui.history.RideHistoryScreen
import com.zwheel.app.ui.nav.ZWheelBottomBar
import com.zwheel.app.ui.onboarding.OemBatteryAdviceScreen
import com.zwheel.app.ui.onboarding.batteryAdviceForManufacturer
import com.zwheel.app.ui.permissions.PermissionsScreen
import com.zwheel.app.ui.settings.SettingsScreen
import com.zwheel.core.ports.ScanResult

@Composable
fun ZWheelAppScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val c = LocalZWheelColors.current
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

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val onScan = {
        if (permissionsGranted) {
            if (!locationGranted) requestLocationPermission()
            viewModel.scan()
        } else {
            requestBlePermissions()
        }
    }
    val onConnect = { deviceId: String ->
        if (locationGranted) {
            viewModel.connect(deviceId)
        } else {
            pendingConnectDeviceId = deviceId
            requestLocationPermission()
        }
    }

    val topLevelRoutes = setOf("ride", "history", "settings")
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        containerColor = c.screenBg,
        bottomBar = {
            if (showBottomBar) {
                ZWheelBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "ride",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("ride") {
                RideTabContent(
                    permissionsGranted = permissionsGranted,
                    permanentlyDenied = permanentlyDenied,
                    connectionState = connectionState,
                    devices = devices,
                    state = state,
                    locationGranted = locationGranted,
                    locationPermanentlyDenied = locationPermanentlyDenied,
                    onGrantPermissions = ::requestBlePermissions,
                    onOpenBleSettings = { context.openAppSettings() },
                    onScan = onScan,
                    onConnect = onConnect,
                    onDisconnect = viewModel::disconnect,
                    onRequestLocation = ::requestLocationPermission,
                )
            }
            composable("history") {
                RideHistoryScreen(
                    onRideClick = { sessionId ->
                        navController.navigate("rideDetail/$sessionId")
                    }
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
                OemBatteryAdviceScreen(
                    advice = batteryAdviceForManufacturer(Build.MANUFACTURER),
                    onOpenSettings = { context.openAppSettings() },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun RideTabContent(
    permissionsGranted: Boolean,
    permanentlyDenied: Boolean,
    connectionState: ConnectionState,
    devices: List<ScanResult>,
    state: DashboardUiState,
    locationGranted: Boolean,
    locationPermanentlyDenied: Boolean,
    onGrantPermissions: () -> Unit,
    onOpenBleSettings: () -> Unit,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRequestLocation: () -> Unit,
) {
    if (!permissionsGranted) {
        PermissionsScreen(
            bleGranted = false,
            blePermanentlyDenied = permanentlyDenied,
            locationGranted = locationGranted,
            locationPermanentlyDenied = locationPermanentlyDenied,
            onRequestBle = onGrantPermissions,
            onOpenBleSettings = onOpenBleSettings,
            onRequestLocation = onRequestLocation,
            onSkipLocation = onScan,
        )
    } else if (connectionState != ConnectionState.Connected) {
        ConnectScreen(
            connectionState = connectionState,
            devices = devices,
            onScan = onScan,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )
    } else {
        DashboardScreen(
            state = state,
            onRequestLocation = onRequestLocation,
            locationGranted = locationGranted,
            locationPermanentlyDenied = locationPermanentlyDenied,
        )
    }
}
