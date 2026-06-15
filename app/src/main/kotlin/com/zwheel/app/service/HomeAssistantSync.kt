package com.zwheel.app.service

import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.core.model.BoardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class HomeAssistantSync(
    private val settingsRepository: SettingsRepository,
    private val boardStateFlow: StateFlow<BoardState>,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            var lastPushedPercent: Int? = null
            combine(settingsRepository.preferences, boardStateFlow) { prefs, boardState ->
                Pair(prefs, boardState)
            }.collect { (prefs, boardState) ->
                val url = prefs.haUrl.takeIf { it.isNotBlank() } ?: return@collect
                val token = prefs.haToken.takeIf { it.isNotBlank() } ?: return@collect
                val pct = boardState.batteryPercent ?: return@collect
                if (pct == lastPushedPercent) return@collect
                lastPushedPercent = pct
                HomeAssistantPusher.push(url, token, pct)
            }
        }
    }
}
