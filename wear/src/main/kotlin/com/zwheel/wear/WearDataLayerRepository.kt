package com.zwheel.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.zwheel.core.model.ConnectionState
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

private fun DataMap.toWatchPayload(): WatchPayload {
    val speedRaw = getFloat("speed_mps_corrected")
    val batteryRaw = getInt("battery_pct")
    val rangeRaw = getFloat("estimated_range_m")
    val connState = try {
        ConnectionState.valueOf(getString("connection_state") ?: "DISCONNECTED")
    } catch (e: IllegalArgumentException) {
        ConnectionState.DISCONNECTED
    }
    return WatchPayload(
        speedMetersPerSecondCorrected = if (speedRaw < 0) null else speedRaw.toDouble(),
        topSpeedMetersPerSecond = getFloat("top_speed_mps").toDouble(),
        batteryPercent = if (batteryRaw < 0) null else batteryRaw,
        estimatedRangeMeters = if (rangeRaw < 0) null else rangeRaw.toDouble(),
        speedUnit = try {
            SpeedUnit.valueOf(getString("speed_unit") ?: "MPH")
        } catch (e: Exception) {
            SpeedUnit.MPH
        },
        isRiding = getBoolean("is_riding"),
        connectionState = connState,
    )
}
