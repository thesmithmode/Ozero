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

    override fun consentGranted(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CONSENT] == true
    }

    override suspend fun markConsentGranted() {
        dataStore.edit { prefs -> prefs[KEY_CONSENT] = true }
    }

    override suspend fun revokeConsent() {
        dataStore.edit { prefs -> prefs.remove(KEY_CONSENT) }
    }

    private companion object {
        val KEY_WALLET_OVERRIDE = stringPreferencesKey("urnetwork_wallet_override")
        val KEY_CONSENT = booleanPreferencesKey("urnetwork_consent")
    }
}
