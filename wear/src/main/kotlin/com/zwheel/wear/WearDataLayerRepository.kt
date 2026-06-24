package com.zwheel.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.zwheel.core.model.ConnectionState
import com.zwheel.core.model.KEY_BATTERY_PCT
import com.zwheel.core.model.KEY_CONNECTION_STATE
import com.zwheel.core.model.KEY_ESTIMATED_RANGE_M
import com.zwheel.core.model.KEY_IS_RIDING
import com.zwheel.core.model.KEY_LAST_ERROR_CODE
import com.zwheel.core.model.KEY_SAFETY_HEADROOM
import com.zwheel.core.model.KEY_SPEED_MPS_CORRECTED
import com.zwheel.core.model.KEY_SPEED_UNIT
import com.zwheel.core.model.KEY_TOP_SPEED_MPS
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.WatchPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearDataLayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DataClient.OnDataChangedListener {

    private val _payload = MutableStateFlow<WatchPayload?>(null)
    val payload: StateFlow<WatchPayload?> = _payload.asStateFlow()

    private val dataClient: DataClient = Wearable.getDataClient(context)

    fun register() {
        dataClient.addListener(this)
    }

    fun unregister() {
        dataClient.removeListener(this)
    }

    fun onDataMapReceived(dataMap: DataMap) {
        _payload.value = dataMap.toWatchPayload()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/zwheel/state"
            ) {
                onDataMapReceived(DataMapItem.fromDataItem(event.dataItem).dataMap)
            }
        }
    }
}

internal fun decodeWatchPayload(
    speedRaw: Float,
    topSpeedRaw: Float,
    batteryRaw: Int,
    rangeRaw: Float,
    speedUnitStr: String?,
    isRiding: Boolean,
    connStateStr: String?,
    safetyHeadroomRaw: Int,
    lastErrorCodeRaw: Int,
): WatchPayload = WatchPayload(
    speedMetersPerSecondCorrected = if (speedRaw < 0) null else speedRaw.toDouble(),
    topSpeedMetersPerSecond = topSpeedRaw.toDouble(),
    batteryPercent = if (batteryRaw < 0) null else batteryRaw,
    estimatedRangeMeters = if (rangeRaw < 0) null else rangeRaw.toDouble(),
    speedUnit = try {
        SpeedUnit.valueOf(speedUnitStr ?: "MPH")
    } catch (_: Exception) {
        SpeedUnit.MPH
    },
    isRiding = isRiding,
    connectionState = try {
        ConnectionState.valueOf(connStateStr ?: "DISCONNECTED")
    } catch (_: IllegalArgumentException) {
        ConnectionState.DISCONNECTED
    },
    safetyHeadroom = if (safetyHeadroomRaw < 0) null else safetyHeadroomRaw,
    lastErrorCode = if (lastErrorCodeRaw <= 0) null else lastErrorCodeRaw,
)

private fun DataMap.toWatchPayload(): WatchPayload = decodeWatchPayload(
    speedRaw = getFloat(KEY_SPEED_MPS_CORRECTED),
    topSpeedRaw = getFloat(KEY_TOP_SPEED_MPS),
    batteryRaw = getInt(KEY_BATTERY_PCT),
    rangeRaw = getFloat(KEY_ESTIMATED_RANGE_M),
    speedUnitStr = getString(KEY_SPEED_UNIT),
    isRiding = getBoolean(KEY_IS_RIDING),
    connStateStr = getString(KEY_CONNECTION_STATE),
    safetyHeadroomRaw = getInt(KEY_SAFETY_HEADROOM, -1),
    lastErrorCodeRaw = getInt(KEY_LAST_ERROR_CODE, -1),
)
