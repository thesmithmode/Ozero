package ru.ozero.enginewarp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
        dnsServers = listOf("1.1.1.1", "2606:4700:4700::1111"),
        keepaliveSeconds = 25,
        awgParams = AwgParams(),
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
        assertEquals(sample.dnsServers, read.dnsServers)
        assertEquals(sample.keepaliveSeconds, read.keepaliveSeconds)
        assertEquals(sample.awgParams, read.awgParams)
    }

    @Test
    fun `save и load roundtrip сохраняет non-default AwgParams — все 9 полей`() = runTest {
        val customAwg = AwgParams(
            junkPacketCount = 10,
            junkPacketMinSize = 77,
            junkPacketMaxSize = 888,
            initPacketJunkSize = 1,
            responsePacketJunkSize = 2,
            initPacketMagicHeader = 999L,
            responsePacketMagicHeader = 1000L,
            cookieReplyMagicHeader = 1001L,
            transportMagicHeader = 1002L,
        )
        val config = sample.copy(awgParams = customAwg)
        val store = newStore()
        store.save(config)
        val read = assertNotNull(store.current().first())
        val p = read.awgParams
        assertEquals(10, p.junkPacketCount)
        assertEquals(77, p.junkPacketMinSize)
        assertEquals(888, p.junkPacketMaxSize)
        assertEquals(1, p.initPacketJunkSize)
        assertEquals(2, p.responsePacketJunkSize)
        assertEquals(999L, p.initPacketMagicHeader)
        assertEquals(1000L, p.responsePacketMagicHeader)
        assertEquals(1001L, p.cookieReplyMagicHeader)
        assertEquals(1002L, p.transportMagicHeader)
    }

    @Test
    fun `загрузка без AWG ключей возвращает default AwgParams`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.updateData {
            mutablePreferencesOf(
                stringPreferencesKey("warp_priv") to "p",
                stringPreferencesKey("warp_peer_pub") to "pp",
                stringPreferencesKey("warp_peer_endpoint") to "h:1",
                stringPreferencesKey("warp_iface_v4") to "1.2.3.4/32",
                stringPreferencesKey("warp_iface_v6") to "::1/128",
            )
        }
        val store = DataStoreWarpConfigStore(ds)
        val read = assertNotNull(store.current().first())
        assertEquals(AwgParams(), read.awgParams)
    }

    @Test
    fun `legacy invalid AWG Jmin Jmax falls back to defaults`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.updateData {
            mutablePreferencesOf(
                stringPreferencesKey("warp_priv") to "p",
                stringPreferencesKey("warp_peer_pub") to "pp",
                stringPreferencesKey("warp_peer_endpoint") to "h:1",
                stringPreferencesKey("warp_iface_v4") to "1.2.3.4/32",
                stringPreferencesKey("warp_iface_v6") to "::1/128",
                intPreferencesKey("awg_jmin") to 200,
                intPreferencesKey("awg_jmax") to 100,
            )
        }
        val store = DataStoreWarpConfigStore(ds)
        val read = assertNotNull(store.current().first())
        assertEquals(AwgParams.DEFAULT_JMIN, read.awgParams.junkPacketMinSize)
        assertEquals(AwgParams.DEFAULT_JMAX, read.awgParams.junkPacketMaxSize)
    }

    @Test
    fun `clear удаляет AWG ключи — нет стейла после re-save с defaults`() = runTest {
        val customAwg = AwgParams(junkPacketCount = 99)
        val store = newStore()
        store.save(sample.copy(awgParams = customAwg))
        store.clear()
        store.save(sample.copy(awgParams = AwgParams()))
        val read = assertNotNull(store.current().first())
        assertEquals(AwgParams.DEFAULT_JC, read.awgParams.junkPacketCount)
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
    fun `H-значения corrupted в DataStore — fallback к defaults без краша`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.updateData {
            mutablePreferencesOf(
                stringPreferencesKey("warp_priv") to "p",
                stringPreferencesKey("warp_peer_pub") to "pp",
                stringPreferencesKey("warp_peer_endpoint") to "h:1",
                stringPreferencesKey("warp_iface_v4") to "1.2.3.4/32",
                stringPreferencesKey("warp_iface_v6") to "::1/128",
                stringPreferencesKey("awg_h1") to "not-a-long",
                stringPreferencesKey("awg_h2") to "",
                stringPreferencesKey("awg_h3") to "9999999999999999999999",
            )
        }
        val store = DataStoreWarpConfigStore(ds)
        val read = assertNotNull(store.current().first())
        assertEquals(AwgParams.DEFAULT_H1, read.awgParams.initPacketMagicHeader)
        assertEquals(AwgParams.DEFAULT_H2, read.awgParams.responsePacketMagicHeader)
        assertEquals(AwgParams.DEFAULT_H3, read.awgParams.cookieReplyMagicHeader)
        assertEquals(AwgParams.DEFAULT_H4, read.awgParams.transportMagicHeader)
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
