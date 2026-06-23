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
import com.zwheel.core.model.BoardType
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
            lastConnectedBoardType = prefs[LAST_BOARD_TYPE]?.let { name ->
                enumValues<BoardType>().firstOrNull { it.name == name }
            },
            lastConnectedTireDiameterInches = prefs[LAST_BOARD_TIRE_DIAMETER],
            lastConnectedSerial = prefs[LAST_BOARD_SERIAL],
            lastConnectedBatterySerial = prefs[LAST_BOARD_BATTERY_SERIAL],
            lastConnectedHardwareRev = prefs[LAST_BOARD_HW_REV],
            lastConnectedFirmwareRev = prefs[LAST_BOARD_FW_REV],
            hasRequestedBatteryOptimization = prefs[HAS_REQUESTED_BATTERY_OPT] ?: false,
            hasAttemptedLocationPermission = prefs[HAS_ATTEMPTED_LOCATION_PERM] ?: false,
            haUrl = prefs[HA_URL] ?: "",
            haToken = token,
            customBoardName = prefs[CUSTOM_BOARD_NAME]?.takeIf { it.isNotBlank() },
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

    suspend fun saveHasAttemptedLocationPermission() {
        dataStore.edit { it[HAS_ATTEMPTED_LOCATION_PERM] = true }
    }

    suspend fun saveLastConnectedDeviceId(id: String?) {
        dataStore.edit { preferences ->
            if (id != null) preferences[LAST_DEVICE_ID] = id
            else preferences.remove(LAST_DEVICE_ID)
        }
    }

    suspend fun saveLastConnectedBoardType(type: BoardType?) {
        dataStore.edit { prefs ->
            if (type != null) prefs[LAST_BOARD_TYPE] = type.name
            else prefs.remove(LAST_BOARD_TYPE)
        }
    }

    suspend fun saveLastConnectedIdentityDetails(
        serial: String?,
        batterySerial: String?,
        hardwareRev: String?,
        firmwareRev: String?,
    ) {
        dataStore.edit { prefs ->
            if (serial != null) prefs[LAST_BOARD_SERIAL] = serial else prefs.remove(LAST_BOARD_SERIAL)
            if (batterySerial != null) prefs[LAST_BOARD_BATTERY_SERIAL] = batterySerial else prefs.remove(LAST_BOARD_BATTERY_SERIAL)
            if (hardwareRev != null) prefs[LAST_BOARD_HW_REV] = hardwareRev else prefs.remove(LAST_BOARD_HW_REV)
            if (firmwareRev != null) prefs[LAST_BOARD_FW_REV] = firmwareRev else prefs.remove(LAST_BOARD_FW_REV)
        }
    }

    suspend fun saveLastConnectedTireDiameter(diameter: Double?) {
        dataStore.edit { prefs ->
            if (diameter != null) prefs[LAST_BOARD_TIRE_DIAMETER] = diameter.coerceIn(TIRE_DIAMETER_RANGE)
            else prefs.remove(LAST_BOARD_TIRE_DIAMETER)
        }
    }

    suspend fun setCustomBoardName(name: String?) {
        // Local-only display override. Never write this value to the board.
        dataStore.edit { preferences ->
            if (name != null && name.isNotBlank()) preferences[CUSTOM_BOARD_NAME] = name.trim()
            else preferences.remove(CUSTOM_BOARD_NAME)
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: default

    private companion object {
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val TIRE_DIAMETER = doublePreferencesKey("tire_diameter")
        val LAST_DEVICE_ID = stringPreferencesKey("last_device_id")
        val LAST_BOARD_TYPE = stringPreferencesKey("last_board_type")
        val LAST_BOARD_TIRE_DIAMETER = doublePreferencesKey("last_board_tire_diameter")
        val LAST_BOARD_SERIAL = stringPreferencesKey("last_board_serial")
        val LAST_BOARD_BATTERY_SERIAL = stringPreferencesKey("last_board_battery_serial")
        val LAST_BOARD_HW_REV = stringPreferencesKey("last_board_hw_rev")
        val LAST_BOARD_FW_REV = stringPreferencesKey("last_board_fw_rev")
        val HAS_REQUESTED_BATTERY_OPT = booleanPreferencesKey("has_requested_battery_opt")
        val HAS_ATTEMPTED_LOCATION_PERM = booleanPreferencesKey("has_attempted_location_perm")
        val HAS_CUSTOM_TIRE_DIAMETER = booleanPreferencesKey("has_custom_tire_diameter")
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val CUSTOM_BOARD_NAME = stringPreferencesKey("custom_board_name")
        const val KEY_HA_TOKEN_SECURE = "ha_token_secure"
        val TIRE_DIAMETER_RANGE = 8.0..16.0
        const val DEFAULT_TIRE_DIAMETER = 11.5
    }
}
