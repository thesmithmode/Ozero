package ru.ozero.engineopera

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel
import java.io.IOException
import java.net.SocketTimeoutException

class OperaEngineTest {
    private class FakeProcess : OperaProxyProcess {
        override var alive = true
        override fun stop() {
            alive = false
        }
    }

    private class FakeTun : OperaTunBridge {
        var accept = true
        var running = false
        var failure: Exception? = null
        var json = ""
        var beforeStart: suspend () -> Unit = {}

        override suspend fun start(tunFd: Int, config: String): Boolean {
            beforeStart()
            failure?.let { throw it }
            json = config
            running = accept
            return accept
        }

        override suspend fun isRunning(): Boolean = running

        override suspend fun stop() {
            running = false
        }
    }

    private fun engine(
        available: Boolean = true,
        launcher: OperaProxyLauncher = OperaProxyLauncher { _, _ -> FakeProcess() },
        tun: FakeTun = FakeTun(),
        closeFd: (Int) -> Unit = {},
        dnsProbe: suspend (Int) -> Unit = {},
    ) = OperaEngine(
        OperaProxyRuntime(launcher, routedProbe = { 12L }, allocatePort = { 12000 }),
        tun,
        binaryAvailable = { available },
        closeFd = closeFd,
        dnsProbe = dnsProbe,
    )

    @Test
    fun `both modes use settings and missing binary skips selection`() = runTest {
        val plugin = engine()
        assertEquals(EngineId.OPERA, plugin.id)
        assertTrue(plugin.capabilities.supportsTcp)
        assertTrue(plugin.capabilities.supportsDoH)
        assertFalse(plugin.capabilities.supportsUdp)
        assertFalse(plugin.capabilities.supportsUpstreamSocks)
        assertEquals(EngineConfig.Opera(), plugin.buildManualConfig(null))
        assertEquals(EngineConfig.Opera(proxyMode = true), plugin.buildProxyConfig(null))
        val settings = SettingsModel.DEFAULT.copy(operaRegion = "AM", operaSelection = "first", ipv6Enabled = true)
        assertEquals(EngineConfig.Opera("AM", "first", ipv6Enabled = true), plugin.buildManualConfig(settings))
        assertEquals(EngineConfig.Opera("AM", "first", true, true), plugin.buildProxyConfig(settings))
        assertNull(engine(false).buildManualConfig(null))
        assertNull(engine(false).buildProxyConfig(null))
        assertTrue(plugin.tunSpec().routeAllV6)
        assertEquals(6_000L, plugin.stopTimeoutMs())
    }

    @Test
    fun `stop cancels readiness and releases subprocess before another start`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val process = FakeProcess()
        val plugin = OperaEngine(
            OperaProxyRuntime(
                OperaProxyLauncher { _, _ -> process },
                routedProbe = {
                    entered.complete(Unit)
                    awaitCancellation()
                },
                allocatePort = { 12000 },
            ),
            FakeTun(),
            binaryAvailable = { true },
        )
        val startup = launch { plugin.start(EngineConfig.Opera(), Upstream.None) }
        entered.await()
        plugin.stop()
        startup.join()

