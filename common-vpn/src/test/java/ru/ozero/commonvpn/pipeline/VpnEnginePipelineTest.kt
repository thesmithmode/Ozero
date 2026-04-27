package ru.ozero.commonvpn.pipeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.HevTunnelConfig
import ru.ozero.commonvpn.HevTunnelGateway
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineCapabilities
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.EngineStats
import ru.ozero.coreapi.ProbeResult
import ru.ozero.coreapi.StartResult
import ru.ozero.coreorchestrator.Orchestrator
import ru.ozero.coreorchestrator.OrchestratorState
import ru.ozero.coreorchestrator.StrategyEngine
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VpnEnginePipelineTest {

    private val tunFd = 42
    private val socksPort = 1080

    @Test
    fun `start picks engine with successful probe and brings tunnel up`() = runTest {
        val byedpi = FakeEngine(
            id = EngineId.BYEDPI,
            probeResult = ProbeResult.Success(latencyMs = 50),
            startResult = StartResult.Success(socksPort = socksPort),
        )
        val handle = newPipeline(mapOf(EngineId.BYEDPI to byedpi as Engine))

        val result = handle.start(tunFd)

        assertIs<VpnEnginePipeline.Result.Connected>(result)
        assertTrue(byedpi.startCalled, "engine.start должен вызваться")
        val cfg = assertNotNull(handle.tunnelGateway.startedConfig, "hev-tunnel должен подняться")
        assertEquals(tunFd, cfg.tunFd)
        assertEquals(socksPort, cfg.socksPort)
        assertIs<TunnelState.Connected>(handle.tunnelController.state.value)
        assertIs<OrchestratorState.Connected>(handle.orchestrator.state.value)
    }

    @Test
    fun `start does not redispatch Connect when orchestrator already probing`() = runTest {
        val byedpi = FakeEngine(
            id = EngineId.BYEDPI,
            probeResult = ProbeResult.Success(latencyMs = 50),
            startResult = StartResult.Success(socksPort = socksPort),
        )
        val handle = newPipeline(mapOf(EngineId.BYEDPI to byedpi as Engine))
        handle.orchestrator.dispatch(ru.ozero.coreorchestrator.OrchestratorTransition.Connect)

        val result = handle.start(tunFd)

        assertIs<VpnEnginePipeline.Result.Connected>(result)
        assertIs<OrchestratorState.Connected>(handle.orchestrator.state.value)
    }

    @Test
    fun `start falls back to byedpi when all probes failed`() = runTest {
        val byedpi = FakeEngine(
            id = EngineId.BYEDPI,
            probeResult = ProbeResult.Failure("no socks"),
            startResult = StartResult.Success(socksPort = socksPort),
        )
        val handle = newPipeline(mapOf(EngineId.BYEDPI to byedpi as Engine))

        val result = handle.start(tunFd)

        assertIs<VpnEnginePipeline.Result.Connected>(result)
        assertTrue(byedpi.startCalled, "fallback должен вызвать engine.start")
        assertNotNull(handle.tunnelGateway.startedConfig)
    }

    @Test
    fun `start handles probe exception and still falls back to byedpi`() = runTest {
        val byedpi = FakeEngine(
            id = EngineId.BYEDPI,
            startResult = StartResult.Success(socksPort = socksPort),
            probeThrow = IllegalStateException("boom"),
        )
        val handle = newPipeline(mapOf(EngineId.BYEDPI to byedpi as Engine))

        val result = handle.start(tunFd)

        assertIs<VpnEnginePipeline.Result.Connected>(result)
        assertTrue(byedpi.startCalled, "fallback после probe exception должен запустить engine")
    }

    @Test
    fun `start propagates engine failure as ConnectFailed transition`() = runTest {
        val byedpi = FakeEngine(
            id = EngineId.BYEDPI,
            probeResult = ProbeResult.Success(latencyMs = 50),
            startResult = StartResult.Failure(reason = "jni err"),
        )
        val handle = newPipeline(mapOf(EngineId.BYEDPI to byedpi as Engine))

        val result = handle.start(tunFd)

        assertIs<VpnEnginePipeline.Result.EngineFailed>(result)
        assertNull(handle.tunnelGateway.startedConfig, "tunnel не поднимается при engine fail")
        assertIs<OrchestratorState.Failed>(handle.orchestrator.state.value)
    }

    @Test
    fun `start returns TunnelFailed when hev gateway returns nonzero and stops engine`() = runTest {
        val byedpi = FakeEngine(
            id = EngineId.BYEDPI,
            probeResult = ProbeResult.Success(latencyMs = 50),
            startResult = StartResult.Success(socksPort = socksPort),
        )
        val gateway = FakeHevGateway(returnCode = 1)
        val handle = newPipeline(mapOf(EngineId.BYEDPI to byedpi as Engine), gateway)

        val result = handle.start(tunFd)

        assertIs<VpnEnginePipeline.Result.TunnelFailed>(result)
        assertTrue(byedpi.stopCalled, "engine откатили после провала туннеля")
    }

    @Test
    fun `start from Connected state physically stops previous engine before reconnect`() = runTest {
        val byedpi = FakeEngine(
            id = EngineId.BYEDPI,
            probeResult = ProbeResult.Success(latencyMs = 50),
            startResult = StartResult.Success(socksPort = socksPort),
        )
        val handle = newPipeline(mapOf(EngineId.BYEDPI to byedpi as Engine))
        handle.start(tunFd)
        assertIs<OrchestratorState.Connected>(handle.orchestrator.state.value)
        byedpi.stopCalled = false
        handle.tunnelGateway.stopCalled = false

        val result = handle.start(tunFd)

        assertIs<VpnEnginePipeline.Result.Connected>(result)
        assertTrue(byedpi.stopCalled, "previous engine.stop должен вызваться перед reconnect")
        assertTrue(handle.tunnelGateway.stopCalled, "previous tunnel.stop должен вызваться перед reconnect")
    }

    @Test
    fun `stop tears down tunnel and engine and resets controllers`() = runTest {
        val byedpi = FakeEngine(
            id = EngineId.BYEDPI,
            probeResult = ProbeResult.Success(latencyMs = 50),
            startResult = StartResult.Success(socksPort = socksPort),
        )
        val handle = newPipeline(mapOf(EngineId.BYEDPI to byedpi as Engine))

        handle.start(tunFd)
        handle.stop()

        assertTrue(handle.tunnelGateway.stopCalled, "hev-tunnel.stop должен вызваться")
        assertTrue(byedpi.stopCalled, "engine.stop должен вызваться")
        assertEquals(TunnelState.Idle, handle.tunnelController.state.value)
        assertEquals(OrchestratorState.Idle, handle.orchestrator.state.value)
    }

    private fun newPipeline(
        engines: Map<EngineId, Engine>,
        gateway: FakeHevGateway = FakeHevGateway(returnCode = 0),
    ): TestPipelineHandle {
        val orchestrator = Orchestrator()
        val tunnelController = TunnelController()
        val strategy = StrategyEngine(engines = engines)
        val pipeline = VpnEnginePipeline(
            engines = engines,
            strategy = strategy,
            orchestrator = orchestrator,
            tunnelController = tunnelController,
            tunnelGateway = gateway,
        )
        return TestPipelineHandle(pipeline, gateway, tunnelController, orchestrator)
    }

    private class TestPipelineHandle(
        private val pipeline: VpnEnginePipeline,
        val tunnelGateway: FakeHevGateway,
        val tunnelController: TunnelController,
        val orchestrator: Orchestrator,
    ) {
        suspend fun start(tunFd: Int) = pipeline.start(tunFd)
        suspend fun stop() = pipeline.stop()
    }

    private class FakeHevGateway(private val returnCode: Int) : HevTunnelGateway {
        var startedConfig: HevTunnelConfig? = null
        var stopCalled: Boolean = false
        override fun start(config: HevTunnelConfig): Int {
            startedConfig = config
            return returnCode
        }
        override fun stop() {
            stopCalled = true
        }
    }

    private class FakeEngine(
        override val id: EngineId,
        private val probeResult: ProbeResult = ProbeResult.Success(latencyMs = 10),
        private val startResult: StartResult,
        private val probeThrow: Throwable? = null,
    ) : Engine {
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = false,
            localOnly = true,
            requiresServer = false,
        )
        var startCalled: Boolean = false
        var stopCalled: Boolean = false
        private val _stats = MutableStateFlow(EngineStats())
        override suspend fun start(config: EngineConfig): StartResult {
            startCalled = true
            return startResult
        }
        override suspend fun stop() {
            stopCalled = true
        }
        override suspend fun probe(): ProbeResult {
            probeThrow?.let { throw it }
            return probeResult
        }
        override fun stats(): Flow<EngineStats> = _stats.asStateFlow()
    }
}
