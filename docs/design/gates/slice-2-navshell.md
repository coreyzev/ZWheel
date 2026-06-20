# Gate: Slice 2 — Navigation Shell + Connect Screen + Permissions Screen

You are implementing the bottom-tab navigation shell and two new gated screens (Connect and
Permissions) for the ZWheel dark instrument-cluster redesign. Read ONLY this file and
`docs/design/spec.md`. Do not read any other docs or gate files.

This slice is **app module only**. Do NOT touch anything under `core/`. Do not add new BLE UUIDs.
Do not make network calls in `app/main` sources.

This slice builds on the Slice 0 foundation and the Slice 1 dashboard. Both are already in place:
- `ZWheelColors` data class + `ZWheelDarkColors` singleton + `LocalZWheelColors` CompositionLocal
  in `app/src/main/kotlin/com/zwheel/app/ui/ZWheelColors.kt`
- `ZWheelColors.ramp(fraction: Float)` helper
- `SairaFamily` / `JetBrainsMonoFamily` + `Typography` in `Type.kt`
- Re-skinned `DashboardCard`, `Label`, `Metric`, `SmallStat` in `DashboardComponents.kt`
- `DashboardScreen` composable in `app/src/main/kotlin/com/zwheel/app/ui/dashboard/DashboardScreen.kt`
  with signature `DashboardScreen(state, modifier, onRequestLocation, locationGranted, locationPermanentlyDenied)`

**Use these; do not redefine them.** Access colors via `val c = LocalZWheelColors.current`.

The **material-icons-extended** artifact is already on the classpath.

---

## Scope (do all of these, in order)

### 1. New bottom-bar composable — `ui/nav/ZWheelBottomBar.kt`

Create `app/src/main/kotlin/com/zwheel/app/ui/nav/ZWheelBottomBar.kt`.

```kotlin
package com.zwheel.app.ui.nav

sealed class TopLevelRoute(val route: String, val label: String) {
    object Ride     : TopLevelRoute("ride",     "Ride")
    object History  : TopLevelRoute("history",  "History")
    object Settings : TopLevelRoute("settings", "Settings")

    companion object {
        val all = listOf(Ride, History, Settings)
    }
}
```

Composable:

```kotlin
@Composable
fun ZWheelBottomBar(
    currentRoute: String?,
    onSelect: (TopLevelRoute) -> Unit,
    modifier: Modifier = Modifier,
)
```

Layout rules:
- Root: `NavigationBar` with `containerColor = c.navBg`, `tonalElevation = 0.dp`.
- For each `TopLevelRoute.all` entry, emit one `NavigationBarItem`:
  - `selected = currentRoute == tab.route`
  - `icon`:
    - `Ride`    → `Icons.Filled.Speed`
    - `History` → `Icons.Filled.Timeline`
    - `Settings`→ `Icons.Filled.Tune`
  - `label = { Text(tab.label, fontFamily = SairaFamily, fontSize = 10.sp, fontWeight = FontWeight.W600) }`
  - `colors = NavigationBarItemDefaults.colors(
        indicatorColor = Color.Transparent,
        selectedIconColor = c.lime,
        selectedTextColor = c.lime,
        unselectedIconColor = c.textLabel,
        unselectedTextColor = c.textLabel,
    )`
  - Item top padding: 10.dp / bottom padding: 15.dp (see spec Spacing section) — use
    `Modifier.padding(top = 10.dp, bottom = 15.dp)` on the icon content if
    `NavigationBarItem` does not expose it directly; otherwise accept M3 defaults here.
  - `alwaysShowLabel = true`
- Do NOT use a `Divider` above the bar; the dark background is sufficient.

Icon imports (all from `androidx.compose.material.icons.filled`):
- `Speed` → `Icons.Filled.Speed`
- `Timeline` → `Icons.Filled.Timeline`
- `Tune` → `Icons.Filled.Tune`

---

### 2. Rewrite `ZWheelAppScreen.kt` — navigation shell

