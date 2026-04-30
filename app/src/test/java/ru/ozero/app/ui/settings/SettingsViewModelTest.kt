package ru.ozero.app.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.EngineId
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
    fun `initial state is Loading until repository emits`() = runTest {
        assertIs<SettingsUiState.Loading>(viewModel.uiState.value)
    }

    @Test
    fun `emits Content with default settings when repository emits default`() = runTest {
        repository.emit(SettingsModel.DEFAULT)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<SettingsUiState.Content>(state)
        assertEquals(SettingsModel.DEFAULT, state.model)
    }

    @Test
    fun `emits Content with new model when repository emits update`() = runTest {
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
    }

    @Test
    fun `onSplitModeChange forwards to repository`() = runTest {
        viewModel.onSplitModeChange(SplitTunnelMode.BYPASS_LAN)
        advanceUntilIdle()

        assertEquals(listOf(SplitTunnelMode.BYPASS_LAN), repository.splitModeUpdates)
    }

    @Test
    fun `onIpv6Toggle forwards to repository`() = runTest {
        viewModel.onIpv6Toggle(true)
        advanceUntilIdle()
        viewModel.onIpv6Toggle(false)
        advanceUntilIdle()

        assertEquals(listOf(true, false), repository.ipv6Updates)
    }

    @Test
    fun `onAutoStartToggle forwards to repository`() = runTest {
        viewModel.onAutoStartToggle(true)
        advanceUntilIdle()

        assertEquals(listOf(true), repository.autoStartUpdates)
    }

    @Test
    fun `onManualEngineSelect forwards engine and null clears`() = runTest {
        viewModel.onManualEngineSelect(EngineId.BYEDPI)
        advanceUntilIdle()
        viewModel.onManualEngineSelect(null)
        advanceUntilIdle()
        assertEquals(listOf(EngineId.BYEDPI, null), repository.manualEngineUpdates)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow<SettingsModel?>(null)

        val splitModeUpdates = mutableListOf<SplitTunnelMode>()
        val ipv6Updates = mutableListOf<Boolean>()
        val autoStartUpdates = mutableListOf<Boolean>()
        val manualEngineUpdates = mutableListOf<EngineId?>()

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

        override suspend fun setManualEngine(engine: EngineId?) {
            manualEngineUpdates += engine
        }
        override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
        override suspend fun setUrnetworkJwt(jwt: String?) = Unit
        override suspend fun setByedpiWinningArgs(args: String?) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: ru.ozero.enginescore.settings.HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
    }
}
