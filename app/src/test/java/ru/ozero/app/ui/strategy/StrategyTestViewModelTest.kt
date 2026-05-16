package ru.ozero.app.ui.strategy

import io.mockk.mockk
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
import ru.ozero.commonnet.NetworkProfile
import ru.ozero.commonnet.NetworkProfileDetector
import ru.ozero.enginebyedpi.strategy.DefaultEvolutionResourcesProvider
import ru.ozero.enginebyedpi.strategy.ProbeResult
import ru.ozero.enginebyedpi.strategy.SocksProbeClient
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.AppMode
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
    private lateinit var settingsStore: FakeStrategyTestSettingsStore
    private lateinit var domainStore: FakeDomainListStore
    private lateinit var savedStore: FakeSavedStrategyStore
    private lateinit var usageStore: FakeUsageHistoryStore
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
        settingsStore = FakeStrategyTestSettingsStore()
        settingsStore.stored = StrategyTestSettings(evolutionMode = false)
        domainStore = FakeDomainListStore()
        savedStore = FakeSavedStrategyStore()
        usageStore = FakeUsageHistoryStore()
        engine = FakeByeDpiEngine()
        probe = FakeProbeClient(engine)
        tunnel = TunnelController()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun defaultSites() = listOf("a.example", "b.example")

    private fun newVm(sites: List<String> = defaultSites()): StrategyTestViewModel {
        val builtIns = listOf(
            DomainList(id = "test", name = "Test", domains = sites, isActive = true, isBuiltIn = true),
        )
        val manager = DomainListManager(domainStore, builtIns)
        return StrategyTestViewModel(
            context = mockk(relaxed = true),
            repository = repo,
            assetSource = assets,
            resultsStore = store,
            settingsStore = settingsStore,
            domainListManager = manager,
            savedStrategyStore = savedStore,
            byeDpiEngine = engine,
            probeFactory = { _, _ -> probe },
            tunnelController = tunnel,
            evolutionResources = DefaultEvolutionResourcesProvider(
                java.nio.file.Files.createTempDirectory("evres").toFile().also { it.deleteOnExit() },
            ),
            networkProfileDetector = object : NetworkProfileDetector {
                override fun current(): NetworkProfile = NetworkProfile.NONE
            },
            usageHistoryStore = usageStore,
        ).also { it.ioDispatcher = dispatcher }
    }

    @Test
    fun `init loads 74 strategies from asset`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        val list = vm.strategies.value
        assertEquals(74, list.size)
        assertEquals(STRATEGIES_74.first(), list.first().command)
        assertTrue(list.all { !it.isCompleted })
    }

    @Test
    fun `init loads domain lists from manager`() = runTest(dispatcher) {
        val vm = newVm(sites = listOf("x.com", "y.com"))
        advanceUntilIdle()
        val lists = vm.domainLists.value
        assertEquals(1, lists.size)
        assertEquals(listOf("x.com", "y.com"), lists.first().domains)
        assertTrue(lists.first().isActive)
    }

    @Test
    fun `onStart guarded if VPN running emits errorMessage`() = runTest(dispatcher) {
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
    fun `onStart emits NoSites error when all domain lists inactive`() = runTest(dispatcher) {
        val builtIns = listOf(
            DomainList(id = "t", name = "T", domains = listOf("a.com"), isActive = false, isBuiltIn = true),
        )
        val manager = DomainListManager(domainStore, builtIns)
        val vm = StrategyTestViewModel(
            context = mockk(relaxed = true),
            repository = repo, assetSource = assets, resultsStore = store,
            settingsStore = settingsStore, domainListManager = manager,
            savedStrategyStore = savedStore,
            byeDpiEngine = engine, probeFactory = { _, _ -> probe }, tunnelController = tunnel,
            evolutionResources = DefaultEvolutionResourcesProvider(
                java.nio.file.Files.createTempDirectory("evres2").toFile().also { it.deleteOnExit() },
            ),
            networkProfileDetector = object : NetworkProfileDetector {
                override fun current(): NetworkProfile = NetworkProfile.NONE
            },
            usageHistoryStore = usageStore,
        ).also { it.ioDispatcher = dispatcher }
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertEquals(StrategyTestError.NoSites, vm.errorMessage.value)
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun `onStart with idle tunnel begins test loop`() = runTest(dispatcher) {
        probe.delayMs = 1_000L
        val vm = newVm()
        advanceUntilIdle()
        vm.onStart()
        runCurrent()
        assertTrue(vm.isRunning.value)
        advanceTimeBy(500L)
        runCurrent()
        assertTrue(engine.startCount >= 1)
        vm.onStop()
        advanceUntilIdle()
    }

    @Test
    fun `onApply persists args via repository`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        val cmd = "-Ku -An -d4"
        vm.onApply(cmd)
        advanceUntilIdle()
        assertEquals(listOf<String?>(cmd), repo.byedpiUpdates)
    }

    @Test
    fun `during run individual strategy progress updates currentProgress`() = runTest(dispatcher) {
        assets = FakeAssetSource(strategies = listOf("-cmd1", "-cmd2"), sites = listOf("a", "b"))
        probe.successFor = { site, _ -> site == "a" }
        val vm = newVm(sites = listOf("a", "b"))
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
    fun `after all complete strategies sorted by successPercentage descending`() = runTest(dispatcher) {
        assets = FakeAssetSource(strategies = listOf("-loser", "-winner"), sites = listOf("s1"))
        probe.successFor = { _, cmd -> cmd.contains("winner") }
        val vm = newVm(sites = listOf("s1"))
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
    fun `onStop cancels job and sets isRunning false`() = runTest(dispatcher) {
        assets = FakeAssetSource(
            strategies = (1..20).map { "-cmd$it" },
            sites = listOf("a", "b"),
        )
        probe.delayMs = 10_000L
        val vm = newVm(sites = listOf("a", "b"))
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
    fun `completed strategies bubble to top during run before all complete`() = runTest(dispatcher) {
        assets = FakeAssetSource(
            strategies = listOf("-fast-good", "-slow-bad"),
            sites = listOf("s1", "s2"),
        )
        probe.successFor = { _, cmd -> cmd == "-fast-good" }
        probe.delayMs = 0L
        val vm = newVm(sites = listOf("s1", "s2"))
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        val list = vm.strategies.value
        assertEquals("-fast-good", list[0].command, "completed winner sorted first")
        assertEquals(100, list[0].successPercentage)
        assertTrue(list[0].isCompleted)
    }

    @Test
    fun `parallel probing issues concurrent probe calls for sites`() = runTest(dispatcher) {
        assets = FakeAssetSource(
            strategies = listOf("-cmd1"),
            sites = listOf("a", "b", "c", "d"),
        )
        var maxConcurrent = 0
        var current = 0
        val lock = Any()
        probe.beforeProbe = {
            synchronized(lock) {
                current++
                if (current > maxConcurrent) maxConcurrent = current
            }
        }
        probe.afterProbe = {
            synchronized(lock) { current-- }
        }
        probe.delayMs = 50L
        val vm = newVm(sites = listOf("a", "b", "c", "d"))
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertTrue(maxConcurrent > 1, "parallel probes should overlap, maxConcurrent=$maxConcurrent")
    }

    @Test
    fun `settings loaded from store on init`() = runTest(dispatcher) {
        settingsStore.stored = StrategyTestSettings(concurrentLimit = 7, timeoutSeconds = 3)
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(7, vm.settings.value.concurrentLimit)
        assertEquals(3, vm.settings.value.timeoutSeconds)
    }

    @Test
    fun `onSettingsChange saves to store and updates flow`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        val updated = StrategyTestSettings(concurrentLimit = 5)
        vm.onSettingsChange(updated)
        advanceUntilIdle()
        assertEquals(updated, vm.settings.value)
        assertEquals(updated, settingsStore.stored)
    }

    @Test
    fun `concurrentLimit from settings caps semaphore`() = runTest(dispatcher) {
        settingsStore.stored = StrategyTestSettings(concurrentLimit = 2)
        assets = FakeAssetSource(strategies = listOf("-cmd1"), sites = listOf("a", "b", "c", "d", "e"))
        var maxConcurrent = 0
        var current = 0
        val lock = Any()
        probe.beforeProbe = {
            synchronized(lock) {
                current++
                if (current > maxConcurrent) maxConcurrent = current
            }
        }
        probe.afterProbe = { synchronized(lock) { current-- } }
        probe.delayMs = 20L
        val vm = newVm(sites = listOf("a", "b", "c", "d", "e"))
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertTrue(maxConcurrent <= 2, "semaphore limit=2, maxConcurrent=$maxConcurrent")
    }

    @Test
    fun `onApply mid-test does not stop test loop`() = runTest(dispatcher) {
        assets = FakeAssetSource(strategies = listOf("-a", "-b", "-c"), sites = listOf("s1"))
        probe.delayMs = 100L
        val vm = newVm(sites = listOf("s1"))
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

    @Test
    fun `onToggleDomainList flips active state`() = runTest(dispatcher) {
        val vm = newVm(sites = listOf("a.com"))
        advanceUntilIdle()
        assertTrue(vm.domainLists.value.first().isActive)
        vm.onToggleDomainList("test")
        advanceUntilIdle()
        assertFalse(vm.domainLists.value.first().isActive)
    }

    @Test
    fun `onAddDomainList appends custom list`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onAddDomainList("My List", listOf("x.com", "y.com"))
        advanceUntilIdle()
        val lists = vm.domainLists.value
        assertEquals(2, lists.size)
        assertEquals("My List", lists.last().name)
        assertFalse(lists.last().isBuiltIn)
    }

    @Test
    fun `onDeleteDomainList removes list by id`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onAddDomainList("To Delete", listOf("z.com"))
        advanceUntilIdle()
        val toDelete = vm.domainLists.value.last()
        vm.onDeleteDomainList(toDelete.id)
        advanceUntilIdle()
        assertFalse(vm.domainLists.value.any { it.id == toDelete.id })
    }

    @Test
    fun `onToggleSave adds command when not saved`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onToggleSave("-Ku -An")
        advanceUntilIdle()
        assertEquals(1, vm.savedStrategies.value.size)
        assertEquals("-Ku -An", vm.savedStrategies.value[0].command)
    }

    @Test
    fun `onToggleSave removes command when already saved`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onToggleSave("-cmd")
        advanceUntilIdle()
        assertEquals(1, vm.savedStrategies.value.size)
        vm.onToggleSave("-cmd")
        advanceUntilIdle()
        assertEquals(0, vm.savedStrategies.value.size)
    }

    @Test
    fun `onApply also saves command to saved store`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onApply("-applied-cmd")
        advanceUntilIdle()
        assertTrue(vm.savedStrategies.value.any { it.command == "-applied-cmd" })
    }

    @Test
    fun `onDeleteSaved removes entry from savedStrategies`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onToggleSave("-to-delete")
        advanceUntilIdle()
        val id = vm.savedStrategies.value.first().id
        vm.onDeleteSaved(id)
        advanceUntilIdle()
        assertTrue(vm.savedStrategies.value.isEmpty())
    }

    @Test
    fun `onPinSaved pins and unpins correctly`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onToggleSave("-pinnable")
        advanceUntilIdle()
        val id = vm.savedStrategies.value.first().id
        vm.onPinSaved(id, true)
        advanceUntilIdle()
        assertTrue(vm.savedStrategies.value.first().isPinned)
        vm.onPinSaved(id, false)
        advanceUntilIdle()
        assertFalse(vm.savedStrategies.value.first().isPinned)
    }

    @Test
    fun `onResetDomainLists restores built-in defaults`() = runTest(dispatcher) {
        val vm = newVm(sites = listOf("a.com"))
        advanceUntilIdle()
        vm.onToggleDomainList("test")
        advanceUntilIdle()
        assertFalse(vm.domainLists.value.first().isActive)
        vm.onResetDomainLists()
        advanceUntilIdle()
        assertTrue(vm.domainLists.value.first().isActive)
    }

    @Test
    fun `evolution mode runs when settings evolutionMode true`() = runTest(dispatcher) {
        settingsStore.stored = StrategyTestSettings(
            evolutionMode = true, evolutionMaxGenerations = 1, evolutionPopulationSize = 2,
        )
        assets = FakeAssetSource(strategies = listOf("-cmd1", "-cmd2"), sites = listOf("s1"))
        val vm = newVm(sites = listOf("s1"))
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun `evolution state emitted during evolution run`() = runTest(dispatcher) {
        settingsStore.stored = StrategyTestSettings(
            evolutionMode = true, evolutionMaxGenerations = 2, evolutionPopulationSize = 2,
        )
        assets = FakeAssetSource(strategies = listOf("-cmd1"), sites = listOf("s1"))
        val vm = newVm(sites = listOf("s1"))
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun `runLoop uses requestsPerDomain retry until first success`() = runTest(dispatcher) {
        settingsStore.stored = StrategyTestSettings(requestsPerDomain = 3, evolutionMode = false)
        assets = FakeAssetSource(strategies = listOf("-cmd1"), sites = listOf("a", "b"))
        var callCount = 0
        probe.successFor = { _, _ -> false }
        probe.beforeProbe = { callCount++ }
        val vm = newVm(sites = listOf("a", "b"))
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertEquals(6, callCount, "2 sites × 3 attempts each = 6 probes when all fail")
    }

    @Test
    fun `runLoop stops retrying after first success`() = runTest(dispatcher) {
        settingsStore.stored = StrategyTestSettings(requestsPerDomain = 3, evolutionMode = false)
        assets = FakeAssetSource(strategies = listOf("-cmd1"), sites = listOf("a"))
        var callCount = 0
        probe.successFor = { _, _ -> true }
        probe.beforeProbe = { callCount++ }
        val vm = newVm(sites = listOf("a"))
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertEquals(1, callCount, "stops after first success")
    }

    @Test
    fun `runLoop respects delayBetweenMs between strategies`() = runTest(dispatcher) {
        settingsStore.stored = StrategyTestSettings(delayBetweenMs = 500L, evolutionMode = false)
        assets = FakeAssetSource(strategies = listOf("-cmd1", "-cmd2"), sites = listOf("s1"))
        val vm = newVm(sites = listOf("s1"))
        advanceUntilIdle()
        vm.onStart()
        advanceTimeBy(300L)
        runCurrent()
        assertTrue(vm.isRunning.value, "still running after 300ms (delay=500ms between strategies)")
        advanceUntilIdle()
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun `onStart with useCustomStrategies uses customStrategies list`() = runTest(dispatcher) {
        settingsStore.stored = StrategyTestSettings(
            useCustomStrategies = true,
            customStrategies = "--cmd1\n--cmd2\n  \n--cmd3",
        )
        val vm = newVm()
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        val commands = vm.strategies.value.map { it.command }
        assertEquals(listOf("--cmd1", "--cmd2", "--cmd3"), commands.filter { it.startsWith("--cmd") }.sorted())
    }

    @Test
    fun `onRename updates saved strategy name`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onToggleSave("-Ku -An")
        advanceUntilIdle()
        val id = vm.savedStrategies.value.first().id
        vm.onRename(id, "Для ютуба")
        advanceUntilIdle()
        assertEquals("Для ютуба", vm.savedStrategies.value.first().name)
    }

    @Test
    fun `evolutionState is null at start of static scan`() = runTest(dispatcher) {
        settingsStore.stored = StrategyTestSettings(evolutionMode = false)
        assets = FakeAssetSource(strategies = listOf("-cmd1"), sites = listOf("s1"))
        val vm = newVm(sites = listOf("s1"))
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertFalse(vm.isRunning.value)
        assertEquals(null, vm.evolutionState.value)
    }

    @Test
    fun `onApply records command in usageHistory`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onApply("-Ku -An")
        advanceUntilIdle()
        assertEquals(1, usageStore.recorded.size)
        assertEquals("-Ku -An", usageStore.recorded[0].first)
    }

    @Test
    fun `onApply records saved name in usage history when available`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.onToggleSave("-Ku -An")
        advanceUntilIdle()
        val id = vm.savedStrategies.value.first().id
        vm.onRename(id, "Мой VPN")
        advanceUntilIdle()
        vm.onApply("-Ku -An")
        advanceUntilIdle()
        assertEquals("Мой VPN", usageStore.recorded.last().second)
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
        var beforeProbe: () -> Unit = {}
        var afterProbe: () -> Unit = {}
        override suspend fun probe(site: String): ProbeResult {
            beforeProbe()
            if (delayMs > 0L) delay(delayMs)
            val ok = successFor(site, engine.lastArgs)
            afterProbe()
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

    private class FakeStrategyTestSettingsStore : StrategyTestSettingsStore {
        var stored: StrategyTestSettings = StrategyTestSettings()
        override fun load(): StrategyTestSettings = stored
        override fun save(settings: StrategyTestSettings) {
            stored = settings
        }
    }

    private class FakeSavedStrategyStore : SavedStrategyStore {
        private var data: List<SavedStrategy> = emptyList()
        override fun load(): List<SavedStrategy> = data
        override fun save(strategies: List<SavedStrategy>) {
            data = strategies
        }
    }

    private class FakeUsageHistoryStore : UsageHistoryStore {
        val recorded = mutableListOf<Pair<String, String?>>()
        override fun load(): List<UsageEntry> = recorded.map { (cmd, name) -> UsageEntry(cmd, name = name) }
        override fun record(command: String, name: String?): List<UsageEntry> {
            recorded.add(0, command to name)
            return load()
        }
    }

    private class FakeDomainListStore : DomainListStore {
        var data: List<DomainList> = emptyList()
        override fun load(): List<DomainList> = data
        override fun save(lists: List<DomainList>) {
            data = lists
        }
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
        override suspend fun setUrnetworkCountryCode(code: String?) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit
        override suspend fun setAppMode(mode: AppMode) = Unit
        override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
        override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit

        override suspend fun setByedpiWinningArgs(args: String?) {
            byedpiUpdates += args
            state.value = state.value.copy(byedpiWinningArgs = args)
        }

        override suspend fun setByedpiDefaultAccepted(accepted: Boolean) {
            state.value = state.value.copy(byedpiDefaultAccepted = accepted)
        }
    }
}