This file currently contains:
- `ZWheelAppScreen()` — a bare `NavHost` with routes: `dashboard`, `history`,
  `rideDetail/{sessionId}`, `mapFullScreen/{sessionId}`, `settings`, `battery`.
- `ZWheelDashboardScreen()` (private) — holds ALL the permission launchers and logic.
- `ZWheelDashboard()` (private) — the old layout composable that calls `ConnectionBar`.

**Replace the entire file** (top-level function + both private functions) with the new structure
described below. Import everything that was previously imported; add new imports as needed. Preserve
the existing `battery` route (it is not a tab destination).

#### 2a. Permission holder — keep ALL existing logic, relocate it

The `ZWheelDashboardScreen` function currently owns every piece of permission logic. That logic is
**correct and must not be simplified**. You are moving it verbatim into the new shell composable
`ZWheelAppScreen()`. Do not rewrite a single branch.

Specifically, preserve these items exactly as they appear today, now living inside
`ZWheelAppScreen()`:

1. `val requiredPermissions = remember { bleScanPermissions() }`
2. `var permissionRequestAttempted by remember { mutableStateOf(false) }`
3. `var permissionsGranted by remember { mutableStateOf(hasAllRequiredPermissions(context, requiredPermissions)) }`
4. `var permanentlyDenied by remember { mutableStateOf(false) }`
5. `var batteryOptimized by remember { mutableStateOf(false) }`
6. `val locationPermissions = remember { rideLocationPermissions() }`
7. `var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }`
8. `val locationPermissionAttempted by viewModel.locationPermissionAttempted.collectAsStateWithLifecycle()`
9. `var pendingConnectDeviceId by remember { mutableStateOf<String?>(null) }`
10. `val permissionLauncher` (the `rememberLauncherForActivityResult` that sets `permissionsGranted`
    and `permanentlyDenied` via `hasPermanentlyDeniedPermission`)
11. `val locationLauncher` (the `rememberLauncherForActivityResult` that sets `locationGranted`,
    calls `viewModel.connect(pendingConnectDeviceId)` when granted, clears `pendingConnectDeviceId`)
12. `fun requestBlePermissions()` — sets `permissionRequestAttempted = true`, launches
    `permissionLauncher`
13. `fun requestLocationPermission()` — checks `locationPermissionAttempted &&
    hasPermanentlyDeniedPermission(...)`, routes to `context.openLocationPermissionSettings()` when
    permanently denied, otherwise calls `viewModel.markLocationPermissionAttempted()` then launches
    `locationLauncher`
14. `val locationPermanentlyDenied` derived val — computed from `!locationGranted &&
    locationPermissionAttempted && hasPermanentlyDeniedPermission(...)`
15. `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` — re-checks `permissionsGranted`,
    `permanentlyDenied`, `locationGranted`, `batteryOptimized` (via `PowerManager`)
16. `LaunchedEffect(requiredPermissions)` — auto-requests BLE on first launch when not yet granted
    and `!permissionRequestAttempted`

The `onScan` lambda (which calls `requestLocationPermission()` before `viewModel.scan()` when
location is not yet granted) and the `onConnect` lambda (which stashes the deviceId in
`pendingConnectDeviceId` and calls `requestLocationPermission()` when location not granted) must
also be preserved verbatim.

#### 2b. New `ZWheelAppScreen()` structure

```kotlin
@Composable
fun ZWheelAppScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    // --- all permission state from section 2a lives here ---
    // (copy the entire block verbatim from the existing ZWheelDashboardScreen)

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    // Bottom-bar visibility: show only on the three top-level tab routes.
    val topLevelRoutes = setOf("ride", "history", "settings")
    val currentRoute by navController.currentBackStackEntryAsState().let {
        remember(it) { derivedStateOf { navController.currentBackStackEntry?.destination?.route } }
    }
    // Simpler alternative that also works:
    // val navBackStackEntry by navController.currentBackStackEntryAsState()
    // val currentRoute = navBackStackEntry?.destination?.route
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
                    // all gating state
                    permissionsGranted = permissionsGranted,
                    permanentlyDenied = permanentlyDenied,
                    connectionState = connectionState,
                    devices = devices,
                    state = state,
                    locationGranted = locationGranted,
                    locationPermanentlyDenied = locationPermanentlyDenied,
                    // callbacks
                    onGrantPermissions = ::requestBlePermissions,
                    onOpenBleSettings = { context.openAppSettings() },
                    onScan = onScan,          // the preserved lambda
                    onConnect = onConnect,    // the preserved lambda
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
```

