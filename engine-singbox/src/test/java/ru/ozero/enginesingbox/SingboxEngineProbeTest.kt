package ru.ozero.enginesingbox

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.ParcelFileDescriptor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
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
import ru.ozero.singboxconfig.BeanSupportError
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import java.io.File
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Suppress("LargeClass")
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
        assertEquals("config failed: DECODE_FAILED", failure.reason)
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
        assertEquals("config failed: NO_SUPPORTED_AUTO_PROFILE", failure.reason)
    }

    @Test
    fun `auto select diagnostics preserve profile id and exact rejection`() {
        val engine = buildEngine()
        val invalid = VLESSBean().apply {
            serverAddress = "invalid.example"
            serverPort = 0
            uuid = "00000000-0000-0000-0000-000000000001"
        }

        val result = engine.buildPendingConfigForTest(
            EngineConfig.Singbox(
                beanBlob = ByteArray(0),
                protocolType = SingboxEngine.PROTOCOL_AUTO_SELECT,
                autoSelectBeanBlobs = listOf(KryoSerializer.serialize(invalid)),
                autoSelectProfileIds = listOf(42L),
            ),
        )

        val failure = assertIs<BuildConfigResult.Failure>(result).inputFailures.single()
        assertEquals(42L, failure.profileId)
        assertEquals(ProfileInputStage.VALIDATION, failure.stage)
        assertEquals(BeanSupportError.INVALID_PORT, failure.reason)
    }

    @Test
    fun `valid auto candidate remains while rejected candidate is diagnosed`() {
        val engine = buildEngine()

        val result = engine.buildPendingConfigForTest(
            EngineConfig.Singbox(
                beanBlob = ByteArray(0),
                protocolType = SingboxEngine.PROTOCOL_AUTO_SELECT,
                autoSelectBeanBlobs = listOf(byteArrayOf(1), makeVlessBlob()),
                autoSelectProfileIds = listOf(10L, 11L),
            ),
        )

        val success = assertIs<BuildConfigResult.Success>(result)
        assertTrue(success.json.contains("proxy-0"))
        assertEquals(10L, success.inputFailures.single().profileId)
        assertEquals(ProfileInputStage.DESERIALIZATION, success.inputFailures.single().stage)
    }

    @Test
    fun `missing declared chain profile is retained as typed failure`() {
        val engine = buildEngine(chainProfileIds = listOf(1L, 99L))
        Thread.sleep(100)
        engine.setPrivateField("cachedSelectedProfileId", 1L)
        engine.setPrivateField("cachedBlob", makeVlessBlob())

        val config = assertIs<EngineConfig.Singbox>(engine.buildManualConfig(null))
        val result = assertIs<BuildConfigResult.Failure>(engine.buildPendingConfigForTest(config))

        assertEquals(listOf(99L), config.chainProfileIds)
        assertEquals(99L, result.inputFailures.single().profileId)
        assertEquals(ProfileInputStage.MISSING_PROFILE, result.inputFailures.single().stage)
    }

    @Test
    fun `declared chain longer than auto limit is not silently truncated`() {
        val wrapperIds = (2L..52L).toList()
        val engine = buildEngine(chainProfileIds = listOf(1L) + wrapperIds)
        Thread.sleep(100)
        engine.setPrivateField("cachedSelectedProfileId", 1L)
        engine.setPrivateField("cachedBlob", makeVlessBlob())

        val config = assertIs<EngineConfig.Singbox>(engine.buildManualConfig(null))
        val result = assertIs<BuildConfigResult.Failure>(engine.buildPendingConfigForTest(config))

        assertEquals(wrapperIds, config.chainProfileIds)
        assertEquals(wrapperIds.size, result.inputFailures.size)
        assertEquals(52L, result.inputFailures.last().profileId)
    }

    @Test
    fun `unsupported selected profile returns exact support error`() = runTest {
        val engine = buildEngine()
        val bean = VLESSBean().apply {
            serverAddress = "invalid.example"
            serverPort = 0
            uuid = "00000000-0000-0000-0000-000000000001"
        }

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = KryoSerializer.serialize(bean),
                protocolType = SingboxEngine.PROTOCOL_VLESS,
            ),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals("profile rejected: INVALID_PORT", failure.reason)
    }

    @Test
    fun `invalid chain wrapper rejects the entire declared chain`() = runTest {
        val engine = buildEngine()

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = makeVlessBlob(),
                protocolType = SingboxEngine.PROTOCOL_VLESS,
                chainBeanBlobs = listOf(byteArrayOf(1, 2, 3)),
            ),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals("config failed: UNSUPPORTED_PROFILE", failure.reason)
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
    fun `start proxy mode clears stale tun auto select flag before awaitReady`() = runTest {
        val engine = buildEngine()
        engine.setPrivateField("activeTunAutoSelect", true)

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = makeVlessBlob(),
                protocolType = SingboxEngine.PROTOCOL_VLESS,
                proxyMode = true,
            ),
            Upstream.None,
        )

        assertIs<StartResult.Failure>(result)
        assertEquals(false, engine.privateBooleanField("activeTunAutoSelect"))
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
        assertEquals("chain recovery failed: DECODE_FAILED", failure.reason)
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
        engine.setPrivateField("activeTunAutoSelect", true)

        engine.stop()

        assertEquals(0, engine.privateIntField("pendingSocksPort"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
        assertEquals(null, engine.privateField("pendingConfig"))
        assertEquals(false, engine.privateField("chainMode"))
        assertEquals(false, engine.privateBooleanField("activeTunAutoSelect"))
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
    fun `attachTun does not block startup on routed probes`() = runTest {
        mockkStatic(ParcelFileDescriptor::class)
        try {
            val engine = buildEngine()
            var calls = 0
            engine.routedProbe = SingboxRoutedProbe {
                calls++
                SingboxHttp204RoutedProbe.LATENCY_FAILED
            }
            val process = mockk<ISingboxEngineProcess>()
            val transportPfd = mockk<ParcelFileDescriptor>(relaxed = true)
            val rawOwner = mockk<ParcelFileDescriptor>(relaxed = true)
            every { ParcelFileDescriptor.fromFd(42) } returns transportPfd
            every { ParcelFileDescriptor.adoptFd(42) } returns rawOwner
            every { process.startWithConfig(transportPfd, any(), any()) } returns Unit
            every { process.runtimeRunning() } returns true
            every { process.stopAndWait(3_000L) } returns true
            engine.setPrivateField("proxy", process)
            engine.setPrivateField("pendingConfig", "{}")
            engine.setPrivateField("pendingSocksPort", 49408)
            engine.setPrivateField("pendingTunAutoSelect", true)

            val result = engine.attachTun(42)

            assertIs<TunAttachResult.Success>(result)
            assertEquals(0, calls)
            verify(exactly = 0) { process.stopAndWait(3_000L) }
            assertEquals(null, engine.privateField("pendingConfig"))
            assertEquals(0, engine.privateIntField("pendingSocksPort"))
            assertEquals(49408, engine.privateIntField("activeSocksPort"))
            assertEquals(true, engine.privateBooleanField("activeTunAutoSelect"))
            verify(exactly = 1) { transportPfd.close() }
            verify(exactly = 1) { rawOwner.close() }
        } finally {
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `attachTun returns raw fd ownership when remote runtime fails`() = runTest {
        mockkStatic(ParcelFileDescriptor::class)
        try {
            val engine = buildEngine()
            val process = mockk<ISingboxEngineProcess>()
            val transportPfd = mockk<ParcelFileDescriptor>(relaxed = true)
            every { ParcelFileDescriptor.fromFd(42) } returns transportPfd
            every { process.startWithConfig(transportPfd, any(), any()) } returns Unit
            every { process.runtimeRunning() } returns false
            every { process.stopAndWait(3_000L) } returns true
            engine.setPrivateField("proxy", process)
            engine.setPrivateField("pendingConfig", "{}")
            engine.setPrivateField("pendingSocksPort", 49408)

            val result = engine.attachTun(42)

            assertIs<TunAttachResult.Failure>(result)
            verify(exactly = 1) { transportPfd.close() }
            verify(exactly = 0) { ParcelFileDescriptor.adoptFd(42) }
        } finally {
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `attachTun keeps successful runtime when raw fd close reports failure`() = runTest {
        mockkStatic(ParcelFileDescriptor::class)
        try {
            val engine = buildEngine()
            val process = mockk<ISingboxEngineProcess>()
            val transportPfd = mockk<ParcelFileDescriptor>(relaxed = true)
            val rawOwner = mockk<ParcelFileDescriptor>()
            every { ParcelFileDescriptor.fromFd(42) } returns transportPfd
            every { ParcelFileDescriptor.adoptFd(42) } returns rawOwner
            every { rawOwner.close() } throws IllegalStateException("close failed")
            every { process.startWithConfig(transportPfd, any(), any()) } returns Unit
            every { process.runtimeRunning() } returns true
            engine.setPrivateField("proxy", process)
            engine.setPrivateField("pendingConfig", "{}")
            engine.setPrivateField("pendingSocksPort", 49408)

            val result = engine.attachTun(42)

            assertIs<TunAttachResult.Success>(result)
            verify(exactly = 1) { transportPfd.close() }
            verify(exactly = 1) { rawOwner.close() }
        } finally {
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `attachTun succeeds for non auto runtime without routed probe gate`() = runTest {
        mockkStatic(ParcelFileDescriptor::class)
        try {
            val engine = buildEngine()
            engine.routedProbe = SingboxRoutedProbe { SingboxHttp204RoutedProbe.LATENCY_FAILED }
            val process = mockk<ISingboxEngineProcess>()
            val transportPfd = mockk<ParcelFileDescriptor>(relaxed = true)
            val rawOwner = mockk<ParcelFileDescriptor>(relaxed = true)
            every { ParcelFileDescriptor.fromFd(42) } returns transportPfd
            every { ParcelFileDescriptor.adoptFd(42) } returns rawOwner
            every { process.startWithConfig(transportPfd, any(), any()) } returns Unit
            every { process.runtimeRunning() } returns true
            every { process.stopAndWait(3_000L) } returns true
            engine.setPrivateField("proxy", process)
            engine.setPrivateField("pendingConfig", "{}")
            engine.setPrivateField("pendingSocksPort", 49408)
            engine.setPrivateField("pendingTunAutoSelect", false)

            val result = engine.attachTun(42)

            assertIs<TunAttachResult.Success>(result)
            verify(exactly = 0) { process.stopAndWait(3_000L) }
            assertEquals(null, engine.privateField("pendingConfig"))
            assertEquals(0, engine.privateIntField("pendingSocksPort"))
            assertEquals(49408, engine.privateIntField("activeSocksPort"))
            assertEquals(false, engine.privateBooleanField("activeTunAutoSelect"))
        } finally {
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `awaitReady rejects external probe failure after local SOCKS is ready`() = runTest {
        mockkStatic(ParcelFileDescriptor::class)
        try {
            val engine = buildEngine()
            var calls = 0
            engine.routedProbe = SingboxRoutedProbe {
                calls++
                SingboxHttp204RoutedProbe.LATENCY_FAILED
            }
            val process = mockk<ISingboxEngineProcess>()
            val transportPfd = mockk<ParcelFileDescriptor>(relaxed = true)
            val rawOwner = mockk<ParcelFileDescriptor>(relaxed = true)
            every { ParcelFileDescriptor.fromFd(42) } returns transportPfd
            every { ParcelFileDescriptor.adoptFd(42) } returns rawOwner
            every { process.startWithConfig(transportPfd, any(), any()) } returns Unit
            every { process.runtimeRunning() } returns true
            engine.setPrivateField("proxy", process)
            engine.setPrivateField("pendingConfig", "{}")
            engine.setPrivateField("pendingSocksPort", 49408)
            engine.setPrivateField("pendingTunAutoSelect", true)

            val result = engine.attachTun(42)
            openLocalSocksListener().use { listener ->
                engine.setPrivateField("activeSocksPort", listener.localPort)

                val ready = engine.awaitReady()

                assertIs<EnginePlugin.ReadyResult.Timeout>(ready)
                assertEquals(listener.localPort, engine.privateIntField("activeSocksPort"))
            }

            assertIs<TunAttachResult.Success>(result)
            assertEquals(1, calls)
            assertEquals(null, engine.privateField("pendingConfig"))
            assertEquals(0, engine.privateIntField("pendingSocksPort"))
            assertEquals(true, engine.privateBooleanField("activeTunAutoSelect"))
            verify(exactly = 0) { process.stopAndWait(any()) }
        } finally {
            unmockkStatic(ParcelFileDescriptor::class)
        }
    }

    @Test
    fun `proxy mode publishes socks port without warmup routed probe`() = runTest {
        val engine = buildEngine()
        var probeCalls = 0
        engine.routedProbe = SingboxRoutedProbe {
            probeCalls++
            SingboxHttp204RoutedProbe.LATENCY_FAILED
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.startProxyMode(any(), any()) } returns Unit
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = makeVlessBlob(),
                protocolType = SingboxEngine.PROTOCOL_VLESS,
                proxyMode = true,
            ),
            Upstream.None,
        )

        val success = assertIs<StartResult.Success>(result)
        assertTrue(success.socksPort > 0)
        assertEquals(0, probeCalls)
        assertEquals(success.socksPort, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `proxy mode keeps runtime when routed probes are unavailable after runtime starts`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = SingboxRoutedProbe { SingboxHttp204RoutedProbe.LATENCY_FAILED }
        val process = mockk<ISingboxEngineProcess>()
        every { process.startProxyMode(any(), any()) } returns Unit
        every { process.runtimeRunning() } returns true
        every { process.stopAndWait(3_000L) } returns true
        engine.setPrivateField("proxy", process)

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = makeVlessBlob(),
                protocolType = SingboxEngine.PROTOCOL_VLESS,
                proxyMode = true,
            ),
            Upstream.None,
        )

        val success = assertIs<StartResult.Success>(result)
        assertTrue(success.socksPort > 0)
        verify(exactly = 0) { process.stopAndWait(3_000L) }
        assertEquals(success.socksPort, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `proxy mode succeeds when routed probe succeeds after runtime starts`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = SingboxRoutedProbe { 9L }
        val process = mockk<ISingboxEngineProcess>()
        every { process.startProxyMode(any(), any()) } returns Unit
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)

        val result = engine.start(
            EngineConfig.Singbox(
                beanBlob = makeVlessBlob(),
                protocolType = SingboxEngine.PROTOCOL_VLESS,
                proxyMode = true,
            ),
            Upstream.None,
        )

        val success = assertIs<StartResult.Success>(result)
        assertTrue(success.socksPort > 0)
        assertEquals(success.socksPort, engine.privateIntField("activeSocksPort"))
        verify(exactly = 0) { process.stopAndWait(any()) }
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
        every { process.runtimeRunning() } returns true
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
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        openLocalSocksListener().use { listener ->
            engine.routedProbe = SingboxRoutedProbe { socksPort ->
                assertEquals(listener.localPort, socksPort)
                17L
            }
            engine.setPrivateField("activeSocksPort", listener.localPort)

            val result = engine.probe()

            val success = assertIs<ProbeResult.Success>(result)
            assertEquals(17L, success.latencyMs)
            assertEquals(listener.localPort, engine.privateIntField("activeSocksPort"))
        }
    }

    @Test
    fun `probe rejects running runtime when routed probe fails and keeps active port`() = runTest {
        val engine = buildEngine()
        engine.routedProbe = object : SingboxRoutedProbe {
            override suspend fun probeLatencyMs(socksPort: Int): Long = SingboxHttp204RoutedProbe.LATENCY_FAILED
            override suspend fun probe(socksPort: Int): RoutedProbeResult =
                RoutedProbeResult.Failure(RoutedProbeResult.Reason.DNS)
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        openLocalSocksListener().use { listener ->
            engine.setPrivateField("activeSocksPort", listener.localPort)

            val result = engine.probe()

            assertTrue(result is ProbeResult.Failure)
            assertTrue(result.reason.contains("DNS"))
            assertEquals(listener.localPort, engine.privateIntField("activeSocksPort"))
        }
    }

    @Test
    fun `awaitReady requires routed TLS endpoint after local SOCKS handshake`() = runTest {
        val engine = buildEngine()
        var routedProbeCalls = 0
        engine.routedProbe = object : SingboxRoutedProbe {
            override suspend fun probeLatencyMs(socksPort: Int): Long {
                routedProbeCalls++
                return SingboxHttp204RoutedProbe.LATENCY_FAILED
            }

            override suspend fun probe(socksPort: Int): RoutedProbeResult {
                routedProbeCalls++
                return RoutedProbeResult.Failure(RoutedProbeResult.Reason.TLS)
            }
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        openLocalSocksListener().use { listener ->
            engine.setPrivateField("activeSocksPort", listener.localPort)

            val result = engine.awaitReady()

            assertIs<EnginePlugin.ReadyResult.Timeout>(result)
            assertEquals(1, routedProbeCalls)
            assertEquals(listener.localPort, engine.privateIntField("activeSocksPort"))
        }
    }

    @Test
    fun `awaitReady does not call routed probe when socks5 handshake fails`() = runTest {
        val engine = buildEngine()
        var calls = 0
        engine.routedProbe = SingboxRoutedProbe {
            calls++
            24L
        }
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        openInvalidLocalListener().use { listener ->
            engine.setPrivateField("activeSocksPort", listener.localPort)

            val result = engine.awaitReady()

            assertIs<EnginePlugin.ReadyResult.Timeout>(result)
            assertEquals(0, calls)
            assertEquals(listener.localPort, engine.privateIntField("activeSocksPort"))
        }
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
        assertEquals(false, spec.allowFamilyV6)
        assertEquals(true, spec.routeAllV4)
        assertEquals(false, spec.routeAllV6)
        assertEquals(null, spec.ipv6Address)
    }

    @Test
    fun `awaitReady preserves runtime state after invalid local socks response`() = runTest {
        val engine = buildEngine()
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        openInvalidLocalListener().use { listener ->
            engine.setPrivateField("activeSocksPort", listener.localPort)

            val result = engine.awaitReady()

            val failure = assertIs<EnginePlugin.ReadyResult.Timeout>(result)
            assertTrue(failure.reason.contains("SOCKS5 listener"))
            assertEquals(listener.localPort, engine.privateIntField("activeSocksPort"))
        }
    }

    @Test
    fun `awaitReady preserves runtime state after closed socks port`() = runTest {
        val engine = buildEngine()
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        val closedPort = ServerSocket(0).use { it.localPort }
        engine.setPrivateField("activeSocksPort", closedPort)

        val result = engine.awaitReady()

        assertIs<EnginePlugin.ReadyResult.Timeout>(result)
        assertEquals(closedPort, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `probe clears stale port when socks5 listener is unavailable`() = runTest {
        val engine = buildEngine()
        val process = mockk<ISingboxEngineProcess>()
        every { process.runtimeRunning() } returns true
        engine.setPrivateField("proxy", process)
        val closedPort = ServerSocket(0).use { it.localPort }
        engine.setPrivateField("activeSocksPort", closedPort)

        val result = engine.probe()

        val failure = assertIs<ProbeResult.Failure>(result)
        assertTrue(failure.reason.contains("SOCKS5 listener"))
        assertEquals(0, engine.privateIntField("activeSocksPort"))
    }

    @Test
    fun `manual unsupported profile is rejected instead of replaced by cached auto profiles`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        val pendingBlock = source.substringAfter("private fun buildPendingConfig")
            .substringBefore("private suspend fun startProxyMode")
        val chainBlock = source.substringAfter("private suspend fun startProxyMode")
            .substringBefore("bindOrFail()?.let")

        assertFalse(pendingBlock.contains("build fallback auto config"))
        assertFalse(chainBlock.contains("chain fallback auto config"))
    }

    private fun openLocalSocksListener(): ServerSocket {
        val server = ServerSocket(0)
        Thread {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        val input = socket.getInputStream()
                        val output = socket.getOutputStream()
                        val version = input.read()
                        val methodsCount = input.read()
                        repeat(methodsCount.coerceAtLeast(0)) { input.read() }
                        if (version == 0x05) {
                            output.write(byteArrayOf(0x05, 0x00))
                            output.flush()
                        }
                    }
                }
            }
        }.apply { isDaemon = true }.start()
        return server
    }

    private fun openInvalidLocalListener(): ServerSocket {
        val server = ServerSocket(0)
        Thread {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        socket.getOutputStream().write(byteArrayOf(0x04, 0xff.toByte()))
                    }
                }
            }
        }.apply { isDaemon = true }.start()
        return server
    }

    private fun buildEngine(chainProfileIds: List<Long> = emptyList()): SingboxEngine =
        SingboxEngine(
            context = unboundContext(),
            dataStore = fakeDataStore(),
            profileDao = fakeProfileDao(),
            proxyChainDao = fakeProxyChainDao(chainProfileIds),
        )

    private fun SingboxEngine.buildPendingConfigForTest(config: EngineConfig.Singbox): BuildConfigResult {
        val method = javaClass.getDeclaredMethod(
            "buildPendingConfig",
            EngineConfig.Singbox::class.java,
            Int::class.java,
        )
        method.isAccessible = true
        return method.invoke(this, config, 39000) as BuildConfigResult
    }

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
            override fun getByIdFlow(id: Long): Flow<ProxyProfile?> = MutableStateFlow(null)
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

    private fun fakeProxyChainDao(profileIds: List<Long> = emptyList()): ProxyChainDao =
        object : ProxyChainDao {
            private val steps = profileIds.mapIndexed { index, profileId ->
                ProxyChainStep(profileId = profileId, userOrder = index)
            }
            override fun getAllFlow(): Flow<List<ProxyChainStep>> = MutableStateFlow(steps)
            override suspend fun getAll(): List<ProxyChainStep> = steps
            override suspend fun clear() = Unit
            override suspend fun deleteByProfileIds(profileIds: Set<Long>) = Unit
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
