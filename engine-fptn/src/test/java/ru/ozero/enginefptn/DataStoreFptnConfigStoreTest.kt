package ru.ozero.enginefptn

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import ru.ozero.enginescore.EngineConfig
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreFptnConfigStoreTest {

    @TempDir
    lateinit var tmp: File

    @Test
    fun `defaults are returned when preferences are absent`() = runTest {
        val store = newStore(this)

        val config = store.config().first()

        assertEquals(FptnConfig(), config)
        assertEquals(config, store.currentConfig())
    }

    @Test
    fun `currentConfig reads persisted preferences before flow collection`() = runTest {
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tmp, "fptn.preferences_pb") },
        )
        try {
            dataStore.edit { prefs ->
                val bypass = FptnBypassMethod.SNI_REALITY_FIREFOX_149.strategyName
                prefs[stringPreferencesKey("fptn_token")] = "persisted-token"
                prefs[stringPreferencesKey("fptn_selected_server")] = "Server 2"
                prefs[stringPreferencesKey("fptn_bypass_method")] = bypass
                prefs[stringPreferencesKey("fptn_sni_domain")] = "front.example.com"
                prefs[booleanPreferencesKey("fptn_auto_select")] = false
                prefs[booleanPreferencesKey("fptn_reconnect_network")] = false
                prefs[booleanPreferencesKey("fptn_reconnect_ip")] = true
                prefs[intPreferencesKey("fptn_max_attempts")] = 8
                prefs[intPreferencesKey("fptn_pause_seconds")] = 6
                prefs[booleanPreferencesKey("fptn_reset_server")] = false
            }
            val store = DataStoreFptnConfigStore(dataStore)

            val config = store.currentConfig()

            assertEquals("persisted-token", config.token)
            assertEquals("Server 2", config.selectedServerName)
            assertEquals(FptnBypassMethod.SNI_REALITY_FIREFOX_149.strategyName, config.bypassMethod)
            assertEquals("front.example.com", config.sniDomain)
            assertEquals(false, config.autoSelect)
            assertEquals(false, config.reconnectOnNetworkChange)
            assertEquals(true, config.reconnectOnIpChange)
            assertEquals(8, config.maxReconnectAttempts)
            assertEquals(6, config.reconnectPauseSeconds)
            assertEquals(false, config.resetServerOnDisconnect)
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun `FptnEngine manual config sees persisted preferences before flow collection`() = runTest {
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tmp, "fptn-manual.preferences_pb") },
        )
        try {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("fptn_token")] = "fptn:persisted"
                prefs[stringPreferencesKey("fptn_selected_server")] = "Stored server"
                prefs[stringPreferencesKey("fptn_bypass_method")] = FptnBypassMethod.SNI.strategyName
                prefs[stringPreferencesKey("fptn_sni_domain")] = "stored.example.com"
                prefs[booleanPreferencesKey("fptn_auto_select")] = false
            }
            val engine = FptnEngine(DataStoreFptnConfigStore(dataStore))

            val config = assertIs<EngineConfig.Fptn>(engine.buildManualConfig(null))

            assertEquals("fptn:persisted", config.token)
            assertEquals("Stored server", config.selectedServerName)
            assertEquals(false, config.autoSelect)
            assertEquals("stored.example.com", config.sniDomain)
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun `update persists every config field`() = runTest {
        val store = newStore(this)

        store.update {
            FptnConfig(
                token = "fptn:token",
                selectedServerName = "Server 1",
                bypassMethod = FptnBypassMethod.SNI_REALITY_FIREFOX_149.strategyName,
                sniDomain = "front.example.com",
                autoSelect = false,
                reconnectOnNetworkChange = false,
                reconnectOnIpChange = true,
                maxReconnectAttempts = 9,
                reconnectPauseSeconds = 7,
                resetServerOnDisconnect = false,
            )
        }

        val config = store.config().first()
        assertEquals("fptn:token", config.token)
        assertEquals("Server 1", config.selectedServerName)
        assertEquals(FptnBypassMethod.SNI_REALITY_FIREFOX_149.strategyName, config.bypassMethod)
        assertEquals("front.example.com", config.sniDomain)
        assertEquals(false, config.autoSelect)
        assertEquals(false, config.reconnectOnNetworkChange)
        assertEquals(true, config.reconnectOnIpChange)
        assertEquals(9, config.maxReconnectAttempts)
        assertEquals(7, config.reconnectPauseSeconds)
        assertEquals(false, config.resetServerOnDisconnect)
    }

    @Test
    fun `currentConfig reflects update immediately without collecting config flow`() = runTest {
        val store = newStore(this)

        store.update {
            it.copy(
                token = "fptn:updated",
                selectedServerName = "Updated server",
                reconnectOnIpChange = true,
            )
        }

        val current = store.currentConfig()
        assertEquals("fptn:updated", current.token)
        assertEquals("Updated server", current.selectedServerName)
        assertEquals(true, current.reconnectOnIpChange)
    }

    @Test
    fun `config flow refreshes currentConfig cache after external datastore edit`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp, "fptn-external.preferences_pb") },
        )
        val store = DataStoreFptnConfigStore(dataStore)

        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("fptn_token")] = "fptn:external"
            prefs[stringPreferencesKey("fptn_selected_server")] = "External server"
        }
        val emitted = store.config().first()

        assertEquals("fptn:external", emitted.token)
        assertEquals("External server", store.currentConfig().selectedServerName)
    }

    @Test
    fun `absent reconnect numeric values use stable defaults`() = runTest {
        val store = newStore(this)

        val config = store.config().first()

        assertEquals(5, config.maxReconnectAttempts)
        assertEquals(2, config.reconnectPauseSeconds)
        assertEquals(true, config.autoSelect)
        assertEquals(true, config.reconnectOnNetworkChange)
        assertEquals(false, config.reconnectOnIpChange)
        assertEquals(true, config.resetServerOnDisconnect)
    }

    @Test
    fun `blank optional strings fall back to defaults on reread`() = runTest {
        val store = newStore(this)

        store.update {
            it.copy(
                selectedServerName = "   ",
                bypassMethod = "   ",
                sniDomain = "   ",
            )
        }

        val reloaded = store.config().first()
        assertNull(reloaded.selectedServerName)
        assertEquals(FptnBypassMethod.DEFAULT.strategyName, reloaded.bypassMethod)
        assertEquals(FptnConfig.DEFAULT_SNI_DOMAIN, reloaded.sniDomain)
    }

    @Test
    fun `null selected server removes persisted value`() = runTest {
        val store = newStore(this)

        store.update { it.copy(selectedServerName = "Server 1") }
        assertEquals("Server 1", store.config().first().selectedServerName)
        store.update { it.copy(selectedServerName = null) }

        assertNull(store.config().first().selectedServerName)
    }

    @Test
    fun `boolean defaults distinguish false and absent values`() = runTest {
        val store = newStore(this)

        store.update {
            it.copy(
                autoSelect = false,
                reconnectOnNetworkChange = false,
                reconnectOnIpChange = false,
                resetServerOnDisconnect = false,
            )
        }

        val config = store.config().first()
        assertEquals(false, config.autoSelect)
        assertEquals(false, config.reconnectOnNetworkChange)
        assertEquals(false, config.reconnectOnIpChange)
        assertEquals(false, config.resetServerOnDisconnect)
    }

    private fun newStore(scope: TestScope): DataStoreFptnConfigStore {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { File(tmp, "fptn.preferences_pb") },
        )
        return DataStoreFptnConfigStore(dataStore)
    }
}
