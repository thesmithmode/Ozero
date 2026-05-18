package ru.ozero.engineurnetwork

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreUrnetworkConfigStore(
    private val dataStore: DataStore<Preferences>,
) : UrnetworkConfigStore {

    override fun config(): Flow<UrnetworkConfig> = dataStore.data.map { prefs ->
        UrnetworkConfig(
            walletOverride = prefs[KEY_WALLET_OVERRIDE]?.takeIf { it.isNotBlank() },
            byJwt = prefs[KEY_BY_JWT]?.takeIf { it.isNotBlank() },
            byClientJwt = prefs[KEY_BY_CLIENT_JWT]?.takeIf { it.isNotBlank() },
            windowType = UrnetworkWindowType.fromRaw(prefs[KEY_WINDOW_TYPE]),
            fixedIpSize = prefs[KEY_FIXED_IP_SIZE] == true,
            allowDirect = prefs[KEY_ALLOW_DIRECT] != false,
            provideEnabled = prefs[KEY_PROVIDE_ENABLED] != false,
            provideControlMode = UrnetworkProvideControlMode.fromRaw(prefs[KEY_PROVIDE_CONTROL_MODE]),
            provideNetworkMode = UrnetworkProvideNetworkMode.fromRaw(prefs[KEY_PROVIDE_NETWORK_MODE]),
            selectedLocation = UrnetworkLocationSelection(
                countryCode = prefs[KEY_SELECTED_COUNTRY_CODE]?.takeIf { it.isNotBlank() },
                region = prefs[KEY_SELECTED_REGION]?.takeIf { it.isNotBlank() },
                city = prefs[KEY_SELECTED_CITY]?.takeIf { it.isNotBlank() },
            ),
        )
    }

    override suspend fun update(transform: (UrnetworkConfig) -> UrnetworkConfig) {
        dataStore.edit { prefs ->
            val current = readConfig(prefs)
            val next = transform(current)
            writeConfig(prefs, next)
        }
    }

    private fun readConfig(prefs: Preferences): UrnetworkConfig = UrnetworkConfig(
        walletOverride = prefs[KEY_WALLET_OVERRIDE]?.takeIf { it.isNotBlank() },
        byJwt = prefs[KEY_BY_JWT]?.takeIf { it.isNotBlank() },
        byClientJwt = prefs[KEY_BY_CLIENT_JWT]?.takeIf { it.isNotBlank() },
        windowType = UrnetworkWindowType.fromRaw(prefs[KEY_WINDOW_TYPE]),
        fixedIpSize = prefs[KEY_FIXED_IP_SIZE] == true,
        allowDirect = prefs[KEY_ALLOW_DIRECT] != false,
        provideEnabled = prefs[KEY_PROVIDE_ENABLED] != false,
        provideControlMode = UrnetworkProvideControlMode.fromRaw(prefs[KEY_PROVIDE_CONTROL_MODE]),
        provideNetworkMode = UrnetworkProvideNetworkMode.fromRaw(prefs[KEY_PROVIDE_NETWORK_MODE]),
        selectedLocation = UrnetworkLocationSelection(
            countryCode = prefs[KEY_SELECTED_COUNTRY_CODE]?.takeIf { it.isNotBlank() },
            region = prefs[KEY_SELECTED_REGION]?.takeIf { it.isNotBlank() },
            city = prefs[KEY_SELECTED_CITY]?.takeIf { it.isNotBlank() },
        ),
    )

    private fun writeConfig(prefs: MutablePreferences, cfg: UrnetworkConfig) {
        prefs.writeOrRemove(KEY_WALLET_OVERRIDE, cfg.walletOverride)
        prefs.writeOrRemove(KEY_BY_JWT, cfg.byJwt)
        prefs.writeOrRemove(KEY_BY_CLIENT_JWT, cfg.byClientJwt)
        prefs[KEY_WINDOW_TYPE] = cfg.windowType.rawValue
        prefs[KEY_FIXED_IP_SIZE] = cfg.fixedIpSize
        prefs[KEY_ALLOW_DIRECT] = cfg.allowDirect
        prefs[KEY_PROVIDE_ENABLED] = cfg.provideEnabled
        prefs[KEY_PROVIDE_CONTROL_MODE] = cfg.provideControlMode.rawValue
        prefs[KEY_PROVIDE_NETWORK_MODE] = cfg.provideNetworkMode.rawValue
        prefs.writeOrRemove(KEY_SELECTED_COUNTRY_CODE, cfg.selectedLocation.countryCode)
        prefs.writeOrRemove(KEY_SELECTED_REGION, cfg.selectedLocation.region)
        prefs.writeOrRemove(KEY_SELECTED_CITY, cfg.selectedLocation.city)
    }

    private companion object {
        val KEY_WALLET_OVERRIDE = stringPreferencesKey("urnetwork_wallet_override")
        val KEY_BY_JWT = stringPreferencesKey("urnetwork_by_jwt")
        val KEY_BY_CLIENT_JWT = stringPreferencesKey("urnetwork_by_client_jwt")
        val KEY_WINDOW_TYPE = stringPreferencesKey("urnetwork_window_type")
        val KEY_FIXED_IP_SIZE = booleanPreferencesKey("urnetwork_fixed_ip_size")
        val KEY_ALLOW_DIRECT = booleanPreferencesKey("urnetwork_allow_direct")
        val KEY_PROVIDE_ENABLED = booleanPreferencesKey("urnetwork_provide_enabled")
        val KEY_PROVIDE_CONTROL_MODE = stringPreferencesKey("urnetwork_provide_control_mode")
        val KEY_PROVIDE_NETWORK_MODE = stringPreferencesKey("urnetwork_provide_network_mode")
        val KEY_SELECTED_COUNTRY_CODE = stringPreferencesKey("urnetwork_selected_country_code")
        val KEY_SELECTED_REGION = stringPreferencesKey("urnetwork_selected_region")
        val KEY_SELECTED_CITY = stringPreferencesKey("urnetwork_selected_city")
    }
}

private fun MutablePreferences.writeOrRemove(key: Preferences.Key<String>, value: String?) {
    value?.takeIf { it.isNotBlank() }?.let { this[key] = it } ?: remove(key)
}
