package com.zwheel.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { preferences ->
        UserPreferences(
            speedUnit = preferences[SPEED_UNIT].toEnumOrDefault(SpeedUnit.MPH),
            temperatureUnit = preferences[TEMPERATURE_UNIT].toEnumOrDefault(TemperatureUnit.FAHRENHEIT),
            tireDiameterInches = preferences[TIRE_DIAMETER]?.coerceIn(TIRE_DIAMETER_RANGE) ?: DEFAULT_TIRE_DIAMETER,
        )
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        dataStore.edit { preferences ->
            preferences[SPEED_UNIT] = unit.name
        }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { preferences ->
            preferences[TEMPERATURE_UNIT] = unit.name
        }
    }

    suspend fun setTireDiameterInches(value: Double) {
        dataStore.edit { preferences ->
            preferences[TIRE_DIAMETER] = value.coerceIn(TIRE_DIAMETER_RANGE)
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: default

    private companion object {
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val TIRE_DIAMETER = doublePreferencesKey("tire_diameter")
        val TIRE_DIAMETER_RANGE = 8.0..16.0
        const val DEFAULT_TIRE_DIAMETER = 11.5
    }
}
