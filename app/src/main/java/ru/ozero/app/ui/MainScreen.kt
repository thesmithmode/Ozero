package ru.ozero.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.commonvpn.BytesFormatter
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.TunnelState
import ru.ozero.commonvpn.TunnelStats

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val stagnant by viewModel.stagnant.collectAsStateWithLifecycle()
    val healthStatus by viewModel.healthStatus.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag(MainScreenTestTags.OPEN_SETTINGS),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.tab_settings),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(targetState = state, label = "status") { s ->
                StatusLabel(s)
            }

            if (state is TunnelState.Connected && stats != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TrafficStatsCard(stats!!)
                if (stagnant) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.main_stagnation_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(MainScreenTestTags.STAGNATION_BADGE),
                    )
                }
                if (healthStatus == HealthMonitor.Status.DEGRADED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.main_health_degraded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(MainScreenTestTags.HEALTH_DEGRADED_BADGE),
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            when (state) {
                is TunnelState.Probing,
                is TunnelState.Connecting,
                is TunnelState.Disconnecting,
                -> {
                    val loadingDesc = stringResource(R.string.a11y_loading)
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp).semantics { contentDescription = loadingDesc },
                    )
                }
                else -> {
                    val isConnected = state is TunnelState.Connected
                    val buttonDesc = stringResource(
                        if (isConnected) R.string.a11y_disconnect_button else R.string.a11y_connect_button,
                    )
                    Button(
                        onClick = onConnectClick,
                        modifier = Modifier.semantics { contentDescription = buttonDesc },
                    ) {
                        Text(stringResource(if (isConnected) R.string.main_disconnect else R.string.main_connect))
                    }
                }
            }
        }
    }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "↓ $rxSpeed   ↑ $txSpeed",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.stats_session, rxTotal, txTotal),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.stats_uptime, uptime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
            style = MaterialTheme.typography.headlineMedium,
        )
        if (engine != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = engine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        if (failedReason != null && failedReason.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = failedReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(MainScreenTestTags.FAILED_REASON),
            )
        }
    }
}
