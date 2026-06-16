package ru.ozero.app.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
        viewModel = SettingsViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading until repository emits`() = runTest(dispatcher) {
        assertIs<SettingsUiState.Loading>(viewModel.uiState.value)
    }

    @Test
    fun `emits Content with default settings when repository emits default`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        repository.emit(SettingsModel.DEFAULT)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<SettingsUiState.Content>(state)
        assertEquals(SettingsModel.DEFAULT, state.model)
        collector.cancel()
    }

    @Test
    fun `emits Content with new model when repository emits update`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        repository.emit(SettingsModel.DEFAULT)
        advanceUntilIdle()

        val updated = SettingsModel(
            splitMode = SplitTunnelMode.ALLOWLIST,
            ipv6Enabled = true,
            autoStart = true,
            manualEngine = EngineId.BYEDPI,
        )
        repository.emit(updated)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<SettingsUiState.Content>(state)
        assertEquals(updated, state.model)
        collector.cancel()
    }

    @Test
    fun `onSplitModeChange forwards to repository`() = runTest(dispatcher) {
        viewModel.onSplitModeChange(SplitTunnelMode.BYPASS_LAN)
        advanceUntilIdle()

        assertEquals(listOf(SplitTunnelMode.BYPASS_LAN), repository.splitModeUpdates)
    }

    @Test
    fun `onIpv6Toggle forwards to repository`() = runTest(dispatcher) {
        viewModel.onIpv6Toggle(true)
        advanceUntilIdle()
        viewModel.onIpv6Toggle(false)
        advanceUntilIdle()

        assertEquals(listOf(true, false), repository.ipv6Updates)
    }

    @Test
    fun `onAutoStartToggle forwards to repository`() = runTest(dispatcher) {
        viewModel.onAutoStartToggle(true)
        advanceUntilIdle()

        assertEquals(listOf(true), repository.autoStartUpdates)
    }

    @Test
    fun `onTrafficModeSelect forwards to repository`() = runTest(dispatcher) {
        viewModel.onTrafficModeSelect(TrafficMode.PROXY)
        advanceUntilIdle()

        assertEquals(listOf(TrafficMode.PROXY), repository.trafficModeUpdates)
    }

    @Test
    fun `onKillswitchToggle forwards true then false`() = runTest(dispatcher) {
        viewModel.onKillswitchToggle(true)
        advanceUntilIdle()
        viewModel.onKillswitchToggle(false)
        advanceUntilIdle()
        assertEquals(listOf(true, false), repository.killswitchUpdates)
    }

    @Test
    fun `onManualEngineSelect forwards engine and null clears`() = runTest(dispatcher) {
        viewModel.onManualEngineSelect(EngineId.BYEDPI)
        advanceUntilIdle()
        viewModel.onManualEngineSelect(null)
        advanceUntilIdle()
        assertEquals(listOf(EngineId.BYEDPI, null), repository.manualEngineUpdates)
    }

    @Test
    fun `onAppModeSelect persists EXPERT then round-trips back to SIMPLE`() = runTest(dispatcher) {
        viewModel.onAppModeSelect(AppMode.EXPERT)
        advanceUntilIdle()
        viewModel.onAppModeSelect(AppMode.SIMPLE)
        advanceUntilIdle()
        assertEquals(listOf(AppMode.EXPERT, AppMode.SIMPLE), repository.appModeUpdates)
    }

    @Test
    fun `onMoveAutoPriority moves engine up by one position`() = runTest(dispatcher) {
        val current = listOf(EngineId.BYEDPI, EngineId.WARP, EngineId.URNETWORK)
        viewModel.onMoveAutoPriority(current, EngineId.WARP, -1)
        advanceUntilIdle()
        assertEquals(
            listOf(listOf(EngineId.WARP, EngineId.BYEDPI, EngineId.URNETWORK)),
            repository.autoPriorityUpdates,
        )
    }

    @Test
    fun `onMoveAutoPriority moves engine down by one position`() = runTest(dispatcher) {
        val current = listOf(EngineId.BYEDPI, EngineId.WARP, EngineId.URNETWORK)
        viewModel.onMoveAutoPriority(current, EngineId.BYEDPI, 1)
        advanceUntilIdle()
        assertEquals(
            listOf(listOf(EngineId.WARP, EngineId.BYEDPI, EngineId.URNETWORK)),
            repository.autoPriorityUpdates,
        )
    }

    @Test
    fun `onMoveAutoPriority no-op when moving first engine up`() = runTest(dispatcher) {
        val current = listOf(EngineId.BYEDPI, EngineId.WARP)
        viewModel.onMoveAutoPriority(current, EngineId.BYEDPI, -1)
        advanceUntilIdle()
        assertEquals(emptyList(), repository.autoPriorityUpdates)
    }

    @Test
    fun `onMoveAutoPriority no-op when moving last engine down`() = runTest(dispatcher) {
        val current = listOf(EngineId.BYEDPI, EngineId.WARP)
        viewModel.onMoveAutoPriority(current, EngineId.WARP, 1)
        advanceUntilIdle()
        assertEquals(emptyList(), repository.autoPriorityUpdates)
    }

    @Test
    fun `onMoveAutoPriority no-op when engine not in list`() = runTest(dispatcher) {
        val current = listOf(EngineId.BYEDPI, EngineId.WARP)
        viewModel.onMoveAutoPriority(current, EngineId.URNETWORK, -1)
        advanceUntilIdle()
        assertEquals(emptyList(), repository.autoPriorityUpdates)
    }

    @Test
    fun `onAlwaysOnBannerDismissed forwards true to repository`() = runTest(dispatcher) {
        viewModel.onAlwaysOnBannerDismissed()
        advanceUntilIdle()
        assertEquals(listOf(true), repository.alwaysOnBannerDismissedUpdates)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow<SettingsModel?>(null)

        val splitModeUpdates = mutableListOf<SplitTunnelMode>()
        val ipv6Updates = mutableListOf<Boolean>()
        val autoStartUpdates = mutableListOf<Boolean>()
        val trafficModeUpdates = mutableListOf<TrafficMode>()
        val manualEngineUpdates = mutableListOf<EngineId?>()
        val appModeUpdates = mutableListOf<AppMode>()
        val killswitchUpdates = mutableListOf<Boolean>()
        val alwaysOnBannerDismissedUpdates = mutableListOf<Boolean>()
        val autoPriorityUpdates = mutableListOf<List<EngineId>>()

        fun emit(model: SettingsModel) {
            state.value = model
        }

        override val settings: Flow<SettingsModel> = flow {
            state.collect { value -> if (value != null) emit(value) }
        }

        override suspend fun setSplitMode(mode: SplitTunnelMode) {
            splitModeUpdates += mode
        }

        override suspend fun setIpv6Enabled(enabled: Boolean) {
            ipv6Updates += enabled
        }

        override suspend fun setAutoStart(enabled: Boolean) {
            autoStartUpdates += enabled
        }

        override suspend fun setTrafficMode(mode: TrafficMode) {
            trafficModeUpdates += mode
        }

        override suspend fun setManualEngine(engine: EngineId?) {
            manualEngineUpdates += engine
        }
        override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
        override suspend fun setUrnetworkJwt(jwt: String?) = Unit
        override suspend fun setUrnetworkCountryCode(code: String?) = Unit
        override suspend fun setByedpiWinningArgs(args: String?) = Unit
        override suspend fun setByedpiDefaultAccepted(accepted: Boolean) = Unit
        override suspend fun setByedpiUseUiMode(enabled: Boolean) = Unit
        override suspend fun setByedpiUiSettings(settings: ByeDpiUiSettings) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: ru.ozero.enginescore.settings.HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit
        override suspend fun setAppMode(mode: AppMode) {
            appModeUpdates += mode
        }
        override suspend fun setKillswitchEnabled(enabled: Boolean) {
            killswitchUpdates += enabled
        }
        override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) {
            alwaysOnBannerDismissedUpdates += dismissed
        }
        override suspend fun setEngineAutoPriority(priority: List<EngineId>) {
            autoPriorityUpdates += priority
        }
    }
}
