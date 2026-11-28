package ru.ozero.enginetgwss

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TelegramWssEngineTest {
    private val warp = mockk<EnginePlugin>(relaxed = true).apply {
        coEvery { start(EngineConfig.WarpProxy(), Upstream.None) } returns StartResult.Success(10811)
        coEvery { awaitReady() } returns EnginePlugin.ReadyResult.Ready
    }

    private fun engine(routeReady: Boolean = true, ws: FakeTelegramWebSockets = FakeTelegramWebSockets()) =
        TelegramWssEngine(warp, { port, _ -> TelegramWssProxy(ws, WarpSocksRelay(port)) {} }, { routeReady })

    @Test
    fun `configuration supports TUN and proxy with TCP and UDP`() {
        val engine = engine()
        assertEquals(EngineId.TGWSS, engine.id)
        assertTrue(engine.id.manualStandaloneOnly)
        assertTrue(engine.capabilities.supportsTcp)
        assertTrue(engine.capabilities.supportsUdp)
        assertFalse(engine.capabilities.supportsUpstreamSocks)
        assertEquals(EngineConfig.TelegramWss(), engine.buildManualConfig(SettingsModel()))
        assertEquals(EngineConfig.TelegramWss(tunnelMode = false), engine.buildProxyConfig(null))
    }

    @Test
    fun `start probe stats restart and stop own WARP lifecycle`() = runTest {
        val engine = engine()
        try {
            assertIs<ProbeResult.Failure>(engine.probe())
            assertEquals(EngineStats(), engine.stats().first())
            assertIs<EnginePlugin.ReadyResult.Timeout>(engine.awaitReady())
            val first = assertIs<StartResult.Success>(engine.start(EngineConfig.TelegramWss(0), Upstream.None))
            assertTrue(first.socksPort > 0)
            assertIs<ProbeResult.Success>(engine.probe())
            assertIs<EnginePlugin.ReadyResult.Ready>(engine.awaitReady())
            assertTrue(engine.stats().first().connectedSince > 0)
            assertEquals(
                ExitNodeStrategy.ViaSocks("127.0.0.1", first.socksPort),
                engine.exitNodeStrategy(first.socksPort),
            )
            assertIs<ExitNodeStrategy.Unavailable>(engine.exitNodeStrategy(0))
            assertIs<StartResult.Success>(engine.start(EngineConfig.TelegramWss(0, false), Upstream.None))
        } finally {
            engine.stop()
        }
        engine.stop()
        coVerify(exactly = 2) { warp.stop() }
        assertIs<ProbeResult.Failure>(engine.probe())
        assertEquals(EngineStats(), engine.stats().first())
        assertIs<ExitNodeStrategy.Unavailable>(engine.exitNodeStrategy(10812))
    }

    @Test
    fun `invalid configuration or occupied port fails and releases WARP`() = runTest {
        val engine = engine()
        assertIs<StartResult.Failure>(engine.start(EngineConfig.Warp, Upstream.None))
        assertIs<StartResult.Failure>(
            engine.start(EngineConfig.TelegramWss(), Upstream.Socks5("127.0.0.1", 1080)),
        )
        val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        ServerSocket(0, 1, loopback).use { occupied ->
            val failure = assertIs<StartResult.Failure>(
                engine.start(EngineConfig.TelegramWss(occupied.localPort), Upstream.None),
            )
            assertEquals("Telegram WSS failed to start local proxy: BindException", failure.reason)
        }
        assertEquals(EngineStats(), engine.stats().first())
        coVerify(exactly = 1) { warp.stop() }
    }

    @Test
    fun `WARP startup and readiness failure never expose a listener`() = runTest {
        val engine = engine()
        coEvery { warp.start(EngineConfig.WarpProxy(), Upstream.None) } returns StartResult.Failure("offline")
        assertIs<StartResult.Failure>(engine.start(EngineConfig.TelegramWss(0), Upstream.None))
        coEvery { warp.start(EngineConfig.WarpProxy(), Upstream.None) } returns StartResult.Success(10811)
        coEvery { warp.awaitReady() } returns EnginePlugin.ReadyResult.Timeout("offline")
        assertIs<StartResult.Failure>(engine.start(EngineConfig.TelegramWss(0), Upstream.None))
        assertEquals(EngineStats(), engine.stats().first())
        coVerify(exactly = 2) { warp.stop() }
    }

    @Test
    fun `established WSS can degrade to healthy WARP without failing the VPN`() = runTest {
        val ws = FakeTelegramWebSockets()
        val engine = engine(ws = ws)
        try {
            assertIs<StartResult.Success>(engine.start(EngineConfig.TelegramWss(0), Upstream.None))
            assertIs<EnginePlugin.ReadyResult.Ready>(engine.awaitReady())
            ws.protocol = null
            assertIs<ProbeResult.Success>(engine.probe())
            ws.protocol = "binary"
            assertIs<ProbeResult.Success>(engine.probe())
        } finally {
            engine.stop()
        }
    }

    @Test
    fun `WSS readiness tries the WARP transport when direct upgrade fails`() = runTest {
        val direct = FakeTelegramWebSockets(protocol = null)
        val fallback = FakeTelegramWebSockets()
        val engine = TelegramWssEngine(
            warp, { port, _ -> TelegramWssProxy(direct, WarpSocksRelay(port), fallback) {} },
            { true },
        )
        try {
            assertIs<StartResult.Success>(engine.start(EngineConfig.TelegramWss(0), Upstream.None))
            assertIs<EnginePlugin.ReadyResult.Ready>(engine.awaitReady())
        } finally {
            engine.stop()
        }
    }

    @Test
    fun `readiness requires routed traffic and a Telegram WSS upgrade`() = runTest {
        val badRoute = engine(routeReady = false)
        try {
            assertIs<StartResult.Success>(badRoute.start(EngineConfig.TelegramWss(0), Upstream.None))
            assertIs<EnginePlugin.ReadyResult.Timeout>(badRoute.awaitReady())
        } finally {
            badRoute.stop()
        }
        val badWss = engine(ws = FakeTelegramWebSockets(protocol = null))
        try {
            assertIs<StartResult.Success>(badWss.start(EngineConfig.TelegramWss(0), Upstream.None))
            assertIs<ProbeResult.Failure>(badWss.probe())
            assertIs<EnginePlugin.ReadyResult.Timeout>(badWss.awaitReady())
        } finally {
            badWss.stop()
        }
    }
}
