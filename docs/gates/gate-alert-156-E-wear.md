# Gate E — Issue #156: Wear OS Alert Receiver and AudioPlayer

## Depends on
Gates A, B, and C must be merged first.

**Key constant defined in Gate C (`app/service/WearAlertDispatcher.kt`):**
```kotlin
internal const val WEAR_ALERT_PATH = "/zwheel/alert"
```
The watch receives a `MessageEvent` on the path `/zwheel/alert`. The message data bytes are the `AlertType` enum name as a UTF-8 string (`"SPEED"` or `"HEADROOM"`).

**Already exists in `core/alerts/`:**
- `AlertType` — `SPEED`, `HEADROOM`

**Existing wear service:** `ZWheelWearableListenerService` extends `WearableListenerService` and currently only handles `onDataChanged`. We need to add `onMessageReceived`.

**Existing wear manifest:** declares `ZWheelWearableListenerService` with an intent-filter for `DATA_CHANGED` on `/zwheel/state`.

**Pattern for audio:** Use `ToneGenerator(AudioManager.STREAM_MUSIC, 85)` — plays through the current audio route including the watch speaker.

## Files to create

### `wear/src/main/kotlin/com/zwheel/wear/AlertPlayer.kt`
```kotlin
package com.zwheel.wear

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zwheel.core.alerts.AlertType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertPlayer @Inject constructor() {
    fun play(type: AlertType) {
        val toneType = when (type) {
            AlertType.SPEED -> ToneGenerator.TONE_PROP_BEEP
            AlertType.HEADROOM -> ToneGenerator.TONE_PROP_BEEP2
        }
        runCatching {
            val gen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            gen.startTone(toneType, 500)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { gen.release() } }, 600)
        }.onFailure { e ->
            Log.w("AlertPlayer", "Failed to play tone: ${e.message}")
        }
    }
}
```

## Files to modify

### `wear/src/main/kotlin/com/zwheel/wear/ZWheelWearableListenerService.kt`

Add Hilt injection of `AlertPlayer` and override `onMessageReceived`:

```kotlin
package com.zwheel.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.zwheel.core.alerts.AlertType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val ALERT_PATH = "/zwheel/alert"

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
        if (event.path != ALERT_PATH) return
        runCatching {
            val type = AlertType.valueOf(String(event.data))
            alertPlayer.play(type)
        }.onFailure { e ->
            android.util.Log.w("ZWheelWear", "Unknown alert type in message: ${e.message}")
        }
    }
}
```

### `wear/src/main/AndroidManifest.xml`

Add a second `<intent-filter>` inside the existing `ZWheelWearableListenerService` `<service>` element to register for `MESSAGE_RECEIVED` events on the alert path:

```xml
<service
    android:name=".ZWheelWearableListenerService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
        <data
            android:scheme="wear"
            android:host="*"
            android:pathPrefix="/zwheel/state" />
    </intent-filter>
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
        <data
            android:scheme="wear"
            android:host="*"
            android:pathPrefix="/zwheel/alert" />
    </intent-filter>
</service>
```

Keep everything else in the manifest exactly as-is. Do not add any permissions.

## Compile and verify
Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :wear:compileDebugKotlin`
Must compile clean. Fix any errors before committing.

## Commit
`feat(wear): receive alert messages and play tone through watch speaker — issue #156`

Do not modify any other files.
