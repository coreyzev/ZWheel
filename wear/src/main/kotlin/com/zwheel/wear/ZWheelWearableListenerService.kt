package com.zwheel.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.zwheel.core.alerts.AlertType
import com.zwheel.core.model.ALERT_MESSAGE_PATH
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ZWheelWearableListenerService : WearableListenerService() {

    @Inject lateinit var repository: WearDataLayerRepository
    @Inject lateinit var alertPlayer: AlertPlayer

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/zwheel/state"
            ) {
                repository.onDataMapReceived(
                    DataMapItem.fromDataItem(event.dataItem).dataMap,
                )
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != ALERT_MESSAGE_PATH) return
        runCatching {
            val type = AlertType.valueOf(String(event.data))
            alertPlayer.play(type)
        }.onFailure { e ->
            android.util.Log.w("ZWheelWear", "Unknown alert type in message: ${e.message}")
        }
    }
}
