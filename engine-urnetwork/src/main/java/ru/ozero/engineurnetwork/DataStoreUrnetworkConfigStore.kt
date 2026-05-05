package ru.ozero.engineurnetwork

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

    private companion object {
        val KEY_WALLET_OVERRIDE = stringPreferencesKey("urnetwork_wallet_override")
        val KEY_BY_JWT = stringPreferencesKey("urnetwork_by_jwt")
        val KEY_BY_CLIENT_JWT = stringPreferencesKey("urnetwork_by_client_jwt")
    }
}
