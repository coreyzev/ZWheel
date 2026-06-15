package com.zwheel.app.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.zwheel.app.ble.ConnectionManager
import com.zwheel.app.ble.ConnectionState
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.service.RideServiceRepository
import com.zwheel.core.calc.DefaultRangeEstimator
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.BoardType
import com.zwheel.core.model.KEY_BATTERY_PCT
import com.zwheel.core.model.KEY_CONNECTION_STATE
import com.zwheel.core.model.KEY_ESTIMATED_RANGE_M
import com.zwheel.core.model.KEY_IS_RIDING
import com.zwheel.core.model.KEY_SPEED_MPS_CORRECTED
import com.zwheel.core.model.KEY_SPEED_UNIT
import com.zwheel.core.model.KEY_TOP_SPEED_MPS
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
    private val connectionManager: ConnectionManager,
    private val rideServiceRepository: RideServiceRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val dataClient = Wearable.getDataClient(context)
    private var lastSentPayload: WatchPayload? = null

    fun startSync(scope: CoroutineScope) {
        scope.launch {
            combine(
                connectionManager.boardState,
                connectionManager.connectionState,
                rideServiceRepository.isRiding,
                settingsRepository.preferences,
                rideServiceRepository.topSpeedMetersPerSecond,
            ) { boardState, connectionState, isRiding, prefs, topSpeedMps ->
                val estimatedRangeMeters = DefaultRangeEstimator.estimateKilometersRemaining(
                    batteryPct = boardState.batteryPercent,
                    boardType = boardState.identity?.type ?: BoardType.UNKNOWN,
                )?.let { it * 1000.0 }
                toWatchPayload(boardState, connectionState, isRiding, prefs.speedUnit, topSpeedMps, estimatedRangeMeters)
            }.collect { payload ->
                if (payload != lastSentPayload) {
                    runCatching { putPayload(payload) }
                    lastSentPayload = payload
                }
            }
        }
    }

    private fun putPayload(payload: WatchPayload) {
        val request = PutDataMapRequest.create(DATA_PATH).apply {
            dataMap.putFloat(KEY_SPEED_MPS_CORRECTED, payload.speedMetersPerSecondCorrected?.toFloat() ?: -1f)
            dataMap.putFloat(KEY_TOP_SPEED_MPS, payload.topSpeedMetersPerSecond.toFloat())
            dataMap.putInt(KEY_BATTERY_PCT, payload.batteryPercent ?: -1)
            dataMap.putFloat(KEY_ESTIMATED_RANGE_M, payload.estimatedRangeMeters?.toFloat() ?: -1f)
            dataMap.putString(KEY_SPEED_UNIT, payload.speedUnit.name)
            dataMap.putBoolean(KEY_IS_RIDING, payload.isRiding)
            dataMap.putString(KEY_CONNECTION_STATE, payload.connectionState.name)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request)
    }
}

internal fun WatchPayload.toDataEntries(): Map<String, Any> = mapOf(
    KEY_SPEED_MPS_CORRECTED to (speedMetersPerSecondCorrected?.toFloat() ?: -1f),
    KEY_TOP_SPEED_MPS to topSpeedMetersPerSecond.toFloat(),
    KEY_BATTERY_PCT to (batteryPercent ?: -1),
    KEY_ESTIMATED_RANGE_M to (estimatedRangeMeters?.toFloat() ?: -1f),
    KEY_SPEED_UNIT to speedUnit.name,
    KEY_IS_RIDING to isRiding,
    KEY_CONNECTION_STATE to connectionState.name,
)

private fun toWatchPayload(
    boardState: BoardState,
    connectionState: ConnectionState,
    isRiding: Boolean,
    speedUnit: SpeedUnit,
    topSpeedMetersPerSecond: Double,
    estimatedRangeMeters: Double?,
): WatchPayload {
    val coreConnectionState = when (connectionState) {
        ConnectionState.Connected -> com.zwheel.core.model.ConnectionState.SUBSCRIBED
        ConnectionState.Scanning -> com.zwheel.core.model.ConnectionState.SCANNING
        ConnectionState.Disconnected -> com.zwheel.core.model.ConnectionState.DISCONNECTED
        ConnectionState.Idle -> com.zwheel.core.model.ConnectionState.IDLE
    }
    return WatchPayload(
        speedMetersPerSecondCorrected = boardState.speedMetersPerSecondCorrected,
        topSpeedMetersPerSecond = topSpeedMetersPerSecond,
        batteryPercent = boardState.batteryPercent,
        estimatedRangeMeters = estimatedRangeMeters,
        speedUnit = speedUnit,
        isRiding = isRiding,
        connectionState = coreConnectionState,
    )
}
