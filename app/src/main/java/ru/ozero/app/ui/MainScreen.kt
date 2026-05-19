package ru.ozero.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import ru.ozero.app.ui.components.addSmooth
import ru.ozero.app.ui.components.chartNiceMax
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val speedHistory by viewModel.speedHistory.collectAsStateWithLifecycle()
    val urnetworkPeerCount by viewModel.urnetworkPeerCount.collectAsStateWithLifecycle()
    val urnetworkPeerSearchSeconds by viewModel.urnetworkPeerSearchSeconds.collectAsStateWithLifecycle()
    val ipInfo by viewModel.ipInfo.collectAsStateWithLifecycle()
    val killswitchActive by viewModel.killswitchActive.collectAsStateWithLifecycle()
    val switching by viewModel.switching.collectAsStateWithLifecycle()
    val isReconnecting by viewModel.isReconnecting.collectAsStateWithLifecycle()
    val powerState = computePowerDiscState(state, switching, urnetworkPeerCount)
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
private fun SimpleMainContent(
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        AnimatedContent(targetState = switching to tunnelState, label = "status") { (sw, s) ->
            StatusLabel(s, sw, urnetworkPeerCount, isReconnecting)
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PowerDisc(
                state = powerState,
                onClick = onConnectClick,
                modifier = Modifier.semantics {
                    contentDescription = if (isConnected) "disconnect" else "connect"
                },
            )
        }

        val visualConnected = isConnected || switching != null
        if (visualConnected && manualEngine == EngineId.URNETWORK) {
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
private fun ExpertMainContent(
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(targetState = switching to tunnelState, label = "status") { (sw, s) ->
            StatusLabel(s, sw, urnetworkPeerCount, isReconnecting)
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PowerDisc(
                state = powerState,
                onClick = onConnectClick,
                modifier = Modifier.semantics {
                    contentDescription = if (isConnected) "disconnect" else "connect"
                },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val visualConnected = isConnected || switching != null
            ExpertStatusBadges(
                visualConnected = visualConnected,
                killswitchActive = killswitchActive,
                manualEngine = manualEngine,
                urnetworkPeerCount = urnetworkPeerCount,
                urnetworkPeerSearchSeconds = urnetworkPeerSearchSeconds,
                ipInfo = ipInfo,
                stats = stats,
                speedHistory = speedHistory,
                stagnant = stagnant,
                healthStatus = healthStatus,
                onRefreshIpInfo = onRefreshIpInfo,
            )

            EngineChipsRow(
                selectedEngine = manualEngine,
                onSelect = onManualEngineSelect,
                modifier = Modifier.fillMaxWidth(),
            )

            BottomDock(
                tabs = expertDockTabs(),
                activeTabId = DOCK_TAB_HOME,
                onTabSelected = { id ->
                    when (id) {
                        DOCK_TAB_SERVERS -> onOpenEngineParams(manualEngine)
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

@Composable
@Suppress("LongParameterList")
private fun ExpertStatusBadges(
    visualConnected: Boolean,
    killswitchActive: Boolean,
    manualEngine: EngineId?,
    urnetworkPeerCount: Int,
    urnetworkPeerSearchSeconds: Int,
    ipInfo: IpInfoState,
    stats: TunnelStats?,
    speedHistory: List<SpeedSample>,
    stagnant: Boolean,
    healthStatus: HealthMonitor.Status,
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
        IpInfoCard(
            state = ipInfo,
            onRefresh = onRefreshIpInfo,
            urnetworkPeerCount = if (manualEngine == EngineId.URNETWORK) urnetworkPeerCount else null,
            urnetworkSearchSeconds = if (manualEngine == EngineId.URNETWORK) urnetworkPeerSearchSeconds else null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    TrafficStatsCard(
        stats = stats,
        speedHistory = speedHistory,
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

internal fun isUrnetworkVisibleInMain(state: TunnelState, manualEngine: EngineId?): Boolean {
    if (manualEngine == EngineId.URNETWORK) return true
    return when (state) {
        is TunnelState.Probing -> state.engineId == EngineId.URNETWORK
        is TunnelState.Connecting -> state.engineId == EngineId.URNETWORK
        is TunnelState.Connected -> state.engineId == EngineId.URNETWORK
        is TunnelState.Failed -> state.engineId == EngineId.URNETWORK
        else -> false
    }
}

@Composable
private fun IpCardPeerSuffix(count: Int?, searchSeconds: Int?) {
    when {
        count != null && count > 0 -> Text(
            text = stringResource(R.string.urnetwork_peer_count_label, count),
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.Text3,
            modifier = Modifier.testTag(MainScreenTestTags.URNETWORK_PEER_COUNT),
        )
        searchSeconds != null && searchSeconds >= URNETWORK_PEER_SEARCH_VISIBLE_THRESHOLD_S -> Text(
            text = stringResource(R.string.urnetwork_peer_searching, searchSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.Text3,
            modifier = Modifier.testTag(MainScreenTestTags.URNETWORK_PEER_SEARCHING),
        )
        else -> Unit
    }
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
    val labelSplit = stringResource(R.string.tab_split_tunnel)
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

private fun computePowerDiscState(
    state: TunnelState,
    switching: SwitchingTransition?,
    urnetworkPeerCount: Int,
): PowerDiscState = when {
    switching != null -> PowerDiscState.Switching
    state is TunnelState.Connected && state.engineId == EngineId.URNETWORK && urnetworkPeerCount == 0 ->
        PowerDiscState.Switching
    state is TunnelState.Connected -> PowerDiscState.Connected
    state is TunnelState.Probing || state is TunnelState.Connecting || state is TunnelState.Disconnecting ->
        PowerDiscState.Connecting
    else -> PowerDiscState.Off
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onRefresh() }
            .testTag(MainScreenTestTags.IP_CARD),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.GlassFill),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.ip_card_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text3,
                )
                IpCardPeerSuffix(
                    count = urnetworkPeerCount,
                    searchSeconds = urnetworkSearchSeconds,
                )
            }
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
    }
}

@Composable
private fun TrafficStatsCard(
    stats: TunnelStats?,
    speedHistory: List<SpeedSample> = emptyList(),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "↓ $rxSpeed  ↑ $txSpeed",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = OzeroPalette.Text,
                )
                Text(
                    text = stringResource(R.string.stats_uptime, uptime),
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text3,
                )
            }
            LiveTrafficChart(
                history = displayHistory,
                selectedTf = selectedTf,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeframeOption.entries.forEach { tf ->
                    FilterChip(
                        selected = selectedTf == tf,
                        onClick = { selectedTf = tf },
                        label = { Text(stringResource(tf.labelRes), style = MaterialTheme.typography.labelSmall) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
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
    }
}

@Composable
private fun LiveTrafficChart(
    history: List<Pair<Float, Float>>,
    selectedTf: TimeframeOption,
    modifier: Modifier = Modifier,
) {
    val colorRx = OzeroPalette.Aqua
    val colorTx = OzeroPalette.Amber
    val gridColor = OzeroPalette.Text3.copy(alpha = 0.25f)
    val borderColor = OzeroPalette.Text3.copy(alpha = 0.35f)
    val density = LocalDensity.current

    val niceMax = remember(history) {
        val raw = if (history.isEmpty()) 0f else history.maxOf { maxOf(it.first, it.second) }
        chartNiceMax(raw)
    }
    val maxLabel = BytesFormatter.humanReadablePerSec(niceMax.toDouble())
    val midLabel = BytesFormatter.humanReadablePerSec((niceMax / 2).toDouble())
    val axisStyle = MaterialTheme.typography.labelSmall.copy(
        color = OzeroPalette.Text3,
        fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
    )

    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.width(44.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(maxLabel, style = axisStyle)
                Text(midLabel, style = axisStyle)
                Text("0", style = axisStyle)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                val stroke = Stroke(width = curvePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val pathRx = Path()
                pathRx.addSmooth(history.map { it.first }, step, h, niceMax)
                drawPath(pathRx, colorRx, style = stroke)
                val pathTx = Path()
                pathTx.addSmooth(history.map { it.second }, step, h, niceMax)
                drawPath(pathTx, colorTx, style = stroke)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(chartTimeAgo(selectedTf.points), style = axisStyle)
            Text(chartTimeAgo(selectedTf.points * 3 / 4), style = axisStyle)
            Text(chartTimeAgo(selectedTf.points / 2), style = axisStyle)
            Text(chartTimeAgo(selectedTf.points / 4), style = axisStyle)
            Text("now", style = axisStyle)
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
        EngineId.XRAY,
        EngineId.AMNEZIA,
        EngineId.HYSTERIA2,
        EngineId.NAIVE,
        EngineId.TOR,
        EngineId.FPTN -> R.string.main_status_connecting
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
    val failedReason = if (switching != null) null else (state as? TunnelState.Failed)?.reason
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
        if (failedReason != null && failedReason.isNotBlank()) {
            Text(
                text = failedReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MainScreenTestTags.FAILED_REASON),
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
