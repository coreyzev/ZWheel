# Gate: Request ACCESS_FINE_LOCATION for GPS ride tracking (API 31+)

Base: main

## Allowed files (touch ONLY these)
app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt
app/src/main/kotlin/com/zwheel/app/ui/ble/BlePermissionUtils.kt

## Context
bleScanPermissions() must stay BT-only (do NOT re-add location there — it wedges
the scan gate because BLUETOOTH_SCAN is neverForLocation). Add a SEPARATE
location-permission concern for GPS.

## Spec
1. BlePermissionUtils.kt: add
       internal fun rideLocationPermissions(): List<String> =
           listOf(Manifest.permission.ACCESS_FINE_LOCATION)
   and a helper hasLocationPermission(context) reusing hasPermission().
2. ZWheelAppScreen.kt (ZWheelDashboardScreen): add a second
   rememberLauncherForActivityResult for ActivityResultContracts
   .RequestMultiplePermissions() bound to rideLocationPermissions(). Track
   locationGranted state, refresh it in the existing ON_RESUME LifecycleEventEffect.
   When the user starts a scan/connect that will record a ride, if location is not
   granted, launch the location request once (guarded by an attempted flag, same
   pattern as the BLE launcher). Do NOT block BLE scanning on location — GPS is
   additive.
3. Pass a boolean down so the existing GPS badge can show "GPS off — tap to enable"
   when locationGranted is false; tapping launches the request. (Reuse the existing
   gpsLocked plumbing in DashboardUiState if simplest; otherwise a new param.)

## Verify
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
Commit: fix(gps): request fine-location at ride start so GPS tracking works on Android 12+
