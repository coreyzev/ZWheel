package com.zwheel.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.ble.ConnectionManager
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.app.service.HaPushResult
import com.zwheel.app.service.HomeAssistantPusher
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val connectionManager: ConnectionManager,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = repo.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

    val boardState: StateFlow<BoardState> = connectionManager.boardState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BoardState(),
    )

    val rssi: StateFlow<Int?> = connectionManager.rssi.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _haTestResult = MutableStateFlow<HaPushResult?>(null)
    val haTestResult: StateFlow<HaPushResult?> = _haTestResult.asStateFlow()

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch {
            repo.setSpeedUnit(unit)
        }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch {
            repo.setTemperatureUnit(unit)
        }
    }

    fun setTireDiameter(value: Double) {
        viewModelScope.launch {
            repo.setTireDiameterInches(value)
        }
    }

    fun setHaUrl(url: String) {
        viewModelScope.launch {
            repo.setHaUrl(url)
        }
    }

    fun setHaToken(token: String) {
        viewModelScope.launch {
            repo.setHaToken(token)
        }
    }

    fun setCustomBoardName(name: String?) {
        viewModelScope.launch {
            repo.setCustomBoardName(name)
        }
    }

    fun forgetBoard() {
        viewModelScope.launch {
            repo.saveLastConnectedDeviceId(null)
        }
    }

    fun testHaConnection() {
        val prefs = preferences.value
        _haTestResult.value = null
        viewModelScope.launch {
            _haTestResult.value = HomeAssistantPusher.push(
                haUrl = prefs.haUrl,
                haToken = prefs.haToken,
                batteryPercent = 50,
            )
        }
    }

    fun clearHaTestResult() {
        _haTestResult.value = null
    }
}
