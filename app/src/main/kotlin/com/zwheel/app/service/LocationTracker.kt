package com.zwheel.app.service

import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

internal class LocationTracker(
    context: Context,
    private val onGpsLocked: (Boolean) -> Unit,
    private val onLocation: (lat: Double, lon: Double, alt: Double) -> Unit,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    fun start() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (loc.accuracy > 30f) {
                    onGpsLocked(false)
                    return
                }
                onLocation(loc.latitude, loc.longitude, loc.altitude)
                onGpsLocked(true)
            }
        }
        callback = cb
        try {
            client.requestLocationUpdates(request, cb, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // ACCESS_FINE_LOCATION not granted; GPS remains null.
        }
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
    }
}
