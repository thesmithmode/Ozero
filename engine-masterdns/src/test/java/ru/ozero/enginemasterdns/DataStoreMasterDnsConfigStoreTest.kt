package ru.ozero.enginemasterdns

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DataStoreMasterDnsConfigStoreTest {

    @Test
    fun `default config is empty and disabled`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        val cfg = store.config().first()
        assertFalse(cfg.enabled)
        assertEquals("", cfg.configToml)
        assertTrue(cfg.resolvers.isEmpty())
    }

    @Test
    fun `setEnabled persists true`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        store.setEnabled(true)
        assertTrue(store.config().first().enabled)
    }

    @Test
    fun `setEnabled persists false after true`(@TempDir tmp: Path) = runTest {
        val store = makeStore(tmp)
        store.setEnabled(true)
        store.setEnabled(false)
        assertFalse(store.config().first().enabled)
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

    private fun makeStore(tmp: Path): DataStoreMasterDnsConfigStore {
        val file = File(tmp.toFile(), "masterdns.preferences_pb")
        val ds = PreferenceDataStoreFactory.create(produceFile = { file })
        return DataStoreMasterDnsConfigStore(ds)
    }
}
