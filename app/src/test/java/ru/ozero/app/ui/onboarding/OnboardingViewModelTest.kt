package ru.ozero.app.ui.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.settings.UserFlags
import ru.ozero.app.settings.UserFlagsRepository
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var flags: FakeUserFlags
    private lateinit var bootstrap: FakeBootstrap
    private lateinit var settings: FakeSettingsRepository
    private lateinit var vm: OnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        flags = FakeUserFlags()
        bootstrap = FakeBootstrap()
        settings = FakeSettingsRepository()
        vm = OnboardingViewModel(flags, bootstrap, settings)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is page 0 not completed`() = runTest {
        assertEquals(0, vm.state.value.pageIndex)
        assertFalse(vm.state.value.completed)
    }

    @Test
    fun `onNext advances page index but stops at last page`() = runTest {
        vm.onNext()
        assertEquals(1, vm.state.value.pageIndex)
        vm.onNext()
        assertEquals(2, vm.state.value.pageIndex)
        vm.onNext()
        assertEquals(3, vm.state.value.pageIndex)
        vm.onNext()
        assertEquals(3, vm.state.value.pageIndex, "last page index = TOTAL_PAGES - 1")
    }

    @Test
    fun `TOTAL_PAGES is 4 — language step + 3 info pages`() {
        assertEquals(4, OnboardingViewModel.TOTAL_PAGES)
    }

    @Test
    fun `onSkip marks completed and persists flag`() = runTest {
        vm.onSkip()
        advanceUntilIdle()
        assertTrue(vm.state.value.completed)
        assertTrue(flags.markedCompleted)
        assertFalse(bootstrap.invoked, "skip не должен дёргать bootstrap")
    }

    @Test
    fun `onFinish marks completed persists flag and runs bootstrap`() = runTest {
        vm.onFinish()
        advanceUntilIdle()
        assertTrue(vm.state.value.completed)
        assertTrue(flags.markedCompleted)
        assertTrue(bootstrap.invoked)
    }

    @Test
    fun `onLocaleSelect persists tag in repository`() = runTest {
        vm.onLocaleSelect("zh-CN")
        advanceUntilIdle()
        assertEquals(listOf<String?>("zh-CN"), settings.localeWrites)
    }

    @Test
    fun `onLocaleSelect with null clears tag`() = runTest {
        vm.onLocaleSelect(null)
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), settings.localeWrites)
    }

    private class FakeUserFlags : UserFlagsRepository {
        var markedCompleted = false
        var markedBattery = false
        override val flags: Flow<UserFlags> = flowOf(UserFlags(false, false))
        override suspend fun isBatteryPromptShown(): Boolean = markedBattery
        override suspend fun markBatteryPromptShown() {
            markedBattery = true
        }
        override suspend fun isOnboardingCompleted(): Boolean = markedCompleted
        override suspend fun markOnboardingCompleted() {
            markedCompleted = true
        }
    }

    private class FakeBootstrap : FirstRunBootstrap {
        var invoked = false
        override suspend fun runIfFirstStart() {
            invoked = true
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(SettingsModel.DEFAULT)
        override val settings: Flow<SettingsModel> = state.asStateFlow()
        val localeWrites = mutableListOf<String?>()

        override suspend fun setSplitMode(mode: SplitTunnelMode) = Unit
        override suspend fun setIpv6Enabled(enabled: Boolean) = Unit
        override suspend fun setAutoStart(enabled: Boolean) = Unit
        override suspend fun setManualEngine(engine: EngineId?) = Unit
        override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
        override suspend fun setUrnetworkJwt(jwt: String?) = Unit
        override suspend fun setByedpiWinningArgs(args: String?) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) {
            localeWrites += tag
            state.value = state.value.copy(uiLocaleTag = tag)
        }
    }
}
