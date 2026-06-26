package com.zwheel.app.service

import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.core.alerts.AlertConfig
import com.zwheel.core.alerts.AlertMonitor
import com.zwheel.core.alerts.AlertOutput
import com.zwheel.core.alerts.AlertType
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val MPH_TO_MPS = 0.44704

internal class AudioAlertsService(
    private val settingsRepository: SettingsRepository,
    private val boardStateFlow: StateFlow<BoardState>,
    private val wearDispatcher: WearAlertDispatcher,
    private val phonePlayer: PhoneAudioPlayer,
) {
    private val monitor = AlertMonitor()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(settingsRepository.preferences, boardStateFlow) { prefs, state ->
                Pair(prefs, state)
            }.collect { (prefs, state) ->
                if (!prefs.audioAlertsEnabled || state.connectionState == ConnectionState.DISCONNECTED) {
                    monitor.reset()
                    return@collect
                }
                val config = AlertConfig(
                    enabled = true,
                    type = prefs.audioAlertType,
                    threshold = when (prefs.audioAlertType) {
                        AlertType.SPEED -> prefs.audioAlertThresholdMph * MPH_TO_MPS
                        AlertType.HEADROOM -> prefs.audioAlertThresholdHeadroom.toDouble()
                    },
                    output = prefs.audioAlertOutput,
                    hysteresis = when (prefs.audioAlertType) {
                        AlertType.SPEED -> 0.894
                        AlertType.HEADROOM -> 2.0
                    },
                )
                val nowMs = System.currentTimeMillis()
                if (
                    monitor.evaluate(
                        config,
                        state.speedMetersPerSecondCorrected,
                        state.safetyHeadroom,
                        nowMs,
                    )
                ) {
                    dispatch(config)
                }
            }
        }
    }

    private fun dispatch(config: AlertConfig) {
        when (config.output) {
            AlertOutput.PHONE -> phonePlayer.play(config.type)
            AlertOutput.WATCH -> wearDispatcher.fireAutoWithFallback(config.type, phonePlayer)
        }
    }
}
