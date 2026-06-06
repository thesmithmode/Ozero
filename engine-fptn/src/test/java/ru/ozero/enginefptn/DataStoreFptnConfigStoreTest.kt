package ru.ozero.enginefptn

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
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
        return DataStoreFptnConfigStore(dataStore, scope.backgroundScope)
    }
}