        assertTrue(startup.isCancelled)
        assertFalse(process.alive)
        assertTrue(plugin.exitNodeStrategy(12000) is ExitNodeStrategy.Unavailable)
    }

    @Test
    fun `stop cancels pending TUN attachment without consuming caller fd`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val bridge = FakeTun().apply {
            beforeStart = {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        val process = FakeProcess()
        val closed = mutableListOf<Int>()
        val plugin = engine(
            launcher = OperaProxyLauncher { _, _ -> process },
            tun = bridge,
            closeFd = { closed.add(it) },
        )
        assertEquals(StartResult.Success(12000), plugin.start(EngineConfig.Opera(), Upstream.None))
        val attachment = launch { plugin.attachTun(42) }
        entered.await()
        plugin.stop()
        attachment.join()

        assertTrue(attachment.isCancelled)
        assertTrue(closed.isEmpty())
        assertFalse(process.alive)
        assertFalse(bridge.running)
        assertTrue(plugin.exitNodeStrategy(12000) is ExitNodeStrategy.Unavailable)
    }

    @Test
    fun `rejects invalid configuration and upstream before launching`() = runTest {
        val plugin = engine(launcher = OperaProxyLauncher { _, _ -> error("unexpected launch") })
        assertTrue(plugin.start(EngineConfig.Warp, Upstream.None) is StartResult.Failure)
        assertTrue(plugin.start(EngineConfig.Opera(), Upstream.Socks5("127.0.0.1", 1080)) is StartResult.Failure)
        assertTrue(plugin.start(EngineConfig.Opera(region = "XX"), Upstream.None) is StartResult.Failure)
        assertTrue(plugin.start(EngineConfig.Opera(selection = "bad"), Upstream.None) is StartResult.Failure)
    }

    @Test
    fun `proxy startup exposes routed probe and stop clears exit route`() = runTest {
        val process = FakeProcess()
        val plugin = engine(
            launcher = OperaProxyLauncher { config, port ->
                assertEquals(OperaProxyConfig(OperaRegion.AS, OperaServerSelection.RANDOM), config)
                assertEquals(12000, port)
                process
            },
        )
        assertTrue(plugin.exitNodeStrategy(12000) is ExitNodeStrategy.Unavailable)
        assertTrue(plugin.attachTun(42) is TunAttachResult.Failure)
        assertEquals(
            StartResult.Success(12000),
            plugin.start(EngineConfig.Opera("AS", "random", proxyMode = true), Upstream.None),
        )
        assertTrue(plugin.attachTun(42) is TunAttachResult.Failure)
        assertEquals(ExitNodeStrategy.ViaSocks("127.0.0.1", 12000), plugin.exitNodeStrategy(12000))
        assertTrue(plugin.exitNodeStrategy(0) is ExitNodeStrategy.Unavailable)
        assertEquals(ProbeResult.Success(12), plugin.probe())
        assertEquals(EnginePlugin.ReadyResult.Ready, plugin.awaitReady())
        plugin.stop()
        assertFalse(process.alive)
        assertTrue(plugin.exitNodeStrategy(12000) is ExitNodeStrategy.Unavailable)
        assertTrue(plugin.awaitReady() is EnginePlugin.ReadyResult.Timeout)
    }

    @Test
    fun `TUN readiness requires attachment and transfers fd only after native start`() = runTest {
        val bridge = FakeTun()
        val closed = mutableListOf<Int>()
        val plugin = engine(tun = bridge, closeFd = { closed.add(it) })
        assertEquals(StartResult.Success(12000), plugin.start(EngineConfig.Opera(), Upstream.None))
        assertTrue(plugin.probe() is ProbeResult.Failure)
        assertTrue(plugin.exitNodeStrategy(12000) is ExitNodeStrategy.Unavailable)
        assertEquals(TunAttachResult.Success, plugin.attachTun(42))
        assertEquals(listOf(42), closed)
        assertTrue(bridge.json.contains("hijack-dns"))
        assertEquals(EnginePlugin.ReadyResult.Ready, plugin.awaitReady())
        assertTrue(plugin.attachTun(43) is TunAttachResult.Failure)
        bridge.running = false
        assertTrue(plugin.probe() is ProbeResult.Failure)
        plugin.stop()
        assertFalse(bridge.running)
    }

    @Test
    fun `TUN readiness fails if routed DNS is unavailable while proxy mode does not require DoH`() = runTest {
        val plugin = engine(dnsProbe = { throw IOException("DNS failure") })
        plugin.start(EngineConfig.Opera(), Upstream.None)
        assertEquals(TunAttachResult.Success, plugin.attachTun(42))
        assertEquals(
            EnginePlugin.ReadyResult.Timeout("Opera routed DNS unavailable: IOException"),
            plugin.awaitReady(),
        )
        plugin.stop()
        plugin.start(EngineConfig.Opera(proxyMode = true), Upstream.None)
        assertEquals(EnginePlugin.ReadyResult.Ready, plugin.awaitReady())
        plugin.stop()
    }

    @Test
    fun `TUN retries one routed DNS timeout and still requires a DNS response`() = runTest {
        var attempts = 0
        val plugin = engine(dnsProbe = { if (++attempts == 1) throw SocketTimeoutException() })
        try {
            plugin.start(EngineConfig.Opera(), Upstream.None)
            assertEquals(TunAttachResult.Success, plugin.attachTun(42))

            assertEquals(EnginePlugin.ReadyResult.Ready, plugin.awaitReady())
            assertEquals(2, attempts)
        } finally {
            plugin.stop()
        }
    }

    @Test
    fun `native rejection or exception rolls back without consuming caller fd`() = runTest {
        for (failure in listOf(null, IllegalStateException("native failure"))) {
            val bridge = FakeTun().apply {
                accept = false
                this.failure = failure
            }
            val process = FakeProcess()
            val plugin = engine(
                launcher = OperaProxyLauncher { _, _ -> process },
                tun = bridge,
                closeFd = { error("caller still owns fd") },
            )
            plugin.start(EngineConfig.Opera(), Upstream.None)
            assertTrue(plugin.attachTun(42) is TunAttachResult.Failure)
            assertFalse(process.alive)
            assertTrue(plugin.exitNodeStrategy(12000) is ExitNodeStrategy.Unavailable)
        }
    }

    @Test
    fun `cancellation of native attachment rolls back`() = runTest {
        val bridge = FakeTun().apply { failure = CancellationException("cancelled") }
        val process = FakeProcess()
        val plugin = engine(launcher = OperaProxyLauncher { _, _ -> process }, tun = bridge)
        plugin.start(EngineConfig.Opera(), Upstream.None)
        assertTrue(runCatching { plugin.attachTun(42) }.exceptionOrNull() is CancellationException)
        assertFalse(process.alive)
    }
}
