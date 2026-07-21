package ru.ozero.enginesingbox

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SingboxEngineWarmReadinessTest {

    @Test
    fun `awaitReady fast fails warm auto select when runtime health clears active port`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = SingboxRoutedProbe { SingboxHttp204RoutedProbe.LATENCY_FAILED }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returnsMany listOf(false, true)
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)
        engine.setPrivateField("activeAutoSelect", true)

        val result = engine.awaitReady()

        val failure = assertIs<EnginePlugin.ReadyResult.Timeout>(result)
        assertTrue(failure.reason.contains("not running"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
        assertEquals(false, engine.privateBooleanField("activeAutoSelect"))
    }

    @Test
    fun `proxy mode auto select preserves running runtime when routed probe is unavailable`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = SingboxRoutedProbe { SingboxHttp204RoutedProbe.LATENCY_FAILED }
        val process = mockk<ISingboxEngineProcess>()
        every { process.startProxyMode(any(), any()) } returns Unit
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)

        assertIs<StartResult.Success>(
            engine.start(
                EngineConfig.Singbox(
                    beanBlob = ByteArray(0),
                    protocolType = SingboxEngine.PROTOCOL_AUTO_SELECT,
                    autoSelectBeanBlobs = listOf(
                        makeVlessBlob("one.example.com"),
                        makeVlessBlob("two.example.com"),
                    ),
                    proxyMode = true,
                ),
                Upstream.None,
            ),
        )
        val ready = engine.awaitReady()

        assertIs<EnginePlugin.ReadyResult.Ready>(ready)
        assertTrue(engine.privateIntField("activeSocksPort") > 0)
        assertEquals(true, engine.privateBooleanField("activeAutoSelect"))
        verify(exactly = 0) { process.stopAndWait(any()) }
    }

    private fun buildEngine(): SingboxEngine =
        SingboxEngine(
            context = unboundContext(),
            dataStore = fakeDataStore(),
            profileDao = fakeProfileDao(),
            proxyChainDao = fakeProxyChainDao(),
        )

    private fun unboundContext(): Context =
        object : ContextWrapper(
            mockk<Context>(relaxed = true) {
                every { packageName } returns "ru.ozero.app"
            },
        ) {
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = false
            override fun unbindService(conn: ServiceConnection) = Unit
        }

    private fun makeVlessBlob(host: String): ByteArray =
        KryoSerializer.serialize(
            VLESSBean().apply {
                uuid = "12345678-1234-1234-1234-123456789abc"
                serverAddress = host
                serverPort = 443
                type = "tcp"
                security = "none"
            },
        )

    private fun fakeDataStore(): DataStore<Preferences> {
        val flow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        return object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(flow.value)
                flow.value = updated
                return updated
            }
        }
    }

    private fun fakeProfileDao(): ProxyProfileDao =
        object : ProxyProfileDao {
            override fun getAllFlow(): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
            override fun getAllLimitedFlow(limit: Int): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
            override fun getAutoCandidatesFlow(limit: Int): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
            override fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
            override suspend fun getByGroupId(groupId: Long): List<ProxyProfile> = emptyList()
            override suspend fun getByGroupIdLimited(groupId: Long, limit: Int): List<ProxyProfile> = emptyList()
            override suspend fun getAutoCandidatesByGroupId(groupId: Long, limit: Int): List<ProxyProfile> =
                emptyList()
            override suspend fun getById(id: Long): ProxyProfile? = null
            override suspend fun insert(profile: ProxyProfile): Long = profile.id
            override suspend fun insertAll(profiles: List<ProxyProfile>) = Unit
            override suspend fun insertAllIgnoringConflicts(profiles: List<ProxyProfile>): List<Long> =
                profiles.map { it.id.takeIf { id -> id != 0L } ?: 1L }
            override suspend fun deleteByGroupId(groupId: Long) = Unit
            override suspend fun getIdsByGroupId(groupId: Long): List<Long> = emptyList()
            override suspend fun deleteByIds(ids: List<Long>) = Unit
            override suspend fun replaceForGroup(groupId: Long, profiles: List<ProxyProfile>) = Unit
            override suspend fun updateProbeResult(
                id: Long,
                latency: Int,
                probeError: String?,
                lastProbeAt: Long,
            ) = Unit
            override suspend fun countByGroupId(groupId: Long): Int = 0
            override suspend fun update(profile: ProxyProfile) = Unit
            override suspend fun delete(profile: ProxyProfile) = Unit
        }

    private fun fakeProxyChainDao(): ProxyChainDao =
        object : ProxyChainDao {
            override fun getAllFlow(): Flow<List<ProxyChainStep>> = MutableStateFlow(emptyList())
            override suspend fun getAll(): List<ProxyChainStep> = emptyList()
            override suspend fun clear() = Unit
            override suspend fun insertAll(steps: List<ProxyChainStep>) = Unit
            override suspend fun replace(profileIds: List<Long>) = Unit
        }

    private fun SingboxEngine.setPrivateField(name: String, value: Any) {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }

    private fun SingboxEngine.privateIntField(name: String): Int {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.getInt(this)
    }

    private fun SingboxEngine.privateBooleanField(name: String): Boolean {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(this)
    }
}
