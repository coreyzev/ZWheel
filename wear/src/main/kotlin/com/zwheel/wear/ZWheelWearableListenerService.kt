package com.zwheel.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ZWheelWearableListenerService : WearableListenerService() {

    @Inject lateinit var repository: WearDataLayerRepository

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
}
