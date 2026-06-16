package com.zwheel.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.zwheel.core.model.SpeedUnit
import com.zwheel.core.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val context: Context,
) {
    // Nullable: Keystore may be unavailable on first boot, after factory reset, or on some
    // OEM devices. Fall back gracefully rather than crashing on initialization.
    private val encryptedPrefs: SharedPreferences? by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "zwheel_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            null
        }
    }

    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        val legacyToken = prefs[HA_TOKEN]
        // Prefer encrypted store; fall back to legacy plaintext key when Keystore is
        // unavailable or for pre-upgrade installs that haven't migrated yet.
        val token = encryptedPrefs?.getString(KEY_HA_TOKEN_SECURE, null)?.takeIf { it.isNotEmpty() }
            ?: legacyToken?.trim()
            ?: ""
        UserPreferences(
            speedUnit = prefs[SPEED_UNIT].toEnumOrDefault(SpeedUnit.MPH),
            temperatureUnit = prefs[TEMPERATURE_UNIT].toEnumOrDefault(TemperatureUnit.FAHRENHEIT),
            tireDiameterInches = prefs[TIRE_DIAMETER]?.coerceIn(TIRE_DIAMETER_RANGE) ?: DEFAULT_TIRE_DIAMETER,
            hasCustomTireDiameter = prefs[HAS_CUSTOM_TIRE_DIAMETER] ?: false,
            lastConnectedDeviceId = prefs[LAST_DEVICE_ID],
            hasRequestedBatteryOptimization = prefs[HAS_REQUESTED_BATTERY_OPT] ?: false,
            haUrl = prefs[HA_URL] ?: "",
            haToken = token,
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
            preferences[HAS_CUSTOM_TIRE_DIAMETER] = true
        }
    }

    suspend fun setHaUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[HA_URL] = url.trim()
        }
    }

    suspend fun setHaToken(token: String) {
        encryptedPrefs?.edit()?.putString(KEY_HA_TOKEN_SECURE, token.trim())?.apply()
        // Clear any legacy plaintext token from DataStore.
        dataStore.edit { preferences ->
            preferences.remove(HA_TOKEN)
        }
    }

    // Moves the legacy plaintext HA token from DataStore into EncryptedSharedPreferences.
    // Idempotent: safe to call every startup. Skipped if Keystore is unavailable.
    suspend fun migrateHaTokenIfNeeded() {
        val prefs = encryptedPrefs ?: return
        dataStore.edit { store ->
            val legacy = store[HA_TOKEN]?.trim()?.takeIf { it.isNotEmpty() } ?: return@edit
            val alreadySecured = prefs.getString(KEY_HA_TOKEN_SECURE, null)?.isNotEmpty() == true
            if (!alreadySecured) {
                prefs.edit().putString(KEY_HA_TOKEN_SECURE, legacy).apply()
            }
            store.remove(HA_TOKEN)
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
        val HAS_CUSTOM_TIRE_DIAMETER = booleanPreferencesKey("has_custom_tire_diameter")
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        const val KEY_HA_TOKEN_SECURE = "ha_token_secure"
        val TIRE_DIAMETER_RANGE = 8.0..16.0
        const val DEFAULT_TIRE_DIAMETER = 11.5
    }
}
