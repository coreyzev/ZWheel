package com.zwheel.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwheel.app.data.settings.SettingsRepository
import com.zwheel.app.data.settings.UserPreferences
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = repo.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

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
}
