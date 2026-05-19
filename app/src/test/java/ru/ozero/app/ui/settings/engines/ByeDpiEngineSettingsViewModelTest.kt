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
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import kotlin.test.assertFalse
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
    fun `onSave с default args выставляет defaultAccepted=true в repo`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onArgsChange(EngineConfig.ByeDpi().args)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf(true), repo.defaultAcceptedUpdates)
    }

    @Test
    fun `onSave с custom args НЕ выставляет defaultAccepted`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onArgsChange("-Ku -An -d4")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(emptyList<Boolean>(), repo.defaultAcceptedUpdates)
    }

    @Test
    fun `onResetToDefault выставляет defaultAccepted=true`() = runTest(dispatcher) {
        repo.emit(SettingsModel.DEFAULT.copy(byedpiWinningArgs = "-X"))
        advanceUntilIdle()
        vm.onResetToDefault()
        advanceUntilIdle()
        assertEquals(listOf(true), repo.defaultAcceptedUpdates)
    }

    @Test
    fun `Content прокидывает defaultAccepted из модели`() = runTest(dispatcher) {
        repo.emit(SettingsModel.DEFAULT.copy(byedpiDefaultAccepted = true))
        advanceUntilIdle()
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertTrue(state.defaultAccepted)
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

    @Test
    fun `после init dnsText пустой если customDnsServers пустой`() = runTest(dispatcher) {
        advanceUntilIdle()
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("", state.dnsText)
        assertEquals("", state.savedDnsText)
    }

    @Test
    fun `после init dnsText заполнен если customDnsServers не пустой`() = runTest(dispatcher) {
        repo.emit(SettingsModel.DEFAULT.copy(customDnsServers = listOf("8.8.8.8", "1.1.1.1")))
        vm = ByeDpiEngineSettingsViewModel(repo)
        advanceUntilIdle()
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("8.8.8.8, 1.1.1.1", state.dnsText)
        assertEquals("8.8.8.8, 1.1.1.1", state.savedDnsText)
    }

    @Test
    fun `onDnsChange меняет dnsText в state`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onDnsChange("9.9.9.9, 149.112.112.112")
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("9.9.9.9, 149.112.112.112", state.dnsText)
    }

    @Test
    fun `dirty=true если только dnsText изменился`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onDnsChange("1.1.1.1")
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertTrue(state.dirty)
    }

    @Test
    fun `dirty=false если dnsText совпадает с savedDnsText и args не изменён`() = runTest(dispatcher) {
        repo.emit(SettingsModel.DEFAULT.copy(customDnsServers = listOf("8.8.8.8")))
        vm = ByeDpiEngineSettingsViewModel(repo)
        advanceUntilIdle()
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertFalse(state.dirty)
    }

    @Test
    fun `onDnsPreset устанавливает форматированный dnsText`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onDnsPreset(listOf("1.1.1.1", "1.0.0.1"))
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("1.1.1.1, 1.0.0.1", state.dnsText)
    }

    @Test
    fun `onDnsPreset с пустым списком очищает dnsText`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onDnsChange("8.8.8.8")
        vm.onDnsPreset(emptyList())
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("", state.dnsText)
    }

    @Test
    fun `onSave сохраняет DNS серверы разбитые запятой`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onDnsChange("8.8.8.8, 1.1.1.1")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf(listOf("8.8.8.8", "1.1.1.1")), repo.dnsUpdates)
    }

    @Test
    fun `onSave с пустым dnsText сохраняет пустой список`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf(emptyList<String>()), repo.dnsUpdates)
    }

    @Test
    fun `при обновлении модели dnsText не сбрасывается если есть несохранённые изменения`() = runTest(dispatcher) {
        advanceUntilIdle()
        vm.onDnsChange("9.9.9.9")
        repo.emit(SettingsModel.DEFAULT.copy(customDnsServers = listOf("8.8.8.8")))
        advanceUntilIdle()
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("9.9.9.9", state.dnsText)
    }

    @Test
    fun `при обновлении модели dnsText обновляется если изменений нет`() = runTest(dispatcher) {
        advanceUntilIdle()
        repo.emit(SettingsModel.DEFAULT.copy(customDnsServers = listOf("8.8.8.8")))
        advanceUntilIdle()
        val state = vm.uiState.value as ByeDpiSettingsUiState.Content
        assertEquals("8.8.8.8", state.dnsText)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(SettingsModel.DEFAULT)
        override val settings: Flow<SettingsModel> = state.asStateFlow()

        val byedpiUpdates = mutableListOf<String?>()
        val defaultAcceptedUpdates = mutableListOf<Boolean>()
        val dnsUpdates = mutableListOf<List<String>>()

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
        override suspend fun setCustomDnsServers(servers: List<String>) {
            dnsUpdates += servers
            state.value = state.value.copy(customDnsServers = servers)
        }
        override suspend fun setHostsMode(mode: ru.ozero.enginescore.settings.HostsMode) = Unit
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
            defaultAcceptedUpdates += accepted
            state.value = state.value.copy(byedpiDefaultAccepted = accepted)
        }

        override suspend fun setByedpiUseUiMode(enabled: Boolean) {
            state.value = state.value.copy(byedpiUseUiMode = enabled)
        }

        override suspend fun setByedpiUiSettings(settings: ByeDpiUiSettings) {
            state.value = state.value.copy(byedpiUiSettings = settings)
        }
    }
}
