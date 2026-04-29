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
import ru.ozero.app.settings.SettingsModel
import ru.ozero.app.settings.SettingsRepository
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
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
    fun `после init Content имеет default args если savedArgs null`() = runTest {
        advanceUntilIdle()
        val state = vm.uiState.value
        assertIs<ByeDpiSettingsUiState.Content>(state)
        assertEquals(EngineConfig.ByeDpi().args, state.args)
        assertNull(state.savedArgs)
        assertTrue(state.usingDefault)
    }

    @Test
    fun `Content показывает savedArgs если есть`() = runTest {
        repo.emit(SettingsModel.DEFAULT.copy(byedpiWinningArgs = "-Ku -An"))
        advanceUntilIdle()
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("-Ku -An", state.args)
        assertEquals("-Ku -An", state.savedArgs)
    }

    @Test
    fun `onArgsChange меняет args, dirty=true`() = runTest {
        advanceUntilIdle()
        vm.onArgsChange("-Ku -An -d4")
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("-Ku -An -d4", state.args)
        assertTrue(state.dirty)
    }

    @Test
    fun `onSave с custom args сохраняет в repository`() = runTest {
        advanceUntilIdle()
        vm.onArgsChange("-Ku -An -d4")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf("-Ku -An -d4"), repo.byedpiUpdates)
    }

    @Test
    fun `onSave с default args записывает null (clear override)`() = runTest {
        advanceUntilIdle()
        vm.onArgsChange(EngineConfig.ByeDpi().args)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), repo.byedpiUpdates)
    }

    @Test
    fun `onResetToDefault сбрасывает args на default и сохраняет null`() = runTest {
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

        override suspend fun setByedpiWinningArgs(args: String?) {
            byedpiUpdates += args
            state.value = state.value.copy(byedpiWinningArgs = args)
        }
    }
}
