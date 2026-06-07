package ru.ozero.enginefptn

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreFptnConfigStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : FptnConfigStore {

    @Volatile
    private var latest = FptnConfig()

    override fun config(): Flow<FptnConfig> = dataStore.data.map { prefs ->
        readConfig(prefs).also { latest = it }
    }

    override fun currentConfig(): FptnConfig = latest

    override suspend fun update(transform: (FptnConfig) -> FptnConfig) {
        dataStore.edit { prefs ->
            val next = transform(readConfig(prefs))
            writeConfig(prefs, next)
            latest = next
        }
    }

    private fun readConfig(prefs: Preferences) = FptnConfig(
        token = prefs[KEY_TOKEN].orEmpty(),
        selectedServerName = prefs[KEY_SELECTED_SERVER]?.takeIf { it.isNotBlank() },
        bypassMethod = prefs[KEY_BYPASS_METHOD]?.takeIf { it.isNotBlank() }
            ?: FptnBypassMethod.DEFAULT.strategyName,
        sniDomain = prefs[KEY_SNI_DOMAIN]?.takeIf { it.isNotBlank() }
            ?: FptnConfig.DEFAULT_SNI_DOMAIN,
        autoSelect = prefs[KEY_AUTO_SELECT] != false,
        reconnectOnNetworkChange = prefs[KEY_RECONNECT_NETWORK] != false,
        reconnectOnIpChange = prefs[KEY_RECONNECT_IP] == true,
        maxReconnectAttempts = prefs[KEY_MAX_ATTEMPTS] ?: 5,
        reconnectPauseSeconds = prefs[KEY_PAUSE_SECONDS] ?: 2,
        resetServerOnDisconnect = prefs[KEY_RESET_SERVER] != false,
    )

    private fun writeConfig(prefs: MutablePreferences, cfg: FptnConfig) {
        prefs[KEY_TOKEN] = cfg.token
        if (cfg.selectedServerName != null) {
            prefs[KEY_SELECTED_SERVER] = cfg.selectedServerName
        } else {
            prefs.remove(KEY_SELECTED_SERVER)
        }
        prefs[KEY_BYPASS_METHOD] = cfg.bypassMethod
        prefs[KEY_SNI_DOMAIN] = cfg.sniDomain
        prefs[KEY_AUTO_SELECT] = cfg.autoSelect
        prefs[KEY_RECONNECT_NETWORK] = cfg.reconnectOnNetworkChange
        prefs[KEY_RECONNECT_IP] = cfg.reconnectOnIpChange
        prefs[KEY_MAX_ATTEMPTS] = cfg.maxReconnectAttempts
        prefs[KEY_PAUSE_SECONDS] = cfg.reconnectPauseSeconds
        prefs[KEY_RESET_SERVER] = cfg.resetServerOnDisconnect
    }

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("fptn_token")
        private val KEY_SELECTED_SERVER = stringPreferencesKey("fptn_selected_server")
        private val KEY_BYPASS_METHOD = stringPreferencesKey("fptn_bypass_method")
        private val KEY_SNI_DOMAIN = stringPreferencesKey("fptn_sni_domain")
        private val KEY_AUTO_SELECT = booleanPreferencesKey("fptn_auto_select")
        private val KEY_RECONNECT_NETWORK = booleanPreferencesKey("fptn_reconnect_network")
        private val KEY_RECONNECT_IP = booleanPreferencesKey("fptn_reconnect_ip")
        private val KEY_MAX_ATTEMPTS = intPreferencesKey("fptn_max_attempts")
        private val KEY_PAUSE_SECONDS = intPreferencesKey("fptn_pause_seconds")
        private val KEY_RESET_SERVER = booleanPreferencesKey("fptn_reset_server")
    }
}
