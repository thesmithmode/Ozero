package ru.ozero.enginetelegram

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreTelegramConfigStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private lateinit var datastoreScope: CoroutineScope
    private lateinit var store: DataStoreTelegramConfigStore

    @BeforeEach
    fun setUp() {
        datastoreScope = CoroutineScope(dispatcher + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = datastoreScope,
            produceFile = { tempDir.resolve("test_prefs.preferences_pb").toFile() },
        )
        store = DataStoreTelegramConfigStore(dataStore)
    }

    @AfterEach
    fun tearDown() {
        datastoreScope.cancel()
    }

    @Test
    fun `default config has correct values`() = testScope.runTest {
        val config = store.config().first()
        assertFalse(config.enabled)
        assertEquals(TelegramProxyConfig.DEFAULT_PORT, config.port)
        assertEquals(TelegramProxyConfig.DEFAULT_DOMAIN, config.domain)
        assertEquals("", config.secret)
    }

    @Test
    fun `setEnabled persists true`() = testScope.runTest {
        store.setEnabled(true)
        assertTrue(store.config().first().enabled)
    }

    @Test
    fun `setEnabled persists false`() = testScope.runTest {
        store.setEnabled(true)
        store.setEnabled(false)
        assertFalse(store.config().first().enabled)
    }

    @Test
    fun `setPort persists value`() = testScope.runTest {
        store.setPort(9090)
        assertEquals(9090, store.config().first().port)
    }

    @Test
    fun `setDomain persists value`() = testScope.runTest {
        store.setDomain("example.com")
        assertEquals("example.com", store.config().first().domain)
    }

    @Test
    fun `setDomain blank falls back to default`() = testScope.runTest {
        store.setDomain("  ")
        assertEquals(TelegramProxyConfig.DEFAULT_DOMAIN, store.config().first().domain)
    }

    @Test
    fun `setSecret persists value`() = testScope.runTest {
        store.setSecret("deadbeef")
        assertEquals("deadbeef", store.config().first().secret)
    }

    @Test
    fun `multiple fields persist independently`() = testScope.runTest {
        store.setEnabled(true)
        store.setPort(5555)
        store.setSecret("mytoken")
        store.setDomain("telegram.org")
        val config = store.config().first()
        assertTrue(config.enabled)
        assertEquals(5555, config.port)
        assertEquals("mytoken", config.secret)
        assertEquals("telegram.org", config.domain)
    }
}
