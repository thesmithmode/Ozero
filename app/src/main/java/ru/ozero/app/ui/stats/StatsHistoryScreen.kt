package ru.ozero.app.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.commonvpn.BytesFormatter
import ru.ozero.corestorage.entity.SessionStatsEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsHistoryScreen(
    onBack: () -> Unit,
    viewModel: StatsHistoryViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag("stats_history"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.stats_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = sessions, key = { it.id }) { session ->
                    SessionCard(session)
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionStatsEntity) {
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val started = dateFormat.format(Date(session.startedAt))
    val durationStr = BytesFormatter.durationHms(session.durationMs)
    val rxStr = BytesFormatter.humanReadable(session.rxBytes)
    val txStr = BytesFormatter.humanReadable(session.txBytes)
    val statusLabel = stringResource(statusLabelRes(session.finalStatus))
    Card(
        modifier = Modifier.fillMaxWidth().testTag("session_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "$started · ${session.engineId} · $durationStr",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.stats_history_traffic, rxStr, txStr),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.stats_history_status_label, statusLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusLabelRes(finalStatus: String): Int = when (finalStatus.lowercase(Locale.ROOT)) {
    "running" -> R.string.stats_history_status_running
    "failed" -> R.string.stats_history_status_failed
    else -> R.string.stats_history_status_disconnected
}
