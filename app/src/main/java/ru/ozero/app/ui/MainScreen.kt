package ru.ozero.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.commonvpn.BytesFormatter
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.TunnelState
import ru.ozero.commonvpn.TunnelStats
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onOpenSettings: () -> Unit,
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

    val powerState = state.toPowerDiscState()
    val backgroundState = state.toBackgroundState()
    val isConnected = state is TunnelState.Connected

    OzeroBackground(state = backgroundState) {
        when (appMode) {
            AppMode.SIMPLE -> SimpleMainContent(
                tunnelState = state,
                powerState = powerState,
                isConnected = isConnected,
                manualEngine = manualEngine,
                urnetworkPeerCount = urnetworkPeerCount,
                urnetworkPeerSearchSeconds = urnetworkPeerSearchSeconds,
                onConnectClick = onConnectClick,
                onOpenEngineParams = onOpenEngineParams,
                onOpenSettings = onOpenSettings,
            )
            AppMode.EXPERT -> ExpertMainContent(
                tunnelState = state,
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
                onConnectClick = onConnectClick,
                onManualEngineSelect = viewModel::onManualEngineSelect,
                onRefreshIpInfo = viewModel::refreshIpInfo,
                onOpenEngineParams = onOpenEngineParams,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SimpleMainContent(
    tunnelState: TunnelState,
    powerState: PowerDiscState,
    isConnected: Boolean,
    manualEngine: EngineId?,
    urnetworkPeerCount: Int,
    urnetworkPeerSearchSeconds: Int,
    onConnectClick: () -> Unit,
    onOpenEngineParams: (EngineId?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        AnimatedContent(targetState = tunnelState, label = "status") { s -> StatusLabel(s) }

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

        if (isConnected && manualEngine == EngineId.URNETWORK) {
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
                tabs = commonDockTabs(),
                activeTabId = DOCK_TAB_HOME,
                onTabSelected = { id ->
                    when (id) {
                        DOCK_TAB_SERVERS -> onOpenEngineParams(manualEngine)
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
    if (count > 0) {
        Text(
            text = stringResource(R.string.urnetwork_peer_count_label, count),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(MainScreenTestTags.URNETWORK_PEER_COUNT),
        )
    } else {
        Text(
            text = stringResource(R.string.urnetwork_peer_searching, searchSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.testTag(MainScreenTestTags.URNETWORK_PEER_SEARCHING),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun ExpertMainContent(
    tunnelState: TunnelState,
    stats: TunnelStats?,
    speedHistory: List<Pair<Float, Float>>,
    stagnant: Boolean,
    healthStatus: HealthMonitor.Status,
    powerState: PowerDiscState,
    isConnected: Boolean,
    manualEngine: EngineId?,
    urnetworkPeerCount: Int,
    urnetworkPeerSearchSeconds: Int,
    ipInfo: IpInfoState,
    killswitchActive: Boolean,
    onConnectClick: () -> Unit,
    onManualEngineSelect: (EngineId?) -> Unit,
    onRefreshIpInfo: () -> Unit,
    onOpenEngineParams: (EngineId?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(targetState = tunnelState, label = "status") { s -> StatusLabel(s) }

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
            if (killswitchActive) {
                Text(
                    text = stringResource(R.string.killswitch_active_badge),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(MainScreenTestTags.KILLSWITCH_BADGE),
                )
            }
            if (isConnected && manualEngine == EngineId.URNETWORK) {
                UrnetworkPeerBadge(
                    count = urnetworkPeerCount,
                    searchSeconds = urnetworkPeerSearchSeconds,
                )
            }
            if (isConnected) {
                IpInfoCard(
                    state = ipInfo,
                    onRefresh = onRefreshIpInfo,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (isConnected && stats != null) {
                TrafficStatsCard(
                    stats = stats,
                    speedHistory = speedHistory,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (stagnant) {
                    Text(
                        text = stringResource(R.string.main_stagnation_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(MainScreenTestTags.STAGNATION_BADGE),
                    )
                }
                if (healthStatus == HealthMonitor.Status.DEGRADED) {
                    Column(
                        modifier = Modifier.testTag(MainScreenTestTags.HEALTH_DEGRADED_BADGE),
                    ) {
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

            EngineChipsRow(
                selectedEngine = manualEngine,
                onSelect = onManualEngineSelect,
                modifier = Modifier.fillMaxWidth(),
            )

            BottomDock(
                tabs = commonDockTabs(),
                activeTabId = DOCK_TAB_HOME,
                onTabSelected = { id ->
                    when (id) {
                        DOCK_TAB_SERVERS -> onOpenEngineParams(manualEngine)
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
private fun commonDockTabs(): List<DockTab> {
    val labelHome = stringResource(R.string.tab_main)
    val labelServers = stringResource(R.string.tab_servers)
    val labelSettings = stringResource(R.string.tab_settings)
    return remember(labelHome, labelServers, labelSettings) {
        listOf(
            DockTab(DOCK_TAB_HOME, Icons.Filled.Home, labelHome),
            DockTab(DOCK_TAB_SERVERS, Icons.Filled.LocationOn, labelServers),
            DockTab(DOCK_TAB_SETTINGS, Icons.Filled.Settings, labelSettings),
        )
    }
}

private fun TunnelState.toPowerDiscState(): PowerDiscState = when (this) {
    is TunnelState.Connected -> PowerDiscState.Connected
    is TunnelState.Probing,
    is TunnelState.Connecting,
    is TunnelState.Disconnecting,
    -> PowerDiscState.Connecting
    is TunnelState.Idle, is TunnelState.Failed -> PowerDiscState.Off
}

private fun TunnelState.toBackgroundState(): OzeroBackgroundState = when (this) {
    is TunnelState.Connected -> OzeroBackgroundState.Connected
    is TunnelState.Probing,
    is TunnelState.Connecting,
    is TunnelState.Disconnecting,
    -> OzeroBackgroundState.Connecting
    is TunnelState.Idle, is TunnelState.Failed -> OzeroBackgroundState.Off
}

@Composable
private fun IpInfoCard(
    state: IpInfoState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
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
            Text(
                text = stringResource(R.string.ip_card_title),
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )
            when (state) {
                is IpInfoState.Idle, is IpInfoState.Loading -> Text(
                    text = stringResource(R.string.ip_card_loading),
                    style = MaterialTheme.typography.titleMedium,
                    color = OzeroPalette.Text,
                )
                is IpInfoState.Loaded -> {
                    Text(
                        text = state.info.ip,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = OzeroPalette.Text,
                    )
                    val country = state.info.country
                        ?: stringResource(R.string.ip_card_country_unknown)
                    val location = listOfNotNull(state.info.city, country).joinToString(", ")
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = OzeroPalette.Text3,
                    )
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
    stats: TunnelStats,
    speedHistory: List<Pair<Float, Float>> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stats.sessionStartMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val sessionMs = if (stats.sessionStartMs > 0L) nowMs - stats.sessionStartMs else 0L
    val rxSpeed = BytesFormatter.humanReadablePerSec(stats.bpsIn)
    val txSpeed = BytesFormatter.humanReadablePerSec(stats.bpsOut)
    val rxTotal = BytesFormatter.humanReadable(stats.rxBytes)
    val txTotal = BytesFormatter.humanReadable(stats.txBytes)
    val uptime = BytesFormatter.durationHms(sessionMs)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MainScreenTestTags.TRAFFIC_STATS),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.GlassFill),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "↓ $rxSpeed   ↑ $txSpeed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OzeroPalette.Text,
            )
            Text(
                text = stringResource(R.string.stats_uptime, uptime),
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )
            Spacer(modifier = Modifier.height(4.dp))
            LiveTrafficChart(
                history = speedHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
    modifier: Modifier = Modifier,
) {
    val colorRx = OzeroPalette.Aqua
    val colorTx = OzeroPalette.Amber
    val gridColor = OzeroPalette.Text3.copy(alpha = 0.25f)
    val labelColor = OzeroPalette.Text3
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxVal = remember(history) {
        if (history.isEmpty()) 0f else history.maxOf { maxOf(it.first, it.second) }
    }
    val maxLabel = if (maxVal > 0f) BytesFormatter.humanReadablePerSec(maxVal.toDouble()) else ""
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val gridStrokePx = with(density) { 1.dp.toPx() }
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = h * i / gridLines
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(w, y),
                strokeWidth = gridStrokePx,
            )
        }
        if (maxLabel.isNotEmpty()) {
            val measured = textMeasurer.measure(maxLabel, style = labelStyle)
            val labelPad = with(density) { 4.dp.toPx() }
            val tx = w - measured.size.width - labelPad
            drawText(
                textLayoutResult = measured,
                topLeft = androidx.compose.ui.geometry.Offset(tx.coerceAtLeast(0f), labelPad),
            )
        }
        if (history.size < 2 || maxVal <= 0f) return@Canvas
        val safeMax = maxVal.coerceAtLeast(1f)
        val step = w / (history.size - 1)
        val strokePx = with(density) { 2.dp.toPx() }
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val pathRx = Path()
        history.forEachIndexed { i, (rx, _) ->
            val x = i * step
            val y = h - (rx / safeMax) * h
            if (i == 0) pathRx.moveTo(x, y) else pathRx.lineTo(x, y)
        }
        drawPath(pathRx, color = colorRx, style = stroke)

        val pathTx = Path()
        history.forEachIndexed { i, (_, tx) ->
            val x = i * step
            val y = h - (tx / safeMax) * h
            if (i == 0) pathTx.moveTo(x, y) else pathTx.lineTo(x, y)
        }
        drawPath(pathTx, color = colorTx, style = stroke)
    }
}

@Composable
private fun StatusLabel(state: TunnelState) {
    val labelRes = when (state) {
        is TunnelState.Idle -> R.string.main_status_disconnected
        is TunnelState.Probing -> if (state.engineId == ru.ozero.enginescore.EngineId.WARP) {
            R.string.main_status_probing_warp
        } else {
            R.string.main_status_probing
        }
        is TunnelState.Connecting -> R.string.main_status_connecting
        is TunnelState.Connected -> R.string.main_status_connected
        is TunnelState.Failed -> R.string.main_status_failed
        is TunnelState.Disconnecting -> R.string.main_status_disconnecting
    }
    val engine = when (state) {
        is TunnelState.Connecting -> state.engineId.name
        is TunnelState.Connected -> state.engineId.name
        is TunnelState.Failed -> state.engineId.name
        else -> null
    }
    val failedReason = (state as? TunnelState.Failed)?.reason
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
private const val DOCK_TAB_SETTINGS = "settings"
