package com.zwheel.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zwheel.app.ble.ConnectionState
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
import com.zwheel.app.ui.settings.SettingsViewModel
import com.zwheel.core.model.BoardType
import com.zwheel.core.ports.ScanResult

@Composable
fun ZWheelAppScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
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
    val settingsPreferences by settingsViewModel.preferences.collectAsStateWithLifecycle()
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
                    savedBoardDeviceId = settingsPreferences.lastConnectedDeviceId,
                    savedBoardType = settingsPreferences.lastConnectedBoardType,
                    onGrantPermissions = ::requestBlePermissions,
                    onOpenBleSettings = { context.openAppSettings() },
                    onScan = onScan,
                    onConnect = onConnect,
                    onDisconnect = viewModel::disconnect,
                    onRequestLocation = ::requestLocationPermission,
                    onOpenLocationSettings = { context.openAppSettings() },
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
            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onDisconnect = viewModel::disconnect,
                    onForgetBoard = {
                        viewModel.disconnect()
                        settingsViewModel.forgetBoard()
                    },
                )
            }
            composable("battery") {
                OemBatteryAdviceScreen(
                    advice = batteryAdviceForManufacturer(Build.MANUFACTURER),
                    deviceLabel = Build.MANUFACTURER.uppercase() + " DETECTED",
                    onOpenSettings = { context.openAppSettings() },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

// FINE and COARSE must be requested together: on Android 12+ a FINE-only runtime
// request is silently ignored (no system dialog appears, result is immediate denial).
private fun rideLocationPermissions(): List<String> =
    listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

// GPS ride tracking needs precise location; coarse-only grants are treated as not granted
// so the dashboard keeps offering the upgrade-to-precise prompt.
private fun hasLocationPermission(context: Context): Boolean =
    hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

private fun bleScanPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // BLUETOOTH_SCAN is declared neverForLocation in the manifest;
        // location permission is requested separately when a ride starts.
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasAllRequiredPermissions(
    context: Context,
    permissions: List<String>,
): Boolean = permissions.all { hasPermission(context, it) }

private fun hasPermanentlyDeniedPermission(
    context: Context,
    permissions: List<String>,
    requestAttempted: Boolean,
): Boolean {
    val activity = context.findActivity() ?: return false
    return requestAttempted && permissions.any { permission ->
        !hasPermission(context, permission) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
}

// Opens the app's details page in system Settings, which has a one-tap "Permissions" entry.
// There is no reliable public intent to deep-link straight to the permissions list because
// the internal MANAGE_APP_PERMISSIONS action is protected and throws on many devices.
private fun Context.openLocationPermissionSettings() = openAppSettings()

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
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
    savedBoardDeviceId: String?,
    savedBoardType: BoardType?,
    onGrantPermissions: () -> Unit,
    onOpenBleSettings: () -> Unit,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
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
            onOpenLocationSettings = onOpenLocationSettings,
            onSkipLocation = onScan,
        )
    } else if (connectionState != ConnectionState.Connected) {
        ConnectScreen(
            connectionState = connectionState,
            devices = devices,
            savedBoardDeviceId = savedBoardDeviceId,
            savedBoardType = savedBoardType,
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
