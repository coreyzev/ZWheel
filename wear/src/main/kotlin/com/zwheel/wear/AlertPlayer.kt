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
    @Volatile private var playing = false

    fun play(type: AlertType) {
        if (playing) return
        playing = true
        val toneType = when (type) {
            AlertType.SPEED -> ToneGenerator.TONE_PROP_BEEP
            AlertType.HEADROOM -> ToneGenerator.TONE_PROP_BEEP2
        }
        runCatching {
            val gen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            gen.startTone(toneType, 500)
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { gen.release() }
                playing = false
            }, 600)
        }.onFailure { e ->
            playing = false
            Log.w("AlertPlayer", "Failed to play tone: ${e.message}")
        }
    }
}
