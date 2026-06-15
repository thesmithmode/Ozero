package ru.ozero.enginesingbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingboxEngineAutoSelectTest {

    private val beanKey = byteArrayPreferencesKey("singbox_vless_bean")
    private val selectedProfileKey = longPreferencesKey("singbox_selected_profile_id")

    private fun makeVlessBlob(host: String = "proxy.example.com", port: Int = 443): ByteArray {
        val bean = VLESSBean().apply {
            uuid = "12345678-1234-1234-1234-123456789abc"
            serverAddress = host
            serverPort = port
            type = "tcp"
            security = "none"
        }
        return KryoSerializer.serialize(bean)
    }

    private fun makeVmessBlob(): ByteArray = KryoSerializer.serialize(
        VMessBean().apply {
            uuid = "vmess-id"
            serverAddress = "vmess.example.com"
            serverPort = 443
            type = "tcp"
        },
    )

    private fun makeTrojanBlob(): ByteArray = KryoSerializer.serialize(
        TrojanBean().apply {
            password = "secret"
            serverAddress = "trojan.example.com"
            serverPort = 443
            type = "tcp"
        },
    )

    private fun makeShadowsocksBlob(): ByteArray = KryoSerializer.serialize(
        ShadowsocksBean().apply {
            method = "aes-128-gcm"
            password = "secret"
            serverAddress = "ss.example.com"
            serverPort = 8388
        },
    )

    private fun makeProfile(id: Long, groupId: Long, host: String, port: Int): ProxyProfile =
        ProxyProfile(
            id = id,
            groupId = groupId,
            name = "Server $host",
            beanBlob = makeVlessBlob(host, port),
            protocolType = SingboxEngine.PROTOCOL_VLESS,
        )

    private fun fakeDataStore(prefs: Preferences = mutablePreferencesOf()): DataStore<Preferences> {
        val flow = MutableStateFlow(prefs)
        return object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(flow.value)
                flow.value = updated
                return updated
            }
        }
    }

    private fun fakeProfileDao(profilesByGroup: Map<Long, List<ProxyProfile>>): ProxyProfileDao =
        object : ProxyProfileDao {
            private val allProfiles = profilesByGroup.values.flatten()
            override fun getAllFlow(): Flow<List<ProxyProfile>> = MutableStateFlow(allProfiles)
            override fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>> =
                MutableStateFlow(profilesByGroup[groupId] ?: emptyList())
            override suspend fun getByGroupId(groupId: Long): List<ProxyProfile> =
                profilesByGroup[groupId] ?: emptyList()
            override suspend fun getById(id: Long): ProxyProfile? = allProfiles.find { it.id == id }
            override suspend fun insert(profile: ProxyProfile): Long = profile.id
            override suspend fun insertAll(profiles: List<ProxyProfile>) {}
            override suspend fun insertAllIgnoringConflicts(profiles: List<ProxyProfile>): List<Long> =
                profiles.map { it.id.takeIf { id -> id != 0L } ?: 1L }
            override suspend fun deleteByGroupId(groupId: Long) {}
            override suspend fun getIdsByGroupId(groupId: Long): List<Long> =
                profilesByGroup[groupId]?.map { it.id } ?: emptyList()
            override suspend fun deleteByIds(ids: List<Long>) {}
            override suspend fun updateLatency(id: Long, latency: Int) {}
            override suspend fun countByGroupId(groupId: Long): Int =
                profilesByGroup[groupId]?.size ?: 0
            override suspend fun update(profile: ProxyProfile) {}
            override suspend fun delete(profile: ProxyProfile) {}
        }

    private fun fakeProxyChainDao(profileIds: List<Long> = emptyList()): ProxyChainDao =
        object : ProxyChainDao {
            private val flow = MutableStateFlow(
                profileIds.mapIndexed { index, id -> ProxyChainStep(profileId = id, userOrder = index) },
            )

            override fun getAllFlow(): Flow<List<ProxyChainStep>> = flow
            override suspend fun getAll(): List<ProxyChainStep> = flow.value
            override suspend fun clear() {
                flow.value = emptyList()
            }

            override suspend fun insertAll(steps: List<ProxyChainStep>) {
                flow.value = steps
            }

            override suspend fun replace(profileIds: List<Long>) {
                flow.value = profileIds.mapIndexed { index, id ->
                    ProxyChainStep(profileId = id, userOrder = index)
                }
            }
        }

    private fun buildEngine(
        prefs: Preferences = mutablePreferencesOf(),
        profilesByGroup: Map<Long, List<ProxyProfile>> = emptyMap(),
        chainProfileIds: List<Long> = emptyList(),
    ): SingboxEngine = SingboxEngine(
        context = mockk(relaxed = true),
        dataStore = fakeDataStore(prefs),
        profileDao = fakeProfileDao(profilesByGroup),
        proxyChainDao = fakeProxyChainDao(chainProfileIds),
    )

    private fun awaitInit() = Thread.sleep(300)

    @Test
    fun `should return null when auto mode active and cache empty`() {
        val prefs = mutablePreferencesOf(selectedProfileKey to SingboxEngine.SELECTED_AUTO)
        val engine = buildEngine(prefs = prefs)
        awaitInit()

        val result = engine.buildManualConfig(null)

        assertNull(result)
    }

    @Test
    fun `should return Singbox config with autoSelectBeanBlobs when auto mode with profiles`() {
        val profiles = listOf(
            makeProfile(1L, 1L, "server1.example.com", 443),
            makeProfile(2L, 1L, "server2.example.com", 444),
        )
        val prefs = mutablePreferencesOf(selectedProfileKey to SingboxEngine.SELECTED_AUTO)
        val engine = buildEngine(
            prefs = prefs,
            profilesByGroup = mapOf(1L to profiles),
        )
        awaitInit()

        val result = engine.buildManualConfig(null)

        assertNotNull(result)
        assertTrue(result is EngineConfig.Singbox)
        assertEquals(SingboxEngine.PROTOCOL_AUTO_SELECT, result.protocolType)
        assertTrue(result.autoSelectBeanBlobs.isNotEmpty())
        assertEquals(2, result.autoSelectBeanBlobs.size)
    }

    @Test
    fun `should return single-profile config when manual profile selected`() {
        val blob = makeVlessBlob()
        val prefs = mutablePreferencesOf(beanKey to blob, selectedProfileKey to 42L)
        val engine = buildEngine(prefs = prefs)
        awaitInit()

        val result = engine.buildManualConfig(null)

        assertNotNull(result)
        assertTrue(result is EngineConfig.Singbox)
        assertTrue(result.autoSelectBeanBlobs.isEmpty())
    }

    @Test
    fun `manual profile config includes chain wrapper blobs`() {
        val selected = makeProfile(42L, 1L, "eu.example.com", 443)
        val wrapper = makeProfile(7L, 1L, "ru.example.com", 443)
        val prefs = mutablePreferencesOf(beanKey to selected.beanBlob, selectedProfileKey to selected.id)
        val engine = buildEngine(
            prefs = prefs,
            profilesByGroup = mapOf(1L to listOf(wrapper, selected)),
            chainProfileIds = listOf(wrapper.id, selected.id),
        )
        awaitInit()

        val result = engine.buildManualConfig(null)

        assertNotNull(result)
        assertTrue(result is EngineConfig.Singbox)
        assertEquals(1, result.chainBeanBlobs.size)
    }

    @Test
    fun `manual profile config prefers selected row blob over stale datastore blob`() {
        val selected = makeProfile(42L, 1L, "eu.example.com", 443)
        val stale = makeVlessBlob("stale.example.com", 8443)
        val prefs = mutablePreferencesOf(beanKey to stale, selectedProfileKey to selected.id)
        val engine = buildEngine(
            prefs = prefs,
            profilesByGroup = mapOf(1L to listOf(selected)),
        )
        awaitInit()

        val result = engine.buildManualConfig(null)

        assertNotNull(result)
        assertTrue(result is EngineConfig.Singbox)
        assertTrue(result.beanBlob.contentEquals(selected.beanBlob))
        assertTrue(!result.beanBlob.contentEquals(stale))
    }

    @Test
    fun `should return null when no blob and no auto mode`() {
        val engine = buildEngine()
        awaitInit()

        val result = engine.buildManualConfig(null)

        assertNull(result)
    }

    @Test
    fun `should aggregate profiles from multiple groups in auto mode`() {
        val profiles1 = listOf(makeProfile(1L, 1L, "s1.example.com", 443))
        val profiles2 = listOf(
            makeProfile(2L, 2L, "s2.example.com", 444),
            makeProfile(3L, 2L, "s3.example.com", 445),
        )
        val prefs = mutablePreferencesOf(selectedProfileKey to SingboxEngine.SELECTED_AUTO)
        val engine = buildEngine(
            prefs = prefs,
            profilesByGroup = mapOf(1L to profiles1, 2L to profiles2),
        )
        awaitInit()

        val result = engine.buildManualConfig(null)

        assertNotNull(result)
        assertTrue(result is EngineConfig.Singbox)
        assertEquals(3, result.autoSelectBeanBlobs.size)
    }

    @Test
    fun `buildManualConfig maps protocol type from selected bean class`() {
        val cases = listOf(
            makeVlessBlob() to SingboxEngine.PROTOCOL_VLESS,
            makeVmessBlob() to SingboxEngine.PROTOCOL_VMESS,
            makeTrojanBlob() to SingboxEngine.PROTOCOL_TROJAN,
            makeShadowsocksBlob() to SingboxEngine.PROTOCOL_SHADOWSOCKS,
        )

        cases.forEachIndexed { index, (blob, expectedType) ->
            val prefs = mutablePreferencesOf(beanKey to blob, selectedProfileKey to (index + 10L))
            val profile = ProxyProfile(
                id = index + 10L,
                groupId = 1L,
                name = "case-$index",
                beanBlob = blob,
                protocolType = expectedType,
            )
            val engine = buildEngine(prefs = prefs, profilesByGroup = mapOf(1L to listOf(profile)))
            awaitInit()

            val result = engine.buildManualConfig(null)

            assertNotNull(result)
            assertTrue(result is EngineConfig.Singbox)
            assertEquals(expectedType, result.protocolType)
        }
    }

    @Test
    fun `buildManualConfig falls back to VLESS protocol for invalid blob`() {
        val invalid = byteArrayOf(1, 2, 3)
        val prefs = mutablePreferencesOf(beanKey to invalid)
        val engine = buildEngine(prefs = prefs)
        awaitInit()

        val result = engine.buildManualConfig(null)

        assertNotNull(result)
        assertTrue(result is EngineConfig.Singbox)
        assertEquals(SingboxEngine.PROTOCOL_VLESS, result.protocolType)
    }

    @Test
    fun `buildProxyConfig mirrors manual config with proxy mode enabled`() {
        val blob = makeVlessBlob()
        val prefs = mutablePreferencesOf(beanKey to blob)
        val engine = buildEngine(prefs = prefs)
        awaitInit()

        val result = engine.buildProxyConfig(null)

        assertNotNull(result)
        val singbox = result as EngineConfig.Singbox
        assertTrue(singbox.proxyMode)
        assertTrue(singbox.beanBlob.contentEquals(blob))
    }
}
