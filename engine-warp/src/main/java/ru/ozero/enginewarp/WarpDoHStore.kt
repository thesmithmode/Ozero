package ru.ozero.enginewarp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WarpDoHStore(private val dataStore: DataStore<Preferences>) {

    val provider: Flow<DoHProvider> = dataStore.data.map { prefs ->
        val name = prefs[KEY_DOH_PROVIDER] ?: DoHProvider.SYSTEM.name
        runCatching { DoHProvider.valueOf(name) }.getOrDefault(DoHProvider.SYSTEM)
    }

    suspend fun setProvider(provider: DoHProvider) {
        dataStore.edit { it[KEY_DOH_PROVIDER] = provider.name }
    }

    private companion object {
        val KEY_DOH_PROVIDER = stringPreferencesKey("warp_doh_provider")
    }
}
