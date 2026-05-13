package ru.ozero.app.ui.settings.engines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.app.ui.theme.OzeroPalette
import ru.ozero.app.ui.utils.formatBytes
import ru.ozero.engineurnetwork.UrnetworkSdkBridge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrnetworkSharedTrafficScreen(
    onBack: () -> Unit,
    viewModel: UrnetworkSharedTrafficViewModel = hiltViewModel(),
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.urnetwork_shared_traffic_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoading && balance == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            } else if (balance == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = OzeroPalette.Bg1),
                ) {
                    Text(
                        text = stringResource(R.string.urnetwork_shared_traffic_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OzeroPalette.Text2,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                BalanceCard(balance = balance!!)
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: UrnetworkSdkBridge.SubscriptionBalanceSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OzeroPalette.Bg1),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            BalanceRow(
                label = stringResource(R.string.urnetwork_shared_traffic_pending),
                value = formatBytes(balance.pendingBytes),
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = OzeroPalette.Line,
            )
            BalanceRow(
                label = stringResource(R.string.urnetwork_shared_traffic_balance),
                value = formatBytes(balance.balanceBytes),
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = OzeroPalette.Line,
            )
            BalanceRow(
                label = stringResource(R.string.urnetwork_shared_traffic_used),
                value = formatBytes(balance.usedBytes.coerceAtLeast(0L)),
            )
            if (balance.plan != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = OzeroPalette.Line,
                )
                BalanceRow(
                    label = stringResource(R.string.urnetwork_shared_traffic_plan),
                    value = balance.plan,
                )
            }
        }
    }
}

@Composable
private fun BalanceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Text2,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = OzeroPalette.Text,
        )
    }
}
