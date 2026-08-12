package ru.ozero.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import ru.ozero.app.ui.components.addPolyline
import ru.ozero.app.ui.components.chartNiceMax
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.ozero.app.R
import ru.ozero.app.ui.components.BottomDock
import ru.ozero.app.ui.components.DockTab
import ru.ozero.app.ui.components.EngineChipsRow
import ru.ozero.app.ui.components.OzeroBackground
import ru.ozero.app.ui.components.OzeroBackgroundState
import ru.ozero.app.ui.components.PowerDisc
import ru.ozero.app.ui.components.PowerDiscState
import ru.ozero.app.ui.icons.OzeroIcons
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.commonnet.CountryFlag
import ru.ozero.commonvpn.BytesFormatter
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.SwitchingTransition
import ru.ozero.commonvpn.TunnelState
import ru.ozero.commonvpn.TunnelStats
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSplitTunnel: () -> Unit = {},
    onOpenEngineParams: (EngineId?) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val stagnant by viewModel.stagnant.collectAsStateWithLifecycle()
    val healthStatus by viewModel.healthStatus.collectAsStateWithLifecycle()
    val appMode by viewModel.appMode.collectAsStateWithLifecycle()
    val manualEngine by viewModel.manualEngine.collectAsStateWithLifecycle()
    val engineAutoPriority by viewModel.engineAutoPriority.collectAsStateWithLifecycle()
    val speedHistory by viewModel.speedHistory.collectAsStateWithLifecycle()
    val urnetworkPeerCount by viewModel.urnetworkPeerCount.collectAsStateWithLifecycle()
    val urnetworkPeerSearchSeconds by viewModel.urnetworkPeerSearchSeconds.collectAsStateWithLifecycle()
    val ipInfo by viewModel.ipInfo.collectAsStateWithLifecycle()
    val killswitchActive by viewModel.killswitchActive.collectAsStateWithLifecycle()
    val switching by viewModel.switching.collectAsStateWithLifecycle()
    val isReconnecting by viewModel.isReconnecting.collectAsStateWithLifecycle()
    val powerState by viewModel.powerDiscState.collectAsStateWithLifecycle()
    val backgroundState = powerState.toBackgroundState()
    val isConnected = state is TunnelState.Connected
    OzeroBackground(state = backgroundState) {
        AnimatedContent(
            targetState = appMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "mode_switch",
        ) { mode ->
            when (mode) {
                AppMode.SIMPLE -> SimpleMainContent(
                    state = SimpleMainState(
                        tunnelState = state,
                        switching = switching,
                        powerState = powerState,
                        isConnected = isConnected,
                        manualEngine = manualEngine,
                        urnetworkPeerCount = urnetworkPeerCount,
                        urnetworkPeerSearchSeconds = urnetworkPeerSearchSeconds,
                        isReconnecting = isReconnecting,
                    ),
                    callbacks = SimpleMainCallbacks(
                        onConnectClick = onConnectClick,
                        onOpenSplitTunnel = onOpenSplitTunnel,
                        onOpenSettings = onOpenSettings,
                    ),
                )
                AppMode.EXPERT -> ExpertMainContent(
                    state = ExpertMainState(
                        tunnelState = state,
                        switching = switching,
                        stats = stats,
                        speedHistory = speedHistory,
                        stagnant = stagnant,
                        healthStatus = healthStatus,
                        powerState = powerState,
                        isConnected = isConnected,
                        manualEngine = manualEngine,
                        engineAutoPriority = engineAutoPriority,
                        urnetworkPeerCount = urnetworkPeerCount,
                        urnetworkPeerSearchSeconds = urnetworkPeerSearchSeconds,
                        ipInfo = ipInfo,
                        killswitchActive = killswitchActive,
                        isReconnecting = isReconnecting,
                    ),
                    callbacks = ExpertMainCallbacks(
                        onConnectClick = onConnectClick,
                        onManualEngineSelect = viewModel::onManualEngineSelect,
                        onRefreshIpInfo = viewModel::refreshIpInfo,
                        onOpenEngineParams = onOpenEngineParams,
                        onOpenSplitTunnel = onOpenSplitTunnel,
                        onOpenSettings = onOpenSettings,
                    ),
                )
            }
        }
    }
}

