package ru.ozero.enginetelegram

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreTelegramConfigStore(
    private val dataStore: DataStore<Preferences>,
) : TelegramConfigStore {

    override fun config(): Flow<TelegramProxyConfig> = dataStore.data.map { prefs ->
        TelegramProxyConfig(
            enabled = prefs[KEY_ENABLED] ?: false,
            port = prefs[KEY_PORT] ?: TelegramProxyConfig.DEFAULT_PORT,
            domain = prefs[KEY_DOMAIN]?.takeIf { it.isNotBlank() } ?: TelegramProxyConfig.DEFAULT_DOMAIN,
            secret = prefs[KEY_SECRET] ?: "",
        )
    }

    override suspend fun setEnabled(value: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = value }
    }

    override suspend fun setPort(value: Int) {
        dataStore.edit { it[KEY_PORT] = value }
    }

    override suspend fun setDomain(value: String) {
        dataStore.edit { it[KEY_DOMAIN] = value }
    }

    override suspend fun setSecret(value: String) {
        dataStore.edit { it[KEY_SECRET] = value }
    }

    companion object {
        val KEY_ENABLED = booleanPreferencesKey("telegram_proxy_enabled")
        val KEY_PORT = intPreferencesKey("telegram_proxy_port")
        val KEY_DOMAIN = stringPreferencesKey("telegram_proxy_domain")
        val KEY_SECRET = stringPreferencesKey("telegram_proxy_secret")
    }
}

interface TelegramConfigStore {
    fun config(): Flow<TelegramProxyConfig>
    suspend fun setEnabled(value: Boolean)
    suspend fun setPort(value: Int)
    suspend fun setDomain(value: String)
    suspend fun setSecret(value: String)
}