**Note on `onScan` / `onConnect` lambdas**: define them as local `fun` (same as today) before the
`Scaffold` block, referencing the state vars and launchers. Do not inline them into the callback
parameters — the structure must be identical to the existing code so the logic is unchanged.

**Note on `currentBackStackEntryAsState`**: import
`androidx.navigation.compose.currentBackStackEntryAsState`. The `derivedStateOf` wrapping shown
above is one correct approach; a direct `val navBackStackEntry by navController.currentBackStackEntryAsState()`
and reading `.destination?.route` inline is equally correct — pick whichever compiles cleanly.

#### 2c. `RideTabContent` — gating logic

Define a private composable in `ZWheelAppScreen.kt`:

```kotlin
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
)
```

Gating logic (three branches, evaluated top to bottom):

```
if (!permissionsGranted) {
    PermissionsScreen(
        bleGranted = false,
        blePermanentlyDenied = permanentlyDenied,
        locationGranted = locationGranted,
        locationPermanentlyDenied = locationPermanentlyDenied,
        onRequestBle = onGrantPermissions,
        onOpenBleSettings = onOpenBleSettings,
        onRequestLocation = onRequestLocation,
        onSkipLocation = onScan,  // proceed to scanning without GPS
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
```

Do not add any extra wrapping or animation between branches yet; plain `if/else` is correct for
this gate.

#### 2d. Old private composables

Remove `ZWheelDashboardScreen()` and `ZWheelDashboard()` entirely from `ZWheelAppScreen.kt` — their
logic has been relocated. Do NOT touch `DashboardComposables.kt`; leave it as-is (it still compiles
and is not referenced by the new shell).

---

### 3. ConnectScreen — new file `ui/connect/ConnectScreen.kt`

Create `app/src/main/kotlin/com/zwheel/app/ui/connect/ConnectScreen.kt`.

