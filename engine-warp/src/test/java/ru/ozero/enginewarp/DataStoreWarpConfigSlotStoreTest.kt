package ru.ozero.enginewarp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
        runBlocking { DataStoreWarpConfigStore(ds).save(config) }
        return ds
    }

    @Test
    fun `slots пустой на старте`() = runTest {
        val store = newStore()
        assertTrue(store.slots().first().isEmpty())
    }

    @Test
    fun `addSlot с rawIni — сохраняется в storage и читается обратно (passthrough roundtrip)`() = runTest {
        val store = newStore()
        val raw = "[Interface]\nPrivateKey = abc\nI1 = <b 0xdeadbeef>\n[Peer]\nPublicKey = def\nEndpoint = h:1\n"
        val id = store.addSlot("Imported", sample, rawIni = raw)
        val slots = store.slots().first()
        val saved = slots.first { it.id == id }
        assertEquals(raw, saved.rawIniOverride, "rawIni roundtrip — full text preserved через JSON storage")
    }

    @Test
    fun `addSlot без rawIni — rawIniOverride остаётся null (auto-config legacy path)`() = runTest {
        val store = newStore()
        val id = store.addSlot("Auto", sample)
        val saved = store.slots().first().first { it.id == id }
        assertNull(saved.rawIniOverride, "default null — backward compat для слотов без raw text")
    }

    @Test
    fun `activeSlot возвращает full WarpConfigSlot — нужен для rawIni доступа в EngineWarp`() = runTest {
        val store = newStore()
        val raw = "[Interface]\nfoo=bar\n[Peer]\n"
        val id = store.addSlot("R", sample, rawIni = raw)
        val active = store.activeSlot().first()
        assertNotNull(active)
        assertEquals(id, active.id)
        assertEquals(raw, active.rawIniOverride)
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
        ds.edit { it[stringPreferencesKey("warp_slots_json")] = "not-valid-json{" }
        val store = DataStoreWarpConfigSlotStore(ds, DataStoreWarpConfigStore(FakePreferencesDataStore()))
        assertTrue(store.slots().first().isEmpty())
    }

    @Test
    fun `один сломанный слот в массиве — валидные слоты не теряются`() = runTest {
        val ds = FakePreferencesDataStore()
        val goodSlot = buildValidSlotJson("id-good", "Good")
        val badSlot = """{"id":"id-bad","name":"Bad"}"""
        ds.edit { it[stringPreferencesKey("warp_slots_json")] = """[$goodSlot,$badSlot]""" }
        val store = DataStoreWarpConfigSlotStore(ds, DataStoreWarpConfigStore(FakePreferencesDataStore()))
        val slots = store.slots().first()
        assertEquals(1, slots.size)
        assertEquals("id-good", slots[0].id)
        assertEquals("Good", slots[0].name)
    }

    @Test
    fun `updateSlot обновляет имя и конфиг слота`() = runTest {
        val store = newStore()
        val id = store.addSlot("OldName", sample)
        val newConfig = sample.copy(peerEndpoint = "new.endpoint:2408", privateKey = "newpriv")
        store.updateSlot(id, "NewName", newConfig)
        val slots = store.slots().first()
        assertEquals(1, slots.size)
        assertEquals("NewName", slots[0].name)
        assertEquals("new.endpoint:2408", slots[0].config.peerEndpoint)
        assertEquals("newpriv", slots[0].config.privateKey)
    }

    @Test
    fun `updateSlot не меняет isActive`() = runTest {
        val store = newStore()
        val id = store.addSlot("S", sample)
        store.updateSlot(id, "S2", sample.copy(peerEndpoint = "x:1"))
        assertTrue(store.slots().first()[0].isActive, "isActive не должен сбрасываться при updateSlot")
    }

    @Test
    fun `updateSlot несуществующего id — ничего не меняет`() = runTest {
        val store = newStore()
        store.addSlot("A", sample)
        store.updateSlot("nonexistent", "X", sample.copy(peerEndpoint = "x:1"))
        val slots = store.slots().first()
        assertEquals("A", slots[0].name)
        assertEquals(sample.peerEndpoint, slots[0].config.peerEndpoint)
    }

    @Test
    fun `setActive несуществующего id — активный слот не меняется`() = runTest {
        val store = newStore()
        val id1 = store.addSlot("First", sample)
        store.addSlot("Second", sample.copy(privateKey = "p2"))
        store.setActive(id1)
        store.setActive("nonexistent-id")
        val slots = store.slots().first()
        assertTrue(slots[0].isActive)
        assertFalse(slots[1].isActive)
    }

    @Test
    fun `addSlot duplicate fingerprint бросает WarpConfigDuplicateException и не добавляет слот`() = runTest {
        val store = newStore()
        val firstId = store.addSlot("First", sample)
        val ex = assertThrows<WarpConfigDuplicateException> {
            store.addSlot("Duplicate", sample)
        }
        assertEquals(firstId, ex.existingSlotId)
        assertEquals("First", ex.existingSlotName)
        val slots = store.slots().first()
        assertEquals(1, slots.size, "duplicate не должен попасть в storage")
        assertEquals("First", slots[0].name)
    }

    @Test
    fun `addSlot duplicate с другим rawIni всё равно отклоняется — fingerprint = privKey+peerPub+endpoint`() = runTest {
        val store = newStore()
        store.addSlot("Imported", sample, rawIni = "[Interface]\nPrivateKey = priv\n[Peer]\n")
        assertThrows<WarpConfigDuplicateException> {
            store.addSlot("Imported2", sample, rawIni = "[Interface]\nPrivateKey = priv\nJc = 4\n[Peer]\n")
        }
        assertEquals(1, store.slots().first().size)
    }

    @Test
    fun `addSlot с другим privateKey — не duplicate, оба слота сохраняются`() = runTest {
        val store = newStore()
        store.addSlot("First", sample)
        val secondId = store.addSlot("Second", sample.copy(privateKey = "different-priv"))
        val slots = store.slots().first()
        assertEquals(2, slots.size)
        assertNotEquals(secondId, slots[0].id)
    }

    @Test
    fun `addSlot с другим peerEndpoint — не duplicate`() = runTest {
        val store = newStore()
        store.addSlot("First", sample)
        store.addSlot("Second", sample.copy(peerEndpoint = "other.endpoint:2408"))
        assertEquals(2, store.slots().first().size)
    }

    @Test
    fun `addSlot с другим peerPublicKey — не duplicate`() = runTest {
        val store = newStore()
        store.addSlot("First", sample)
        store.addSlot("Second", sample.copy(peerPublicKey = "different-peer-pub"))
        assertEquals(2, store.slots().first().size)
    }

    @Test
    fun `WarpConfigDuplicateException содержит existingSlotId и existingSlotName для UI hint`() = runTest {
        val store = newStore()
        val id = store.addSlot("Original Name", sample)
        val ex = assertThrows<WarpConfigDuplicateException> { store.addSlot("Attempt", sample) }
        assertEquals(id, ex.existingSlotId)
        assertEquals("Original Name", ex.existingSlotName)
        assertTrue(ex.message?.contains("Original Name") == true, "message обязан содержать имя оригинала")
    }

    @Test
    fun `migrateAwgParams — старые DEFAULT S3=19 S4=20 I1=28 I2=29 I5=10 сбрасываются в 0 при чтении`() = runTest {
        val store = newStore()
        val oldDefaultAwg = AwgParams(
            underloadPacketJunkSize = 19,
            payloadPacketJunkSize = 20,
            payloadPacketSizeCount1 = 28,
            payloadPacketSizeCount2 = 29,
            payloadPacketSizeCount3 = 10,
        )
        val id = store.addSlot("OldMirror", sample.copy(awgParams = oldDefaultAwg))
        val saved = store.slots().first().first { it.id == id }
        val awg = saved.config.awgParams
        assertEquals(0, awg.underloadPacketJunkSize, "S3=19 (old DEFAULT) обязан мигрировать в 0")
        assertEquals(0, awg.payloadPacketJunkSize, "S4=20 (old DEFAULT) обязан мигрировать в 0")
        assertEquals(0, awg.payloadPacketSizeCount1, "I1=28 (old DEFAULT) обязан мигрировать в 0")
        assertEquals(0, awg.payloadPacketSizeCount2, "I2=29 (old DEFAULT) обязан мигрировать в 0")
        assertEquals(0, awg.payloadPacketSizeCount3, "I5=10 (old DEFAULT) обязан мигрировать в 0")
    }

    @Test
    fun `addSlot с endpointList — список сохраняется и читается обратно`() = runTest {
        val store = newStore()
        val endpoints = listOf("1.1.1.1:2408", "8.8.8.8:2408", "188.114.97.1:500")
        val id = store.addSlot("Ultra", sample, endpointList = endpoints)
        val saved = store.slots().first().first { it.id == id }
        assertEquals(endpoints, saved.endpointList)
    }

    @Test
    fun `addSlot без endpointList — десериализуется как emptyList (backward compat)`() = runTest {
        val ds = FakePreferencesDataStore()
        val goodSlot = buildValidSlotJson("id-no-ep", "NoEndpoints")
        ds.edit { it[stringPreferencesKey("warp_slots_json")] = """[$goodSlot]""" }
        val store = DataStoreWarpConfigSlotStore(ds, DataStoreWarpConfigStore(FakePreferencesDataStore()))
        val slots = store.slots().first()
        assertEquals(1, slots.size)
        assertTrue(slots[0].endpointList.isEmpty(), "слот без endpointList в JSON → emptyList()")
    }

    @Test
    fun `слот без doHProvider — fallback Cloudflare DoH как новый WARP default`() = runTest {
        val ds = FakePreferencesDataStore()
        val goodSlot = buildValidSlotJson("id-no-doh", "NoDoH")
        ds.edit { it[stringPreferencesKey("warp_slots_json")] = """[$goodSlot]""" }
        val store = DataStoreWarpConfigSlotStore(ds, DataStoreWarpConfigStore(FakePreferencesDataStore()))

        val slot = store.slots().first().single()

        assertEquals(WarpConfig.DEFAULT_DOH_PROVIDER, slot.config.doHProvider)
    }

    @Test
    fun `legacy current without doHProvider falls back to Cloudflare DoH`() = runTest {
        val legacy = FakePreferencesDataStore()
        runBlocking { DataStoreWarpConfigStore(legacy).save(sample) }

        val current = DataStoreWarpConfigStore(legacy).current().first()

        assertNotNull(current)
        assertEquals(WarpConfig.DEFAULT_DOH_PROVIDER, current.doHProvider)
    }

    @Test
    fun `legacy slot with explicit SYSTEM doHProvider preserves SYSTEM`() = runTest {
        val ds = FakePreferencesDataStore()
        val slot = buildValidSlotJson("id-system", "SystemDoH", doHProvider = DoHProvider.SYSTEM.name)
        ds.edit { it[stringPreferencesKey("warp_slots_json")] = """[$slot]""" }
        val store = DataStoreWarpConfigSlotStore(ds, DataStoreWarpConfigStore(FakePreferencesDataStore()))

        val saved = store.slots().first().single()

        assertEquals(DoHProvider.SYSTEM, saved.config.doHProvider)
    }

    @Test
    fun `updateSlot с endpointList — обновляет список`() = runTest {
        val store = newStore()
        val id = store.addSlot("S", sample)
        val newEndpoints = listOf("10.0.0.1:2408", "10.0.0.2:2408")
        store.updateSlot(id, "S", sample, endpointList = newEndpoints)
        val saved = store.slots().first().first { it.id == id }
        assertEquals(newEndpoints, saved.endpointList)
    }

    @Test
    fun `migrateAwgParams — частичное совпадение не триггерит миграцию (защита от false positive)`() = runTest {
        val store = newStore()
        val intentional = AwgParams(
            underloadPacketJunkSize = 19,
            payloadPacketJunkSize = 5,
        )
        val id = store.addSlot("Manual", sample.copy(awgParams = intentional))
        val saved = store.slots().first().first { it.id == id }
        assertEquals(19, saved.config.awgParams.underloadPacketJunkSize, "S3=19 с другим S4 — намеренный, не трогать")
        assertEquals(5, saved.config.awgParams.payloadPacketJunkSize)
    }

    private fun buildValidSlotJson(
        id: String,
        name: String,
        doHProvider: String? = null,
    ): String {
        val c = sample
        val cfg = buildString {
            append("""{"priv":"${c.privateKey}","pub":"${c.publicKey}","peerPub":"${c.peerPublicKey}",""")
            append(""" "peerEndpoint":"${c.peerEndpoint}","ifaceV4":"${c.interfaceAddressV4}","""")
            append(""" "ifaceV6":"${c.interfaceAddressV6}","license":"${c.accountLicense}","mtu":${c.mtu},"""")
            append(""" "dnsServers":["1.1.1.1"],"allowedIps":["0.0.0.0/0","::/0"],""")
            if (doHProvider != null) {
                append(" \"doHProvider\":\"$doHProvider\",")
            }
            append(""" "keepalive":${c.keepaliveSeconds},""")
            append(""" "awgParams":{"jc":0,"jmin":0,"jmax":0,"s1":0,"s2":0,"h1":1,"h2":2,"h3":3,"h4":4}}""")
        }.replace("  ", "")
        return """{"id":"$id","name":"$name","isActive":false,"config":$cfg}"""
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
