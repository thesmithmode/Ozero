package ru.ozero.app.ui.splittunnel

import androidx.compose.ui.graphics.ImageBitmap
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.EngineId
import ru.ozero.commonvpn.TunnelController
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
    private lateinit var tunnelController: TunnelController
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
        tunnelController = TunnelController()
        viewModel = SplitTunnelViewModel(apps, dao, settings, tunnelController)
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
    fun `state остаётся Loading пока loadApps не завершён даже после combine pass`() = runTest {
        val gated = GatedAppListProvider()
        val gatedVm = SplitTunnelViewModel(gated, dao, settings, tunnelController)

        advanceUntilIdle()

        assertIs<SplitTunnelUiState.Loading>(
            gatedVm.uiState.value,
            "uiState must remain Loading until loadApps completes — empty apps must NOT " +
                "produce Content. Реальный device эмитит Content(empty) сразу из-за combine " +
                "над MutableStateFlow(emptyList()) — sentinel против этого паттерна.",
        )

        gated.complete(sample)
        advanceUntilIdle()

        val state = assertIs<SplitTunnelUiState.Content>(gatedVm.uiState.value)
        assertEquals(3, state.apps.size)
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

        val rule = dao.upserts.first { it.packageName == "com.user.foo" }
        assertEquals(false, rule.isExcluded, "ALL mode toggle → isExcluded=false (allowlist)")
    }

    @Test
    fun `onToggleApp в BLOCKLIST режиме вставляет isExcluded=true`() = runTest {
        settings.setSplitMode(SplitTunnelMode.BLOCKLIST)
        advanceUntilIdle()

        viewModel.onToggleApp("com.user.bar", checked = true)
        advanceUntilIdle()

        val rule = dao.upserts.first { it.packageName == "com.user.bar" }
        assertEquals(true, rule.isExcluded, "BLOCKLIST mode toggle → isExcluded=true")
    }

    @Test
    fun `onToggleApp в ALLOWLIST режиме вставляет isExcluded=false`() = runTest {
        settings.setSplitMode(SplitTunnelMode.ALLOWLIST)
        advanceUntilIdle()

        viewModel.onToggleApp("com.user.foo", checked = true)
        advanceUntilIdle()

        val rule = dao.upserts.first { it.packageName == "com.user.foo" }
        assertEquals(false, rule.isExcluded, "ALLOWLIST mode toggle → isExcluded=false")
    }

    @Test
    fun `BLOCKLIST mode показывает isExcluded=true как included, isExcluded=false — нет`() = runTest {
        settings.setSplitMode(SplitTunnelMode.BLOCKLIST)
        dao.emit(
            listOf(
                AppSplitRule("com.user.foo", isExcluded = false),
                AppSplitRule("com.user.bar", isExcluded = true),
            ),
        )
        val vm = SplitTunnelViewModel(apps, dao, settings, tunnelController)
        advanceUntilIdle()

        val state = vm.uiState.value as SplitTunnelUiState.Content
        val barRow = state.apps.first { it.packageName == "com.user.bar" }
        val fooRow = state.apps.first { it.packageName == "com.user.foo" }
        assertTrue(barRow.included, "blocklist entry должен быть included в BLOCKLIST mode")
        assertTrue(!fooRow.included, "allowlist entry НЕ должен быть included в BLOCKLIST mode")
    }

    @Test
    fun `ALLOWLIST mode не показывает isExcluded=true как included`() = runTest {
        settings.setSplitMode(SplitTunnelMode.ALLOWLIST)
        dao.emit(
            listOf(
                AppSplitRule("com.user.foo", isExcluded = false),
                AppSplitRule("com.user.bar", isExcluded = true),
            ),
        )
        val vm = SplitTunnelViewModel(apps, dao, settings, tunnelController)
        advanceUntilIdle()

        val state = vm.uiState.value as SplitTunnelUiState.Content
        val fooRow = state.apps.first { it.packageName == "com.user.foo" }
        val barRow = state.apps.first { it.packageName == "com.user.bar" }
        assertTrue(fooRow.included, "allowlist entry included в ALLOWLIST mode")
        assertTrue(!barRow.included, "blocklist entry НЕ included в ALLOWLIST mode")
    }

    @Test
    fun `переключение режимов сохраняет оба списка независимо`() = runTest {
        settings.setSplitMode(SplitTunnelMode.ALLOWLIST)
        dao.emit(
            listOf(
                AppSplitRule("com.user.foo", isExcluded = false),
                AppSplitRule("com.user.bar", isExcluded = true),
            ),
        )
        val vm = SplitTunnelViewModel(apps, dao, settings, tunnelController)
        advanceUntilIdle()

        val allowlistState = vm.uiState.value as SplitTunnelUiState.Content
        assertEquals(1, allowlistState.selectedCount, "ALLOWLIST: только foo (isExcluded=false)")

        settings.setSplitMode(SplitTunnelMode.BLOCKLIST)
        advanceUntilIdle()

        val blocklistState = vm.uiState.value as SplitTunnelUiState.Content
        assertEquals(1, blocklistState.selectedCount, "BLOCKLIST: только bar (isExcluded=true)")
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
    fun `selectedCount reflects included apps`() = runTest {
        dao.emit(
            listOf(
                AppSplitRule("com.user.foo", isExcluded = false),
                AppSplitRule("com.user.bar", isExcluded = false),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as SplitTunnelUiState.Content
        assertEquals(2, state.selectedCount)
    }

    @Test
    fun `selectedCount zero when no rules`() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value as SplitTunnelUiState.Content
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `onClearAll deletes current mode rules`() = runTest {
        dao.emit(
            listOf(
                AppSplitRule("com.user.foo", isExcluded = false),
                AppSplitRule("com.user.bar", isExcluded = false),
            ),
        )
        advanceUntilIdle()

        viewModel.onClearAll()
        advanceUntilIdle()

        assertEquals(listOf("com.user.foo", "com.user.bar").sorted(), dao.deletes.sorted())
    }

    @Test
    fun `onClearAll in BLOCKLIST mode preserves allowlist rules`() = runTest {
        settings.setSplitMode(SplitTunnelMode.BLOCKLIST)
        dao.emit(
            listOf(
                AppSplitRule("com.user.foo", isExcluded = false),
                AppSplitRule("com.user.bar", isExcluded = true),
            ),
        )
        val vm = SplitTunnelViewModel(apps, dao, settings, tunnelController)
        advanceUntilIdle()

        vm.onClearAll()
        advanceUntilIdle()

        assertEquals(listOf("com.user.bar"), dao.deletes)
        assertEquals(listOf(AppSplitRule("com.user.foo", isExcluded = false)), dao.flow.value)
    }

    @Test
    fun `onClearAll noop when no rules`() = runTest {
        advanceUntilIdle()

        viewModel.onClearAll()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), dao.deletes)
    }

    @Test
    fun `onClearAll игнорируется когда tunnel не Idle`() = runTest {
        dao.emit(
            listOf(
                AppSplitRule("com.user.foo", isExcluded = false),
                AppSplitRule("com.user.bar", isExcluded = false),
            ),
        )
        tunnelController.onProbing(EngineId.BYEDPI)
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, socksPort = 1080)
        advanceUntilIdle()

        viewModel.onClearAll()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), dao.deletes, "Connected → clear all игнор")
        assertEquals(2, dao.flow.value.size)
    }

    @Test
    fun `included apps всплывают наверх списка`() = runTest {
        dao.emit(listOf(AppSplitRule("com.user.foo", isExcluded = false)))
        advanceUntilIdle()

        val state = viewModel.uiState.value as SplitTunnelUiState.Content
        assertEquals(
            "com.user.foo",
            state.apps.first().packageName,
            "Включённые приложения обязаны быть сверху — UX порт PORTAL_WG. Got: " +
                state.apps.map { it.packageName },
        )
        assertTrue(state.apps.first().included)
    }

    @Test
    fun `BYPASS_LAN persisted мигрирует в ALL — UI не показывает 4й таб`() = runTest {
        settings.modeUpdates.clear()
        settings.setSplitMode(SplitTunnelMode.BYPASS_LAN)
        val vm = SplitTunnelViewModel(apps, dao, settings, tunnelController)
        advanceUntilIdle()

        val state = vm.uiState.value as SplitTunnelUiState.Content
        assertEquals(SplitTunnelMode.ALL, state.mode, "BYPASS_LAN persisted → ALL в UI")
        assertTrue(
            settings.modeUpdates.contains(SplitTunnelMode.ALL),
            "VM обязана записать ALL в settings когда обнаружила BYPASS_LAN — миграция " +
                "(BYPASS_LAN скрыт из UI tabs). modeUpdates=${settings.modeUpdates}",
        )
    }

    @Test
    fun `onModeChange BYPASS_LAN игнорируется — UI больше не предлагает этот режим`() = runTest {
        advanceUntilIdle()
        settings.modeUpdates.clear()

        viewModel.onModeChange(SplitTunnelMode.BYPASS_LAN)
        advanceUntilIdle()

        assertEquals(
            emptyList<SplitTunnelMode>(),
            settings.modeUpdates,
            "onModeChange(BYPASS_LAN) обязан быть no-op — режим скрыт из UI tabs.",
        )
    }

    @Test
    fun `editable=true когда tunnel Idle`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value as SplitTunnelUiState.Content
        assertTrue(state.editable, "Idle → editable=true")
    }

    @Test
    fun `editable=false когда tunnel Connected`() = runTest {
        tunnelController.onProbing(EngineId.BYEDPI)
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, socksPort = 1080)
        advanceUntilIdle()
        val state = viewModel.uiState.value as SplitTunnelUiState.Content
        assertTrue(!state.editable, "Connected → editable=false (без редактирования при VPN ON)")
    }

    @Test
    fun `onModeChange игнорируется когда tunnel не Idle`() = runTest {
        tunnelController.onProbing(EngineId.BYEDPI)
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, socksPort = 1080)
        advanceUntilIdle()
        settings.modeUpdates.clear()

        viewModel.onModeChange(SplitTunnelMode.ALLOWLIST)
        advanceUntilIdle()

        assertEquals(
            emptyList<SplitTunnelMode>(),
            settings.modeUpdates,
            "onModeChange при Connected — no-op (sentinel против race с Snapshot)",
        )
    }

    @Test
    fun `onToggleApp игнорируется когда tunnel не Idle`() = runTest {
        tunnelController.onProbing(EngineId.BYEDPI)
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, socksPort = 1080)
        advanceUntilIdle()

        viewModel.onToggleApp("com.user.foo", checked = true)
        advanceUntilIdle()

        assertEquals(emptyList<AppSplitRule>(), dao.upserts, "Connected → toggle игнор")
    }

    @Test
    fun `onToggleApp deletes nothing when tunnel not Idle`() = runTest {
        tunnelController.onProbing(EngineId.BYEDPI)
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, socksPort = 1080)
        advanceUntilIdle()

        viewModel.onToggleApp("com.user.foo", checked = false)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), dao.deletes)
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

    @Test
    fun `onQuery blank keeps all apps`() = runTest {
        advanceUntilIdle()

        viewModel.onQuery("")
        advanceUntilIdle()

        val state = viewModel.uiState.value as SplitTunnelUiState.Content
        assertEquals("", state.query)
        assertEquals(3, state.apps.size)
    }

    @Test
    fun `refreshApps keeps content query and selected rules while refresh is in flight`() = runTest {
        val refreshProvider = RefreshGatedAppListProvider(sample)
        val vm = SplitTunnelViewModel(refreshProvider, dao, settings, tunnelController)
        advanceUntilIdle()
        vm.onQuery("foo")
        dao.emit(listOf(AppSplitRule("com.user.foo", isExcluded = false)))
        advanceUntilIdle()

        vm.refreshApps()
        advanceUntilIdle()

        val inFlight = assertIs<SplitTunnelUiState.Content>(vm.uiState.value)
        assertEquals("foo", inFlight.query)
        assertEquals(1, inFlight.selectedCount)
        assertEquals(listOf("com.user.foo"), inFlight.apps.map { it.packageName })

        refreshProvider.completeRefresh(
            listOf(
                InstalledApp("com.user.foo", "Foo Updated", isSystem = false),
                InstalledApp("com.user.baz", "Baz", isSystem = false),
            ),
        )
        advanceUntilIdle()

        val refreshed = assertIs<SplitTunnelUiState.Content>(vm.uiState.value)
        assertEquals("foo", refreshed.query)
        assertEquals(1, refreshed.selectedCount)
        assertTrue(refreshed.apps.single { it.packageName == "com.user.foo" }.included)
    }

    @Test
    fun `onResume triggers refreshApps`() = runTest {
        val refreshProvider = RefreshCountingAppListProvider(sample)
        val vm = SplitTunnelViewModel(refreshProvider, dao, settings, tunnelController)
        advanceUntilIdle()

        vm.onResume()
        advanceUntilIdle()

        assertEquals(1, refreshProvider.refreshCalls)
    }

    @Test
    fun `loadIcon delegates to provider`() = runTest {
        val icon = mockk<ImageBitmap>()
        val provider = IconProvidingAppListProvider(sample, icon)
        val vm = SplitTunnelViewModel(provider, dao, settings, tunnelController)
        advanceUntilIdle()

        assertEquals(icon, vm.loadIcon("com.user.foo"))
    }

    @Test
    fun `loadIcon returns null when provider has no icon`() = runTest {
        advanceUntilIdle()

        assertEquals(null, viewModel.loadIcon("com.user.bar"))
    }

    @Test
    fun `onQuery matches label and package name`() = runTest {
        advanceUntilIdle()

        viewModel.onQuery("sys")
        advanceUntilIdle()

        val byLabel = viewModel.uiState.value as SplitTunnelUiState.Content
        assertEquals(listOf("com.android.system"), byLabel.apps.map { it.packageName })

        viewModel.onQuery("bar")
        advanceUntilIdle()

        val byPackage = viewModel.uiState.value as SplitTunnelUiState.Content
        assertEquals(listOf("com.user.bar"), byPackage.apps.map { it.packageName })
    }

    @Test
    fun `packageChanges triggers refreshApps without resetting current content`() = runTest {
        val refreshProvider = RefreshGatedAppListProvider(sample)
        val vm = SplitTunnelViewModel(refreshProvider, dao, settings, tunnelController)
        advanceUntilIdle()
        vm.onQuery("bar")
        advanceUntilIdle()

        refreshProvider.emitPackageChange()
        advanceUntilIdle()

        assertEquals(1, refreshProvider.refreshCalls)
        val inFlight = assertIs<SplitTunnelUiState.Content>(vm.uiState.value)
        assertEquals("bar", inFlight.query)
        assertEquals(listOf("com.user.bar"), inFlight.apps.map { it.packageName })

        refreshProvider.completeRefresh(
            listOf(
                InstalledApp("com.user.bar", "Bar Updated", isSystem = false),
                InstalledApp("com.user.new", "New", isSystem = false),
            ),
        )
        advanceUntilIdle()

        val refreshed = assertIs<SplitTunnelUiState.Content>(vm.uiState.value)
        assertEquals("bar", refreshed.query)
        assertEquals(listOf("com.user.bar"), refreshed.apps.map { it.packageName })
    }

    @Test
    fun `stale slower refresh result не перетирает более свежий refresh`() = runTest {
        val refreshProvider = MultiRefreshGatedAppListProvider(sample)
        val vm = SplitTunnelViewModel(refreshProvider, dao, settings, tunnelController)
        advanceUntilIdle()

        vm.refreshApps()
        vm.refreshApps()
        advanceUntilIdle()

        refreshProvider.completeRefresh(
            1,
            listOf(InstalledApp("com.user.fresh", "Fresh", isSystem = false)),
        )
        advanceUntilIdle()

        val fresh = assertIs<SplitTunnelUiState.Content>(vm.uiState.value)
        assertEquals(listOf("com.user.fresh"), fresh.apps.map { it.packageName })

        refreshProvider.completeRefresh(
            0,
            listOf(InstalledApp("com.user.stale", "Stale", isSystem = false)),
        )
        advanceUntilIdle()

        val afterStale = assertIs<SplitTunnelUiState.Content>(vm.uiState.value)
        assertEquals(listOf("com.user.fresh"), afterStale.apps.map { it.packageName })
    }

    private class FakeAppListProvider(apps: List<InstalledApp>) : AppListProvider {
        private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        private var currentApps = apps
        var refreshCalls = 0
        override val packageChanges: Flow<Unit> = events.asSharedFlow()
        override suspend fun loadApps(): List<InstalledApp> = currentApps
        override suspend fun refreshApps(): List<InstalledApp> {
            refreshCalls += 1
            return currentApps
        }
        override suspend fun loadIcon(packageName: String): androidx.compose.ui.graphics.ImageBitmap? = null
    }

    private class GatedAppListProvider : AppListProvider {
        private val signal = CompletableDeferred<List<InstalledApp>>()
        override val packageChanges: Flow<Unit> = MutableSharedFlow<Unit>()
        override suspend fun loadApps(): List<InstalledApp> = signal.await()
        override suspend fun refreshApps(): List<InstalledApp> = signal.await()
        override suspend fun loadIcon(packageName: String): androidx.compose.ui.graphics.ImageBitmap? = null
        fun complete(apps: List<InstalledApp>) {
            signal.complete(apps)
        }
    }

    private class RefreshCountingAppListProvider(apps: List<InstalledApp>) : AppListProvider {
        private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        private var currentApps = apps
        var refreshCalls = 0
        override val packageChanges: Flow<Unit> = events.asSharedFlow()
        override suspend fun loadApps(): List<InstalledApp> = currentApps
        override suspend fun refreshApps(): List<InstalledApp> {
            refreshCalls += 1
            return currentApps
        }
        override suspend fun loadIcon(packageName: String): androidx.compose.ui.graphics.ImageBitmap? = null
    }

    private class IconProvidingAppListProvider(
        apps: List<InstalledApp>,
        private val icon: ImageBitmap,
    ) : AppListProvider {
        private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        private val currentApps = apps
        override val packageChanges: Flow<Unit> = events.asSharedFlow()
        override suspend fun loadApps(): List<InstalledApp> = currentApps
        override suspend fun refreshApps(): List<InstalledApp> = currentApps
        override suspend fun loadIcon(packageName: String): androidx.compose.ui.graphics.ImageBitmap? =
            if (packageName == "com.user.foo") icon else null
    }

    private class RefreshGatedAppListProvider(initialApps: List<InstalledApp>) : AppListProvider {
        private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        private var currentApps = initialApps
        private var refreshSignal = CompletableDeferred<List<InstalledApp>>()
        var refreshCalls = 0
        override val packageChanges: Flow<Unit> = events.asSharedFlow()
        override suspend fun loadApps(): List<InstalledApp> = currentApps
        override suspend fun refreshApps(): List<InstalledApp> {
            refreshCalls += 1
            val refreshed = refreshSignal.await()
            currentApps = refreshed
            refreshSignal = CompletableDeferred()
            return refreshed
        }
        override suspend fun loadIcon(packageName: String): androidx.compose.ui.graphics.ImageBitmap? = null
        fun completeRefresh(apps: List<InstalledApp>) {
            refreshSignal.complete(apps)
        }
        fun emitPackageChange() {
            events.tryEmit(Unit)
        }
    }

    private class MultiRefreshGatedAppListProvider(initialApps: List<InstalledApp>) : AppListProvider {
        private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        private var currentApps = initialApps
        private val refreshSignals = mutableListOf<CompletableDeferred<List<InstalledApp>>>()
        override val packageChanges: Flow<Unit> = events.asSharedFlow()
        override suspend fun loadApps(): List<InstalledApp> = currentApps
        override suspend fun refreshApps(): List<InstalledApp> {
            val signal = CompletableDeferred<List<InstalledApp>>()
            refreshSignals += signal
            val refreshed = signal.await()
            currentApps = refreshed
            return refreshed
        }
        override suspend fun loadIcon(packageName: String): androidx.compose.ui.graphics.ImageBitmap? = null
        fun completeRefresh(index: Int, apps: List<InstalledApp>) {
            refreshSignals[index].complete(apps)
        }
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
        override suspend fun setAppMode(mode: ru.ozero.enginescore.settings.AppMode) = Unit
        override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
        override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit
    }
}
