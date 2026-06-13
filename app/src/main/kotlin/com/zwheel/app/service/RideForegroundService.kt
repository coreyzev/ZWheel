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
import com.zwheel.app.data.ride.RideRepository
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.wear.WearDataLayerRepository
import com.zwheel.core.calc.DefaultTopSpeedTracker
import com.zwheel.core.ports.Clock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
    @Inject lateinit var rideRepository: RideRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var clock: Clock
    @Inject lateinit var wearDataLayerRepository: WearDataLayerRepository

    private var topSpeedTracker = DefaultTopSpeedTracker()
    private var wakelock: PowerManager.WakeLock? = null
    private var rideRecorder: RideRecorder? = null
    private var speedAboveThresholdTicks = 0
    private var speedBelowThresholdTicks = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakelockIfNeeded()
        mirrorConnectionState()
        mirrorBoardStateAndUpdateNotification()
        startRideRecorderTicker()
        wearDataLayerRepository.startSync(lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification("ZWheel · Connecting…"))
        lifecycleScope.launch {
            val prefs = settingsRepository.preferences.first()
            if (!prefs.hasRequestedBatteryOptimization) {
                settingsRepository.saveHasRequestedBatteryOptimization()
                requestBatteryOptimizationExemption()
            }
        }
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
        runBlocking {
            rideRecorder?.endCurrentSession()
        }
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
                topSpeedTracker.consume(state.speedMetersPerSecondCorrected)
                rideServiceRepository.updateTopSpeed(topSpeedTracker.currentTripMaxMetersPerSecond ?: 0.0)
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

    private fun startRideRecorderTicker() {
        val recorder = RideRecorder(rideRepository, clock)
        recorder.onSessionChanged = { isRiding ->
            rideServiceRepository.updateIsRiding(isRiding)
            if (!isRiding) {
                topSpeedTracker = DefaultTopSpeedTracker()
                rideServiceRepository.updateTopSpeed(0.0)
            }
        }
        rideRecorder = recorder
        lifecycleScope.launch {
            while (isActive) {
                delay(1_000L)
                recorder.onTick(rideServiceRepository.boardState.value)
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

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:$packageName"),
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
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
