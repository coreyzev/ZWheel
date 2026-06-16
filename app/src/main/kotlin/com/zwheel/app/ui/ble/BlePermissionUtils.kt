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

internal fun rideLocationPermissions(): List<String> =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION)

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

// Opens the app's permissions list directly via the permission controller (API 23+).
// Falls back to general app info if the permission controller is unavailable on the device.
internal fun Context.openLocationPermissionSettings() {
    val permissionsIntent = Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
        putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
    }
    try {
        startActivity(permissionsIntent)
    } catch (_: android.content.ActivityNotFoundException) {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
