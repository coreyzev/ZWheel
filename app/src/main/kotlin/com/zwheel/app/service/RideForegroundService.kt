package com.zwheel.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.zwheel.app.MainActivity
import com.zwheel.app.R
import com.zwheel.app.ble.ConnectionManager
import com.zwheel.app.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "zwheel_ride"
private const val NOTIFICATION_ID = 1
private const val WAKELOCK_TAG = "zwheel:ride"
private const val SPEED_ON_THRESHOLD = 0.5
private const val WAKELOCK_ACQUIRE_TICKS = 3
private const val WAKELOCK_RELEASE_TICKS = 90

@AndroidEntryPoint
class RideForegroundService : LifecycleService() {

    @Inject lateinit var connectionManager: ConnectionManager
    @Inject lateinit var rideServiceRepository: RideServiceRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private var wakelock: PowerManager.WakeLock? = null
    private var speedAboveThresholdTicks = 0
    private var speedBelowThresholdTicks = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakelockIfNeeded()
        mirrorConnectionState()
        mirrorBoardStateAndUpdateNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification("ZWheel · Connecting…"))
        if (intent?.action == "DISCONNECT") {
            stopSelf()
            return START_NOT_STICKY
        }
        val deviceId = intent?.getStringExtra("deviceId")
        if (deviceId != null) {
            lifecycleScope.launch {
                settingsRepository.saveLastConnectedDeviceId(deviceId)
                connectionManager.connect(deviceId)
            }
        } else {
            lifecycleScope.launch {
                val lastId = settingsRepository.preferences.first().lastConnectedDeviceId
                if (lastId != null) connectionManager.connect(lastId)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        connectionManager.disconnect()
        wakelock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun mirrorConnectionState() {
        lifecycleScope.launch {
            connectionManager.connectionState.collect { state ->
                rideServiceRepository.updateConnectionState(state)
            }
        }
    }

    private fun mirrorBoardStateAndUpdateNotification() {
        lifecycleScope.launch {
            connectionManager.boardState.collect { state ->
                rideServiceRepository.updateBoardState(state)
                updateWakelockState(state.speedMetersPerSecondCorrected ?: 0.0)
                val speed = state.speedMetersPerSecondCorrected
                val battery = state.batteryPercent
                val content = when {
                    speed != null && battery != null ->
                        "%.0f mph · %d%%".format(speed * 2.237, battery)
                    battery != null -> "%d%%".format(battery)
                    else -> "ZWheel · Connected"
                }
                notify(content)
            }
        }
    }

    private fun updateWakelockState(speed: Double) {
        if (speed > SPEED_ON_THRESHOLD) {
            speedBelowThresholdTicks = 0
            speedAboveThresholdTicks++
            if (speedAboveThresholdTicks >= WAKELOCK_ACQUIRE_TICKS) acquireWakelockIfNeeded()
        } else {
            speedAboveThresholdTicks = 0
            speedBelowThresholdTicks++
            if (speedBelowThresholdTicks >= WAKELOCK_RELEASE_TICKS) {
                wakelock?.takeIf { it.isHeld }?.release()
            }
        }
    }

    private fun acquireWakelockIfNeeded() {
        if (wakelock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun notify(content: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String) = run {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RideForegroundService::class.java).apply { action = "DISCONNECT" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ZWheel")
            .setContentText(content)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Disconnect", disconnectIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ride",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active ride status"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
}
