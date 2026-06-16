package ru.ozero.enginemasterdns

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DataStoreMasterDnsConfigStoreTest {

    @Test
    fun `default config is empty`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        val cfg = store.config().first()
        assertEquals("", cfg.configToml)
        assertTrue(cfg.resolvers.isEmpty())
    }

    @Test
    fun `setConfigToml persists multiline`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        val text = "DOMAINS = [\"v.x\"]\nENCRYPTION_KEY = \"k\"\n"
        store.setConfigToml(text)
        assertEquals(text, store.config().first().configToml)
    }

    @Test
    fun `setResolvers persists list`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        store.setResolvers(listOf("8.8.8.8", "1.1.1.1"))
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), store.config().first().resolvers)
    }

    @Test
    fun `setResolvers preserves order while dropping blank and whitespace only entries`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)

        store.setResolvers(listOf("", "  ", "9.9.9.9", "\t", "1.0.0.1", " 8.8.4.4 "))

        assertEquals(listOf("9.9.9.9", "1.0.0.1", "8.8.4.4"), store.config().first().resolvers)
    }

    @Test
    fun `setResolvers with empty list clears`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        store.setResolvers(listOf("8.8.8.8"))
        store.setResolvers(emptyList())
        assertTrue(store.config().first().resolvers.isEmpty())
    }

    @Test
    fun `resolver lines with whitespace trimmed and blanks dropped`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        store.setResolvers(listOf(" 8.8.8.8 ", "", "1.1.1.1\t"))
        val resolvers = store.config().first().resolvers
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), resolvers)
    }

    @Test
    fun `default serverIp is empty and serverPort is 22`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        val cfg = store.config().first()
        assertEquals("", cfg.serverIp)
        assertEquals(22, cfg.serverPort)
    }

    @Test
    fun `setServerIp persists`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        store.setServerIp("192.168.1.100")
        assertEquals("192.168.1.100", store.config().first().serverIp)
    }

    @Test
    fun `setServerPort persists`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        store.setServerPort(2222)
        assertEquals(2222, store.config().first().serverPort)
    }

    @Test
    fun `setServerPort persists zero and high custom ports without normalization`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)

        store.setServerPort(0)
        assertEquals(0, store.config().first().serverPort)

        store.setServerPort(65535)
        assertEquals(65535, store.config().first().serverPort)
    }

    @Test
    fun `setServerIp with empty string clears`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        store.setServerIp("10.0.0.1")
        store.setServerIp("")
        assertEquals("", store.config().first().serverIp)
    }

    @Test
    fun `setConfigToml and server settings survive independent writes`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        val toml = "SERVER = \"203.0.113.10\"\nENCRYPTION_KEY = \"k\"\n"

        store.setConfigToml(toml)
        store.setServerIp("203.0.113.10")
        store.setServerPort(2222)
        store.setResolvers(listOf("8.8.8.8"))

        val cfg = store.config().first()
        assertEquals(toml, cfg.configToml)
        assertEquals("203.0.113.10", cfg.serverIp)
        assertEquals(2222, cfg.serverPort)
        assertEquals(listOf("8.8.8.8"), cfg.resolvers)
    }

    private fun makeStore(tmp: Path): DataStoreMasterDnsConfigStore {
        val file = File(tmp.toFile(), "masterdns.preferences_pb")
        val ds = PreferenceDataStoreFactory.create(produceFile = { file })
        return DataStoreMasterDnsConfigStore(ds)
    }
}