```kotlin
@Composable
fun ConnectScreen(
    connectionState: ConnectionState,
    devices: List<ScanResult>,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Layout — `LazyColumn` with `contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp)`
and `verticalArrangement = Arrangement.spacedBy(14.dp)`:

#### 3a. Title block

```
item {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Connect your board",
            fontFamily = SairaFamily, fontWeight = FontWeight.W800,
            fontSize = 26.sp, letterSpacing = (-0.4).sp,
            color = c.textPrimary,
        )
        Text(
            text = "Power on your Onewheel and keep it nearby.",
            fontFamily = SairaFamily, fontWeight = FontWeight.W400,
            fontSize = 14.sp, lineHeight = 21.sp,
            color = c.textMuted,
        )
    }
}
```

#### 3b. State-chip row

```
item { BleStateChips(connectionState) }
```

Private composable `BleStateChips(connectionState: ConnectionState)`:
- A `Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState()))`.
- Emit one chip per `ConnectionState` value in this order: `Idle`, `Scanning`, `Connected`,
  `Disconnected`.
- Each chip is a `Surface`:
  - `shape = RoundedCornerShape(999.dp)` (pill token)
  - Active (current state): `color = c.lime`, border none.
  - Inactive: `color = Color.Transparent`, `border = BorderStroke(1.dp, c.border)`.
  - Padding: `PaddingValues(horizontal = 10.dp, vertical = 4.dp)`.
  - Label text: `state.name.uppercase()`, JetBrains Mono 9sp / W400,
    color = active → `Color(0xFF0A0B0E)` (dark text on lime), inactive → `c.textLabel`.

#### 3c. Scanning indicator

Show only when `connectionState == ConnectionState.Scanning`:

```
item {
    if (connectionState == ConnectionState.Scanning) {
        ScanningIndicator()
    }
}
```

Private composable `ScanningIndicator()`:
- `Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp))`:
  - `Icon(Icons.Filled.BluetoothSearching, contentDescription = "Scanning", tint = c.cyan, modifier = Modifier.size(22.dp))`
    → import `androidx.compose.material.icons.filled.BluetoothSearching`
  - `Text("Scanning for boards…", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 15.sp, color = c.cyan)`
- **Optional polish:** animate a `Canvas`-drawn ring expanding from the icon center (scale
  0.6f → 1.4f, alpha 1f → 0f, `infiniteTransition`, 1800ms ease-out). Static state (no animation)
  is fully acceptable — implement only if it doesn't push the file over 300 lines.

#### 3d. Found-device list

```
items(devices) { device ->
    DeviceRow(device = device, connectionState = connectionState, onConnect = onConnect)
}
```

Private composable `DeviceRow(device: ScanResult, connectionState: ConnectionState, onConnect: (String) -> Unit)`:
- `Surface(shape = RoundedCornerShape(16.dp), color = c.card, border = BorderStroke(1.dp, c.border), modifier = Modifier.fillMaxWidth())`:
  - `Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween)`:
    - Left `Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp))`:
      - Name row: `Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically)`:
        - `Text(device.displayName ?: device.deviceId, fontFamily = SairaFamily, fontWeight = FontWeight.W700, fontSize = 15.sp, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)`
        - If `device.displayName != null`: lime badge `Surface(shape = RoundedCornerShape(5.dp), color = Color.Transparent, border = BorderStroke(1.dp, c.borderLime))` → `Text("PINT X", JetBrains Mono 9sp W700, c.lime, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))`.
          Note: the board type string is not yet in `ScanResult`; hardcode `"PINT X"` as a
          placeholder with a `// TODO: use device.boardType when available` comment.
      - RSSI line: `Text(device.rssi?.let { "$it dBm" } ?: "—", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = c.textMuted)`
    - Right: `Button(onClick = { onConnect(device.deviceId) }, enabled = connectionState != ConnectionState.Connected, shape = RoundedCornerShape(999.dp), colors = ButtonDefaults.buttonColors(containerColor = c.lime, contentColor = Color(0xFF0A0B0E), disabledContainerColor = c.cardElevated, disabledContentColor = c.textDim))`:
      - `Text("Connect", fontFamily = SairaFamily, fontWeight = FontWeight.W700, fontSize = 13.sp)`

#### 3e. Empty-state (no devices, not scanning)

```
item {
    if (devices.isEmpty() && connectionState == ConnectionState.Idle) {
        EmptyDeviceState(onScan = onScan)
    }
}
```

Private composable `EmptyDeviceState(onScan: () -> Unit)`:
- `Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp))`:
  - `Icon(Icons.Filled.BluetoothSearching, contentDescription = null, tint = c.textDim, modifier = Modifier.size(48.dp))`
  - `Text("No boards found", fontFamily = SairaFamily, fontWeight = FontWeight.W600, fontSize = 16.sp, color = c.textSecondary)`
  - `Text("Tap Scan to search for nearby boards.", fontFamily = SairaFamily, fontWeight = FontWeight.W400, fontSize = 14.sp, color = c.textMuted)`
  - `Button(onClick = onScan, shape = RoundedCornerShape(999.dp), colors = ButtonDefaults.buttonColors(containerColor = c.lime, contentColor = Color(0xFF0A0B0E)))`:
    - `Text("Scan for boards", fontFamily = SairaFamily, fontWeight = FontWeight.W700, fontSize = 14.sp)`

---

### 4. PermissionsScreen — new file `ui/permissions/PermissionsScreen.kt`

