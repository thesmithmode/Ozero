package ru.ozero.enginetgwss

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
import ru.ozero.enginescore.settings.TrafficMode
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramWssEngineTest {
    @Test
    fun `configuration requires explicit proxy selection and forbids TUN`() {
        val engine = TelegramWssEngine()
        assertEquals(EngineId.TGWSS, engine.id)
        assertTrue(engine.id.proxyOnly)
        assertTrue(engine.capabilities.supportsTcp)
        assertFalse(engine.capabilities.supportsUdp)
        assertFalse(engine.capabilities.supportsUpstreamSocks)
        assertNull(engine.buildManualConfig(SettingsModel()))
        assertNull(engine.buildProxyConfig(null))
        assertNull(engine.buildProxyConfig(SettingsModel(manualEngine = EngineId.TGWSS)))
        assertNull(engine.buildProxyConfig(SettingsModel(trafficMode = TrafficMode.PROXY)))
        assertEquals(
            EngineConfig.TelegramWss(),
            engine.buildProxyConfig(SettingsModel(trafficMode = TrafficMode.PROXY, manualEngine = EngineId.TGWSS)),
        )
    }

    @Test
    fun `start probe stats restart and stop follow local listener lifecycle`() = runTest {
        val webSockets = FakeTelegramWebSockets()
        val engine = TelegramWssEngine { TelegramWssProxy(webSockets) {} }
        try {
            assertIs<ProbeResult.Failure>(engine.probe())
            assertEquals(EngineStats(), engine.stats().first())
            assertIs<EnginePlugin.ReadyResult.Timeout>(engine.awaitReady())
            val first = assertIs<StartResult.Success>(engine.start(EngineConfig.TelegramWss(0), Upstream.None))
            assertTrue(first.socksPort > 0)
            assertIs<ProbeResult.Success>(engine.probe())
            assertIs<EnginePlugin.ReadyResult.Ready>(engine.awaitReady())
            assertTrue(engine.stats().first().connectedSince > 0)
            assertIs<ExitNodeStrategy.Unavailable>(engine.exitNodeStrategy(first.socksPort))
            assertIs<StartResult.Success>(engine.start(EngineConfig.TelegramWss(0), Upstream.None))
        } finally {
            engine.stop()
        }
        engine.stop()
        assertIs<ProbeResult.Failure>(engine.probe())
        assertEquals(EngineStats(), engine.stats().first())
    }

    @Test
    fun `invalid config upstream and occupied port fail without publishing session`() = runTest {
        val engine = TelegramWssEngine()
        assertIs<StartResult.Failure>(engine.start(EngineConfig.Warp, Upstream.None))
        assertIs<StartResult.Failure>(
            engine.start(EngineConfig.TelegramWss(), Upstream.Socks5("127.0.0.1", 1080)),
        )
        val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        ServerSocket(0, 1, loopback).use { occupied ->
            assertIs<StartResult.Failure>(engine.start(EngineConfig.TelegramWss(occupied.localPort), Upstream.None))
        }
        assertEquals(EngineStats(), engine.stats().first())
        engine.stop()
    }
}
