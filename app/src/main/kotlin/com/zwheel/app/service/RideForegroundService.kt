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
    private val wakelock: PowerManager.WakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
            .apply { setReferenceCounted(false) }
    }
    private var rideRecorder: RideRecorder? = null
    private var speedAboveThresholdTicks = 0
    private var speedBelowThresholdTicks = 0
    @Volatile private var lastLatitude: Double? = null
    @Volatile private var lastLongitude: Double? = null
    @Volatile private var lastAltitude: Double? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
        lifecycleScope.launch { settingsRepository.migrateHaTokenIfNeeded() }
        trackSpeedUnitPreference()
        observeBoardForNotificationAndWakelock()
        startRideRecorderTicker()
        wearDataLayerRepository.startSync(lifecycleScope)
        locationTracker.start()
        HomeAssistantSync(settingsRepository, connectionManager.boardState).start(lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(RIDE_NOTIFICATION_ID, notifications.build("ZWheel · Connecting…", null))
        acquireWakelockIfNeeded()
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
        lifecycleScope.launch { closeOrphanedSessions() }
        val deviceId = intent?.getStringExtra("deviceId")
        if (deviceId != null) {
            lifecycleScope.launch {
                settingsRepository.saveLastConnectedDeviceId(deviceId)
                runCatching { connectionManager.connect(deviceId) }
                    .onFailure { notifications.notify("Connection failed", null) }
            }
        } else {
            lifecycleScope.launch {
                val lastId = settingsRepository.preferences.first().lastConnectedDeviceId
                if (lastId != null) {
                    runCatching { connectionManager.connect(lastId) }
                        .onFailure { notifications.notify("Connection failed", null) }
                }
            }
        }
        return START_STICKY
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onDestroy() {
        val latch = java.util.concurrent.CountDownLatch(1)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { rideRecorder?.endCurrentSession() }
            latch.countDown()
        }
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        locationTracker.stop()
        connectionManager.disconnect()
        if (wakelock.isHeld) wakelock.release()
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
                notifications.notify(content, battery)
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
                runCatching {
                    recorder.onTick(
                        connectionManager.boardState.value,
                        lastLatitude,
                        lastLongitude,
                        lastAltitude,
                    )
                }
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
                if (wakelock.isHeld) wakelock.release()
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
        if (!wakelock.isHeld) wakelock.acquire(10 * 60 * 1000L)
    }

    // On START_STICKY restart, close any sessions left open by the previous process.
    // Multiple orphans are possible if the process was killed more than once;
    // getOpenSession() uses LIMIT 1 so we must use getAllOpenSessions() here.
    private suspend fun closeOrphanedSessions() {
        val orphans = rideRepository.getAllOpenSessions()
        if (orphans.isEmpty()) return
        val now = clock.nowEpochMillis()
        orphans.forEach { session -> rideRepository.closeSession(session.id, now) }
    }
}
