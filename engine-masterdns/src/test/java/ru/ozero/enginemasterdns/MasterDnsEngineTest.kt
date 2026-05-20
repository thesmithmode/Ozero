package ru.ozero.enginemasterdns

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream

class MasterDnsEngineTest {

    @Test
    fun `id is MASTERDNS`() {
        val engine = makeEngine(service = FakeService())
        assertEquals(EngineId.MASTERDNS, engine.id)
    }

    @Test
    fun `capabilities flags set`() {
        val engine = makeEngine(service = FakeService())
        val c = engine.capabilities
        assertTrue(c.supportsTcp)
        assertFalse(c.supportsUdp)
        assertFalse(c.supportsDoH)
        assertFalse(c.localOnly)
        assertTrue(c.requiresServer)
        assertFalse(c.supportsUpstreamSocks)
    }

    @Test
    fun `start with non-MasterDns config returns Failure`() = runTest {
        val engine = makeEngine(service = FakeService())
        val result = engine.start(EngineConfig.Warp, Upstream.None)
        assertTrue(result is StartResult.Failure) { "got=$result" }
        assertTrue((result as StartResult.Failure).reason.contains("MasterDns"))
    }

    @Test
    fun `start with MasterDns config returns Success with allocated port`() = runTest {
        val service = FakeService(succeedWithPort = 18733)
        val engine = MasterDnsEngine(
            serviceFactory = { service },
            portAllocator = StubAllocator(18733),
        )
        val result = engine.start(masterDnsConfig(), Upstream.None)
        assertTrue(result is StartResult.Success) { "got=$result" }
        assertEquals(18733, (result as StartResult.Success).socksPort)
    }

    @Test
    fun `start with failing service returns Failure`() = runTest {
        val service = FakeService(errorMessage = "boom")
        val engine = MasterDnsEngine(
            serviceFactory = { service },
            portAllocator = StubAllocator(18000),
        )
        val result = engine.start(masterDnsConfig(), Upstream.None)
        assertTrue(result is StartResult.Failure) { "got=$result" }
        assertEquals("boom", (result as StartResult.Failure).reason)
    }

    @Test
    fun `stop delegates to service`() = runTest {
        val service = FakeService()
        val engine = makeEngine(service = service)
        engine.start(masterDnsConfig(), Upstream.None)
        engine.stop()
        assertTrue(service.stopped)
    }

    @Test
    fun `stop without prior start is noop`() = runTest {
        val engine = makeEngine(service = FakeService())
        engine.stop()
    }

    @Test
    fun `probe returns Failure stub`() = runTest {
        val engine = makeEngine(service = FakeService())
        val r = engine.probe()
        assertTrue(r is ProbeResult.Failure)
    }

    @Test
    fun `stats emits default EngineStats`() = runTest {
        val engine = makeEngine(service = FakeService())
        val stats = engine.stats().first()
        assertEquals(0L, stats.bytesIn)
        assertEquals(0L, stats.bytesOut)
        assertEquals(0, stats.activeConnections)
    }

    @Test
    fun `preflight returns MasterDnsPreflight`() {
        val engine = makeEngine(service = FakeService())
        val pf = engine.preflight()
        assertNotNull(pf)
        assertTrue(pf is EnginePreflight)
    }

    @Test
    fun `as EnginePlugin contract`() {
        val plugin: ru.ozero.enginescore.EnginePlugin = makeEngine(service = FakeService())
        assertEquals(EngineId.MASTERDNS, plugin.id)
        assertNotNull(plugin.capabilities)
        assertNotNull(plugin.stats())
    }

    private fun makeEngine(service: MasterDnsClientServiceContract) = MasterDnsEngine(
        serviceFactory = { service },
        portAllocator = StubAllocator(18000),
    )

    private fun masterDnsConfig() = EngineConfig.MasterDns(
        configToml = "DOMAINS = [\"v.x\"]\n",
        resolvers = listOf("8.8.8.8"),
        socksPort = 18000,
    )

    private class StubAllocator(private val value: Int) : MasterDnsPortAllocator() {
        override fun allocate(desired: Int): Int = value
    }

    private class FakeService(
        private val succeedWithPort: Int = 18000,
        private val errorMessage: String? = null,
    ) : MasterDnsClientServiceContract {
        var stopped: Boolean = false
        private val flow = MutableStateFlow<MasterDnsClientState>(MasterDnsClientState.Idle)
        override val state: StateFlow<MasterDnsClientState> = flow.asStateFlow()
        override fun start(runtime: MasterDnsRuntimeConfig) {
            flow.value = MasterDnsClientState.Starting
            flow.value = if (errorMessage != null) {
                MasterDnsClientState.Error(errorMessage)
            } else {
                MasterDnsClientState.Running(succeedWithPort)
            }
        }
        override fun stop() {
            stopped = true
            flow.value = MasterDnsClientState.Idle
        }
    }
}
