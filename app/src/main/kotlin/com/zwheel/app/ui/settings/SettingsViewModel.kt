package com.zwheel.app.ui.settings

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.ble.ConnectionManager
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.app.service.HaPushResult
import com.zwheel.app.service.HomeAssistantPusher
import com.zwheel.app.ui.ble.BleDebugLogExporter
import com.zwheel.core.model.BoardState
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit
import com.zwheel.core.protocol.debug.BleDebugRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val bleRecorder: BleDebugRecorder,
    @ApplicationContext private val appContext: Context,
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

    private val bleExporter: BleDebugLogExporter by lazy { BleDebugLogExporter(appContext) }

    private val _isDebugLogging = MutableStateFlow(false)
    val isDebugLogging: StateFlow<Boolean> = _isDebugLogging.asStateFlow()

    private val _debugStatus = MutableStateFlow<String?>(null)
    val debugStatus: StateFlow<String?> = _debugStatus.asStateFlow()

    fun setDebugLogging(enabled: Boolean) {
        if (enabled) {
            bleRecorder.reset()
            _isDebugLogging.value = true
            _debugStatus.value = "Logging"
        } else {
            _isDebugLogging.value = false
            _debugStatus.value = null
        }
    }

    fun restartDebugLogging() {
        bleRecorder.reset()
        _debugStatus.value = "Restarted — logging"
    }

    fun saveDebugPassword(password: String) {
        viewModelScope.launch { repo.saveDebugPassword(password) }
    }

    fun pairDebug() {
        val password = preferences.value.bleDebugPassword
        if (password.isBlank()) { _debugStatus.value = "Enter a password first"; return }
        _debugStatus.value = "Pairing..."
        viewModelScope.launch {
            runCatching { bleExporter.pair(password) }
                .onSuccess { msg -> _debugStatus.value = msg }
                .onFailure { err -> _debugStatus.value = "Pair failed: ${err.message}" }
        }
    }

    fun uploadDebug() {
        _debugStatus.value = "Uploading ${bleRecorder.eventCount} events..."
        viewModelScope.launch {
            runCatching { bleExporter.upload(bleRecorder.toJsonLines()) }
                .onSuccess { msg -> _debugStatus.value = msg }
                .onFailure { err -> _debugStatus.value = "Upload failed: ${err.message}" }
        }
    }

    fun shareDebug() {
        viewModelScope.launch {
            runCatching { bleExporter.share(bleRecorder.toJsonLines()) }
                .onSuccess { msg -> _debugStatus.value = msg }
                .onFailure { err -> _debugStatus.value = "Share failed: ${err.message}" }
        }
    }

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

    fun setAudioAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setAudioAlertsEnabled(enabled) }
    }

    fun setAudioAlertType(type: com.zwheel.core.alerts.AlertType) {
        viewModelScope.launch { repo.setAudioAlertType(type) }
    }

    fun setAudioAlertThresholdMph(mph: Int) {
        viewModelScope.launch { repo.setAudioAlertThresholdMph(mph) }
    }

    fun setAudioAlertThresholdHeadroom(value: Int) {
        viewModelScope.launch { repo.setAudioAlertThresholdHeadroom(value) }
    }

    fun setAudioAlertOutput(output: com.zwheel.core.alerts.AlertOutput) {
        viewModelScope.launch { repo.setAudioAlertOutput(output) }
    }

    fun setAudioAlertTone(tone: com.zwheel.core.alerts.AlertTone) {
        viewModelScope.launch { repo.setAudioAlertTone(tone) }
    }

    fun previewAlertTone(tone: com.zwheel.core.alerts.AlertTone) {
        val (toneType, durationMs) = when (tone) {
            com.zwheel.core.alerts.AlertTone.SHORT_BEEP -> ToneGenerator.TONE_CDMA_HIGH_SS to 500
            com.zwheel.core.alerts.AlertTone.TRIPLE_BEEP -> ToneGenerator.TONE_CDMA_ABBR_ALERT to 1000
            com.zwheel.core.alerts.AlertTone.ALARM -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK to 1500
        }
        runCatching {
            val gen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            gen.startTone(toneType, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { gen.release() } }, durationMs + 100L)
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

    fun saveBoardTireDiameter(diameter: Double) {
        viewModelScope.launch {
            repo.saveLastConnectedTireDiameter(diameter)
        }
    }

    fun forgetBoard() {
        viewModelScope.launch {
            repo.saveLastConnectedDeviceId(null)
            repo.saveLastConnectedBoardType(null)
            repo.saveLastConnectedTireDiameter(null)
            repo.saveLastConnectedIdentityDetails(null, null, null, null, null)
            repo.saveLastConnectedLifetimeStats(null, null)
            repo.saveLastConnectedCellCount(null)
        }
    }

    fun testHaConnection() {
        val prefs = preferences.value
        _haTestResult.value = null
        viewModelScope.launch {
            _haTestResult.value = HomeAssistantPusher.test(
                haUrl = prefs.haUrl,
                haToken = prefs.haToken,
            )
        }
    }

    fun clearHaSensors() {
        val prefs = preferences.value
        _haTestResult.value = null
        viewModelScope.launch {
            _haTestResult.value = HomeAssistantPusher.clearLegacySensor(
                haUrl = prefs.haUrl,
                haToken = prefs.haToken,
            )
        }
    }

    fun clearHaTestResult() {
        _haTestResult.value = null
    }
}