Create `app/src/main/kotlin/com/zwheel/app/ui/permissions/PermissionsScreen.kt`.

```kotlin
@Composable
fun PermissionsScreen(
    bleGranted: Boolean,
    blePermanentlyDenied: Boolean,
    locationGranted: Boolean,
    locationPermanentlyDenied: Boolean,
    onRequestBle: () -> Unit,
    onOpenBleSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onSkipLocation: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Note: in the current gating flow `bleGranted` will always be `false` when this screen is shown
(the screen only appears when `!permissionsGranted`). Include it anyway for completeness and future
flexibility.

Layout — `Column(modifier.fillMaxSize().background(c.screenBg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp))`:

#### 4a. Title block

```kotlin
Text(
    "Permissions",
    fontFamily = SairaFamily, fontWeight = FontWeight.W800,
    fontSize = 26.sp, letterSpacing = (-0.4).sp,
    color = c.textPrimary,
)
Text(
    "ZWheel needs Nearby Devices access to find and connect to your board.",
    fontFamily = SairaFamily, fontWeight = FontWeight.W400,
    fontSize = 14.sp, lineHeight = 21.sp, color = c.textMuted,
)
```

#### 4b. BLE / Nearby Devices permission card

`PermissionCard` composable (private, defined in same file):

```kotlin
@Composable
private fun PermissionCard(
    label: String,
    description: String,
    granted: Boolean,
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
)
```

Card construction:
- `Surface(shape = RoundedCornerShape(16.dp), color = c.card, border = BorderStroke(1.dp, if (granted) c.borderGreen else c.borderRed), modifier = Modifier.fillMaxWidth())`:
  - `Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp))`:
    - Header row: `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically)`:
      - `Text(label, fontFamily = SairaFamily, fontWeight = FontWeight.W700, fontSize = 15.sp, color = c.textPrimary)`
      - If `granted`: `Text("● GRANTED", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, fontWeight = FontWeight.W700, color = c.rampGood, letterSpacing = 1.5.sp)`
        else: `Text("● DENIED", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, fontWeight = FontWeight.W700, color = c.rampDanger, letterSpacing = 1.5.sp)`
    - `Text(description, fontFamily = SairaFamily, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 19.sp, color = c.textMuted)`
    - If `!granted`:
      - If `permanentlyDenied`: `Button(onClick = onOpenSettings, shape = RoundedCornerShape(999.dp), colors = ButtonDefaults.buttonColors(containerColor = c.lime, contentColor = Color(0xFF0A0B0E)), modifier = Modifier.fillMaxWidth())` → `Text("Open in settings", fontFamily = SairaFamily, fontWeight = FontWeight.W700, fontSize = 14.sp)`
      - Else: `Button(onClick = onRequest, shape = RoundedCornerShape(999.dp), colors = ButtonDefaults.buttonColors(containerColor = c.lime, contentColor = Color(0xFF0A0B0E)), modifier = Modifier.fillMaxWidth())` → `Text("Grant permission", fontFamily = SairaFamily, fontWeight = FontWeight.W700, fontSize = 14.sp)`

Call site for BLE card:
```kotlin
PermissionCard(
    label = "Nearby devices",
    description = "Required to scan for and connect to your Onewheel over Bluetooth.",
    granted = bleGranted,
    permanentlyDenied = blePermanentlyDenied,
    onRequest = onRequestBle,
    onOpenSettings = onOpenBleSettings,
)
```

Call site for Location card:
```kotlin
PermissionCard(
    label = "Location",
    description = "Used for GPS ride tracking. You can skip this and connect without GPS.",
    granted = locationGranted,
    permanentlyDenied = locationPermanentlyDenied,
    onRequest = onRequestLocation,
    onOpenSettings = onRequestLocation, // openLocationPermissionSettings() == openAppSettings(); the callback already handles routing
)
```

#### 4c. Legend card

```kotlin
Surface(
    shape = RoundedCornerShape(16.dp),
    color = c.legendCard,
    border = BorderStroke(1.dp, c.border),
    modifier = Modifier.fillMaxWidth(),
) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "PERMISSION STATES HANDLED",
            fontFamily = JetBrainsMonoFamily, fontSize = 9.sp,
            fontWeight = FontWeight.W700, letterSpacing = 1.5.sp,
            color = c.textDimmest,
        )
        LegendRow(dot = c.rampGood,   label = "Granted — feature available")
        LegendRow(dot = c.rampCaution, label = "Not yet requested — will prompt")
        LegendRow(dot = c.rampDanger,  label = "Denied — open Settings to fix")
    }
}
```

Private helper `LegendRow(dot: Color, label: String)`:
```kotlin
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
    Text(label, fontFamily = SairaFamily, fontWeight = FontWeight.W400, fontSize = 13.sp, color = c.textSecondary)
}
```

#### 4d. Footer — skip / connect without GPS

```kotlin
Spacer(Modifier.weight(1f))  // pushes footer to bottom when in a Column with weight support
TextButton(
    onClick = onSkipLocation,
    modifier = Modifier.fillMaxWidth(),
) {
    Text(
        "Skip for now · connect without GPS",
        fontFamily = SairaFamily, fontWeight = FontWeight.W400,
        fontSize = 13.sp, color = c.textMuted,
        textAlign = TextAlign.Center,
    )
}
```

Note: `Spacer(Modifier.weight(1f))` requires the parent `Column` to have `modifier = Modifier.fillMaxSize(...)`.
If the `verticalScroll` modifier prevents `weight` from working (it does when the Column is
scrollable with unconstrained height), replace with `Spacer(Modifier.height(32.dp))` instead — the
footer can sit below the legend card without being pinned to the screen bottom.

---

### 5. Icon imports for this slice

All from `androidx.compose.material.icons.filled` unless noted:

| Spec name / description | Compose icon |
|---|---|
| `speed` (Ride tab) | `Icons.Filled.Speed` |
| `timeline` (History tab) | `Icons.Filled.Timeline` |
| `tune` (Settings tab) | `Icons.Filled.Tune` |
| `bluetooth_searching` | `Icons.Filled.BluetoothSearching` |

If `Icons.Filled.BluetoothSearching` is not in the extended library, use
`Icons.Filled.Bluetooth` as a fallback and add a `// TODO: swap for BluetoothSearching` comment.

