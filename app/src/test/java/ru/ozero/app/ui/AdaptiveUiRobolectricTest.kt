package ru.ozero.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.ozero.app.logging.LogEntry
import ru.ozero.app.logging.LogLevel
import ru.ozero.app.ui.backup.BackupTestTags
import ru.ozero.app.ui.backup.CategoryPickerDialog
import ru.ozero.app.ui.components.BOTTOM_DOCK_TAB_TEST_TAG_PREFIX
import ru.ozero.app.ui.components.POWER_DISC_TEST_TAG
import ru.ozero.app.ui.components.PowerDiscState
import ru.ozero.app.ui.logs.LogsScreenContent
import ru.ozero.app.ui.logs.LogsScreenTestTags
import ru.ozero.app.ui.logs.LogsUiState
import ru.ozero.app.ui.onboarding.OnboardingContent
import ru.ozero.app.ui.servers.ServersScreenContent
import ru.ozero.app.ui.servers.ServersTestTags
import ru.ozero.app.ui.servers.ServersUiState
import ru.ozero.app.ui.settings.engines.UrnetworkSharedTrafficContent
import ru.ozero.app.ui.settings.engines.UrnetworkSharedTrafficTestTags
import ru.ozero.app.ui.settings.engines.UrnetworkSharedTrafficUiState
import ru.ozero.app.ui.stats.ENGINE_ID_ALL
import ru.ozero.app.ui.stats.SessionSort
import ru.ozero.app.ui.stats.TrafficChartData
import ru.ozero.app.ui.stats.TrafficStatsScreenCallbacks
import ru.ozero.app.ui.stats.TrafficStatsScreenContent
import ru.ozero.app.ui.stats.TrafficStatsScreenState
import ru.ozero.app.ui.stats.TrafficStatsTestTags
import ru.ozero.app.ui.stats.TrafficSummary
import ru.ozero.app.ui.stats.TrafficTimeframe
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.app.urnetwork.DayBytes
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.TunnelState
import ru.ozero.corebackup.BackupCategory
import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h700dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveUiRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `expert main keeps traffic and every dock action reachable at large font scale`() {
        val connectCalls = AtomicInteger()
        val selectedEngine = AtomicReference<EngineId?>()
        val openedEngine = AtomicReference<EngineId?>()
        val splitCalls = AtomicInteger()
        val settingsCalls = AtomicInteger()
        composeRule.setContent {
            ScaledViewport {
                ExpertMainContent(
                    state = ExpertMainState(
                        tunnelState = TunnelState.Connected(EngineId.WARP, socksPort = 0),
                        switching = null,
                        stats = null,
                        speedHistory = emptyList(),
                        stagnant = false,
                        healthStatus = HealthMonitor.Status.HEALTHY,
                        powerState = PowerDiscState.Connected,
                        isConnected = true,
                        manualEngine = EngineId.WARP,
                        engineAutoPriority = listOf(EngineId.WARP, EngineId.BYEDPI),
                        urnetworkPeerCount = 0,
                        urnetworkPeerSearchSeconds = 0,
                        ipInfo = IpInfoState.Idle,
                        killswitchActive = false,
                    ),
                    callbacks = ExpertMainCallbacks(
                        onConnectClick = { connectCalls.incrementAndGet() },
                        onManualEngineSelect = selectedEngine::set,
                        onRefreshIpInfo = {},
                        onOpenEngineParams = openedEngine::set,
                        onOpenSplitTunnel = { splitCalls.incrementAndGet() },
                        onOpenSettings = { settingsCalls.incrementAndGet() },
                    ),
                )
            }
        }

        val viewport = composeRule.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(viewport.width - 320f) < 1f)
        assertTrue(abs(viewport.height - 600f) < 1f)
        composeRule.onNodeWithTag(POWER_DISC_TEST_TAG)
            .assertContentDescriptionEquals(
                ApplicationProvider.getApplicationContext<Application>()
                    .getString(R.string.a11y_disconnect_button),
            )
            .performClick()
        composeRule.onNodeWithTag(MainScreenTestTags.ENGINE_CHIPS_ROW).performScrollTo()
        composeRule.onNodeWithTag(MainScreenTestTags.ENGINE_CHIP_PREFIX + EngineId.BYEDPI.name)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "servers").performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "split_tunnel").performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "settings").performClick()
        composeRule.onNodeWithTag(MainScreenTestTags.TRAFFIC_STATS).performScrollTo().assertIsDisplayed()
        val chartHeight = composeRule.onNodeWithTag(MainScreenTestTags.TRAFFIC_CHART)
            .performScrollTo()
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        assertTrue(chartHeight >= 110f)
        assertChartGeometry(
            chartTag = MainScreenTestTags.TRAFFIC_CHART,
            yAxisTag = MainScreenTestTags.TRAFFIC_CHART_Y_AXIS,
            plotTag = MainScreenTestTags.TRAFFIC_CHART_PLOT,
            xAxisTag = MainScreenTestTags.TRAFFIC_CHART_X_AXIS,
        )
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "home").assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "servers").assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "split_tunnel").assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "settings").assertIsDisplayed()
        assertEquals(1, connectCalls.get())
        assertEquals(EngineId.BYEDPI, selectedEngine.get())
        assertEquals(EngineId.WARP, openedEngine.get())
        assertEquals(1, splitCalls.get())
        assertEquals(1, settingsCalls.get())
    }

    @Test
    fun `simple main invokes all compact actions at large font scale`() {
        val connectCalls = AtomicInteger()
        val splitCalls = AtomicInteger()
        val settingsCalls = AtomicInteger()
        composeRule.setContent {
            ScaledViewport {
                SimpleMainContent(
                    state = SimpleMainState(
                        tunnelState = TunnelState.Idle,
                        switching = null,
                        powerState = PowerDiscState.Off,
                        isConnected = false,
                        manualEngine = EngineId.BYEDPI,
                        urnetworkPeerCount = 0,
                        urnetworkPeerSearchSeconds = 0,
                    ),
                    callbacks = SimpleMainCallbacks(
                        onConnectClick = { connectCalls.incrementAndGet() },
                        onOpenSplitTunnel = { splitCalls.incrementAndGet() },
                        onOpenSettings = { settingsCalls.incrementAndGet() },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(POWER_DISC_TEST_TAG)
            .assertContentDescriptionEquals(
                ApplicationProvider.getApplicationContext<Application>()
                    .getString(R.string.a11y_connect_button),
            )
            .performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "split_tunnel").performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "settings").performClick()
        assertEquals(1, connectCalls.get())
        assertEquals(1, splitCalls.get())
        assertEquals(1, settingsCalls.get())
    }

    @Test
    fun `onboarding actions remain reachable at large font scale`() {
        val skipCalls = AtomicInteger()
        val finishCalls = AtomicInteger()
        composeRule.setContent {
            ScaledViewport {
                OnboardingContent(
                    pageIndex = 4,
                    currentLocaleTag = null,
                    currentAppMode = AppMode.SIMPLE,
                    onLocaleSelect = {},
                    onAppModeSelect = {},
                    onNext = {},
                    onSkip = { skipCalls.incrementAndGet() },
                    onFinish = { finishCalls.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithTag("onboarding_skip").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("onboarding_finish").assertIsDisplayed().performClick()
        assertEquals(1, skipCalls.get())
        assertEquals(1, finishCalls.get())
    }

    @Test
    fun `server actions remain reachable at large font scale`() {
        val saveCalls = AtomicInteger()
        val clearCalls = AtomicInteger()
        composeRule.setContent {
            ScaledViewport {
                ServersScreenContent(
                    state = ServersUiState.Content(
                        servers = listOf(server("entry", "RU"), server("exit", "DE")),
                        entryId = "entry",
                        exitId = "exit",
                    ),
                    onBack = {},
                    onEntrySelect = {},
                    onExitSelect = {},
                    onSavePair = { saveCalls.incrementAndGet() },
                    onClearPair = { clearCalls.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithTag(ServersTestTags.SAVE).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(ServersTestTags.CLEAR).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, saveCalls.get())
        assertEquals(1, clearCalls.get())
    }

    @Test
    fun `traffic stats keeps filters and chart usable at large font scale`() {
        val selectedTimeframe = AtomicReference<TrafficTimeframe>()
        val selectedEngine = AtomicReference<String>()
        composeRule.setContent {
            ScaledViewport {
                TrafficStatsScreenContent(
                    state = TrafficStatsScreenState(
                        timeframe = TrafficTimeframe.DAY,
                        engineFilter = emptySet(),
                        availableEngines = listOf(EngineId.WARP.name, EngineId.BYEDPI.name),
                        summary = TrafficSummary(30L, 15L, 2, 2_000L),
                        engineSummaries = emptyList(),
                        chartData = TrafficChartData(
                            buckets = listOf(0L, 3_600_000L),
                            lines = mapOf(
                                ENGINE_ID_ALL to listOf(10L, 20L),
                                EngineId.WARP.name to listOf(5L, 12L),
                            ),
                        ),
                        sessions = emptyList(),
                        sessionsExpanded = false,
                        sessionSort = SessionSort.TIME_DESC,
                    ),
                    callbacks = TrafficStatsScreenCallbacks(
                        onBack = {},
                        onTimeframeSelect = selectedTimeframe::set,
                        onEngineToggle = selectedEngine::set,
                        onEngineClear = {},
                        onSessionsExpandedChange = {},
                        onSessionSortSelect = {},
                        onClearSessions = {},
                        onDeleteSession = {},
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("tf_all").performScrollTo().performClick()
        composeRule.onNodeWithTag("engine_filter_${EngineId.WARP.name}").performScrollTo().performClick()
        val chartHeight = composeRule.onNodeWithTag(TrafficStatsTestTags.CHART)
            .performScrollTo()
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        assertEquals(TrafficTimeframe.ALL, selectedTimeframe.get())
        assertEquals(EngineId.WARP.name, selectedEngine.get())
        assertTrue(chartHeight >= 200f)
        assertChartGeometry(
            chartTag = TrafficStatsTestTags.CHART,
            yAxisTag = TrafficStatsTestTags.CHART_Y_AXIS,
            plotTag = TrafficStatsTestTags.CHART_PLOT,
            xAxisTag = TrafficStatsTestTags.CHART_X_AXIS,
        )
    }

    @Test
    fun `logs keep filtering and clear action usable at large font scale`() {
        val entry = LogEntry(1L, LogLevel.INFO, "Engine", 1, "connected")
        val selectedTag = AtomicReference<String>()
        val clearCalls = AtomicInteger()
        composeRule.setContent {
            ScaledViewport {
                LogsScreenContent(
                    state = LogsUiState(entries = listOf(entry)),
                    onBack = {},
                    onClear = { clearCalls.incrementAndGet() },
                    onCopyAll = { "" },
                    onCopyFiltered = { "" },
                    onCreateFilteredFile = { _, done -> done(null) },
                    onTagFilter = selectedTag::set,
                    onLevelFilter = {},
                )
            }
        }

        composeRule.onNodeWithTag(LogsScreenTestTags.filterChip(entry.tag)).performClick()
        composeRule.onNodeWithTag(LogsScreenTestTags.logRow(entry)).assertIsDisplayed()
        composeRule.onNodeWithTag(LogsScreenTestTags.CLEAR_FOOTER).assertIsDisplayed().performClick()
        assertEquals(entry.tag, selectedTag.get())
        assertEquals(1, clearCalls.get())
    }

    @Test
    fun `backup category dialog keeps last option and actions reachable at large font scale`() {
        val confirmed = AtomicReference<Set<BackupCategory>>()
        val dismissCalls = AtomicInteger()
        composeRule.setContent {
            ScaledViewport {
                CategoryPickerDialog(
                    title = "Backup",
                    available = BackupCategory.ALL,
                    initiallySelected = BackupCategory.ALL,
                    onConfirm = confirmed::set,
                    onDismiss = { dismissCalls.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithTag(
            BackupTestTags.CATEGORY_CHECKBOX_PREFIX + BackupCategory.SPLIT_TUNNEL.name,
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(BackupTestTags.CATEGORY_CONFIRM).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(BackupTestTags.CATEGORY_CANCEL).assertIsDisplayed().performClick()
        assertEquals(BackupCategory.ALL, confirmed.get())
        assertEquals(1, dismissCalls.get())
    }

    @Test
    fun `urnetwork loading is centered and state replacement stays reachable`() {
        val state = mutableStateOf(
            UrnetworkSharedTrafficUiState(
                unpaidBytes = 0L,
                isLoading = true,
                dailyBytes = emptyList(),
            ),
        )
        composeRule.setContent {
            ScaledViewport {
                UrnetworkSharedTrafficContent(state = state.value, onBack = {})
            }
        }

        val loadingBounds = composeRule.onNodeWithTag(UrnetworkSharedTrafficTestTags.LOADING)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val indicatorBounds = composeRule.onNodeWithTag(UrnetworkSharedTrafficTestTags.LOADING_INDICATOR)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val loadingCenterX = (loadingBounds.left + loadingBounds.right) / 2f
        val loadingCenterY = (loadingBounds.top + loadingBounds.bottom) / 2f
        val indicatorCenterX = (indicatorBounds.left + indicatorBounds.right) / 2f
        val indicatorCenterY = (indicatorBounds.top + indicatorBounds.bottom) / 2f
        assertTrue(abs(loadingCenterX - indicatorCenterX) < 1f)
        assertTrue(abs(loadingCenterY - indicatorCenterY) < 1f)

        composeRule.runOnIdle {
            state.value = UrnetworkSharedTrafficUiState(
                unpaidBytes = 1_024L,
                isLoading = false,
                dailyBytes = listOf(DayBytes(LocalDate.of(2026, 8, 12), 512L)),
            )
        }
        composeRule.onNodeWithTag(UrnetworkSharedTrafficTestTags.LOADING).assertDoesNotExist()
        composeRule.onNodeWithTag(UrnetworkSharedTrafficTestTags.CONTENT).assertIsDisplayed()
        composeRule.onNodeWithTag("urnetwork_shared_traffic_chart").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun ScaledViewport(content: @androidx.compose.runtime.Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
            OzeroTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 600.dp)
                        .testTag(VIEWPORT_TAG),
                ) {
                    content()
                }
            }
        }
    }

    private fun assertChartGeometry(
        chartTag: String,
        yAxisTag: String,
        plotTag: String,
        xAxisTag: String,
    ) {
        val chart = composeRule.onNodeWithTag(chartTag).fetchSemanticsNode().boundsInRoot
        val yAxis = composeRule.onNodeWithTag(yAxisTag).fetchSemanticsNode().boundsInRoot
        val plot = composeRule.onNodeWithTag(plotTag).fetchSemanticsNode().boundsInRoot
        val xAxis = composeRule.onNodeWithTag(xAxisTag).fetchSemanticsNode().boundsInRoot
        assertContained(chart, yAxis)
        assertContained(chart, plot)
        assertContained(chart, xAxis)
        assertTrue(plot.width > 0f && plot.height > 0f)
        assertTrue(yAxis.right <= plot.left + 1f)
        assertTrue(plot.bottom <= xAxis.top + 1f)
    }

    private fun assertContained(container: Rect, child: Rect) {
        assertTrue(child.left >= container.left - 1f)
        assertTrue(child.top >= container.top - 1f)
        assertTrue(child.right <= container.right + 1f)
        assertTrue(child.bottom <= container.bottom + 1f)
    }

    private fun server(id: String, country: String): ServerEntity =
        ServerEntity(
            id = id,
            country = country,
            role = id,
            protocol = "vless",
            uri = "vless://$id",
            port = 443,
        )

    private companion object {
        const val VIEWPORT_TAG = "adaptive_ui_viewport"
    }
}
