package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.Upstream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineWarpHardRestartTest {

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
    fun `hardRestart вызывает sdkBridge_forceProcessRestart и сбрасывает stats`() = runTest {
        val bridge = TrackingBridge(forceProcessRestartResult = true)
        val reader = FixedReader(
            WarpUapiState(handshakeAgeSeconds = 10L, rxBytes = 100L, txBytes = 200L, peersSeen = 1),
        )
        val e = newEngine(bridge = bridge, reader = reader, scope = backgroundScope, pollMs = 100L)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 11)
        runCurrent()
        advanceTimeBy(200L)
        runCurrent()

        val result = e.hardRestart()

        assertEquals(
            1,
            bridge.forceProcessRestartCalls.get(),
            "hardRestart обязан вызвать sdkBridge.forceProcessRestart ровно один раз",
        )
        assertIs<EnginePlugin.RecoverResult.Failed>(
            result,
            "hardRestart возвращает Failed — onProcessDied callback (через DeathRecipient) " +
                "поднимет recovery flow в TunnelController. Watchdog не зацикливается.",
        )
        assertTrue(
            (result as EnginePlugin.RecoverResult.Failed).reason.contains("process killed"),
            "reason обязан содержать 'process killed' для трассировки. Got: ${result.reason}",
        )
        val stats = e.stats().first()
        assertEquals(0L, stats.bytesIn, "hardRestart обнуляет bytesIn")
        assertEquals(0, stats.activeConnections, "hardRestart обнуляет activeConnections")
        assertEquals(0L, stats.connectedSince, "hardRestart сбрасывает connectedSince")
    }

    @Test
    fun `hardRestart возвращает Failed когда kill процесса не удался`() = runTest {
        val bridge = TrackingBridge(forceProcessRestartResult = false)
        val reader = FixedReader(null)
        val e = newEngine(bridge = bridge, reader = reader, scope = backgroundScope)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 11)
        runCurrent()

        val result = e.hardRestart()

        val failed = assertIs<EnginePlugin.RecoverResult.Failed>(result)
        assertTrue(
            failed.reason.contains("pid не найден") || failed.reason.contains("не удался"),
            "kill=false → reason обязан указывать на причину. Got: ${failed.reason}",
        )
    }

    @Test
    fun `UAPI null 3 раза подряд → activeConnections=0 в stats (peer watchdog подберёт)`() = runTest {
        val bridge = TrackingBridge()
        val calls = AtomicInteger(0)
        val reader = WarpUapiStateReader { _, _ ->
            calls.incrementAndGet()
            null
        }
        val e = newEngine(bridge = bridge, reader = reader, scope = backgroundScope, pollMs = 100L)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 11)
        runCurrent()
        advanceTimeBy(500L)
        runCurrent()

        assertTrue(
            calls.get() >= EngineWarp.UAPI_NULL_DEGRADED_THRESHOLD,
            "stats poll обязан прочитать UAPI ≥${EngineWarp.UAPI_NULL_DEGRADED_THRESHOLD} раз. Got: ${calls.get()}",
        )
        val stats = e.stats().first()
        assertEquals(
            0,
            stats.activeConnections,
            "при null UAPI activeConnections обязан стать 0 — peer watchdog видит провал. " +
                "Регрессия: остаётся 1 → 'Connected без интернета' навечно (2026-05-20 prod баг).",
        )
        assertEquals(0L, stats.connectedSince, "connectedSince сброшен при null UAPI")
    }

    @Test
    fun `после non-null read activeConnections восстанавливается`() = runTest {
        val bridge = TrackingBridge()
        val toggle = AtomicInteger(0)
        val reader = WarpUapiStateReader { _, _ ->
            val n = toggle.incrementAndGet()
            if (n <= 3) {
                null
            } else {
                WarpUapiState(handshakeAgeSeconds = 5L, rxBytes = 50L, txBytes = 100L, peersSeen = 1)
            }
        }
        val e = newEngine(bridge = bridge, reader = reader, scope = backgroundScope, pollMs = 100L)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 11)
        runCurrent()
        advanceTimeBy(800L)
        runCurrent()

        val stats = e.stats().first()
        assertEquals(
            1,
            stats.activeConnections,
            "после восстановления UAPI activeConnections=1 — счётчик null сбросился",
        )
        assertEquals(50L, stats.bytesIn)
    }

    private fun interface WarpUapiStateReader {
        operator fun invoke(uapiPath: String, tunnelName: String): WarpUapiState?
    }

    private class FixedReader(private val state: WarpUapiState?) : WarpUapiStateReader {
        override fun invoke(uapiPath: String, tunnelName: String): WarpUapiState? = state
    }

    private fun newEngine(
        bridge: WarpSdkBridge,
        reader: WarpUapiStateReader,
        scope: kotlinx.coroutines.CoroutineScope,
        pollMs: Long = 5_000L,
    ): EngineWarp = EngineWarp(
        autoConfig = FakeAuto(),
        configStore = FakeStore(sampleConfig),
        sdkBridge = bridge,
        uapiPathProvider = { "/tmp/uapi" },
        socketProtector = ru.ozero.enginescore.VpnSocketProtector { true },
        ipv6EnabledProvider = { false },
        handshakeChecker = { _, _ -> true },
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
        override suspend fun addSlot(name: String, config: WarpConfig, rawIni: String?): String = "id"
        override suspend fun setActive(id: String) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun updateSlot(id: String, name: String, config: WarpConfig, rawIni: String?) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun clear() = Unit
        override suspend fun replaceAll(slots: List<WarpConfigSlot>) = Unit
    }

    private class TrackingBridge(
        private val forceProcessRestartResult: Boolean = true,
    ) : WarpSdkBridge {
        val forceProcessRestartCalls = AtomicInteger(0)
        private var running = false
        override suspend fun attachTun(
            tunnelName: String,
            tunFd: Int,
            iniConfig: String,
            uapiPath: String,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.AttachResult {
            running = true
            return WarpSdkBridge.AttachResult.Success
        }
        override suspend fun detachTun() {
            running = false
        }
        override fun isRunning(): Boolean = running
        override suspend fun forceProcessRestart(): Boolean {
            forceProcessRestartCalls.incrementAndGet()
            return forceProcessRestartResult
        }
    }
}
