package com.zwheel.app.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.service.RideServiceRepository
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.WatchPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val DATA_PATH = "/zwheel/state"

@Singleton
class WearDataLayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rideServiceRepository: RideServiceRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val dataClient = Wearable.getDataClient(context)
    private var lastSentPayload: WatchPayload? = null

    fun startSync(scope: CoroutineScope) {
        scope.launch {
            combine(
                rideServiceRepository.boardState,
                rideServiceRepository.connectionState,
                rideServiceRepository.isRiding,
                settingsRepository.preferences,
            ) { boardState, connectionState, isRiding, prefs ->
                toWatchPayload(boardState, connectionState, isRiding, prefs.speedUnit)
            }.collect { payload ->
                if (payload != lastSentPayload) {
                    putPayload(payload)
                    lastSentPayload = payload
                }
            }
        }
    }

    private fun putPayload(payload: WatchPayload) {
        val request = PutDataMapRequest.create(DATA_PATH).apply {
            dataMap.putFloat(
                "speed_mps_corrected",
                payload.speedMetersPerSecondCorrected?.toFloat() ?: -1f,
            )
            dataMap.putFloat("top_speed_mps", payload.topSpeedMetersPerSecond.toFloat())
            dataMap.putInt("battery_pct", payload.batteryPercent ?: -1)
            dataMap.putFloat(
                "estimated_range_m",
                payload.estimatedRangeMeters?.toFloat() ?: -1f,
            )
            dataMap.putString("speed_unit", payload.speedUnit.name)
            dataMap.putBoolean("is_riding", payload.isRiding)
            dataMap.putString("connection_state", payload.connectionState.name)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request)
    }
}

private fun toWatchPayload(
    boardState: BoardState,
    connectionState: ConnectionState,
    isRiding: Boolean,
    speedUnit: SpeedUnit,
): WatchPayload {
    val coreConnectionState = when (connectionState) {
        ConnectionState.Connected -> com.zwheel.core.model.ConnectionState.SUBSCRIBED
        ConnectionState.Scanning -> com.zwheel.core.model.ConnectionState.SCANNING
        ConnectionState.Disconnected -> com.zwheel.core.model.ConnectionState.DISCONNECTED
        ConnectionState.Idle -> com.zwheel.core.model.ConnectionState.IDLE
    }
    return WatchPayload(
        speedMetersPerSecondCorrected = boardState.speedMetersPerSecondCorrected,
        topSpeedMetersPerSecond = 0.0, // TODO(p4): wire TopSpeedTracker into service
        batteryPercent = boardState.batteryPercent,
        estimatedRangeMeters = null,   // TODO(p4): wire RangeEstimator into service
        speedUnit = speedUnit,
        isRiding = isRiding,
        connectionState = coreConnectionState,
    )
}