---

### 6. Files to delete vs. keep

**Delete** only after confirming: search the entire `app/` source tree with
`grep -r "ZWheelDashboard\|ZWheelDashboardScreen" --include="*.kt"` before removing anything.

- If the grep returns **only** `ZWheelAppScreen.kt`, the old `ZWheelDashboardScreen` and
  `ZWheelDashboard` private functions have no other callers. They are being removed from
  `ZWheelAppScreen.kt` in step 2d above (the file is being rewritten) — no separate deletion step
  is needed.
- `DashboardComposables.kt` — **keep as-is**. It defines `Header`, `SpeedCard`, `BatteryPackCard`,
  `CellVoltageCard`, `TripStatsCard`, `RideModeCard` and is still referenced by
  `ZWheelDashboard()` which you are removing, BUT the Slice 1 dashboard components may import
  helpers defined there. Do not delete it in this gate; leave deletion for a future cleanup gate.
- `ConnectionBar.kt` — **keep as-is**. It is an internal composable; even though it is no longer
  called from the new shell, deleting it could break builds if something else imports it. Leave it.

---

## Constraints (self-review before commit)

1. **`core/` untouched.** No Android imports, no new BLE UUIDs, no network calls in `app/main`.
2. **300-line soft limit per file.** Four new/modified files: `ZWheelAppScreen.kt`,
   `ZWheelBottomBar.kt`, `ConnectScreen.kt`, `PermissionsScreen.kt`. Split further if needed.
3. **Token access only via `LocalZWheelColors.current`.** No hardcoded hex values except
   `Color(0xFF0A0B0E)` (dark text on lime buttons — this exact value is the `screenBg` token;
   use `c.screenBg` instead wherever possible).
