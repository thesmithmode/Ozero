package ru.ozero.app.ui.strategy

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyTestScreen(
    onBack: () -> Unit,
    viewModel: StrategyTestViewModel = hiltViewModel(),
) {
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val strategies by viewModel.strategies.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler {
        if (isRunning) viewModel.onStop() else onBack()
    }
    val errorText = when (errorMessage) {
        StrategyTestError.VpnRunning -> stringResource(R.string.strategy_test_vpn_running_error)
        StrategyTestError.NoSites -> stringResource(R.string.strategy_test_no_sites)
        null -> null
    }
    if (errorText != null) {
        AlertDialog(
            modifier = Modifier.testTag("strategy_test_error_dialog"),
            onDismissRequest = viewModel::onErrorDismiss,
            title = { Text(stringResource(R.string.error_generic)) },
            text = { Text(errorText) },
            confirmButton = {
                TextButton(onClick = viewModel::onErrorDismiss) {
                    Text(stringResource(R.string.strategy_test_error_close))
                }
            },
        )
    }
    Scaffold(
        modifier = Modifier.testTag("strategy_test_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.strategy_test_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = {
                    if (isRunning) {
                        TextButton(
                            onClick = viewModel::onStop,
                            modifier = Modifier.testTag("strategy_test_stop_btn"),
                        ) {
                            Text(stringResource(R.string.strategy_test_stop))
                        }
                    } else {
                        TextButton(
                            onClick = viewModel::onStart,
                            modifier = Modifier.testTag("strategy_test_start_btn"),
                        ) {
                            Text(stringResource(R.string.strategy_test_start))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            itemsIndexed(
                items = strategies,
                key = { index, item -> item.command + "_" + index },
            ) { index, item ->
                StrategyRow(
                    index = index,
                    item = item,
                    onApply = {
                        viewModel.onApply(item.command)
                        Toast.makeText(
                            context,
                            R.string.strategy_test_applied_toast,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun StrategyRow(
    index: Int,
    item: StrategyResult,
    onApply: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("strategy_item_$index"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.totalRequests > 0 && !item.isCompleted) {
                val progress = item.currentProgress.toFloat() / item.totalRequests.toFloat()
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (item.isCompleted) {
                        "${item.successCount}/${item.totalRequests} · ${item.successPercentage}%"
                    } else if (item.totalRequests > 0) {
                        "${item.currentProgress}/${item.totalRequests}"
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onApply,
                    modifier = Modifier.testTag("strategy_apply_$index"),
                ) {
                    Text(stringResource(R.string.strategy_test_apply))
                }
            }
        }
    }
}
