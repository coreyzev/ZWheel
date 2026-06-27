package com.zwheel.app.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.zwheel.core.alerts.AlertTone

internal class PhoneAudioPlayer(private val context: Context) {
    @Volatile private var playing = false

    fun play(tone: AlertTone) {
        if (playing) return
        playing = true
        val (toneType, durationMs) = when (tone) {
            AlertTone.SHORT_BEEP -> ToneGenerator.TONE_CDMA_HIGH_SS to 500
            AlertTone.TRIPLE_BEEP -> ToneGenerator.TONE_CDMA_ABBR_ALERT to 1000
            AlertTone.ALARM -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK to 1500
        }
        runCatching {
            val gen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            gen.startTone(toneType, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { gen.release() }
                playing = false
            }, durationMs + 100L)
        }.onFailure { e ->
            playing = false
            android.util.Log.w("PhoneAudioPlayer", "Failed to play alert tone: ${e.message}")
        }
    }
}
