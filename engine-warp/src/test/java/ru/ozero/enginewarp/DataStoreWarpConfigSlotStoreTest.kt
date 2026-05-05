package ru.ozero.enginewarp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStoreWarpConfigSlotStoreTest {

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

    private fun newStore(
        legacyConfig: WarpConfig? = null,
    ): DataStoreWarpConfigSlotStore {
        val slotDs = FakePreferencesDataStore()
        val legacyDs = buildLegacyDataStore(legacyConfig)
        val legacy = DataStoreWarpConfigStore(legacyDs)
        return DataStoreWarpConfigSlotStore(slotDs, legacy)
    }

    private fun buildLegacyDataStore(config: WarpConfig?): FakePreferencesDataStore {
        val ds = FakePreferencesDataStore()
        if (config == null) return ds
        kotlinx.coroutines.runBlocking {
            ds.updateData { prefs ->
                val m = prefs.toMutablePreferences()
                m[stringPreferencesKey("warp_priv")] = config.privateKey
                m[stringPreferencesKey("warp_pub")] = config.publicKey
                m[stringPreferencesKey("warp_peer_pub")] = config.peerPublicKey
                m[stringPreferencesKey("warp_peer_endpoint")] = config.peerEndpoint
                m[stringPreferencesKey("warp_iface_v4")] = config.interfaceAddressV4
                m[stringPreferencesKey("warp_iface_v6")] = config.interfaceAddressV6
                m
            }
        }
        return ds
    }

    @Test
    fun `slots пустой на старте`() = runTest {
        val store = newStore()
        assertTrue(store.slots().first().isEmpty())
    }

    @Test
    fun `activeConfig null на старте`() = runTest {
        val store = newStore()
        assertNull(store.activeConfig().first())
    }

    @Test
    fun `addSlot первый становится активным`() = runTest {
        val store = newStore()
        val id = store.addSlot("Test", sample)
        val slots = store.slots().first()
        assertEquals(1, slots.size)
        assertEquals("Test", slots[0].name)
        assertEquals(id, slots[0].id)
        assertTrue(slots[0].isActive)
    }

    @Test
    fun `addSlot второй не активный — первый остаётся активным`() = runTest {
        val store = newStore()
        store.addSlot("First", sample)
        store.addSlot("Second", sample.copy(privateKey = "p2"))
        val slots = store.slots().first()
        assertEquals(2, slots.size)
        assertTrue(slots[0].isActive)
        assertFalse(slots[1].isActive)
    }

    @Test
    fun `activeConfig возвращает активный config`() = runTest {
        val store = newStore()
        store.addSlot("A", sample)
        assertEquals(sample, store.activeConfig().first())
    }

    @Test
    fun `setActive переключает активный слот`() = runTest {
        val store = newStore()
        store.addSlot("First", sample)
        val id2 = store.addSlot("Second", sample.copy(privateKey = "p2"))
        store.setActive(id2)
        val slots = store.slots().first()
        assertFalse(slots[0].isActive)
        assertTrue(slots[1].isActive)
        assertEquals("p2", store.activeConfig().first()?.privateKey)
    }

    @Test
    fun `rename обновляет имя`() = runTest {
        val store = newStore()
        val id = store.addSlot("Old", sample)
        store.rename(id, "New")
        val slots = store.slots().first()
        assertEquals("New", slots[0].name)
    }

    @Test
    fun `rename несуществующего id — ничего не меняет`() = runTest {
        val store = newStore()
        store.addSlot("A", sample)
        store.rename("nonexistent", "X")
        assertEquals("A", store.slots().first()[0].name)
    }

    @Test
    fun `delete удаляет слот`() = runTest {
        val store = newStore()
        val id = store.addSlot("A", sample)
        store.delete(id)
        assertTrue(store.slots().first().isEmpty())
    }

    @Test
    fun `delete активного — первый оставшийся становится активным`() = runTest {
        val store = newStore()
        val id1 = store.addSlot("First", sample)
        store.addSlot("Second", sample.copy(privateKey = "p2"))
        store.delete(id1)
        val slots = store.slots().first()
        assertEquals(1, slots.size)
        assertTrue(slots[0].isActive)
    }

    @Test
    fun `delete неактивного — активный слот не меняется`() = runTest {
        val store = newStore()
        store.addSlot("First", sample)
        val id2 = store.addSlot("Second", sample.copy(privateKey = "p2"))
        store.delete(id2)
        val slots = store.slots().first()
        assertEquals(1, slots.size)
        assertTrue(slots[0].isActive)
    }

    @Test
    fun `clear удаляет все слоты`() = runTest {
        val store = newStore()
        store.addSlot("A", sample)
        store.addSlot("B", sample.copy(privateKey = "p2"))
        store.clear()
        assertTrue(store.slots().first().isEmpty())
        assertNull(store.activeConfig().first())
    }

    @Test
    fun `JSON roundtrip сохраняет все поля WarpConfig`() = runTest {
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
        store.addSlot("Full", config)
        val read = assertNotNull(store.activeConfig().first())
        assertEquals(config.privateKey, read.privateKey)
        assertEquals(config.publicKey, read.publicKey)
        assertEquals(config.peerPublicKey, read.peerPublicKey)
        assertEquals(config.peerEndpoint, read.peerEndpoint)
        assertEquals(config.interfaceAddressV4, read.interfaceAddressV4)
        assertEquals(config.interfaceAddressV6, read.interfaceAddressV6)
        assertEquals(config.accountLicense, read.accountLicense)
        assertEquals(config.mtu, read.mtu)
        assertEquals(config.dnsServers, read.dnsServers)
        assertEquals(config.keepaliveSeconds, read.keepaliveSeconds)
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
    fun `migrateIfNeeded — нет legacy config — слоты пустые`() = runTest {
        val store = newStore(legacyConfig = null)
        store.migrateIfNeeded()
        assertTrue(store.slots().first().isEmpty())
    }

    @Test
    fun `migrateIfNeeded — есть legacy config — создаёт слот Migrated`() = runTest {
        val legacyConfig = sample.copy(accountLicense = "", awgParams = AwgParams())
        val store = newStore(legacyConfig = legacyConfig)
        store.migrateIfNeeded()
        val slots = store.slots().first()
        assertEquals(1, slots.size)
        assertEquals("Migrated", slots[0].name)
        assertEquals(legacyConfig.privateKey, slots[0].config.privateKey)
        assertEquals(legacyConfig.peerEndpoint, slots[0].config.peerEndpoint)
        assertTrue(slots[0].isActive)
    }

    @Test
    fun `migrateIfNeeded — повторный вызов не дублирует слот`() = runTest {
        val legacyConfig = sample.copy(accountLicense = "", awgParams = AwgParams())
        val store = newStore(legacyConfig = legacyConfig)
        store.migrateIfNeeded()
        store.migrateIfNeeded()
        assertEquals(1, store.slots().first().size)
    }

    @Test
    fun `migrateIfNeeded — существующие слоты не перезаписываются`() = runTest {
        val legacyConfig = sample.copy(accountLicense = "", awgParams = AwgParams())
        val store = newStore(legacyConfig = legacyConfig)
        store.addSlot("Existing", sample.copy(privateKey = "existing"))
        store.migrateIfNeeded()
        assertEquals(1, store.slots().first().size)
        assertEquals("Existing", store.slots().first()[0].name)
    }

    @Test
    fun `corrupted JSON в DataStore — slots возвращает пустой список без краша`() = runTest {
        val ds = FakePreferencesDataStore()
        ds.updateData { prefs ->
            val mutable = prefs.toMutablePreferences()
            mutable[stringPreferencesKey("warp_slots_json")] =
                "not-valid-json{"
            mutable
        }
        val store = DataStoreWarpConfigSlotStore(ds, DataStoreWarpConfigStore(FakePreferencesDataStore()))
        assertTrue(store.slots().first().isEmpty())
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
