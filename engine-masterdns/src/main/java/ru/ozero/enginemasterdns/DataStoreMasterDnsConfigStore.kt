package ru.ozero.enginemasterdns

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreMasterDnsConfigStore(
    private val dataStore: DataStore<Preferences>,
) : MasterDnsConfigStore {

    override fun config(): Flow<MasterDnsPersistedConfig> = dataStore.data.map { prefs ->
        MasterDnsPersistedConfig(
            enabled = prefs[KEY_ENABLED] ?: false,
            configToml = prefs[KEY_TOML].orEmpty(),
            resolvers = prefs[KEY_RESOLVERS].orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            serverIp = prefs[KEY_SERVER_IP].orEmpty(),
            serverPort = prefs[KEY_SERVER_PORT] ?: 22,
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    override suspend fun setConfigToml(toml: String) {
        dataStore.edit { it[KEY_TOML] = toml }
    }

    override suspend fun setResolvers(resolvers: List<String>) {
        dataStore.edit { it[KEY_RESOLVERS] = resolvers.joinToString("\n") }
    }

    override suspend fun setServerIp(ip: String) {
        dataStore.edit { it[KEY_SERVER_IP] = ip }
    }

    override suspend fun setServerPort(port: Int) {
        dataStore.edit { it[KEY_SERVER_PORT] = port }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("masterdns.enabled")
        val KEY_TOML = stringPreferencesKey("masterdns.toml")
        val KEY_RESOLVERS = stringPreferencesKey("masterdns.resolvers")
        val KEY_SERVER_IP = stringPreferencesKey("masterdns.server_ip")
        val KEY_SERVER_PORT = intPreferencesKey("masterdns.server_port")
    }
}