4. **DO NOT regress the permission flow.** The `permissionLauncher`, `locationLauncher`,
   `requestBlePermissions()`, `requestLocationPermission()`, `locationPermanentlyDenied` derived
   val, `LifecycleEventEffect(ON_RESUME)`, and `LaunchedEffect(requiredPermissions)` blocks must
   exist verbatim in the new `ZWheelAppScreen()` function. If anything is unclear, preserve the
   existing code path unchanged and only alter its surrounding structure (the Scaffold + NavHost).
5. **`DashboardScreen` is the only connected-state UI.** Do not reconstruct any dashboard
   composables in `ConnectScreen.kt` or `PermissionsScreen.kt`.
6. **Bottom bar hidden on pushed routes.** `rideDetail/{sessionId}`, `mapFullScreen/{sessionId}`,
   and `battery` must NOT render the bottom bar. The `showBottomBar = currentRoute in topLevelRoutes`
   guard handles this because those routes are not in `topLevelRoutes`.
7. **`batteryOptimized` banner** — the existing amber battery-optimization warning card was rendered
   inline in the old `ZWheelDashboard`. It is NOT part of this gate's new screens. Omit it for now;
   it will be re-added to `DashboardScreen` in a future gate. Keep `batteryOptimized` state in the
   shell for the `ON_RESUME` re-check (the `LifecycleEventEffect` reads it), but do not render a
   banner from the shell.
8. **Single `NavController`.** Do not create a second NavController anywhere.
9. **No `@Preview` annotation on screens that receive a `NavController` or hoist permission
   launchers.** Add `@Preview` only to `ConnectScreen` and `PermissionsScreen` with hardcoded
   preview state.
10. All numeric `Text` values use `fontFeatureSettings = "tnum"` for tabular figures.

---

## Build & commit

1. From the worktree root run:
   ```
   GRADLE_OPTS="-Xmx4g" ./gradlew :app:compileDebugKotlin
   ```
   Fix ALL errors until it succeeds. Common pitfalls:
   - `currentBackStackEntryAsState` requires `import androidx.navigation.compose.currentBackStackEntryAsState`
   - `NavigationBar` / `NavigationBarItem` / `NavigationBarItemDefaults` are in `androidx.compose.material3`
   - `rememberScrollState` + `horizontalScroll` for the chip row requires `androidx.compose.foundation`
   - `CircleShape` is in `androidx.compose.foundation.shape`
   - `TextAlign` is in `androidx.compose.ui.text.style`
   - If `Icons.Filled.BluetoothSearching` causes an unresolved reference, use `Icons.Filled.Bluetooth` as noted in §5

2. Commit with:
   ```
   feat(ui): bottom-tab navigation shell + connect & permissions screens
   ```
   Conventional Commits, one commit. Do NOT add any Co-Authored-By line.

---

## ADDENDUM: Screenshot tests (do these before committing)

So the new screens can be visually verified headlessly, add Roborazzi screenshot tests following the
existing pattern in `app/src/test/kotlin/com/zwheel/app/ui/screenshots/DashboardScreenshotTest.kt`:

- `ConnectScreenshotTest` → renders `ZWheelTheme { ConnectScreen(...) }` with a representative
  scanning/found-devices state (a couple of fake `ScanResult`s, `connectionState = Scanning`) to
  `app/build/outputs/roborazzi/connect.png`.
- `PermissionsScreenshotTest` → renders `ZWheelTheme { PermissionsScreen(...) }` with BLE granted +
  location permanently-denied (so both the green "GRANTED" card and the red "Open settings" card show)
  to `app/build/outputs/roborazzi/permissions.png`.

Run `GRADLE_OPTS="-Xmx4g" ./gradlew :app:recordRoborazziDebug` and confirm both PNGs are non-empty
valid PNGs before committing. Put these tests in a separate commit:
`test(ui): screenshot tests for connect and permissions screens` (no Co-Authored-By line).
