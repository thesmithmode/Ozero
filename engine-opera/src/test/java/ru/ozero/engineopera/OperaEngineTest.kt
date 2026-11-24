package ru.ozero.engineopera

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel

class OperaEngineTest {
    private class FakeProcess : OperaProxyProcess {
        override var alive = true
        override fun stop() {
            alive = false
        }
    }

    private fun engine(
        available: Boolean = true,
        launcher: OperaProxyLauncher = OperaProxyLauncher { _, _ -> FakeProcess() },
    ) = OperaEngine(
        OperaProxyRuntime(launcher, routedProbe = { 12L }, allocatePort = { 12000 }),
        binaryAvailable = { available },
    )

    @Test
    fun `only proxy mode is supported and missing binary skips automatic selection`() {
        val plugin = engine()
        assertEquals(EngineId.OPERA, plugin.id)
        assertTrue(plugin.id.proxyOnly)
        assertTrue(plugin.capabilities.supportsTcp)
        assertFalse(plugin.capabilities.supportsUdp)
        assertFalse(plugin.capabilities.supportsUpstreamSocks)
        assertNull(plugin.buildManualConfig(SettingsModel.DEFAULT))
        assertEquals(EngineConfig.Opera("EU", "fastest"), plugin.buildProxyConfig(null))
        assertEquals(
            EngineConfig.Opera("AM", "first"),
            plugin.buildProxyConfig(SettingsModel.DEFAULT.copy(operaRegion = "AM", operaSelection = "first")),
        )
        assertNull(engine(false).buildProxyConfig(null))
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
    fun `successful startup exposes routed probe and stop clears exit route`() = runTest {
        val process = FakeProcess()
        val plugin = engine(launcher = OperaProxyLauncher { config, port ->
            assertEquals(OperaProxyConfig(OperaRegion.AS, OperaServerSelection.RANDOM), config)
            assertEquals(12000, port)
            process
        })
        assertTrue(plugin.exitNodeStrategy(12000) is ExitNodeStrategy.Unavailable)
        assertEquals(StartResult.Success(12000), plugin.start(EngineConfig.Opera("AS", "random"), Upstream.None))
        assertEquals(ExitNodeStrategy.ViaSocks("127.0.0.1", 12000), plugin.exitNodeStrategy(12000))
        assertTrue(plugin.exitNodeStrategy(0) is ExitNodeStrategy.Unavailable)
        assertEquals(ProbeResult.Success(12), plugin.probe())
        plugin.stop()
        assertFalse(process.alive)
        assertTrue(plugin.exitNodeStrategy(12000) is ExitNodeStrategy.Unavailable)
    }
}
