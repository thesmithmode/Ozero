package ru.ozero.app.ui.strategy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginebyedpi.strategy.ProbeResult
import ru.ozero.enginebyedpi.strategy.SocksProbeClient
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StrategyTestViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeSettingsRepository
    private lateinit var assets: FakeAssetSource
    private lateinit var store: FakeResultsStore
    private lateinit var engine: FakeByeDpiEngine
    private lateinit var probe: FakeProbeClient
    private lateinit var tunnel: TunnelController

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeSettingsRepository()
        assets = FakeAssetSource(
            strategies = STRATEGIES_74,
            sites = listOf("a.example", "b.example"),
        )
        store = FakeResultsStore()
        engine = FakeByeDpiEngine()
        probe = FakeProbeClient(engine)
        tunnel = TunnelController()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): StrategyTestViewModel = StrategyTestViewModel(
        repository = repo,
        assetSource = assets,
        resultsStore = store,
        byeDpiEngine = engine,
        probeFactory = { probe },
        tunnelController = tunnel,
    )

    @Test
    fun `init loads 74 strategies from asset`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        val list = vm.strategies.value
        assertEquals(74, list.size)
        assertEquals(STRATEGIES_74.first(), list.first().command)
        assertTrue(list.all { !it.isCompleted })
    }

    @Test
    fun `onStart guarded if VPN running emits errorMessage`() = runTest {
        tunnel.onProbing()
        tunnel.onConnecting(EngineId.BYEDPI)
        tunnel.onEngineStarted(EngineId.BYEDPI, socksPort = 1080)
        val vm = newVm()
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertNotNull(vm.errorMessage.value)
        assertFalse(vm.isRunning.value)
        assertEquals(0, engine.startCount)
    }

    @Test
    fun `onStart with idle tunnel begins test loop`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        vm.onStart()
        advanceTimeBy(50L)
        runCurrent()
        assertTrue(vm.isRunning.value)
        advanceUntilIdle()
        assertTrue(engine.startCount >= 1)
    }

    @Test
    fun `onApply persists args via repository`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        val cmd = "-Ku -An -d4"
        vm.onApply(cmd)
        advanceUntilIdle()
        assertEquals(listOf<String?>(cmd), repo.byedpiUpdates)
    }

    @Test
    fun `during run individual strategy progress updates currentProgress`() = runTest {
        assets = FakeAssetSource(strategies = listOf("-cmd1", "-cmd2"), sites = listOf("a", "b"))
        probe.successFor = { site, _ -> site == "a" }
        val vm = newVm()
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        val list = vm.strategies.value
        val first = list.first { it.command == "-cmd1" }
        assertEquals(2, first.totalRequests)
        assertEquals(2, first.currentProgress)
        assertEquals(1, first.successCount)
        assertTrue(first.isCompleted)
    }

    @Test
    fun `after all complete strategies sorted by successPercentage descending`() = runTest {
        assets = FakeAssetSource(strategies = listOf("-loser", "-winner"), sites = listOf("s1"))
        probe.successFor = { _, cmd -> cmd.contains("winner") }
        val vm = newVm()
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        val list = vm.strategies.value
        assertEquals("-winner", list[0].command)
        assertEquals(100, list[0].successPercentage)
        assertEquals("-loser", list[1].command)
        assertEquals(0, list[1].successPercentage)
        assertFalse(vm.isRunning.value)
        assertEquals(2, store.savedHistory.last().size)
    }

    @Test
    fun `onStop cancels job and sets isRunning false`() = runTest {
        assets = FakeAssetSource(
            strategies = (1..20).map { "-cmd$it" },
            sites = listOf("a", "b"),
        )
        probe.delayMs = 10_000L
        val vm = newVm()
        advanceUntilIdle()
        vm.onStart()
        advanceTimeBy(50L)
        runCurrent()
        assertTrue(vm.isRunning.value)
        vm.onStop()
        advanceUntilIdle()
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun `onApply mid-test does not stop test loop`() = runTest {
        assets = FakeAssetSource(strategies = listOf("-a", "-b", "-c"), sites = listOf("s1"))
        probe.delayMs = 100L
        val vm = newVm()
        advanceUntilIdle()
        vm.onStart()
        advanceTimeBy(50L)
        runCurrent()
        assertTrue(vm.isRunning.value)
        vm.onApply("-handpicked")
        advanceTimeBy(20L)
        runCurrent()
        assertTrue(vm.isRunning.value)
        assertEquals(listOf<String?>("-handpicked"), repo.byedpiUpdates)
        advanceUntilIdle()
        assertFalse(vm.isRunning.value)
        assertTrue(vm.strategies.value.all { it.isCompleted })
    }

    private companion object {
        val STRATEGIES_74: List<String> = (1..74).map { "-strategy$it" }
    }

    private class FakeAssetSource(
        private val strategies: List<String>,
        private val sites: List<String>,
    ) : StrategyAssetSource {
        override fun loadStrategies(): List<String> = strategies
        override fun loadSites(): List<String> = sites
    }

    private class FakeResultsStore : StrategyResultsStore {
        val savedHistory: MutableList<List<StrategyResult>> = mutableListOf()
        override fun load(): List<StrategyResult> = emptyList()
        override fun save(results: List<StrategyResult>) {
            savedHistory += results
        }
    }

    private class FakeProbeClient(
        private val engine: FakeByeDpiEngine,
    ) : SocksProbeClient {
        var delayMs: Long = 0L
        var successFor: (site: String, cmd: String) -> Boolean = { _, _ -> true }
        override suspend fun probe(site: String): ProbeResult {
            if (delayMs > 0L) delay(delayMs)
            val ok = successFor(site, engine.lastArgs)
            return ProbeResult(site = site, success = ok, durationMs = 1L)
        }
    }

    private class FakeByeDpiEngine : EnginePlugin {
        override val id: EngineId = EngineId.BYEDPI
        override val capabilities: EngineCapabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = false,
            localOnly = true,
            requiresServer = false,
            supportsUpstreamSocks = false,
        )
        private val _stats = MutableStateFlow(EngineStats())
        override fun stats(): Flow<EngineStats> = _stats.asStateFlow()
        var startCount: Int = 0
        var stopCount: Int = 0
        var lastArgs: String = ""

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            require(config is EngineConfig.ByeDpi)
            startCount++
            lastArgs = config.args
            return StartResult.Success(socksPort = config.socksPort)
        }

        override suspend fun stop() {
            stopCount++
        }

        override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
            ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0L)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(SettingsModel.DEFAULT)
        override val settings: Flow<SettingsModel> = state.asStateFlow()
        val byedpiUpdates: MutableList<String?> = mutableListOf()

        fun emit(model: SettingsModel) {
            state.value = model
        }

        override suspend fun setSplitMode(mode: SplitTunnelMode) = Unit
        override suspend fun setIpv6Enabled(enabled: Boolean) = Unit
        override suspend fun setAutoStart(enabled: Boolean) = Unit
        override suspend fun setManualEngine(engine: EngineId?) = Unit
        override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
        override suspend fun setUrnetworkJwt(jwt: String?) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit

        override suspend fun setByedpiWinningArgs(args: String?) {
            byedpiUpdates += args
            state.value = state.value.copy(byedpiWinningArgs = args)
        }
    }
}
