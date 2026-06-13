package com.zwheel.app.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface RideServiceController {
    fun connect(deviceId: String)
    fun disconnect()
}

@Singleton
class RideServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : RideServiceController {
    override fun connect(deviceId: String) {
        val intent = Intent(context, RideForegroundService::class.java).apply {
            putExtra("deviceId", deviceId)
        }
        context.startForegroundService(intent)
    }

    override fun disconnect() {
        context.stopService(Intent(context, RideForegroundService::class.java))
    }
}
