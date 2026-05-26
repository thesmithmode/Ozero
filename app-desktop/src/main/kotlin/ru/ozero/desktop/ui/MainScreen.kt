package ru.ozero.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.ozero.desktop.model.AppMode
import ru.ozero.desktop.model.BytesFormatter
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.SpeedSample
import ru.ozero.desktop.model.SwitchingTransition
import ru.ozero.desktop.model.TunnelState
import ru.ozero.desktop.model.TunnelStats
import ru.ozero.desktop.model.bucketizeTimeAligned
import ru.ozero.desktop.strings.Strings
import ru.ozero.desktop.ui.components.BottomDock
import ru.ozero.desktop.ui.components.DockTab
import ru.ozero.desktop.ui.components.EngineChipsRow
import ru.ozero.desktop.ui.components.OzeroBackground
import ru.ozero.desktop.ui.components.OzeroBackgroundState
import ru.ozero.desktop.ui.components.PowerDisc
import ru.ozero.desktop.ui.components.PowerDiscState
import ru.ozero.desktop.ui.components.addSmooth
import ru.ozero.desktop.ui.components.chartNiceMax
import ru.ozero.desktop.ui.icons.OzeroIcons
import ru.ozero.desktop.ui.theme.OzeroPalette

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSplitTunnel: () -> Unit = {},
    onOpenEngineParams: (EngineId?) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val stagnant by viewModel.stagnant.collectAsState()
    val appMode by viewModel.appMode.collectAsState()
    val manualEngine by viewModel.manualEngine.collectAsState()
    val engineAutoPriority by viewModel.engineAutoPriority.collectAsState()
    val speedHistory by viewModel.speedHistory.collectAsState()
    val switching by viewModel.switching.collectAsState()
    val isReconnecting by viewModel.isReconnecting.collectAsState()
    val powerState by viewModel.powerDiscState.collectAsState()
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
                    tunnelState = state,
                    switching = switching,
                    powerState = powerState,
                    isConnected = isConnected,
                    isReconnecting = isReconnecting,
                    onConnectClick = onConnectClick,
                    onOpenSplitTunnel = onOpenSplitTunnel,
                    onOpenSettings = onOpenSettings,
                )
                AppMode.EXPERT -> ExpertMainContent(
                    tunnelState = state,
                    switching = switching,
                    stats = stats,
                    speedHistory = speedHistory,
                    stagnant = stagnant,
                    powerState = powerState,
                    isConnected = isConnected,
                    manualEngine = manualEngine,
                    engineAutoPriority = engineAutoPriority,
                    isReconnecting = isReconnecting,
                    onConnectClick = onConnectClick,
                    onManualEngineSelect = viewModel::onManualEngineSelect,
                    onOpenEngineParams = onOpenEngineParams,
                    onOpenSplitTunnel = onOpenSplitTunnel,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun SimpleMainContent(
    tunnelState: TunnelState,
    switching: SwitchingTransition?,
    powerState: PowerDiscState,
    isConnected: Boolean,
    isReconnecting: Boolean,
    onConnectClick: () -> Unit,
    onOpenSplitTunnel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        AnimatedContent(
            targetState = switching to tunnelState,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "status",
        ) { (sw, s) ->
            StatusLabel(s, sw, isReconnecting)
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
        ) {
            BottomDock(
                tabs = simpleDockTabs(),
                activeTabId = "home",
                onTabSelected = { id ->
                    when (id) {
                        "split_tunnel" -> onOpenSplitTunnel()
                        "settings" -> onOpenSettings()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ExpertMainContent(
    tunnelState: TunnelState,
    switching: SwitchingTransition?,
    stats: TunnelStats?,
    speedHistory: List<SpeedSample>,
    stagnant: Boolean,
    powerState: PowerDiscState,
    isConnected: Boolean,
    manualEngine: EngineId?,
    engineAutoPriority: List<EngineId>,
    isReconnecting: Boolean,
    onConnectClick: () -> Unit,
    onManualEngineSelect: (EngineId?) -> Unit,
    onOpenEngineParams: (EngineId?) -> Unit,
    onOpenSplitTunnel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
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
            StatusLabel(s, sw, isReconnecting)
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
            TrafficStatsCard(
                stats = stats,
                speedHistory = speedHistory,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (stagnant && isConnected) {
                Text(
                    text = Strings.MAIN_STAGNATION_WARNING,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            EngineChipsRow(
                selectedEngine = resolveUiSelectedEngine(tunnelState, switching, manualEngine),
                engineOrder = engineAutoPriority,
                onSelect = onManualEngineSelect,
                modifier = Modifier.fillMaxWidth(),
            )
            BottomDock(
                tabs = expertDockTabs(),
                activeTabId = "home",
                onTabSelected = { id ->
                    when (id) {
                        "servers" -> onOpenEngineParams(
                            resolveUiSelectedEngine(tunnelState, switching, manualEngine),
                        )
                        "split_tunnel" -> onOpenSplitTunnel()
                        "settings" -> onOpenSettings()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
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

private fun PowerDiscState.toBackgroundState(): OzeroBackgroundState = when (this) {
    PowerDiscState.Connected -> OzeroBackgroundState.Connected
    PowerDiscState.Connecting, PowerDiscState.Switching -> OzeroBackgroundState.Connecting
    PowerDiscState.Off -> OzeroBackgroundState.Off
}

@Composable
private fun StatusLabel(
    state: TunnelState,
    switching: SwitchingTransition? = null,
    isReconnecting: Boolean = false,
) {
    val label = pickStatusLabel(state, switching, isReconnecting)
    val engine = pickStatusEngine(state, switching)
    val failedReason = if (switching != null) null else (state as? TunnelState.Failed)?.reason
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
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
                text = Strings.MAIN_ENGINE_FAILED_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )
        }
    }
}

private fun pickStatusLabel(
    state: TunnelState,
    switching: SwitchingTransition?,
    isReconnecting: Boolean,
): String {
    if (switching != null) return Strings.MAIN_STATUS_SWITCHING
    return when (state) {
        is TunnelState.Idle -> Strings.MAIN_STATUS_DISCONNECTED
        is TunnelState.Probing -> if (isReconnecting) Strings.MAIN_STATUS_RECONNECTING else Strings.MAIN_STATUS_PROBING
        is TunnelState.Connecting -> if (isReconnecting) Strings.MAIN_STATUS_RECONNECTING else Strings.MAIN_STATUS_CONNECTING
        is TunnelState.Connected -> Strings.MAIN_STATUS_CONNECTED
        is TunnelState.Failed -> if (isReconnecting) Strings.MAIN_STATUS_RECONNECTING else Strings.MAIN_STATUS_FAILED
        is TunnelState.Disconnecting -> Strings.MAIN_STATUS_DISCONNECTING
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
private fun simpleDockTabs(): List<DockTab> = remember {
    listOf(
        DockTab("home", Icons.Filled.Home, Strings.TAB_MAIN),
        DockTab("split_tunnel", OzeroIcons.CallSplit, Strings.TAB_SPLIT_TUNNEL),
        DockTab("settings", Icons.Filled.Settings, Strings.TAB_SETTINGS),
    )
}

@Composable
private fun expertDockTabs(): List<DockTab> = remember {
    listOf(
        DockTab("home", Icons.Filled.Home, Strings.TAB_MAIN),
        DockTab("servers", Icons.Filled.LocationOn, Strings.TAB_SERVERS),
        DockTab("split_tunnel", OzeroIcons.CallSplit, Strings.TAB_SPLIT_TUNNEL),
        DockTab("settings", Icons.Filled.Settings, Strings.TAB_SETTINGS),
    )
}

@Composable
private fun TrafficStatsCard(
    stats: TunnelStats?,
    speedHistory: List<SpeedSample> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val sessionStartMs = stats?.sessionStartMs ?: 0L
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
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
        modifier = modifier.fillMaxWidth().testTag("traffic_stats"),
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
                    text = Strings.statsUptime(uptime),
                    style = MaterialTheme.typography.bodySmall,
                    color = OzeroPalette.Text3,
                )
            }
            LiveTrafficChart(
                history = displayHistory,
                selectedTf = selectedTf,
                modifier = Modifier.fillMaxWidth().height(96.dp),
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
                        label = { Text(tf.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "↓ $rxTotal", style = MaterialTheme.typography.bodySmall, color = OzeroPalette.Aqua)
                Text(text = "↑ $txTotal", style = MaterialTheme.typography.bodySmall, color = OzeroPalette.Amber)
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
        fontSize = TextUnit(8f, TextUnitType.Sp),
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
                for (i in 1 until 4) {
                    val y = h * i / 4
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), linePx)
                }
                for (i in 1 until 4) {
                    val x = w * i / 4
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

private enum class TimeframeOption(val label: String, val points: Int, val buckets: Int) {
    M1(Strings.CHART_TF_1MIN, 60, 60),
    M5(Strings.CHART_TF_5MIN, 300, 30),
    M30(Strings.CHART_TF_30MIN, 1_800, 30),
    H1(Strings.CHART_TF_1H, 3_600, 60),
}
