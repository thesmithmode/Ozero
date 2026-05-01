package ru.ozero.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import ru.ozero.app.ui.components.OzeroBackground
import ru.ozero.app.ui.components.OzeroBackgroundState
import ru.ozero.app.ui.components.PowerDisc
import ru.ozero.app.ui.components.PowerDiscState
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.commonvpn.BytesFormatter
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.TunnelState
import ru.ozero.commonvpn.TunnelStats

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenServers: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenSubs: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val stagnant by viewModel.stagnant.collectAsStateWithLifecycle()
    val healthStatus by viewModel.healthStatus.collectAsStateWithLifecycle()

    val powerState = state.toPowerDiscState()
    val backgroundState = state.toBackgroundState()
    val isConnected = state is TunnelState.Connected
    val statsValue = stats

    OzeroBackground(state = backgroundState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(targetState = state, label = "status") { s -> StatusLabel(s) }

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
                if (isConnected && statsValue != null) {
                    TrafficStatsCard(statsValue)
                    if (stagnant) {
                        Text(
                            text = stringResource(R.string.main_stagnation_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(MainScreenTestTags.STAGNATION_BADGE),
                        )
                    }
                    if (healthStatus == HealthMonitor.Status.DEGRADED) {
                        Text(
                            text = stringResource(R.string.main_health_degraded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(MainScreenTestTags.HEALTH_DEGRADED_BADGE),
                        )
                    }
                }
                BottomDock(
                    tabs = simpleDockTabs(),
                    activeTabId = DOCK_TAB_HOME,
                    onTabSelected = { id ->
                        when (id) {
                            DOCK_TAB_SERVERS -> onOpenServers()
                            DOCK_TAB_STATS -> onOpenStats()
                            DOCK_TAB_SUBS -> onOpenSubs()
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
private fun simpleDockTabs(): List<DockTab> = listOf(
    DockTab(DOCK_TAB_HOME, Icons.Filled.Home, stringResource(R.string.tab_main)),
    DockTab(DOCK_TAB_SERVERS, Icons.Filled.LocationOn, stringResource(R.string.tab_servers)),
    DockTab(DOCK_TAB_STATS, Icons.Filled.Info, stringResource(R.string.tab_stats)),
    DockTab(DOCK_TAB_SUBS, Icons.Filled.Star, stringResource(R.string.tab_subs)),
    DockTab(DOCK_TAB_SETTINGS, Icons.Filled.Settings, stringResource(R.string.tab_settings)),
)

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
private fun TrafficStatsCard(stats: TunnelStats) {
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
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MainScreenTestTags.TRAFFIC_STATS),
        colors = CardDefaults.cardColors(
            containerColor = OzeroPalette.GlassFill,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "↓ $rxSpeed   ↑ $txSpeed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = stringResource(R.string.stats_session, rxTotal, txTotal),
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text2,
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
private fun StatusLabel(state: TunnelState) {
    val labelRes = when (state) {
        is TunnelState.Idle -> R.string.main_status_disconnected
        is TunnelState.Probing -> R.string.main_status_probing
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
private const val DOCK_TAB_STATS = "stats"
private const val DOCK_TAB_SUBS = "subs"
private const val DOCK_TAB_SETTINGS = "settings"
