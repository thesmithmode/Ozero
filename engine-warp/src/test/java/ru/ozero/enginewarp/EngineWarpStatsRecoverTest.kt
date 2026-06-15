package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import android.content.Context
import android.net.ConnectivityManager
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.Upstream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify

class EngineWarpStatsRecoverTest {

    private val sampleConfig = WarpConfig(
        privateKey = "p",
        publicKey = "P",
        peerPublicKey = "PP",
        peerEndpoint = "162.159.192.1:2408",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700::1/128",
        accountLicense = "L",
    )

    @Test
    fun `stats poll обновляет bytesIn bytesOut и activeConnections при свежем handshake`() = runTest {
        val bridge = FakeBridge()
        val reader = FixedReader(
            WarpUapiState(handshakeAgeSeconds = 5L, rxBytes = 1_234L, txBytes = 5_678L, peersSeen = 1),
        )
        val e = newEngine(bridge = bridge, reader = reader, scope = backgroundScope, pollMs = 1_000L)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 11)
        runCurrent()
        advanceTimeBy(50L)
        runCurrent()
        val s = e.stats().first()
        assertEquals(1_234L, s.bytesIn, "rx_bytes из UAPI попадают в EngineStats.bytesIn")
        assertEquals(5_678L, s.bytesOut, "tx_bytes из UAPI попадают в EngineStats.bytesOut")
        assertEquals(1, s.activeConnections, "fresh handshake (<180s) → 1 active peer")
        assertTrue(s.connectedSince > 0L, "connectedSince set при первом success handshake")
    }

    @Test
    fun `null UAPI обнуляет activeConnections — peer watchdog видит провал`() = runTest {
        val reader = WarpUapiStateReader { _, _ -> null }
        val e = newEngine(bridge = FakeBridge(), reader = reader, scope = backgroundScope, pollMs = 50L)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 11)
        runCurrent()
        advanceTimeBy(300L)
        runCurrent()
        val s = e.stats().first()
        assertEquals(
            0,
            s.activeConnections,
            "при null UAPI activeConnections обязан стать 0. Регрессия 2026-05-20: оставалось 1, " +
                "peer watchdog не видел провал, юзер ночью висел Connected без интернета.",
        )
        assertEquals(0L, s.connectedSince, "connectedSince сброшен при null UAPI")
    }

    @Test
    fun `stale handshake → activeConnections=0 — peer watchdog узнает`() = runTest {
        val bridge = FakeBridge()
        val reader = FixedReader(
            WarpUapiState(handshakeAgeSeconds = 500L, rxBytes = 0L, txBytes = 0L, peersSeen = 1),
        )
        val e = newEngine(bridge = bridge, reader = reader, scope = backgroundScope, pollMs = 1_000L)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 11)
        runCurrent()
        advanceTimeBy(50L)
        runCurrent()
        val s = e.stats().first()
        assertEquals(
            0,
            s.activeConnections,
            "stale handshake (>180s) → 0 peers — watchdog обязан понять что peer мёртв",
        )
    }

    @Test
    fun `stop() отменяет stats poll и сбрасывает stats`() = runTest {
        val calls = AtomicInteger(0)
        val reader = WarpUapiStateReader { _, _ ->
            calls.incrementAndGet()
            WarpUapiState(handshakeAgeSeconds = 1L, rxBytes = 100L, txBytes = 200L, peersSeen = 1)
        }
        val e = newEngine(bridge = FakeBridge(), reader = reader, scope = backgroundScope, pollMs = 100L)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 1)
        runCurrent()
        advanceTimeBy(300L)
        runCurrent()
        val callsBeforeStop = calls.get()
        e.stop()
        runCurrent()
        advanceTimeBy(500L)
        runCurrent()
        val callsAfterStop = calls.get()
        assertEquals(
            callsBeforeStop,
            callsAfterStop,
            "stop() обязан отменить stats job, чтобы UAPI больше не звался",
        )
        val s = e.stats().first()
        assertEquals(0L, s.bytesIn, "stop() сбрасывает stats")
        assertEquals(0, s.activeConnections, "stop() сбрасывает activeConnections")
    }

    @Test
    fun `recover Success при свежем handshake`() = runTest {
        val reader = FixedReader(
            WarpUapiState(handshakeAgeSeconds = 30L, rxBytes = 0L, txBytes = 0L, peersSeen = 1),
        )
        val e = newEngine(bridge = FakeBridge(), reader = reader, scope = backgroundScope)
        val r = e.recover()
        assertEquals(EnginePlugin.RecoverResult.Success, r, "handshake <180s → Success, без действий")
    }

    @Test
    fun `recover Failed при stale handshake — watchdog продолжает retry, VPN не убивается`() = runTest {
        val reader = FixedReader(
            WarpUapiState(handshakeAgeSeconds = 999L, rxBytes = 0L, txBytes = 0L, peersSeen = 1),
        )
        val e = newEngine(bridge = FakeBridge(), reader = reader, scope = backgroundScope)
        val r = e.recover()
        assertIs<EnginePlugin.RecoverResult.Failed>(
            r,
            "stale handshake → Failed. Watchdog НЕ должен вызывать handleEngineFailure — " +
                "amneziawg-go ретраит handshake в фоне, мы просто ждём. Регрессия 2026-05-20: " +
                "NotSupported убивал VPN, юзер видел красную кнопку и должен был дёргать заново — " +
                "вместо бесконечного retry с жёлтым indicator.",
        )
    }

    @Test
    fun `recover Failed когда last_handshake_time_sec=0 (handshake ни разу не происходил)`() = runTest {
        val reader = FixedReader(
            WarpUapiState(handshakeAgeSeconds = null, rxBytes = 0L, txBytes = 0L, peersSeen = 1),
        )
        val e = newEngine(bridge = FakeBridge(), reader = reader, scope = backgroundScope)
        val r = e.recover()
        assertIs<EnginePlugin.RecoverResult.Failed>(
            r,
            "handshake age=null (никогда не было handshake) → Failed — retry бесконечный",
        )
    }

    @Test
    fun `recover никогда не возвращает NotSupported — sentinel против регрессии`() = runTest {
        val cases = listOf(
            WarpUapiState(handshakeAgeSeconds = 5L, rxBytes = 0L, txBytes = 0L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 999L, rxBytes = 0L, txBytes = 0L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = null, rxBytes = 0L, txBytes = 0L, peersSeen = 1),
            null,
        )
        cases.forEachIndexed { idx, state ->
            val reader = FixedReader(state)
            val e = newEngine(bridge = FakeBridge(), reader = reader, scope = backgroundScope)
            val r = e.recover()
            assertTrue(
                r !is EnginePlugin.RecoverResult.NotSupported,
                "case[$idx]=$state: recover вернул NotSupported. NotSupported → watchdog stop VPN → " +
                    "красная кнопка. Юзер хочет бесконечный retry с жёлтым indicator. Got: $r",
            )
        }
    }

    @Test
    fun `recover Failed когда UAPI недоступен`() = runTest {
        val reader = WarpUapiStateReader { _, _ -> null }
        val e = newEngine(bridge = FakeBridge(), reader = reader, scope = backgroundScope)
        val r = e.recover()
        assertIs<EnginePlugin.RecoverResult.Failed>(
            r,
            "UAPI socket недоступен → Failed (watchdog retry), не NotSupported (не убиваем VPN)",
        )
    }

    @Test
    fun `recover reattach succeeds when handshake returns ready`() = runTest {
        val bridge = FakeBridge()
        val e = newEngine(
            bridge = bridge,
            reader = FixedReader(WarpUapiState(handshakeAgeSeconds = 999L, rxBytes = 0L, txBytes = 0L, peersSeen = 1)),
            scope = backgroundScope,
            handshakeChecker = { _, _ -> true },
        )

        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 10)
        assertIs<EnginePlugin.RecoverResult.Failed>(e.recover())
        assertIs<EnginePlugin.RecoverResult.Success>(e.recover())
        assertEquals(2, bridge.attachCalls)
    }

    @Test
    fun `recover reattach stays success even when handshake never succeeds`() = runTest {
        val bridge = FakeBridge()
        val e = newEngine(
            bridge = bridge,
            reader = FixedReader(WarpUapiState(handshakeAgeSeconds = 999L, rxBytes = 0L, txBytes = 0L, peersSeen = 1)),
            scope = backgroundScope,
            handshakeChecker = { _, _ -> false },
        )

        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 11)
        assertIs<EnginePlugin.RecoverResult.Failed>(e.recover())
        assertIs<EnginePlugin.RecoverResult.Success>(e.recover())
        assertEquals(2, bridge.attachCalls)
    }

    @Test
    fun `recover second stale returns success when handshakeChecker throws during reattach wait`() = runTest {
        val bridge = FakeBridge()
        val reader = FixedReader(WarpUapiState(handshakeAgeSeconds = 999L, rxBytes = 1L, txBytes = 2L, peersSeen = 1))
        val e = newEngine(
            bridge = bridge,
            reader = reader,
            scope = backgroundScope,
            handshakeChecker = { _, _ -> throw IllegalStateException("checker boom") },
        )

        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(77)
        assertIs<EnginePlugin.RecoverResult.Failed>(e.recover())
        val second = e.recover()

        assertIs<EnginePlugin.RecoverResult.Success>(second)
        assertEquals(2, bridge.attachCalls)
        assertEquals(1, bridge.detachCalls)
    }

    @Test
    fun `recover second stale unregisters network callback when reattaching`() = runTest {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.registerDefaultNetworkCallback(any()) } returns Unit

        val bridge = FakeBridge()
        val reader = FixedReader(WarpUapiState(handshakeAgeSeconds = 999L, rxBytes = 1L, txBytes = 1L, peersSeen = 1))
        val e = newEngine(
            bridge = bridge,
            reader = reader,
            scope = backgroundScope,
            handshakeChecker = { _, _ -> true },
            context = context,
        )

        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(78)
        assertIs<EnginePlugin.RecoverResult.Failed>(e.recover())
        assertIs<EnginePlugin.RecoverResult.Success>(e.recover())

        verify(exactly = 1) { connectivityManager.registerDefaultNetworkCallback(any()) }
        verify(exactly = 1) { connectivityManager.unregisterNetworkCallback(any()) }
    }

    @Test
    fun `stats poll tracks latest counters after repeated non-null updates`() = runTest {
        val calls = AtomicInteger(0)
        val states = listOf(
            WarpUapiState(handshakeAgeSeconds = 5L, rxBytes = 10L, txBytes = 20L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 300L, rxBytes = 20L, txBytes = 30L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 5L, rxBytes = 30L, txBytes = 50L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 5L, rxBytes = 40L, txBytes = 80L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 5L, rxBytes = 50L, txBytes = 120L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 5L, rxBytes = 60L, txBytes = 180L, peersSeen = 1),
        )
        val reader = WarpUapiStateReader { _, _ ->
            val idx = calls.getAndIncrement()
            states.getOrElse(idx) { states.last() }
        }
        val e = newEngine(
            bridge = FakeBridge(),
            reader = reader,
            scope = backgroundScope,
            pollMs = 1L,
        )
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 12)
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()

        val s = e.stats().first { it.bytesIn == 60L && it.bytesOut == 180L }
        assertEquals(60L, s.bytesIn)
        assertEquals(180L, s.bytesOut)
        assertEquals(1, s.activeConnections)
    }

    @Test
    fun `stats poll handles null, stale and fresh handshake phases`() = runTest {
        val readCalls = AtomicInteger(0)
        val sequence = listOf(
            null,
            null,
            null,
            null,
            null,
            WarpUapiState(handshakeAgeSeconds = 500L, rxBytes = 1L, txBytes = 2L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 5L, rxBytes = 3L, txBytes = 4L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 6L, rxBytes = 5L, txBytes = 7L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 7L, rxBytes = 9L, txBytes = 11L, peersSeen = 1),
            WarpUapiState(handshakeAgeSeconds = 8L, rxBytes = 13L, txBytes = 17L, peersSeen = 1),
        )
        val reader = WarpUapiStateReader { _, _ ->
            val idx = sequence.getOrNull(readCalls.getAndIncrement()) ?: sequence.last()
            idx
        }
        val e = newEngine(bridge = FakeBridge(), reader = reader, scope = backgroundScope, pollMs = 1L)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 55)
        val snapshots = mutableListOf<EngineStats>()
        val collectJob = launch {
            e.stats().collect { snapshots.add(it) }
        }

        advanceTimeBy(20L)
        runCurrent()
        collectJob.cancel()
        collectJob.join()

        val intermediate = snapshots.firstOrNull {
            it.connectedSince == 0L && it.activeConnections == 0 && it.bytesIn == 1L
        }
        assertTrue(intermediate != null, "intermediate stale handshake snapshot present")
        assertEquals(1L, intermediate!!.bytesIn)
        assertEquals(0L, intermediate.connectedSince)
        val final = snapshots.firstOrNull {
            it.connectedSince > 0L && it.activeConnections == 1 && it.bytesIn == 13L
        }
        assertTrue(final != null, "final fresh handshake snapshot present")
        assertEquals(17L, final!!.bytesOut)
    }

    @Test
    fun `null handshake age keeps activeConnections at zero during stats poll`() = runTest {
        val reader = WarpUapiStateReader { _, _ ->
            WarpUapiState(handshakeAgeSeconds = null, rxBytes = 11L, txBytes = 22L, peersSeen = 1)
        }
        val e = newEngine(bridge = FakeBridge(), reader = reader, scope = backgroundScope, pollMs = 50L)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 11)
        runCurrent()
        advanceTimeBy(300L)
        runCurrent()
        val s = e.stats().first()
        assertEquals(0, s.activeConnections)
        assertEquals(0L, s.connectedSince)
        assertEquals(11L, s.bytesIn)
    }

    @Test
    fun `recover reattach unavailable if no saved fd and ini`() = runTest {
        val e = newEngine(
            bridge = FakeBridge(),
            reader = FixedReader(WarpUapiState(handshakeAgeSeconds = 999L, rxBytes = 0L, txBytes = 0L, peersSeen = 1)),
            scope = backgroundScope,
        )

        assertIs<EnginePlugin.RecoverResult.Failed>(e.recover())
        val second = e.recover()
        val failed = assertIs<EnginePlugin.RecoverResult.Failed>(second)
        assertEquals("handshake stale, reattach unavailable", failed.reason)
    }

    private fun interface WarpUapiStateReader {
        operator fun invoke(uapiPath: String, tunnelName: String): WarpUapiState?
    }

    private class FixedReader(private val state: WarpUapiState?) : WarpUapiStateReader {
        override fun invoke(uapiPath: String, tunnelName: String): WarpUapiState? = state
    }

    private fun newEngine(
        bridge: FakeBridge,
        reader: WarpUapiStateReader,
        scope: kotlinx.coroutines.CoroutineScope,
        pollMs: Long = 5_000L,
        context: Context? = null,
        ipv6Enabled: Boolean = false,
        handshakeChecker: (String, String) -> Boolean = { _, _ -> true },
    ): EngineWarp = EngineWarp(
        autoConfig = FakeAuto(),
        configStore = FakeStore(sampleConfig),
        sdkBridge = bridge,
        uapiPathProvider = { "/tmp/uapi" },
        context = context,
        socketProtector = ru.ozero.enginescore.VpnSocketProtector { true },
        ipv6EnabledProvider = { ipv6Enabled },
        handshakeChecker = handshakeChecker,
        uapiStateReader = { p, n -> reader(p, n) },
        warpReadyTimeoutMs = 100L,
        warpReadyPollMs = 10L,
        statsPollIntervalMs = pollMs,
        handshakeStaleThresholdSec = 180L,
        pluginScope = scope,
    )

    private class FakeAuto : WarpAutoConfig {
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<RegisteredWarpConfig> =
            error("not used")
    }

    private class FakeStore(private val active: WarpConfig) : WarpConfigSlotStore {
        private val slots = MutableStateFlow(
            listOf(
                WarpConfigSlot(
                    id = "fixed",
                    name = "Fixed",
                    config = active,
                    isActive = true,
                    rawIniOverride = null,
                ),
            ),
        )
        override fun slots(): Flow<List<WarpConfigSlot>> = slots
        override fun activeSlot(): Flow<WarpConfigSlot?> =
            MutableStateFlow(slots.value.firstOrNull { it.isActive })
        override fun activeConfig(): Flow<WarpConfig?> = MutableStateFlow(active)
        override suspend fun addSlot(
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ): String = "id"
        override suspend fun setActive(id: String) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun updateSlot(
            id: String,
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun clear() = Unit
        override suspend fun replaceAll(slots: List<WarpConfigSlot>) = Unit
    }

    private class FakeBridge(
        private val attachResult: WarpSdkBridge.AttachResult = WarpSdkBridge.AttachResult.Success,
        private val proxyResult: WarpSdkBridge.ProxyResult = WarpSdkBridge.ProxyResult.Failed("not supported"),
    ) : WarpSdkBridge {
        var attachCalls = 0
        var detachCalls = 0
        var proxyCalls = 0
        var stopProxyCalls = 0
        private var running = false
        override suspend fun attachTun(
            tunnelName: String,
            tunFd: Int,
            iniConfig: String,
            uapiPath: String,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.AttachResult {
            attachCalls++
            running = attachResult is WarpSdkBridge.AttachResult.Success
            return attachResult
        }
        override suspend fun detachTun() {
            detachCalls++
            running = false
        }
        override suspend fun startProxy(
            tunnelName: String,
            iniConfig: String,
            uapiPath: String,
            socksPort: Int,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.ProxyResult {
            proxyCalls++
            running = proxyResult is WarpSdkBridge.ProxyResult.Success
            return proxyResult
        }
        override suspend fun stopProxy() {
            stopProxyCalls++
            running = false
        }
        override fun isRunning(): Boolean = running
        override fun reprotectSockets() {}
    }
}
