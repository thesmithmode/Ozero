package ru.ozero.enginewarp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WarpConfigStoreTest {

    private val sample = WarpConfig(
        privateKey = "priv",
        publicKey = "pub",
        peerPublicKey = "peer",
        peerEndpoint = "engage.cloudflareclient.com:2408",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700::1/128",
        accountLicense = "lic",
        mtu = 1280,
    )

    private fun newStore(): DataStoreWarpConfigStore =
        DataStoreWarpConfigStore(FakePreferencesDataStore())

    @Test
    fun `current возвращает null до save`() = runTest {
        val store = newStore()
        assertNull(store.current().first())
    }

    @Test
    fun `save затем current возвращает non-null с теми же полями`() = runTest {
        val store = newStore()
        store.save(sample)
        val read = assertNotNull(store.current().first())
        assertEquals(sample.privateKey, read.privateKey)
        assertEquals(sample.publicKey, read.publicKey)
        assertEquals(sample.peerPublicKey, read.peerPublicKey)
        assertEquals(sample.peerEndpoint, read.peerEndpoint)
        assertEquals(sample.interfaceAddressV4, read.interfaceAddressV4)
        assertEquals(sample.interfaceAddressV6, read.interfaceAddressV6)
        assertEquals(sample.accountLicense, read.accountLicense)
        assertEquals(sample.mtu, read.mtu)
    }

    @Test
    fun `clear после save возвращает null`() = runTest {
        val store = newStore()
        store.save(sample)
        store.clear()
        assertNull(store.current().first())
    }

    @Test
    fun `proxy-конфиг без pub и license сохраняется и возвращается с пустыми строками`() = runTest {
        val proxyConfig = sample.copy(publicKey = "", accountLicense = "")
        val store = newStore()
        store.save(proxyConfig)
        val read = assertNotNull(store.current().first())
        assertEquals("", read.publicKey)
        assertEquals("", read.accountLicense)
        assertEquals(proxyConfig.privateKey, read.privateKey)
        assertEquals(proxyConfig.peerEndpoint, read.peerEndpoint)
    }

    @Test
    fun `current возвращает null если записан только priv без остальных ключей`() = runTest {
        val ds = FakePreferencesDataStore()
        val partial = mutablePreferencesOf(stringPreferencesKey("warp_priv") to "priv-only")
        ds.updateData { partial }
        val store = DataStoreWarpConfigStore(ds)
        assertNull(store.current().first())
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> get() = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
