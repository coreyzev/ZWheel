package com.zwheel.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.zwheel.core.alerts.AlertOutput
import com.zwheel.core.alerts.AlertType
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
            lastConnectedBoardName = prefs[LAST_BOARD_NAME],
            lastConnectedSerial = prefs[LAST_BOARD_SERIAL],
            lastConnectedBatterySerial = prefs[LAST_BOARD_BATTERY_SERIAL],
            lastConnectedHardwareRev = prefs[LAST_BOARD_HW_REV],
            lastConnectedFirmwareRev = prefs[LAST_BOARD_FW_REV],
            lastConnectedLifetimeMiles = prefs[LAST_BOARD_LIFETIME_MILES],
            lastConnectedLifetimeAmpHours = prefs[LAST_BOARD_LIFETIME_AH],
            lastConnectedCellCount = prefs[LAST_BOARD_CELL_COUNT],
            hasRequestedBatteryOptimization = prefs[HAS_REQUESTED_BATTERY_OPT] ?: false,
            hasAttemptedLocationPermission = prefs[HAS_ATTEMPTED_LOCATION_PERM] ?: false,
            haUrl = prefs[HA_URL] ?: "",
            haToken = token,
            customBoardName = prefs[CUSTOM_BOARD_NAME]?.takeIf { it.isNotBlank() },
            bleDebugPassword = prefs[BLE_DEBUG_PASSWORD] ?: "",
            audioAlertsEnabled = prefs[AUDIO_ALERTS_ENABLED] ?: false,
            audioAlertType = prefs[AUDIO_ALERT_TYPE].toEnumOrDefault(AlertType.SPEED),
            audioAlertThresholdMph = prefs[AUDIO_ALERT_THRESHOLD_MPH] ?: 16,
            audioAlertThresholdHeadroom = prefs[AUDIO_ALERT_THRESHOLD_HEADROOM] ?: 0,
            audioAlertOutput = prefs[AUDIO_ALERT_OUTPUT].toEnumOrDefault(AlertOutput.AUTO),
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

    suspend fun saveDebugPassword(password: String) {
        dataStore.edit { prefs -> prefs[BLE_DEBUG_PASSWORD] = password }
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
        name: String?,
        serial: String?,
        batterySerial: String?,
        hardwareRev: String?,
        firmwareRev: String?,
    ) {
        dataStore.edit { prefs ->
            if (name != null) prefs[LAST_BOARD_NAME] = name else prefs.remove(LAST_BOARD_NAME)
            if (serial != null) prefs[LAST_BOARD_SERIAL] = serial else prefs.remove(LAST_BOARD_SERIAL)
            if (batterySerial != null) prefs[LAST_BOARD_BATTERY_SERIAL] = batterySerial else prefs.remove(LAST_BOARD_BATTERY_SERIAL)
            if (hardwareRev != null) prefs[LAST_BOARD_HW_REV] = hardwareRev else prefs.remove(LAST_BOARD_HW_REV)
            if (firmwareRev != null) prefs[LAST_BOARD_FW_REV] = firmwareRev else prefs.remove(LAST_BOARD_FW_REV)
        }
    }

    suspend fun saveLastConnectedLifetimeStats(lifetimeMiles: Int?, lifetimeAh: Double?) {
        dataStore.edit { prefs ->
            if (lifetimeMiles != null) prefs[LAST_BOARD_LIFETIME_MILES] = lifetimeMiles
            else prefs.remove(LAST_BOARD_LIFETIME_MILES)
            if (lifetimeAh != null) prefs[LAST_BOARD_LIFETIME_AH] = lifetimeAh
            else prefs.remove(LAST_BOARD_LIFETIME_AH)
        }
    }

    suspend fun saveLastConnectedCellCount(cellCount: Int?) {
        dataStore.edit { prefs ->
            if (cellCount != null) prefs[LAST_BOARD_CELL_COUNT] = cellCount
            else prefs.remove(LAST_BOARD_CELL_COUNT)
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

    suspend fun setAudioAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[AUDIO_ALERTS_ENABLED] = enabled }
    }

    suspend fun setAudioAlertType(type: AlertType) {
        dataStore.edit { it[AUDIO_ALERT_TYPE] = type.name }
    }

    suspend fun setAudioAlertThresholdMph(mph: Int) {
        dataStore.edit { it[AUDIO_ALERT_THRESHOLD_MPH] = mph.coerceIn(1, 60) }
    }

    suspend fun setAudioAlertThresholdHeadroom(value: Int) {
        dataStore.edit { it[AUDIO_ALERT_THRESHOLD_HEADROOM] = value.coerceIn(-10, 20) }
    }

    suspend fun setAudioAlertOutput(output: AlertOutput) {
        dataStore.edit { it[AUDIO_ALERT_OUTPUT] = output.name }
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
        val LAST_BOARD_NAME = stringPreferencesKey("last_board_name")
        val LAST_BOARD_SERIAL = stringPreferencesKey("last_board_serial")
        val LAST_BOARD_BATTERY_SERIAL = stringPreferencesKey("last_board_battery_serial")
        val LAST_BOARD_HW_REV = stringPreferencesKey("last_board_hw_rev")
        val LAST_BOARD_FW_REV = stringPreferencesKey("last_board_fw_rev")
        val LAST_BOARD_LIFETIME_MILES = intPreferencesKey("last_board_lifetime_miles")
        val LAST_BOARD_LIFETIME_AH = doublePreferencesKey("last_board_lifetime_ah")
        val LAST_BOARD_CELL_COUNT = intPreferencesKey("last_board_cell_count")
        val HAS_REQUESTED_BATTERY_OPT = booleanPreferencesKey("has_requested_battery_opt")
        val HAS_ATTEMPTED_LOCATION_PERM = booleanPreferencesKey("has_attempted_location_perm")
        val HAS_CUSTOM_TIRE_DIAMETER = booleanPreferencesKey("has_custom_tire_diameter")
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val BLE_DEBUG_PASSWORD = stringPreferencesKey("ble_debug_password")
        val CUSTOM_BOARD_NAME = stringPreferencesKey("custom_board_name")
        val AUDIO_ALERTS_ENABLED = booleanPreferencesKey("audio_alerts_enabled")
        val AUDIO_ALERT_TYPE = stringPreferencesKey("audio_alert_type")
        val AUDIO_ALERT_THRESHOLD_MPH = intPreferencesKey("audio_alert_threshold_mph")
        val AUDIO_ALERT_THRESHOLD_HEADROOM = intPreferencesKey("audio_alert_threshold_headroom")
        val AUDIO_ALERT_OUTPUT = stringPreferencesKey("audio_alert_output")
        const val KEY_HA_TOKEN_SECURE = "ha_token_secure"
        val TIRE_DIAMETER_RANGE = 8.0..16.0
        const val DEFAULT_TIRE_DIAMETER = 11.5
    }
}
