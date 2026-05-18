package ru.ozero.engineurnetwork

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreUrnetworkConfigStore(
    private val dataStore: DataStore<Preferences>,
) : UrnetworkConfigStore {

    override fun walletAddress(): Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_WALLET_OVERRIDE]?.takeIf { it.isNotBlank() } ?: UrnetworkDefaults.PRESET_WALLET
    }

    override fun walletOverride(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_WALLET_OVERRIDE]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setWalletOverride(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(KEY_WALLET_OVERRIDE)
            } else {
                prefs[KEY_WALLET_OVERRIDE] = value
            }
        }
    }

    override fun byJwt(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_BY_JWT]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setByJwt(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(KEY_BY_JWT)
            } else {
                prefs[KEY_BY_JWT] = value
            }
        }
    }

    override fun byClientJwt(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_BY_CLIENT_JWT]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setByClientJwt(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(KEY_BY_CLIENT_JWT)
            } else {
                prefs[KEY_BY_CLIENT_JWT] = value
            }
        }
    }

    override fun windowType(): Flow<UrnetworkWindowType> = dataStore.data.map { prefs ->
        UrnetworkWindowType.fromRaw(prefs[KEY_WINDOW_TYPE])
    }

    override suspend fun setWindowType(value: UrnetworkWindowType) {
        dataStore.edit { prefs -> prefs[KEY_WINDOW_TYPE] = value.rawValue }
    }

    override fun fixedIpSize(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FIXED_IP_SIZE] == true
    }

    override suspend fun setFixedIpSize(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_FIXED_IP_SIZE] = value }
    }

    override fun provideEnabled(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PROVIDE_ENABLED] != false
    }

    override suspend fun setProvideEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PROVIDE_ENABLED] = value }
    }

    override fun provideControlMode(): Flow<UrnetworkProvideControlMode> = dataStore.data.map { prefs ->
        UrnetworkProvideControlMode.fromRaw(prefs[KEY_PROVIDE_CONTROL_MODE])
    }

    override suspend fun setProvideControlMode(value: UrnetworkProvideControlMode) {
        dataStore.edit { prefs -> prefs[KEY_PROVIDE_CONTROL_MODE] = value.rawValue }
    }

    override fun provideNetworkMode(): Flow<UrnetworkProvideNetworkMode> = dataStore.data.map { prefs ->
        UrnetworkProvideNetworkMode.fromRaw(prefs[KEY_PROVIDE_NETWORK_MODE])
    }

    override suspend fun setProvideNetworkMode(value: UrnetworkProvideNetworkMode) {
        dataStore.edit { prefs -> prefs[KEY_PROVIDE_NETWORK_MODE] = value.rawValue }
    }

    override fun selectedCountryCode(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_COUNTRY_CODE]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setSelectedCountryCode(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(KEY_SELECTED_COUNTRY_CODE)
            else prefs[KEY_SELECTED_COUNTRY_CODE] = value.trim().uppercase()
        }
    }

    override fun selectedRegion(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_REGION]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setSelectedRegion(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(KEY_SELECTED_REGION)
            else prefs[KEY_SELECTED_REGION] = value.trim()
        }
    }

    override fun selectedCity(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_CITY]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setSelectedCity(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(KEY_SELECTED_CITY)
            else prefs[KEY_SELECTED_CITY] = value.trim()
        }
    }

    private companion object {
        val KEY_WALLET_OVERRIDE = stringPreferencesKey("urnetwork_wallet_override")
        val KEY_BY_JWT = stringPreferencesKey("urnetwork_by_jwt")
        val KEY_BY_CLIENT_JWT = stringPreferencesKey("urnetwork_by_client_jwt")
        val KEY_WINDOW_TYPE = stringPreferencesKey("urnetwork_window_type")
        val KEY_FIXED_IP_SIZE = booleanPreferencesKey("urnetwork_fixed_ip_size")
        val KEY_PROVIDE_ENABLED = booleanPreferencesKey("urnetwork_provide_enabled")
        val KEY_PROVIDE_CONTROL_MODE = stringPreferencesKey("urnetwork_provide_control_mode")
        val KEY_PROVIDE_NETWORK_MODE = stringPreferencesKey("urnetwork_provide_network_mode")
        val KEY_SELECTED_COUNTRY_CODE = stringPreferencesKey("urnetwork_selected_country_code")
        val KEY_SELECTED_REGION = stringPreferencesKey("urnetwork_selected_region")
        val KEY_SELECTED_CITY = stringPreferencesKey("urnetwork_selected_city")
    }
}
