package ru.ozero.app.ui.splittunnel

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
import ru.ozero.coreapi.EngineId
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.entity.AppSplitRule
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SplitTunnelViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var apps: FakeAppListProvider
    private lateinit var dao: FakeAppSplitRuleDao
    private lateinit var settings: FakeSettingsRepository
    private lateinit var viewModel: SplitTunnelViewModel

    private val sample = listOf(
        InstalledApp("com.user.foo", "Foo", isSystem = false),
        InstalledApp("com.user.bar", "Bar", isSystem = false),
        InstalledApp("com.android.system", "System", isSystem = true),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        apps = FakeAppListProvider(sample)
        dao = FakeAppSplitRuleDao()
        settings = FakeSettingsRepository()
        viewModel = SplitTunnelViewModel(apps, dao, settings)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state Loading until apps loaded`() = runTest {
        assertIs<SplitTunnelUiState.Loading>(viewModel.uiState.value)
    }

    @Test
    fun `apps load + dao + settings combine to Content`() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<SplitTunnelUiState.Content>(state)
        assertEquals(SplitTunnelMode.ALL, state.mode)
        assertEquals(3, state.apps.size)
        assertTrue(state.apps.all { !it.included })
    }

    @Test
    fun `existing dao rules mark apps as included`() = runTest {
        dao.emit(listOf(AppSplitRule("com.user.foo", isExcluded = false)))
        advanceUntilIdle()

        val state = viewModel.uiState.value as SplitTunnelUiState.Content
        assertTrue(state.apps.first { it.packageName == "com.user.foo" }.included)
        assertTrue(state.apps.none { it.packageName == "com.user.bar" && it.included })
    }

    @Test
    fun `onModeChange forwards to settings repository`() = runTest {
        advanceUntilIdle()
        viewModel.onModeChange(SplitTunnelMode.ALLOWLIST)
        advanceUntilIdle()

        assertEquals(listOf(SplitTunnelMode.ALLOWLIST), settings.modeUpdates)
    }

    @Test
    fun `onToggleApp adds rule when not included`() = runTest {
        advanceUntilIdle()
        viewModel.onToggleApp("com.user.foo", checked = true)
        advanceUntilIdle()

        assertTrue(dao.upserts.any { it.packageName == "com.user.foo" })
    }

    @Test
    fun `onToggleApp removes rule when unchecked`() = runTest {
        dao.emit(listOf(AppSplitRule("com.user.foo", isExcluded = false)))
        advanceUntilIdle()

        viewModel.onToggleApp("com.user.foo", checked = false)
        advanceUntilIdle()

        assertEquals(listOf("com.user.foo"), dao.deletes)
    }

    @Test
    fun `onQuery filters apps case-insensitive`() = runTest {
        advanceUntilIdle()

        viewModel.onQuery("foo")
        advanceUntilIdle()

        val state = viewModel.uiState.value as SplitTunnelUiState.Content
        assertEquals("foo", state.query)
        assertEquals(listOf("com.user.foo"), state.apps.map { it.packageName })
    }

    private class FakeAppListProvider(val apps: List<InstalledApp>) : AppListProvider {
        override suspend fun loadApps(): List<InstalledApp> = apps
    }

    private class FakeAppSplitRuleDao : AppSplitRuleDao {
        val flow = MutableStateFlow<List<AppSplitRule>>(emptyList())
        val upserts = mutableListOf<AppSplitRule>()
        val deletes = mutableListOf<String>()

        fun emit(rules: List<AppSplitRule>) {
            flow.value = rules
        }

        override suspend fun upsert(rule: AppSplitRule) {
            upserts += rule
            flow.value = flow.value.filterNot { it.packageName == rule.packageName } + rule
        }

        override fun observeAll(): Flow<List<AppSplitRule>> = flow.asStateFlow()

        override suspend fun delete(packageName: String) {
            deletes += packageName
            flow.value = flow.value.filterNot { it.packageName == packageName }
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        val modeUpdates = mutableListOf<SplitTunnelMode>()
        private val state = MutableStateFlow(SettingsModel.DEFAULT)
        override val settings: Flow<SettingsModel> = state.asStateFlow()

        override suspend fun setSplitMode(mode: SplitTunnelMode) {
            modeUpdates += mode
            state.value = state.value.copy(splitMode = mode)
        }

        override suspend fun setIpv6Enabled(enabled: Boolean) = Unit

        override suspend fun setAutoStart(enabled: Boolean) = Unit

        override suspend fun setManualEngine(engine: EngineId?) = Unit
    }
}
