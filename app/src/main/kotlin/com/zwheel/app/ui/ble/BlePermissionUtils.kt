package com.zwheel.app.ui.ble

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// FINE and COARSE must be requested together: on Android 12+ a FINE-only runtime
// request is silently ignored (no system dialog appears, result is immediate denial).
internal fun rideLocationPermissions(): List<String> =
    listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

// GPS ride tracking needs precise location; coarse-only grants are treated as not granted
// so the dashboard keeps offering the upgrade-to-precise prompt.
internal fun hasLocationPermission(context: Context): Boolean =
    hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

internal fun bleScanPermissions(): List<String> =
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

internal fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

internal fun hasAllRequiredPermissions(
    context: Context,
    permissions: List<String>,
): Boolean = permissions.all { hasPermission(context, it) }

internal fun hasPermanentlyDeniedPermission(
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

internal fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
}

// Opens the app's details page in system Settings, which has a one-tap "Permissions" entry.
// There is no reliable public intent to deep-link straight to the permissions list — the
// internal MANAGE_APP_PERMISSIONS action is protected and throws on many devices — so this
// uses ACTION_APPLICATION_DETAILS_SETTINGS, which is guaranteed to exist.
internal fun Context.openLocationPermissionSettings() = openAppSettings()

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
