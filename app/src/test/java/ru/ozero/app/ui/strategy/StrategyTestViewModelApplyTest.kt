package ru.ozero.app.ui.strategy

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.commonnet.NetworkProfile
import ru.ozero.commonnet.NetworkProfileDetector
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginebyedpi.ByeDpiEngine
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
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StrategyTestViewModelApplyTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeSettingsRepository
    private lateinit var usageStore: FakeUsageHistoryStore

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeSettingsRepository()
        usageStore = FakeUsageHistoryStore()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onApply persists args and switches ByeDPI to CMD mode`() = runTest(dispatcher) {
        repo.emit(SettingsModel.DEFAULT.copy(byedpiUseUiMode = true))
        val vm = newVm()
        advanceUntilIdle()
        val command = "-Y -Ar -s5"

        vm.onApply(command)
        advanceUntilIdle()

        assertEquals(listOf(command), repo.byedpiUpdates)
        assertEquals(listOf(false), repo.byedpiUseUiModeUpdates)
        assertFalse(repo.current().byedpiUseUiMode)
        assertEquals(command, repo.current().byedpiWinningArgs)
        assertEquals(command, ByeDpiEngine().buildManualConfig(repo.current()).args)
    }

    @Test
    fun `onApply does not record history when CMD mode switch fails`() = runTest(dispatcher) {
        repo.failUseUiModeUpdate = true
        val vm = newVm()
        advanceUntilIdle()

        vm.onApply("-Y -Ar -s5")
        advanceUntilIdle()

        assertTrue(usageStore.recorded.isEmpty())
    }

    private fun newVm(): StrategyTestViewModel {
        val builtIns = listOf(
            DomainList(id = "test", name = "Test", domains = listOf("a.example"), isActive = true, isBuiltIn = true),
        )
        return StrategyTestViewModel(
            context = mockk(relaxed = true),
            repository = repo,
            assetSource = FakeAssetSource(),
            resultsStore = FakeResultsStore(),
            settingsStore = FakeStrategyTestSettingsStore(),
            domainListManager = DomainListManager(FakeDomainListStore(), builtIns),
            savedStrategyStore = FakeSavedStrategyStore(),
            byeDpiEngine = FakeByeDpiEngine(),
            probeFactory = { _, _ -> FakeProbeClient() },
            tunnelController = TunnelController(),
            evolutionResources = DefaultEvolutionResourcesProvider(
                java.nio.file.Files.createTempDirectory("apply-evres").toFile().also { it.deleteOnExit() },
            ),
            networkProfileDetector = object : NetworkProfileDetector {
                override fun current(): NetworkProfile = NetworkProfile.NONE
            },
            usageHistoryStore = usageStore,
        ).also { it.ioDispatcher = dispatcher }
    }

    private class FakeAssetSource : StrategyAssetSource {
        override fun loadStrategies(): List<String> = listOf("-strategy")
        override fun loadSites(): List<String> = listOf("a.example")
    }

    private class FakeResultsStore : StrategyResultsStore {
        override fun load(): List<StrategyResult> = emptyList()
        override fun save(results: List<StrategyResult>) = Unit
    }

    private class FakeStrategyTestSettingsStore : StrategyTestSettingsStore {
        override fun load(): StrategyTestSettings = StrategyTestSettings(evolutionMode = false)
        override fun save(settings: StrategyTestSettings) = Unit
    }

    private class FakeSavedStrategyStore : SavedStrategyStore {
        override fun load(): List<SavedStrategy> = emptyList()
        override fun save(strategies: List<SavedStrategy>) = Unit
    }

    private class FakeUsageHistoryStore : UsageHistoryStore {
        val recorded = mutableListOf<Pair<String, String?>>()
        override fun load(): List<UsageEntry> = recorded.map { (command, name) -> UsageEntry(command, name = name) }
        override fun record(command: String, name: String?): List<UsageEntry> {
            recorded.add(command to name)
            return load()
        }
    }

    private class FakeDomainListStore : DomainListStore {
        override fun load(): List<DomainList> = emptyList()
        override fun save(lists: List<DomainList>) = Unit
    }

    private class FakeProbeClient : SocksProbeClient {
        override suspend fun probe(site: String): ProbeResult =
            ProbeResult(site = site, success = true, durationMs = 1L)
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
        private val stats = MutableStateFlow(EngineStats())
        override fun stats(): Flow<EngineStats> = stats.asStateFlow()
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(socksPort = 49_152)
        override suspend fun stop() = Unit
        override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
            ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0L)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(SettingsModel.DEFAULT)
        override val settings: Flow<SettingsModel> = state.asStateFlow()
        val byedpiUpdates: MutableList<String?> = mutableListOf()
        val byedpiUseUiModeUpdates: MutableList<Boolean> = mutableListOf()
        var failUseUiModeUpdate: Boolean = false

        fun emit(model: SettingsModel) {
            state.value = model
        }

        fun current(): SettingsModel = state.value

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

        override suspend fun setByedpiUseUiMode(enabled: Boolean) {
            if (failUseUiModeUpdate) error("failed to switch ByeDPI mode")
            byedpiUseUiModeUpdates += enabled
            state.value = state.value.copy(byedpiUseUiMode = enabled)
        }

        override suspend fun setByedpiUiSettings(settings: ByeDpiUiSettings) {
            state.value = state.value.copy(byedpiUiSettings = settings)
        }
    }
}
