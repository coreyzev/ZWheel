package com.zwheel.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
            lastConnectedDeviceId = preferences[LAST_DEVICE_ID],
            hasRequestedBatteryOptimization = preferences[HAS_REQUESTED_BATTERY_OPT] ?: false,
            haUrl = preferences[HA_URL] ?: "",
            haToken = preferences[HA_TOKEN] ?: "",
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

    suspend fun setHaUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[HA_URL] = url.trim()
        }
    }

    suspend fun setHaToken(token: String) {
        dataStore.edit { preferences ->
            preferences[HA_TOKEN] = token.trim()
        }
    }

    suspend fun saveHasRequestedBatteryOptimization() {
        dataStore.edit { it[HAS_REQUESTED_BATTERY_OPT] = true }
    }

    suspend fun saveLastConnectedDeviceId(id: String?) {
        dataStore.edit { preferences ->
            if (id != null) preferences[LAST_DEVICE_ID] = id
            else preferences.remove(LAST_DEVICE_ID)
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: default

    private companion object {
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val TIRE_DIAMETER = doublePreferencesKey("tire_diameter")
        val LAST_DEVICE_ID = stringPreferencesKey("last_device_id")
        val HAS_REQUESTED_BATTERY_OPT = booleanPreferencesKey("has_requested_battery_opt")
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val TIRE_DIAMETER_RANGE = 8.0..16.0
        const val DEFAULT_TIRE_DIAMETER = 11.5
    }
}
