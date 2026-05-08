package ru.ozero.app.ui.settings.engines

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
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ByeDpiEngineSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeSettingsRepository
    private lateinit var vm: ByeDpiEngineSettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeSettingsRepository()
        vm = ByeDpiEngineSettingsViewModel(repo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `после init Content имеет default args если savedArgs null`() = runTest(dispatcher) {
        advanceUntilIdle()
        val state = vm.uiState.value
        assertIs<ByeDpiSettingsUiState.Content>(state)
        assertEquals(EngineConfig.ByeDpi().args, state.args)
        assertNull(state.savedArgs)
        assertTrue(state.usingDefault)
    }

    @Test
    fun `Content показывает savedArgs если есть`() = runTest(dispatcher) {
        repo.emit(SettingsModel.DEFAULT.copy(byedpiWinningArgs = "-Ku -An"))
        advanceUntilIdle()
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("-Ku -An", state.args)
        assertEquals("-Ku -An", state.savedArgs)
    }

    @Test
    fun `onArgsChange меняет args, dirty=true`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onArgsChange("-Ku -An -d4")
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("-Ku -An -d4", state.args)
        assertTrue(state.dirty)
    }

    @Test
    fun `onSave с custom args сохраняет в repository`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onArgsChange("-Ku -An -d4")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf<String?>("-Ku -An -d4"), repo.byedpiUpdates)
    }

    @Test
    fun `onSave с default args записывает null (clear override)`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onArgsChange(EngineConfig.ByeDpi().args)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), repo.byedpiUpdates)
    }

    @Test
    fun `onResetToDefault сбрасывает args на default и сохраняет null`() = runTest(dispatcher) {
        repo.emit(SettingsModel.DEFAULT.copy(byedpiWinningArgs = "-X"))
        advanceUntilIdle()
        vm.onResetToDefault()
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), repo.byedpiUpdates)
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals(EngineConfig.ByeDpi().args, state.args)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(SettingsModel.DEFAULT)
        override val settings: Flow<SettingsModel> = state.asStateFlow()

        val byedpiUpdates = mutableListOf<String?>()

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
        override suspend fun setHostsMode(mode: ru.ozero.enginescore.settings.HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit
        override suspend fun setAppMode(mode: AppMode) = Unit
        override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit

        override suspend fun setByedpiWinningArgs(args: String?) {
            byedpiUpdates += args
            state.value = state.value.copy(byedpiWinningArgs = args)
        }
    }
}
