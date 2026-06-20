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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunSpec
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

class SingboxEngineProbeTest {

    @Test
    fun `probe fails when process is not connected`() = runTest {
        val engine = buildEngine()

        val result = engine.probe()

        val failure = assertIs<ProbeResult.Failure>(result)
        assertTrue(failure.reason.contains("not connected"))
    }

    @Test
    fun `start throws when config type is not singbox`() = runTest {
        val engine = buildEngine()

        val thrown = kotlin.runCatching { engine.start(EngineConfig.ByeDpi(), Upstream.None) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        assertTrue(thrown.message.orEmpty().contains("EngineConfig.Singbox"))
    }

    @Test
    fun `start fails before binding when selected blob is invalid`() = runTest {
        val engine = buildEngine()

        val result = engine.start(
            EngineConfig.Singbox(beanBlob = byteArrayOf(1, 2, 3), protocolType = SingboxEngine.PROTOCOL_VLESS),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("failed to build"))
    }

    @Test
    fun `start fails before binding when auto select blobs are invalid`() = runTest {
        val engine = buildEngine()

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = ByteArray(0),
                protocolType = SingboxEngine.PROTOCOL_AUTO_SELECT,
                autoSelectBeanBlobs = listOf(byteArrayOf(1), byteArrayOf(2)),
            ),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("failed to build"))
    }

    @Test
    fun `start rejects non socks upstream before binding`() = runTest {
        val engine = buildEngine()

        val result = engine.start(
            EngineConfig.Singbox(beanBlob = byteArrayOf(1, 2, 3), protocolType = SingboxEngine.PROTOCOL_VLESS),
            Upstream.Http("127.0.0.1", 8080),
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("requires Socks5"))
    }

    @Test
    fun `start builds direct tun config before failing unavailable service binding`() = runTest {
        val engine = buildEngine()

        val result = engine.start(
            EngineConfig.Singbox(beanBlob = makeVlessBlob(), protocolType = SingboxEngine.PROTOCOL_VLESS),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("bindService failed"))
        assertEquals(null, engine.privateField("pendingConfig"))
        assertEquals(0, engine.privateIntField("pendingSocksPort"))
    }

    @Test
    fun `start builds proxy mode config before failing unavailable service binding`() = runTest {
        val engine = buildEngine()

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = makeVlessBlob(),
                protocolType = SingboxEngine.PROTOCOL_VLESS,
                proxyMode = true,
            ),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("bindService failed"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `start builds socks chain config before failing unavailable service binding`() = runTest {
        val engine = buildEngine()

        val result = engine.start(
            EngineConfig.Singbox(beanBlob = makeVlessBlob(), protocolType = SingboxEngine.PROTOCOL_VLESS),
            Upstream.Socks5("127.0.0.1", 1080),
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("bindService failed"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `start builds auto select config before failing unavailable service binding`() = runTest {
        val engine = buildEngine()

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = ByteArray(0),
                protocolType = SingboxEngine.PROTOCOL_AUTO_SELECT,
                autoSelectBeanBlobs = listOf(makeVlessBlob("one.example.com"), makeVlessBlob("two.example.com")),
            ),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("bindService failed"))
        assertEquals(null, engine.privateField("pendingConfig"))
        assertEquals(0, engine.privateIntField("pendingSocksPort"))
    }

    @Test
    fun `start proxy mode fails before binding when selected blob is invalid`() = runTest {
        val engine = buildEngine()

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = byteArrayOf(1, 2, 3),
                protocolType = SingboxEngine.PROTOCOL_VLESS,
                proxyMode = true,
            ),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("chain deserialize"))
    }

    @Test
    fun `stop clears runtime state when remote stop times out`() = runTest {
        val engine = buildEngine()
        val process = mockk<ISingboxEngineProcess>()
        every { process.stopAndWait(3_000L) } returns false
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("pendingConfig", "{}")
        engine.setPrivateField("pendingSocksPort", 49408)
        engine.setPrivateField("activeSocksPort", 49409)
        engine.setPrivateField("chainMode", true)

        engine.stop()

        assertEquals(0, engine.privateIntField("pendingSocksPort"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
        assertEquals(null, engine.privateField("pendingConfig"))
        assertEquals(false, engine.privateField("chainMode"))
        assertEquals(null, engine.privateField("proxy"))
    }

    @Test
    fun `stop clears runtime state when remote stop throws`() = runTest {
        val engine = buildEngine()
        val process = mockk<ISingboxEngineProcess>()
        every { process.stopAndWait(3_000L) } throws IllegalStateException("binder died")
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("pendingConfig", "{}")
        engine.setPrivateField("pendingSocksPort", 49408)
        engine.setPrivateField("activeSocksPort", 49409)
        engine.setPrivateField("chainMode", true)

        engine.stop()

        assertEquals(0, engine.privateIntField("pendingSocksPort"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
        assertEquals(null, engine.privateField("pendingConfig"))
        assertEquals(false, engine.privateField("chainMode"))
        assertEquals(null, engine.privateField("proxy"))
    }

    @Test
    fun `attachTun fails immediately in chain mode`() = runTest {
        val engine = buildEngine()
        engine.setPrivateField("chainMode", true)

        val result = engine.attachTun(42)

        val failure = assertIs<TunAttachResult.Failure>(result)
        assertTrue(failure.reason.contains("chain mode"))
    }

    @Test
    fun `attachTun fails before start when no pending config`() = runTest {
        val engine = buildEngine()

        val result = engine.attachTun(42)

        val failure = assertIs<TunAttachResult.Failure>(result)
        assertTrue(failure.reason.contains("before start"))
    }

    @Test
    fun `attachTun without connected process clears pending runtime state`() = runTest {
        val engine = buildEngine()
        engine.setPrivateField("pendingConfig", "{}")
        engine.setPrivateField("pendingSocksPort", 49408)
        engine.setPrivateField("activeSocksPort", 49409)

        val result = engine.attachTun(42)

        val failure = assertIs<TunAttachResult.Failure>(result)
        assertTrue(failure.reason.contains("not connected"))
        assertEquals(null, engine.privateField("pendingConfig"))
        assertEquals(0, engine.privateIntField("pendingSocksPort"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `stats maps process counters to EngineStats`() = runTest {
        val engine = buildEngine()
        val process = mockk<ISingboxEngineProcess>()
        every { process.stats } returns SingboxStats(
            txTotal = 123L,
            rxTotal = 456L,
            activeConnections = 7,
        )
        engine.setPrivateField("proxy", process)

        val stats = withTimeout(1_000L) { engine.stats().first() }

        assertEquals(456L, stats.bytesIn)
        assertEquals(123L, stats.bytesOut)
        assertEquals(7, stats.activeConnections)
    }

    @Test
    fun `probe fails when active socks port is absent`() = runTest {
        val engine = buildEngine()
        val process = mockk<ISingboxEngineProcess>()
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 0)

        val result = engine.probe()

        val failure = assertIs<ProbeResult.Failure>(result)
        assertTrue(failure.reason.contains("not active"))
    }

    @Test
    fun `probe clears runtime state when runtimeRunning throws`() = runTest {
        val engine = buildEngine()
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } throws IllegalStateException("binder died")
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)

        val result = engine.probe()

        val failure = assertIs<ProbeResult.Failure>(result)
        assertTrue(failure.reason.contains("health check failed"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `probe clears runtime state when runtime is stopped`() = runTest {
        val engine = buildEngine()
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns false
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)

        val result = engine.probe()

        val failure = assertIs<ProbeResult.Failure>(result)
        assertTrue(failure.reason.contains("not running"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `probe succeeds when runtime and routed probe are healthy`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = SingboxRoutedProbe { socksPort ->
            assertEquals(49408, socksPort)
            17L
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)

        val result = engine.probe()

        val success = assertIs<ProbeResult.Success>(result)
        assertEquals(17L, success.latencyMs)
        assertEquals(49408, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `probe rejects running runtime when routed probe fails and clears stale port`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = object : SingboxRoutedProbe {
            override suspend fun probeLatencyMs(socksPort: Int): Long = SingboxHttp204RoutedProbe.LATENCY_FAILED
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)

        val result = engine.probe()

        assertTrue(result is ProbeResult.Failure)
        assertTrue(result.reason.contains("routed probe"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `awaitReady fails when routed probe fails`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = object : SingboxRoutedProbe {
            override suspend fun probeLatencyMs(socksPort: Int): Long = SingboxHttp204RoutedProbe.LATENCY_FAILED
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)

        val result = engine.awaitReady()

        assertTrue(result is EnginePlugin.ReadyResult.Timeout)
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `awaitReady keeps auto select runtime ready when routed probe is still warming up`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = SingboxRoutedProbe { SingboxHttp204RoutedProbe.LATENCY_FAILED }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)
        engine.setPrivateField("activeAutoSelect", true)

        val result = engine.awaitReady()

        assertIs<EnginePlugin.ReadyResult.Ready>(result)
        assertEquals(49408, engine.privateIntField("activeSocksPort"))
        assertEquals(true, engine.privateBooleanField("activeAutoSelect"))
    }

    @Test
    fun `awaitReady retries transient routed probe failures without clearing active port`() = runTest {
        val engine = buildEngine()
        var calls = 0
        engine.routedProbe = SingboxRoutedProbe {
            calls++
            if (calls < 3) SingboxHttp204RoutedProbe.LATENCY_FAILED else 24L
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)

        val result = engine.awaitReady()

        assertIs<EnginePlugin.ReadyResult.Ready>(result)
        assertEquals(3, calls)
        assertEquals(49408, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `awaitReady returns ready after successful probe`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = SingboxRoutedProbe { 1L }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        engine.setPrivateField("activeSocksPort", 49408)

        val result = engine.awaitReady()

        assertIs<EnginePlugin.ReadyResult.Ready>(result)
    }

    @Test
    fun `exitNodeStrategy returns socks only when active port exists`() = runTest {
        val engine = buildEngine()
        assertIs<ExitNodeStrategy.Unavailable>(engine.exitNodeStrategy(0))

        engine.setPrivateField("activeSocksPort", 49408)

        val strategy = assertIs<ExitNodeStrategy.ViaSocks>(engine.exitNodeStrategy(0))
        assertEquals("127.0.0.1", strategy.host)
        assertEquals(49408, strategy.port)
    }

    @Test
    fun `tunSpec exposes singbox tun contract`() = runTest {
        val spec = buildEngine().tunSpec()

        assertIs<TunSpec>(spec)
        assertEquals("Sing-box", spec.sessionName)
        assertEquals(9000, spec.mtu)
        assertEquals(false, spec.blocking)
        assertEquals(true, spec.allowFamilyV4)
        assertEquals(true, spec.allowFamilyV6)
        assertEquals(true, spec.routeAllV4)
        assertEquals(true, spec.routeAllV6)
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

    private fun makeVlessBlob(host: String = "proxy.example.com"): ByteArray =
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
            override suspend fun updateLatency(id: Long, latency: Int) = Unit
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

    private fun SingboxEngine.privateField(name: String): Any? {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
    }
}
