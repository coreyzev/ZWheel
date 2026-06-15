package com.zwheel.app.service

import android.content.Intent
import android.os.PowerManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.zwheel.app.ble.ConnectionManager
import com.zwheel.app.data.ride.RideRepository
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.wear.WearDataLayerRepository
import com.zwheel.core.calc.DefaultTopSpeedTracker
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.ports.Clock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

    private val notifications by lazy { RideNotifications(this) }
    private val locationTracker by lazy {
        LocationTracker(
            context = this,
            onGpsLocked = { locked -> rideServiceRepository.updateGpsLock(locked) },
            onLocation = { lat, lon, alt ->
                lastLatitude = lat; lastLongitude = lon; lastAltitude = alt
            },
        )
    }

    private var topSpeedTracker = DefaultTopSpeedTracker()
    private var notificationSpeedUnit = SpeedUnit.MPH
    private var wakelock: PowerManager.WakeLock? = null
    private var rideRecorder: RideRecorder? = null
    private var speedAboveThresholdTicks = 0
    private var speedBelowThresholdTicks = 0
    @Volatile private var lastLatitude: Double? = null
    @Volatile private var lastLongitude: Double? = null
    @Volatile private var lastAltitude: Double? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
        acquireWakelockIfNeeded()
        trackSpeedUnitPreference()
        observeBoardForNotificationAndWakelock()
        startRideRecorderTicker()
        wearDataLayerRepository.startSync(lifecycleScope)
        locationTracker.start()
        HomeAssistantSync(settingsRepository, connectionManager.boardState).start(lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(RIDE_NOTIFICATION_ID, notifications.build("ZWheel · Connecting…"))
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
        runBlocking { rideRecorder?.endCurrentSession() }
        locationTracker.stop()
        connectionManager.disconnect()
        wakelock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun trackSpeedUnitPreference() {
        lifecycleScope.launch {
            settingsRepository.preferences.collect { prefs ->
                notificationSpeedUnit = prefs.speedUnit
            }
        }
    }

    private fun observeBoardForNotificationAndWakelock() {
        lifecycleScope.launch {
            connectionManager.boardState.collect { state ->
                topSpeedTracker.consume(state.speedMetersPerSecondCorrected)
                rideServiceRepository.updateTopSpeed(topSpeedTracker.currentTripMaxMetersPerSecond ?: 0.0)
                updateWakelockState(state.speedMetersPerSecondCorrected ?: 0.0)
                val speed = state.speedMetersPerSecondCorrected
                val battery = state.batteryPercent
                val content = when {
                    speed != null && battery != null -> {
                        val isMph = notificationSpeedUnit == SpeedUnit.MPH
                        val speedDisplay = if (isMph) speed * 2.23694 else speed * 3.6
                        val unitLabel = if (isMph) "mph" else "kph"
                        "%.0f %s · %d%%".format(speedDisplay, unitLabel, battery)
                    }
                    battery != null -> "%d%%".format(battery)
                    else -> "ZWheel · Connected"
                }
                notifications.notify(content)
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
                recorder.onTick(
                    connectionManager.boardState.value,
                    lastLatitude,
                    lastLongitude,
                    lastAltitude,
                )
            }
        }
        lifecycleScope.launch {
            recorder.tripDistanceMeters.collect { meters ->
                rideServiceRepository.updateTripDistance(meters)
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
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
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
}
