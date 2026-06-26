package com.zwheel.app.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.zwheel.core.alerts.AlertType

internal class PhoneAudioPlayer(private val context: Context) {
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
            android.util.Log.w("PhoneAudioPlayer", "Failed to play alert tone: ${e.message}")
        }
    }
}