internal fun isCompactMainLayout(
    width: Dp,
    height: Dp,
    fontScale: Float,
): Boolean =
    width < 360.dp || height < 720.dp || fontScale > 1f

data class SimpleMainState(
    val tunnelState: TunnelState,
    val switching: SwitchingTransition?,
    val powerState: PowerDiscState,
    val isConnected: Boolean,
    val manualEngine: EngineId?,
    val urnetworkPeerCount: Int,
    val urnetworkPeerSearchSeconds: Int,
    val isReconnecting: Boolean = false,
)

data class SimpleMainCallbacks(
    val onConnectClick: () -> Unit,
    val onOpenSplitTunnel: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
internal fun SimpleMainContent(
    state: SimpleMainState,
    callbacks: SimpleMainCallbacks,
) {
    val tunnelState = state.tunnelState
    val switching = state.switching
    val powerState = state.powerState
    val isConnected = state.isConnected
    val manualEngine = state.manualEngine
    val urnetworkPeerCount = state.urnetworkPeerCount
    val urnetworkPeerSearchSeconds = state.urnetworkPeerSearchSeconds
    val isReconnecting = state.isReconnecting
    val onConnectClick = callbacks.onConnectClick
    val onOpenSplitTunnel = callbacks.onOpenSplitTunnel
    val onOpenSettings = callbacks.onOpenSettings
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (isCompactMainLayout(maxWidth, maxHeight, LocalDensity.current.fontScale)) {
            CompactSimpleMainContent(state = state, callbacks = callbacks)
            return@BoxWithConstraints
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = switching to tunnelState,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "status",
            ) { (sw, s) ->
                StatusLabel(s, sw, urnetworkPeerCount, isReconnecting)
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PowerDisc(
                    state = powerState,
                    onClick = onConnectClick,
                    contentDescription = stringResource(
                        if (isConnected) R.string.a11y_disconnect_button else R.string.a11y_connect_button,
                    ),
                )
            }

            val visualConnected = isConnected || switching != null
            if (
                visualConnected &&
                resolveUiSelectedEngine(tunnelState, switching, manualEngine) == EngineId.URNETWORK
            ) {
                UrnetworkPeerBadge(
                    count = urnetworkPeerCount,
                    searchSeconds = urnetworkPeerSearchSeconds,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BottomDock(
                    tabs = simpleDockTabs(),
                    activeTabId = DOCK_TAB_HOME,
                    onTabSelected = { id ->
                        when (id) {
                            DOCK_TAB_SPLIT_TUNNEL -> onOpenSplitTunnel()
                            DOCK_TAB_SETTINGS -> onOpenSettings()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CompactSimpleMainContent(
    state: SimpleMainState,
    callbacks: SimpleMainCallbacks,
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(
                targetState = state.switching to state.tunnelState,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "compact_status",
            ) { (switching, tunnelState) ->
                StatusLabel(
                    state = tunnelState,
                    switching = switching,
                    urnetworkPeerCount = state.urnetworkPeerCount,
                    isReconnecting = state.isReconnecting,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PowerDisc(
                    state = state.powerState,
                    onClick = callbacks.onConnectClick,
                    contentDescription = stringResource(
                        if (state.isConnected) R.string.a11y_disconnect_button else R.string.a11y_connect_button,
                    ),
                    diameterDp = 204,
                )
            }
            if (
                (state.isConnected || state.switching != null) &&
                resolveUiSelectedEngine(
                    state.tunnelState,
                    state.switching,
                    state.manualEngine,
                ) == EngineId.URNETWORK
            ) {
                UrnetworkPeerBadge(
                    count = state.urnetworkPeerCount,
                    searchSeconds = state.urnetworkPeerSearchSeconds,
                )
            }
        }
        BottomDock(
            tabs = simpleDockTabs(),
            activeTabId = DOCK_TAB_HOME,
            onTabSelected = { id ->
                when (id) {
                    DOCK_TAB_SPLIT_TUNNEL -> callbacks.onOpenSplitTunnel()
                    DOCK_TAB_SETTINGS -> callbacks.onOpenSettings()
                }
            },
            adaptiveLabels = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun UrnetworkPeerBadge(count: Int, searchSeconds: Int) {
    when {
        count > 0 -> Text(
            text = stringResource(R.string.urnetwork_peer_count_label, count),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(MainScreenTestTags.URNETWORK_PEER_COUNT),
        )
        searchSeconds >= URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S -> Text(
            text = stringResource(R.string.urnetwork_peer_searching, searchSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.testTag(MainScreenTestTags.URNETWORK_PEER_SEARCHING),
        )
    }
}

private const val URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S: Int = 20

data class ExpertMainState(
    val tunnelState: TunnelState,
    val switching: SwitchingTransition?,
    val stats: TunnelStats?,
    val speedHistory: List<SpeedSample>,
    val stagnant: Boolean,
    val healthStatus: HealthMonitor.Status,
    val powerState: PowerDiscState,
    val isConnected: Boolean,
    val manualEngine: EngineId?,
    val engineAutoPriority: List<EngineId>,
    val urnetworkPeerCount: Int,
    val urnetworkPeerSearchSeconds: Int,
    val ipInfo: IpInfoState,
    val killswitchActive: Boolean,
    val isReconnecting: Boolean = false,
)

data class ExpertMainCallbacks(
    val onConnectClick: () -> Unit,
    val onManualEngineSelect: (EngineId?) -> Unit,
    val onRefreshIpInfo: () -> Unit,
    val onOpenEngineParams: (EngineId?) -> Unit,
    val onOpenSplitTunnel: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
internal fun ExpertMainContent(
    state: ExpertMainState,
    callbacks: ExpertMainCallbacks,
) {
    val tunnelState = state.tunnelState
    val switching = state.switching
    val stats = state.stats
    val speedHistory = state.speedHistory
    val stagnant = state.stagnant
    val healthStatus = state.healthStatus
    val powerState = state.powerState
    val isConnected = state.isConnected
    val manualEngine = state.manualEngine
    val engineAutoPriority = state.engineAutoPriority
    val urnetworkPeerCount = state.urnetworkPeerCount
    val urnetworkPeerSearchSeconds = state.urnetworkPeerSearchSeconds
    val ipInfo = state.ipInfo
    val killswitchActive = state.killswitchActive
    val isReconnecting = state.isReconnecting
    val onConnectClick = callbacks.onConnectClick
    val onManualEngineSelect = callbacks.onManualEngineSelect
    val onRefreshIpInfo = callbacks.onRefreshIpInfo
    val onOpenEngineParams = callbacks.onOpenEngineParams
    val onOpenSplitTunnel = callbacks.onOpenSplitTunnel
    val onOpenSettings = callbacks.onOpenSettings
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        if (isCompactMainLayout(maxWidth, maxHeight, fontScale)) {
            CompactExpertMainContent(state = state, callbacks = callbacks)
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = switching to tunnelState,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "status",
            ) { (sw, s) ->
                StatusLabel(s, sw, urnetworkPeerCount, isReconnecting)
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PowerDisc(
                    state = powerState,
                    onClick = onConnectClick,
                    contentDescription = stringResource(
                        if (isConnected) R.string.a11y_disconnect_button else R.string.a11y_connect_button,
                    ),
                    diameterDp = 256,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val visualConnected = isConnected ||
                    switching != null ||
                    tunnelState is TunnelState.Probing ||
                    tunnelState is TunnelState.Connecting
                ExpertStatusBadges(
                    visualConnected = visualConnected,
                    killswitchActive = killswitchActive,
                    manualEngine = manualEngine,
                    tunnelState = tunnelState,
                    switching = switching,
                    urnetworkPeerCount = urnetworkPeerCount,
                    urnetworkPeerSearchSeconds = urnetworkPeerSearchSeconds,
                    ipInfo = ipInfo,
                    stats = stats,
                    speedHistory = speedHistory,
                    stagnant = stagnant,
                    healthStatus = healthStatus,
                    compactVertical = false,
                    onRefreshIpInfo = onRefreshIpInfo,
                )

                EngineChipsRow(
                    selectedEngine = resolveUiSelectedEngine(
                        tunnelState = tunnelState,
                        switching = switching,
                        manualEngine = manualEngine,
                    ),
                    engineOrder = engineAutoPriority,
                    onSelect = onManualEngineSelect,
                    modifier = Modifier.fillMaxWidth(),
                )

                BottomDock(
                    tabs = expertDockTabs(),
                    activeTabId = DOCK_TAB_HOME,
                    onTabSelected = { id ->
                        when (id) {
                            DOCK_TAB_SERVERS -> onOpenEngineParams(
                                resolveUiSelectedEngine(
                                    tunnelState = tunnelState,
                                    switching = switching,
                                    manualEngine = manualEngine,
                                ),
                            )
                            DOCK_TAB_SPLIT_TUNNEL -> onOpenSplitTunnel()
                            DOCK_TAB_SETTINGS -> onOpenSettings()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CompactExpertMainContent(
    state: ExpertMainState,
    callbacks: ExpertMainCallbacks,
) {
    val visualConnected = state.isConnected ||
        state.switching != null ||
        state.tunnelState is TunnelState.Probing ||
        state.tunnelState is TunnelState.Connecting
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(
                targetState = state.switching to state.tunnelState,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "compact_status",
            ) { (switching, tunnelState) ->
                StatusLabel(
                    tunnelState,
                    switching,
                    state.urnetworkPeerCount,
                    state.isReconnecting,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PowerDisc(
                    state = state.powerState,
                    onClick = callbacks.onConnectClick,
                    contentDescription = stringResource(
                        if (state.isConnected) R.string.a11y_disconnect_button else R.string.a11y_connect_button,
                    ),
                    diameterDp = 204,
                )
            }
            ExpertStatusBadges(
                visualConnected = visualConnected,
                killswitchActive = state.killswitchActive,
                manualEngine = state.manualEngine,
                tunnelState = state.tunnelState,
                switching = state.switching,
                urnetworkPeerCount = state.urnetworkPeerCount,
                urnetworkPeerSearchSeconds = state.urnetworkPeerSearchSeconds,
                ipInfo = state.ipInfo,
                stats = state.stats,
                speedHistory = state.speedHistory,
                stagnant = state.stagnant,
                healthStatus = state.healthStatus,
                compactVertical = true,
                onRefreshIpInfo = callbacks.onRefreshIpInfo,
            )
            EngineChipsRow(
                selectedEngine = resolveUiSelectedEngine(
                    tunnelState = state.tunnelState,
                    switching = state.switching,
                    manualEngine = state.manualEngine,
                ),
                engineOrder = state.engineAutoPriority,
                onSelect = callbacks.onManualEngineSelect,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BottomDock(
            tabs = expertDockTabs(),
            activeTabId = DOCK_TAB_HOME,
            onTabSelected = { id ->
                when (id) {
                    DOCK_TAB_SERVERS -> callbacks.onOpenEngineParams(
                        resolveUiSelectedEngine(
                            tunnelState = state.tunnelState,
                            switching = state.switching,
                            manualEngine = state.manualEngine,
                        ),
                    )
                    DOCK_TAB_SPLIT_TUNNEL -> callbacks.onOpenSplitTunnel()
                    DOCK_TAB_SETTINGS -> callbacks.onOpenSettings()
                }
            },
            adaptiveLabels = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun ExpertStatusBadges(
    visualConnected: Boolean,
    killswitchActive: Boolean,
    manualEngine: EngineId?,
    tunnelState: TunnelState,
    switching: SwitchingTransition?,
    urnetworkPeerCount: Int,
    urnetworkPeerSearchSeconds: Int,
    ipInfo: IpInfoState,
    stats: TunnelStats?,
    speedHistory: List<SpeedSample>,
    stagnant: Boolean,
    healthStatus: HealthMonitor.Status,
    compactVertical: Boolean = false,
    onRefreshIpInfo: () -> Unit,
) {
    if (killswitchActive) {
        Text(
            text = stringResource(R.string.killswitch_active_badge),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(MainScreenTestTags.KILLSWITCH_BADGE),
        )
    }
    if (visualConnected) {
        val urnetworkActive = isUrnetworkVisibleInMain(
            state = tunnelState,
            switching = switching,
            manualEngine = manualEngine,
        )
        IpInfoCard(
            state = ipInfo,
            onRefresh = onRefreshIpInfo,
            urnetworkPeerCount = if (urnetworkActive) urnetworkPeerCount else null,
            urnetworkSearchSeconds = if (urnetworkActive) urnetworkPeerSearchSeconds else null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    TrafficStatsCard(
        stats = stats,
        speedHistory = speedHistory,
        compactVertical = compactVertical,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (visualConnected && stagnant) {
        Text(
            text = stringResource(R.string.main_stagnation_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(MainScreenTestTags.STAGNATION_BADGE),
        )
    }
    if (visualConnected && healthStatus == HealthMonitor.Status.DEGRADED) {
        Column(modifier = Modifier.testTag(MainScreenTestTags.HEALTH_DEGRADED_BADGE)) {
            Text(
                text = stringResource(R.string.main_health_degraded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.main_health_degraded_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun resolveUiSelectedEngine(
    tunnelState: TunnelState,
    switching: SwitchingTransition?,
    manualEngine: EngineId?,
): EngineId? {
    if (switching?.to != null) return switching.to
    return when (tunnelState) {
        is TunnelState.Probing -> tunnelState.engineId ?: manualEngine
        is TunnelState.Connecting -> tunnelState.engineId
        is TunnelState.Connected -> tunnelState.engineId
        is TunnelState.Failed -> tunnelState.engineId
        else -> manualEngine
    }
}

internal fun isUrnetworkVisibleInMain(
    state: TunnelState,
    switching: SwitchingTransition? = null,
    manualEngine: EngineId?,
): Boolean = resolveUiSelectedEngine(state, switching, manualEngine) == EngineId.URNETWORK

@Composable
private fun IpCardPeerValue(count: Int?, searchSeconds: Int?) {
    val (label, tag) = when {
        count != null && count > 0 ->
            count.toString() to MainScreenTestTags.URNETWORK_PEER_COUNT
        searchSeconds != null && searchSeconds >= URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S ->
            stringResource(R.string.engine_status_peers_searching_value, searchSeconds) to
                MainScreenTestTags.URNETWORK_PEER_SEARCHING
        else ->
            stringResource(R.string.engine_status_peers_unavailable) to
                MainScreenTestTags.URNETWORK_PEER_COUNT
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = OzeroPalette.Text,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun simpleDockTabs(): List<DockTab> {
    val labelHome = stringResource(R.string.tab_main)
    val labelSplit = stringResource(R.string.tab_split_tunnel)
    val labelSettings = stringResource(R.string.tab_settings)
    return remember(labelHome, labelSplit, labelSettings) {
        listOf(
            DockTab(DOCK_TAB_HOME, Icons.Filled.Home, labelHome),
            DockTab(DOCK_TAB_SPLIT_TUNNEL, OzeroIcons.CallSplit, labelSplit),
            DockTab(DOCK_TAB_SETTINGS, Icons.Filled.Settings, labelSettings),
        )
    }
}

@Composable
private fun expertDockTabs(): List<DockTab> {
    val labelHome = stringResource(R.string.tab_main)
    val labelServers = stringResource(R.string.tab_servers)
    val labelSplit = stringResource(R.string.tab_tunneling)
    val labelSettings = stringResource(R.string.tab_settings)
    return remember(labelHome, labelServers, labelSplit, labelSettings) {
        listOf(
            DockTab(DOCK_TAB_HOME, Icons.Filled.Home, labelHome),
            DockTab(DOCK_TAB_SERVERS, Icons.Filled.LocationOn, labelServers),
            DockTab(DOCK_TAB_SPLIT_TUNNEL, OzeroIcons.CallSplit, labelSplit),
            DockTab(DOCK_TAB_SETTINGS, Icons.Filled.Settings, labelSettings),
        )
    }
}

private fun PowerDiscState.toBackgroundState(): OzeroBackgroundState = when (this) {
    PowerDiscState.Connected -> OzeroBackgroundState.Connected
    PowerDiscState.Connecting, PowerDiscState.Switching -> OzeroBackgroundState.Connecting
    PowerDiscState.Off -> OzeroBackgroundState.Off
}

@Composable
private fun IpInfoCard(
    state: IpInfoState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    urnetworkPeerCount: Int? = null,
    urnetworkSearchSeconds: Int? = null,
) {
    val showPeerColumn = urnetworkPeerCount != null || urnetworkSearchSeconds != null
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onRefresh() }
            .testTag(MainScreenTestTags.IP_CARD),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.GlassFill),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.ip_card_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text3,
                )
                IpCardExitNodeValue(state = state)
            }
            if (showPeerColumn) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.engine_status_peers_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = OzeroPalette.Text3,
                    )
                    IpCardPeerValue(
                        count = urnetworkPeerCount,
                        searchSeconds = urnetworkSearchSeconds,
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IpCardExitNodeValue(state: IpInfoState) {
    when (state) {
        is IpInfoState.Idle, is IpInfoState.Loading -> Text(
            text = stringResource(R.string.ip_card_loading),
            style = MaterialTheme.typography.titleMedium,
            color = OzeroPalette.Text,
        )
        is IpInfoState.AutoSelected -> Text(
            text = stringResource(R.string.urnetwork_auto_select),
            style = MaterialTheme.typography.titleMedium,
            color = OzeroPalette.Text,
        )
        is IpInfoState.Loaded -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val hasFlag = state.info.countryCode?.length == 2
                if (hasFlag) {
                    Text(
                        text = CountryFlag.emoji(state.info.countryCode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (state.info.ip.isNotBlank()) {
                    Text(
                        text = state.info.ip,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = OzeroPalette.Text,
                    )
                } else {
                    Text(
                        text = state.info.country?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.ip_card_country_unknown),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = OzeroPalette.Text,
                    )
                }
            }
            if (state.info.ip.isNotBlank()) {
                val country = state.info.country
                    ?: stringResource(R.string.ip_card_country_unknown)
                val location = listOfNotNull(state.info.city, country).joinToString(", ")
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text3,
                )
            }
        }
        is IpInfoState.Error -> {
            Text(
                text = stringResource(R.string.ip_card_error, state.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.ip_card_refresh),
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Aqua,
            )
        }
    }
}

@Composable
private fun TrafficStatsCard(
    stats: TunnelStats?,
    speedHistory: List<SpeedSample> = emptyList(),
    compactVertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sessionStartMs = stats?.sessionStartMs ?: 0L
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sessionStartMs) {
        if (sessionStartMs <= 0L) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val sessionMs = if (sessionStartMs > 0L) nowMs - sessionStartMs else 0L
    val rxSpeed = BytesFormatter.humanReadablePerSec(stats?.bpsIn ?: 0.0)
    val txSpeed = BytesFormatter.humanReadablePerSec(stats?.bpsOut ?: 0.0)
    val rxTotal = BytesFormatter.humanReadable(stats?.rxBytes ?: 0L)
    val txTotal = BytesFormatter.humanReadable(stats?.txBytes ?: 0L)
    val uptime = BytesFormatter.durationHms(sessionMs)
    val chartHeight = if (LocalDensity.current.fontScale > 1f) 120.dp else 96.dp

    var selectedTf by remember { mutableStateOf(TimeframeOption.M1) }
    val displayHistory = remember(speedHistory, selectedTf) {
        bucketizeTimeAligned(
            samples = speedHistory,
            windowMs = selectedTf.points * 1_000L,
            bucketCount = selectedTf.buckets,
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MainScreenTestTags.TRAFFIC_STATS),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.GlassFill),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TrafficStatsHeader(
                rxSpeed = rxSpeed,
                txSpeed = txSpeed,
                uptime = uptime,
                compact = compactVertical,
            )
            LiveTrafficChart(
                history = displayHistory,
                selectedTf = selectedTf,
                compact = compactVertical,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .testTag(MainScreenTestTags.TRAFFIC_CHART),
            )
            TrafficStatsFooter(
                selectedTf = selectedTf,
                onTimeframeSelected = { selectedTf = it },
                rxTotal = rxTotal,
                txTotal = txTotal,
                compact = compactVertical,
            )
        }
    }
}

@Composable
private fun TrafficStatsHeader(
    rxSpeed: String,
    txSpeed: String,
    uptime: String,
    compact: Boolean,
) {
    val speed = "↓ $rxSpeed  ↑ $txSpeed"
    if (compact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = speed,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OzeroPalette.Text,
            )
            Text(
                text = stringResource(R.string.stats_uptime, uptime),
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
                modifier = Modifier.align(Alignment.End),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = speed,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OzeroPalette.Text,
            )
            Text(
                text = stringResource(R.string.stats_uptime, uptime),
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )
        }
    }
}

@Composable
private fun TrafficStatsFooter(
    selectedTf: TimeframeOption,
    onTimeframeSelected: (TimeframeOption) -> Unit,
    rxTotal: String,
    txTotal: String,
    compact: Boolean,
) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TimeframeOption.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { timeframe ->
                        TimeframeChip(
                            timeframe = timeframe,
                            selected = selectedTf == timeframe,
                            onClick = { onTimeframeSelected(timeframe) },
                        )
                    }
                }
            }
            TotalsRow(rxTotal = rxTotal, txTotal = txTotal)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeframeOption.entries.forEach { timeframe ->
                TimeframeChip(
                    timeframe = timeframe,
                    selected = selectedTf == timeframe,
                    onClick = { onTimeframeSelected(timeframe) },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TotalsRow(rxTotal = rxTotal, txTotal = txTotal)
        }
    }
}

@Composable
private fun TimeframeChip(
    timeframe: TimeframeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(timeframe.labelRes), style = MaterialTheme.typography.labelSmall) },
    )
}

@Composable
private fun TotalsRow(rxTotal: String, txTotal: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "↓ $rxTotal",
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.Aqua,
        )
        Text(
            text = "↑ $txTotal",
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.Amber,
        )
    }
}

@Composable
private fun LiveTrafficChart(
    history: List<Pair<Float, Float>>,
    selectedTf: TimeframeOption,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorRx = OzeroPalette.Aqua
    val colorTx = OzeroPalette.Amber
    val gridColor = OzeroPalette.Text3.copy(alpha = 0.25f)
    val borderColor = OzeroPalette.Text3.copy(alpha = 0.35f)
    val density = LocalDensity.current
    val largeText = density.fontScale > 1f
    val axisWidth = if (largeText) 56.dp else 44.dp

    val niceMax = remember(history) {
        val raw = if (history.isEmpty()) 0f else history.maxOf { maxOf(it.first, it.second) }
        chartNiceMax(raw)
    }
    val maxLabel = BytesFormatter.humanReadablePerSec(niceMax.toDouble())
    val midLabel = BytesFormatter.humanReadablePerSec((niceMax / 2).toDouble())
    val axisStyle = MaterialTheme.typography.labelSmall.copy(
        color = OzeroPalette.Text3,
        fontSize = 8.sp,
        lineHeight = 9.sp,
    )

    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .width(axisWidth)
                    .fillMaxHeight()
                    .testTag(MainScreenTestTags.TRAFFIC_CHART_Y_AXIS),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(maxLabel, style = axisStyle)
                Text(midLabel, style = axisStyle)
                Text("0", style = axisStyle)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(MainScreenTestTags.TRAFFIC_CHART_PLOT),
            ) {
                val w = size.width
                val h = size.height
                val linePx = with(density) { 1.dp.toPx() }
                val gridDivs = 4
                for (i in 1 until gridDivs) {
                    val y = h * i / gridDivs
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), linePx)
                }
                val timeDivs = 4
                for (i in 1 until timeDivs) {
                    val x = w * i / timeDivs
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), linePx)
                }
                drawRect(borderColor, style = Stroke(width = linePx))
                if (history.size < 2 || niceMax <= 0f) return@Canvas
                val step = w / (history.size - 1)
                val curvePx = with(density) { 2.dp.toPx() }
                val stroke = Stroke(width = curvePx, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
                val pathRx = Path()
                pathRx.addPolyline(history.map { it.first }, step, h, niceMax)
                drawPath(pathRx, colorRx, style = stroke)
                val pathTx = Path()
                pathTx.addPolyline(history.map { it.second }, step, h, niceMax)
                drawPath(pathTx, colorTx, style = stroke)
            }
        }
        val timeLabels = if (compact || largeText) {
            listOf(
                chartTimeAgo(selectedTf.points),
                chartTimeAgo(selectedTf.points / 2),
                "now",
            )
        } else {
            listOf(
                chartTimeAgo(selectedTf.points),
                chartTimeAgo(selectedTf.points * 3 / 4),
                chartTimeAgo(selectedTf.points / 2),
                chartTimeAgo(selectedTf.points / 4),
                "now",
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = axisWidth + 4.dp)
                .testTag(MainScreenTestTags.TRAFFIC_CHART_X_AXIS),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            timeLabels.forEach { label ->
                Text(label, style = axisStyle)
            }
        }
    }
}

private fun chartTimeAgo(seconds: Int): String = when {
    seconds >= 3_600 -> "-${seconds / 3_600}h"
    seconds >= 60 -> "-${seconds / 60}m"
    else -> "-${seconds}s"
}

internal fun pickStatusLabelRes(
    state: TunnelState,
    switching: SwitchingTransition?,
    urnetworkPeerCount: Int,
    isReconnecting: Boolean,
): Int {
    if (switching != null) return R.string.main_status_switching
    if (state is TunnelState.Connected &&
        state.engineId == EngineId.URNETWORK &&
        urnetworkPeerCount == 0
    ) {
        return R.string.main_status_urnetwork_searching
    }
    return when (state) {
        is TunnelState.Idle -> R.string.main_status_disconnected
        is TunnelState.Probing -> probingLabelRes(state.engineId, isReconnecting)
        is TunnelState.Connecting ->
            if (isReconnecting) R.string.main_status_reconnecting else R.string.main_status_connecting
        is TunnelState.Connected -> R.string.main_status_connected
        is TunnelState.Failed ->
            if (isReconnecting) R.string.main_status_reconnecting else R.string.main_status_failed
        is TunnelState.Disconnecting -> R.string.main_status_disconnecting
    }
}

internal fun probingLabelRes(engineId: EngineId?, isReconnecting: Boolean): Int {
    if (isReconnecting) return R.string.main_status_reconnecting
    if (engineId == null) return R.string.main_status_probing
    return when (engineId) {
        EngineId.WARP -> R.string.main_status_probing_warp
        EngineId.BYEDPI -> R.string.main_status_connecting
        EngineId.URNETWORK -> R.string.main_status_probing
        EngineId.MASTERDNS -> R.string.main_status_probing
        EngineId.FPTN -> R.string.main_status_probing
        EngineId.SINGBOX -> R.string.main_status_connecting
    }
}

private fun pickStatusEngine(state: TunnelState, switching: SwitchingTransition?): String? {
    if (switching != null) return switching.from?.name
    return when (state) {
        is TunnelState.Connecting -> state.engineId.name
        is TunnelState.Connected -> state.engineId.name
        is TunnelState.Failed -> state.engineId.name
        else -> null
    }
}

@Composable
private fun StatusLabel(
    state: TunnelState,
    switching: SwitchingTransition? = null,
    urnetworkPeerCount: Int = 0,
    isReconnecting: Boolean = false,
) {
    val labelRes = pickStatusLabelRes(state, switching, urnetworkPeerCount, isReconnecting)
    val engine = pickStatusEngine(state, switching)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = OzeroPalette.Text,
        )
        if (engine != null) {
            Text(
                text = engine,
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )
        }
    }
}

private const val DOCK_TAB_HOME = "home"
private const val DOCK_TAB_SERVERS = "servers"
private const val DOCK_TAB_SPLIT_TUNNEL = "split_tunnel"
private const val DOCK_TAB_SETTINGS = "settings"

private enum class TimeframeOption(val labelRes: Int, val points: Int, val buckets: Int) {
    M1(R.string.chart_tf_1min, 60, 60),
    M5(R.string.chart_tf_5min, 300, 30),
    M30(R.string.chart_tf_30min, 1_800, 30),
    H1(R.string.chart_tf_1h, 3_600, 60),
}
